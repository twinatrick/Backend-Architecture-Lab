"""僅查詢官方 PyPI 與白名單 PyTorch 索引，不下載或執行套件。"""

from html.parser import HTMLParser
import json
from pathlib import PurePosixPath
import re
from typing import Any
from urllib.parse import quote, unquote, urljoin, urlsplit

import requests

from review_snapshot import InspectionStatus, ReviewSnapshot, RuleInspectionResult, RuleInspector

RULE_PACKAGE_VERSION_EXISTS = "RULE_PACKAGE_VERSION_EXISTS"
PIN_RE = re.compile(r"^([A-Za-z0-9][A-Za-z0-9._-]*)==([A-Za-z0-9][A-Za-z0-9.!+_-]*)$")
INDEX_RE = re.compile(r"https://download\.pytorch\.org/whl/[A-Za-z0-9._-]+/?")


class WheelLinks(HTMLParser):
    """只擷取連結資料；不跟隨連結或執行索引內容。"""

    def __init__(self) -> None:
        super().__init__()
        self.links: list[tuple[str, bool]] = []

    def handle_starttag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        attributes = dict(attrs)
        if tag == "a" and attributes.get("href"):
            self.links.append((attributes["href"], "data-yanked" in attributes))


def official_indexes(lines: list[str]) -> list[str] | None:
    """拒絕未支援的來源語法；查詢 URL 只由固定官方來源建構。"""
    indexes: list[str] = []
    for raw in lines:
        text = raw.split("#", 1)[0].strip()
        if not text:
            continue
        option = re.fullmatch(r"--extra-index-url(?:\s+|=)(\S+)", text)
        if option and INDEX_RE.fullmatch(option[1]):
            channel = option[1].rstrip("/").rsplit("/", 1)[-1]
            if channel in (".", ".."):
                return None
            indexes.append(option[1].rstrip("/"))
        elif text.startswith("-") or "${" in text or "://" in text:
            return None
    unique = list(dict.fromkeys(indexes))
    return unique if len(unique) <= 4 else None


class PackageVersionInspector(RuleInspector):
    """存在性只回答指定官方來源是否提供版本，不證明安全性或環境相容。"""

    rule_name = RULE_PACKAGE_VERSION_EXISTS

    def __init__(self, get_fn: Any = None) -> None:
        self.getter = get_fn or requests.get

    def inspect(self, finding: dict[str, Any], snapshot: ReviewSnapshot) -> RuleInspectionResult:
        path, line = snapshot.parse_location(str(finding.get("location", "")))
        location = f"{path}:{line}"

        def result(status: InspectionStatus, reason: str,
                   snippet: str = "") -> RuleInspectionResult:
            return RuleInspectionResult(status, self.rule_name, location, reason,
                                        verified_snippet=snippet)

        name = PurePosixPath(path).name
        if not (name.startswith("requirements") and name.endswith(".txt")):
            return result(InspectionStatus.INCONCLUSIVE, "第一版僅支援 requirements*.txt 精確版本。")
        source = snapshot.get_file_content(path)
        if not source:
            return RuleInspectionResult(
                InspectionStatus.INCONCLUSIVE, self.rule_name, location,
                "缺少固定 SHA 的依賴檔。", missing_context=(f"{path}:full_content",),
            )
        lines = source.splitlines()
        if not 1 <= line <= len(lines):
            return result(InspectionStatus.INCONCLUSIVE, "指定依賴行不存在。")
        declaration = lines[line - 1].split("#", 1)[0].strip()
        match = PIN_RE.fullmatch(declaration)
        if not match:
            return result(InspectionStatus.INCONCLUSIVE, "需單一精確版本；區間、標記與直接 URL 尚未支援。")
        indexes = official_indexes(lines)
        if indexes is None:
            return result(InspectionStatus.INCONCLUSIVE, "含自訂來源或巢狀依賴，尚未確認有效索引。")
        declarations = [declaration]
        evidence = str(finding.get("evidence", "")).strip()
        if evidence:
            candidates = [text.strip() for text in evidence.splitlines() if text.strip()]
            actual = {text.split("#", 1)[0].strip() for text in lines}
            if (len(candidates) > 8 or declaration not in candidates
                    or any(not PIN_RE.fullmatch(text) or text not in actual
                           for text in candidates)):
                return result(InspectionStatus.INCONCLUSIVE, "套件證據無法完整對應精確版本宣告。")
            declarations = list(dict.fromkeys(candidates))
        proofs: list[str] = []
        for pin in declarations:
            proof = self._lookup(pin, indexes)
            if proof is None:
                return result(InspectionStatus.INCONCLUSIVE, "官方索引證據不足，不能判定版本不存在。")
            proofs.append(pin + "\n" + json.dumps(proof, ensure_ascii=False))
        return result(
            InspectionStatus.COMPLIANT,
            "官方索引有全部指定版本及發布檔案，版本不存在主張不成立；不代表無漏洞、未撤回或相容。",
            "\n".join(proofs),
        )

    def _lookup(self, declaration: str, indexes: list[str]) -> dict[str, Any] | None:
        """僅正向證明存在；查詢失敗或缺項不推論不存在。"""
        package, version = declaration.split("==", 1)
        canonical = re.sub(r"[-_.]+", "-", package).lower()
        if "+" in version and indexes:
            return self._lookup_wheels(canonical, version, indexes)
        url = f"https://pypi.org/pypi/{quote(canonical, safe='')}/{quote(version, safe='')}/json"
        try:
            response = self.getter(url, timeout=8, allow_redirects=False)
            if response.status_code == 404:
                return self._lookup_wheels(canonical, version, indexes)
            if response.status_code != 200:
                return None
            data = response.json()
            info = data.get("info") if isinstance(data, dict) else None
            urls = data.get("urls") if isinstance(data, dict) else None
            if (not isinstance(info, dict) or not isinstance(info.get("name"), str)
                    or re.sub(r"[-_.]+", "-", info["name"]).lower() != canonical
                    or info.get("version") != version or not isinstance(urls, list) or not urls
                    or any(not isinstance(item, dict) or not isinstance(item.get("filename"), str)
                           or type(item.get("yanked")) is not bool for item in urls)):
                return None
            return {"source": url, "name": info["name"], "version": version,
                                   "files": [{"filename": item["filename"],
                                               "yanked": item["yanked"]} for item in urls]}
        except (requests.RequestException, ValueError, TypeError):
            return None

    def _lookup_wheels(self, package: str, version: str,
                       indexes: list[str]) -> dict[str, Any] | None:
        """比對官方 HTML 的 wheel 檔名、套件及完整版本（含 local 標籤）。"""
        for index in indexes:
            url = f"{index}/{quote(package, safe='')}/"
            try:
                response = self.getter(url, timeout=8, allow_redirects=False)
                if response.status_code != 200:
                    continue
                parser = WheelLinks()
                parser.feed(response.text)
                for href, yanked in parser.links:
                    target = urlsplit(urljoin(url, href))
                    if (target.scheme != "https"
                            or target.netloc not in ("download.pytorch.org",
                                                     "download-r2.pytorch.org")
                            or not target.path.startswith(urlsplit(index).path + "/")):
                        continue
                    filename = unquote(target.path.rsplit("/", 1)[-1])
                    parts = filename[:-4].split("-") if filename.endswith(".whl") else []
                    if (len(parts) in (5, 6)
                            and re.sub(r"[-_.]+", "-", parts[0]).lower() == package
                            and parts[1] == version and all(parts)):
                        return {"source": url, "name": package, "version": version,
                                "files": [{"filename": filename, "yanked": yanked}]}
            except (requests.RequestException, ValueError, TypeError):
                continue
        return None

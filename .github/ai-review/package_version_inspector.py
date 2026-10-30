"""僅查詢官方 PyPI 中固定來源的精確版本，不下載或執行套件。"""

import json
from pathlib import PurePosixPath
import re
from typing import Any
from urllib.parse import quote

import requests

from review_snapshot import InspectionStatus, ReviewSnapshot, RuleInspectionResult, RuleInspector

RULE_PACKAGE_VERSION_EXISTS = "RULE_PACKAGE_VERSION_EXISTS"
PIN_RE = re.compile(r"^([A-Za-z0-9][A-Za-z0-9._-]*)==([A-Za-z0-9][A-Za-z0-9.!+_-]*)$")


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
        if source is None:
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
        # 來源旗標、巢狀清單及變數可能改變解析來源；不得據公開索引缺項判定私有套件不存在。
        custom_source = any(
            text.strip() and not text.lstrip().startswith("#") and (
                text.lstrip().startswith("-") or "${" in text or "://" in text
            ) for text in lines
        )
        if custom_source:
            return result(InspectionStatus.INCONCLUSIVE, "含自訂來源或巢狀依賴，尚未確認有效索引。")
        package, version = match.groups()
        canonical = re.sub(r"[-_.]+", "-", package).lower()
        url = f"https://pypi.org/pypi/{quote(canonical, safe='')}/{quote(version, safe='')}/json"
        try:
            response = self.getter(url, timeout=8, allow_redirects=False)
            if response.status_code == 404:
                return result(
                    InspectionStatus.INCONCLUSIVE,
                    "官方 PyPI 目前找不到此版本；尚未證明 CI 未使用外部私有索引設定。",
                    declaration,
                )
            if response.status_code != 200:
                return result(InspectionStatus.INCONCLUSIVE, "官方索引不可用，不能判定版本不存在。")
            data = response.json()
            info = data.get("info") if isinstance(data, dict) else None
            urls = data.get("urls") if isinstance(data, dict) else None
            if (not isinstance(info, dict) or not isinstance(info.get("name"), str)
                    or re.sub(r"[-_.]+", "-", info["name"]).lower() != canonical
                    or info.get("version") != version or not isinstance(urls, list) or not urls
                    or any(not isinstance(item, dict) or not isinstance(item.get("filename"), str)
                           or type(item.get("yanked")) is not bool for item in urls)):
                return result(InspectionStatus.INCONCLUSIVE, "索引證據不完整或名稱／版本不符。")
            evidence = json.dumps({"source": url, "name": info["name"], "version": version,
                                   "files": [{"filename": item["filename"],
                                              "yanked": item["yanked"]} for item in urls]},
                                  ensure_ascii=False)
            return result(
                InspectionStatus.COMPLIANT,
                "官方 PyPI 有此版本及發布檔案，版本不存在主張不成立；不代表無漏洞、未撤回或相容。",
                declaration + "\n" + evidence,
            )
        except (requests.RequestException, ValueError, TypeError):
            return result(InspectionStatus.INCONCLUSIVE, "官方索引查詢或資料解析失敗。")

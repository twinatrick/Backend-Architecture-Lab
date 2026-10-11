import os
import re
import subprocess
from typing import Any

from check_rules import (
    FOREIGN_ENTITIES,
    JPA_BUILTIN_METHODS,
    RULE_DEAD_CODE_REPOSITORY,
    RULE_DERIVED_DELETE_TRANSACTIONAL,
    RULE_IAM_UNIDIRECTIONAL,
    RULE_MICROSERVICE_ISOLATION,
    RULE_PROHIBIT_SELF_FEIGN,
    RULE_WEAK_TYPED_COLLECTION,
    SERVICE_MODULES,
    make_finding,
)

WEAK_MAP_METHOD_PATTERN = re.compile(
    r"(?:public\s+|protected\s+)?(?:<[^>]+>\s+)?(?:ResponseEntity<|ResponseType<|"
    r"CompletableFuture<)?\s*Map\s*<\s*(?:Object\s*,\s*[^>]+|(?:Object|\?|String)\s*,\s*"
    r"(?:Object|\?))\s*>(?:>)?\s+[a-zA-Z_]\w*\s*\("
)


def detect_module(path: str) -> str:
    norm_path = path.replace("\\", "/").lower()
    for mod in SERVICE_MODULES:
        if mod in norm_path:
            return mod
    return ""


def check_cross_module(
    path: str, line: str, idx: int, mod: str
) -> dict[str, Any] | None:
    if not mod or not line.strip().startswith("import "):
        return None
    is_entity_or_repo = ".Entity." in line or ".Repository." in line
    if not is_entity_or_repo:
        return None

    forbidden = FOREIGN_ENTITIES.get(mod, ())
    if any(target in line for target in forbidden):
        return make_finding(
            path, idx, "HIGH", "ARCHITECTURE",
            RULE_MICROSERVICE_ISOLATION,
            f"微服務 {mod} 中直接引用外部微服務之 Entity 或 Repository",
            line.strip(),
            "破壞微服務資料庫獨立性與資料存取邊界",
            "微服務間嚴禁共用或直接操作 Entity，應透過 Feign Client 呼叫 API",
        )
    return None


def check_self_feign_block(
    path: str, content: str, mod: str, changed_lines: set[int] | None
) -> list[dict[str, Any]]:
    findings: list[dict[str, Any]] = []
    if not mod or "@FeignClient" not in content:
        return findings
    feign_pattern = re.compile(
        r'@FeignClient\s*\((?:[^)]*?\b(?:name|value)\s*=\s*["\']([^"\']+)["\']|'
        r'["\']([^"\']+)["\'])',
        re.DOTALL,
    )
    for match in feign_pattern.finditer(content):
        target_client = match.group(1) or match.group(2) or ""
        if (
            target_client == mod
            or target_client == mod.replace("backend-", "")
            or f":{mod}" in target_client
        ):
            start_pos = match.start()
            end_pos = match.end()
            start_line = content[:start_pos].count("\n") + 1
            end_line = content[:end_pos].count("\n") + 1
            scope_lines = range(start_line, end_line + 1)
            if changed_lines is None or any(line_num in changed_lines for line_num in scope_lines):
                findings.append(make_finding(
                    path, start_line, "HIGH", "ARCHITECTURE",
                    RULE_PROHIBIT_SELF_FEIGN,
                    f"微服務 {mod} 內部宣告了指向自身的 Feign Client: {target_client}",
                    match.group(0).strip()[:100],
                    "自我 Feign 呼叫增加非必要網路開銷並繞過事務隔離",
                    "移除自我 Feign，改用 Spring @Lazy 介面自我代理處理 @Transactional/@Caching",
                ))
    return findings


def check_iam_reverse_dependency(
    path: str, line: str, idx: int, mod: str
) -> dict[str, Any] | None:
    if mod != "backend-iam-service" or not line.strip().startswith("import "):
        return None
    if any(
        business in line
        for business in (
            "CompetencyFeignClient", "SkillFeignClient", "AlertFeignClient",
            "JobFeignClient", "JobPostingFeignClient",
        )
    ):
        return make_finding(
            path, idx, "HIGH", "ARCHITECTURE",
            RULE_IAM_UNIDIRECTIONAL,
            "IAM 基礎服務中反向引用業務微服務之 FeignClient",
            line.strip(),
            "違反 IAM 作為基礎核心服務的單向依賴架構，造成微服務循環依賴",
            "移除 IAM 對業務微服務的依賴，由業務微服務單向依賴 IAM",
        )
    return None


def default_repo_searcher(method_name: str, repo_path: str) -> list[str]:
    """使用 git grep 檢索方法名稱於專案中之非宣告處引用檔案。"""
    try:
        res = subprocess.run(
            ["git", "grep", "-l", "-w", method_name],
            capture_output=True,
            text=True,
            encoding="utf-8",
            errors="replace",
            check=False,
        )
        if res.returncode == 0:
            norm_repo_path = repo_path.replace("\\", "/").lstrip("./")
            matches = [
                line.strip().replace("\\", "/").lstrip("./")
                for line in res.stdout.splitlines()
                if line.strip()
            ]
            return [
                matched_path
                for matched_path in matches
                if not (
                    matched_path == norm_repo_path
                    or matched_path.endswith("/" + norm_repo_path)
                    or norm_repo_path.endswith("/" + matched_path)
                )
            ]
        return []
    except (subprocess.SubprocessError, OSError):
        return []


def extract_interface_methods(content: str) -> list[tuple[int, int, str, str]]:
    """抽取 Java interface 中宣告的抽象方法 (start_line, end_line, method_name, statement)"""
    methods: list[tuple[int, int, str, str]] = []
    lines = content.splitlines()
    in_block_comment = False
    buffer = ""
    start_line = 1

    for idx, raw_line in enumerate(lines, start=1):
        line = raw_line.strip()
        if in_block_comment:
            if "*/" in line:
                in_block_comment = False
                line = line.split("*/", 1)[1].strip()
            else:
                continue
        if "/*" in line:
            if "*/" in line:
                line = re.sub(r"/\*.*?\*/", "", line).strip()
            else:
                in_block_comment = True
                line = line.split("/*", 1)[0].strip()
        if "//" in line:
            line = line.split("//", 1)[0].strip()

        if not line:
            continue

        if not buffer:
            start_line = idx
        buffer += (" " if buffer else "") + line

        if ";" in buffer:
            stmt = buffer.strip()
            buffer = ""
            if stmt.startswith(("package ", "import ", "default ", "static ")):
                continue
            clean_stmt = re.sub(r"@\w+(?:\([^)]*\))?", "", stmt).strip()
            match = re.search(
                r"^(?:public\s+)?(?:<[^>]+>\s+)?[\w<>,.?\[\]]+\s+([a-zA-Z_]\w*)\s*\(",
                clean_stmt,
            )
            if match:
                method_name = match.group(1)
                methods.append((start_line, idx, method_name, stmt))
        elif "{" in buffer or "}" in buffer:
            buffer = ""

    return methods


def has_method_transactional(lines: list[str], idx: int) -> bool:
    """檢查方法定義上方之註解是否包含 @Transactional"""
    for prev_idx in range(idx - 2, -1, -1):
        prev_line = lines[prev_idx].strip()
        if prev_line.startswith("@Transactional"):
            return True
        if not prev_line.startswith("@") and prev_line:
            break
    return False


def check_repository_dead_code(
    path: str,
    content: str,
    changed_lines: set[int] | None = None,
    repo_searcher: Any | None = None,
) -> list[dict[str, Any]]:
    """檢索 Repository 介面中的孤島死代碼方法。"""
    findings: list[dict[str, Any]] = []
    filename = os.path.basename(path)
    is_test = "src/test/java/" in path.replace("\\", "/")
    is_repo = (
        "Repository" in filename
        and filename.endswith(".java")
        and "interface " in content
        and not is_test
    )
    if not is_repo:
        return findings

    searcher = repo_searcher or default_repo_searcher
    repo_methods = extract_interface_methods(content)
    for start_line, end_line, method_name, raw_stmt in repo_methods:
        if changed_lines is not None and not any(
            line_no in changed_lines for line_no in range(start_line, end_line + 1)
        ):
            continue
        if method_name in JPA_BUILTIN_METHODS:
            continue
        callers = searcher(method_name, path)
        if not callers:
            findings.append(make_finding(
                path, start_line, "HIGH", "ARCHITECTURE",
                RULE_DEAD_CODE_REPOSITORY,
                f"Repository 介面宣告之方法 `{method_name}` 於專案中無任何呼叫端（孤島死代碼）",
                raw_stmt.strip(),
                "殘留廢棄方法違反 YAGNI 原則與程式碼精簡標準，增加無效維護成本",
                f"移除 Repository 中未被使用的 `{method_name}` 方法，或補齊呼叫端與單元測試",
            ))
    return findings


def check_derived_delete_transaction(
    path: str,
    raw_line: str,
    line: str,
    idx: int,
    lines: list[str],
    has_class_tx: bool,
) -> dict[str, Any] | None:
    """檢查 DataAccessImpl 與 ServiceImpl 衍生刪除方法是否具備事務宣告。"""
    del_match = re.search(
        r"^\s*(?:public|protected)\s+[\w<>,.?\[\]]+\s+"
        r"((?:deleteBy(?!Id\b)|removeBy(?!Id\b))\w*)\s*\(",
        line,
    )
    if not del_match:
        return None
    del_method_name = del_match.group(1)
    has_tx = has_class_tx or has_method_transactional(lines, idx) or "@Transactional" in line
    if not has_tx:
        return make_finding(
            path, idx, "HIGH", "ARCHITECTURE",
            RULE_DERIVED_DELETE_TRANSACTIONAL,
            f"衍生刪除方法 `{del_method_name}` 缺少 @Transactional 交易邊界宣告",
            raw_line.strip(),
            "Spring Data JPA 衍生刪除必須在活躍交易內執行，否則將拋出 TransactionRequiredException",
            "在方法或類別上標註 @Transactional 確保具備交易邊界保護",
        )
    return None


def check_weak_typed_map(
    path: str,
    raw_line: str,
    line: str,
    idx: int,
) -> dict[str, Any] | None:
    """檢查方法簽名回傳是否含有弱型別 Map。"""
    if re.search(r"^\s*private\s+", line):
        return None
    if WEAK_MAP_METHOD_PATTERN.search(line):
        return make_finding(
            path, idx, "HIGH", "ARCHITECTURE",
            RULE_WEAK_TYPED_COLLECTION,
            "方法簽名回傳了弱型別 Map（如 Map<Object, Object>），違反強型別契約紀律",
            raw_line.strip(),
            "弱型別集合破壞 API 與 Service 契約清晰度，增加維護負擔與型別轉換異常風險",
            "建立強型別 Record 或 Vo 取代動態 Map 結構，維持強型別架構一致性",
        )
    return None

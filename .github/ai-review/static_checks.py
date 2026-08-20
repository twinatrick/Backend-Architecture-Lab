import os
import re
from pathlib import Path
from typing import Any

BANNED_PERMISSIONS = (
    "PersonalEdit",
    "EditAll",
    "SkillManagement",
    "RolePermission",
    "AquarkDataAvg",
    "LimitSetting",
)


def check_workflow_file(path: str, content: str) -> list[dict[str, Any]]:
    """針對 GitHub Actions Workflow 檔案進行確定性安全檢查。"""
    findings: list[dict[str, Any]] = []

    # 1. 檢查 pull_request_target 搭配動態 checkout
    if "pull_request_target" in content:
        has_checkout = "actions/checkout" in content
        has_dynamic_ref = re.search(
            r"ref:\s*['\"]?\$\{\{\s*github\.event\.pull_request\.head",
            content,
        )
        if has_checkout and has_dynamic_ref:
            findings.append({
                "location": f"{path}:1",
                "severity": "HIGH",
                "confidence": "HIGH",
                "rule": "開發規範 §2 CI 信任邊界防護",
                "problem": "pull_request_target 搭配檢出不信任的 PR 程式碼存在 RCE 風險",
                "evidence": "發現 pull_request_target 搭配動態 ref checkout",
                "risk": "攻擊者可透過 PR 注入惡意程式碼並讀取 Repository Secrets",
                "recommendation": "改用 workflow_run 機制或移除動態 untrusted ref checkout",
            })

    # 2. 檢查 run 腳本中的直接表達式注入
    lines = content.splitlines()
    for idx, raw_line in enumerate(lines, start=1):
        line = raw_line.lstrip("+- ")
        if re.search(
            r"\$\{\{\s*github\.event\.(?:issue|pull_request|comment)\.",
            line,
        ):
            findings.append({
                "location": f"{path}:{idx}",
                "severity": "HIGH",
                "confidence": "HIGH",
                "rule": "開發規範 §2 CI 腳本表達式注入防護",
                "problem": "在腳本中直接內嵌 github.event 上下文表達式",
                "evidence": raw_line.strip()[:100],
                "risk": "攻擊者可構造特殊 PR 標題或內容進行 Bash 命令注入",
                "recommendation": "將 github.event 參數映射至 env 變數後於腳本使用",
            })

    # 3. 檢查 permissions: write-all
    if "permissions: write-all" in content:
        findings.append({
            "location": f"{path}:1",
            "severity": "MEDIUM",
            "confidence": "HIGH",
            "rule": "開發規範 §2 CI 最小權限原則",
            "problem": "Workflow 宣告 permissions: write-all",
            "evidence": "permissions: write-all",
            "risk": "授予 Workflow 過多非必要權限，增加 Token 洩漏風險",
            "recommendation": "依據 Job 實際需求宣告最小必要權限",
        })

    return findings


def check_java_file(path: str, content: str) -> list[dict[str, Any]]:
    """針對 Java Controller 與 Service 實作進行確定性架構規範檢查。"""
    findings: list[dict[str, Any]] = []
    filename = os.path.basename(path)
    lines = content.splitlines()
    is_test_file = "src/test/java/" in path.replace("\\", "/")

    is_controller = "Controller" in filename or "@RestController" in content
    is_service_impl = (
        "ServiceImpl" in filename
        or "/Service/Impl/" in path.replace("\\", "/")
    )

    for idx, raw_line in enumerate(lines, start=1):
        line = raw_line.lstrip("+- ")

        # 檢查禁用權限字串
        for banned in BANNED_PERMISSIONS:
            if banned in line and ("@RequirePermission" in line or "hasAuthority" in line):
                findings.append({
                    "location": f"{path}:{idx}",
                    "severity": "HIGH",
                    "confidence": "HIGH",
                    "rule": "開發規範 §2 權限字典與禁用字串規範",
                    "problem": f"使用了已被廢棄或禁用的權限字串: {banned}",
                    "evidence": raw_line.strip()[:100],
                    "risk": "使用非標準權限名稱將導致 RBAC 權限失效或鑑權失敗",
                    "recommendation": "依據《開發規範.md》§2 權限字典替換為標準權限命名",
                })

        # 生產程式碼禁止 @Autowired 欄位注入
        if not is_test_file and re.search(r"@Autowired\b", line):
            if "required = false" not in line and "required=false" not in line:
                findings.append({
                    "location": f"{path}:{idx}",
                    "severity": "HIGH",
                    "confidence": "HIGH",
                    "rule": "開發規範 §1.4 依賴注入規範",
                    "problem": "生產程式碼中嚴禁使用 @Autowired 進行欄位注入",
                    "evidence": raw_line.strip()[:100],
                    "risk": "欄位注入隱藏依賴且不利於單元測試",
                    "recommendation": "全面採用 Lombok @RequiredArgsConstructor 搭配 private final",
                })

    if is_controller:
        for idx, raw_line in enumerate(lines, start=1):
            line = raw_line.lstrip("+- ")

            # Controller OpenAPI 原生註解檢查
            if re.search(r"@Operation\(", line):
                findings.append({
                    "location": f"{path}:{idx}",
                    "severity": "MEDIUM",
                    "confidence": "HIGH",
                    "rule": "開發規範 §3 OpenAPI 標註規範",
                    "problem": "Controller 應使用專案封裝之 OpenApi 註解取代原生 @Operation",
                    "evidence": raw_line.strip()[:100],
                    "risk": "缺少統一的 API 響應結構與錯誤碼說明",
                    "recommendation": "使用 @OpenApiCommonResponse 或專案標準註解封裝",
                })

            # Controller 禁止注入 EntityManager, Repository, Mapper
            if re.search(r"private\s+final\s+.*EntityManager\b", line):
                findings.append({
                    "location": f"{path}:{idx}",
                    "severity": "HIGH",
                    "confidence": "HIGH",
                    "rule": "開發規範 §1.3 Controller 與資料層隔離規範",
                    "problem": "Controller 嚴禁直接注入 EntityManager",
                    "evidence": raw_line.strip()[:100],
                    "risk": "破壞 Controller/Service/DataAccess 分層架構",
                    "recommendation": "移除 EntityManager，僅透過 Service 介面操作",
                })
            if re.search(r"private\s+final\s+.*(?:Repository|Mapper)\b", line):
                findings.append({
                    "location": f"{path}:{idx}",
                    "severity": "HIGH",
                    "confidence": "HIGH",
                    "rule": "開發規範 §1.3 Controller 依賴規範",
                    "problem": "Controller 嚴禁直接注入 Repository 或 Mapper",
                    "evidence": raw_line.strip()[:100],
                    "risk": "違反 MVC 分層職責與資料隔離",
                    "recommendation": "Controller 僅可注入 Service 介面，透過 Vo 進行互動",
                })

            # Controller 嚴禁直接回傳 Entity
            if re.search(r"public\s+(?:ResponseEntity<)?\w+Entity(?:>)?\s+\w+\(", line):
                findings.append({
                    "location": f"{path}:{idx}",
                    "severity": "HIGH",
                    "confidence": "HIGH",
                    "rule": "開發規範 §1.3 Entity 使用規範",
                    "problem": "Controller 方法回傳型別包含 Entity",
                    "evidence": raw_line.strip()[:100],
                    "risk": "Entity 洩漏至 API 對外介面，破壞封裝",
                    "recommendation": "將回傳型別轉換為 Vo",
                })

    if is_service_impl:
        for idx, raw_line in enumerate(lines, start=1):
            line = raw_line.lstrip("+- ")
            if re.search(r"private\s+final\s+.*EntityManager\b", line):
                findings.append({
                    "location": f"{path}:{idx}",
                    "severity": "HIGH",
                    "confidence": "HIGH",
                    "rule": "開發規範 §1.1 Service 禁止操作 EntityManager",
                    "problem": "Service Impl 禁止直接注入 EntityManager",
                    "evidence": raw_line.strip()[:100],
                    "risk": "違反資料存取抽象化規範",
                    "recommendation": "將資料庫操作封裝至 DataAccess 或 Repository",
                })

    return findings


def check_python_file(path: str, content: str) -> list[dict[str, Any]]:
    """針對 Python 原始碼進行單檔行數、單行長度與例外捕捉靜態檢查。"""
    findings: list[dict[str, Any]] = []
    lines = content.splitlines()

    # 1. 檢查單檔總行數上限 (LOC)
    if len(lines) > 300:
        findings.append({
            "location": f"{path}:1",
            "severity": "HIGH",
            "confidence": "HIGH",
            "rule": "開發規範 §4.3 單一職責與單檔行數限制",
            "problem": f"Python 檔案總行數 ({len(lines)}) 超過 300 行上限",
            "evidence": f"Total lines: {len(lines)}",
            "risk": "God Module 難以維護且違反 SRP 原則",
            "recommendation": "依職責拆分模組至獨立檔案 (< 300 行)",
        })

    # 2. 檢查單行長度上限與泛型 Exception 捕捉
    for idx, raw_line in enumerate(lines, start=1):
        line = raw_line.lstrip("+- ")

        # 檢查單行長度上限（排除長 URL 或純註解）
        if len(raw_line) > 100 and not raw_line.strip().startswith("#"):
            findings.append({
                "location": f"{path}:{idx}",
                "severity": "MEDIUM",
                "confidence": "HIGH",
                "rule": "開發規範 §4.2 程式碼格式與行長規範",
                "problem": f"單行長度 ({len(raw_line)}) 超過 100 字元上限",
                "evidence": raw_line[:90] + "...",
                "risk": "降低程式碼可讀性與審查效率",
                "recommendation": "適當換行拆分表達式，確保行長 <= 100 字元",
            })

        if re.search(r"^\s*except\s*:\s*$", line) or re.search(
            r"^\s*except\s+Exception(?:\s+as\s+\w+)?:\s*$",
            line,
        ):
            findings.append({
                "location": f"{path}:{idx}",
                "severity": "MEDIUM",
                "confidence": "HIGH",
                "rule": "開發規範 §4.4 具體例外處理規範",
                "problem": "捕捉過於寬泛的泛型 Exception 或 bare except",
                "evidence": raw_line.strip()[:100],
                "risk": "隱蔽非預期系統錯誤或鍵盤中斷",
                "recommendation": "改為捕捉具體的例外型別 (如 RequestException, KeyError)",
            })

    return findings


def run_static_checks(files: list[dict[str, Any]]) -> list[dict[str, Any]]:
    """對變更檔案清單執行確定性靜態規則檢查。"""
    all_findings: list[dict[str, Any]] = []

    for file_item in files:
        path = file_item.get("filename") or file_item.get("path") or ""
        patch = file_item.get("patch") or ""
        content = file_item.get("content") or patch

        if not path:
            continue

        normalized_path = path.replace("\\", "/")

        if (
            normalized_path.startswith(".github/workflows/")
            and (normalized_path.endswith(".yml") or normalized_path.endswith(".yaml"))
        ):
            if content:
                all_findings.extend(check_workflow_file(path, content))

        elif normalized_path.endswith(".java"):
            if content:
                all_findings.extend(check_java_file(path, content))

        elif normalized_path.endswith(".py"):
            # 對 Python 檔案優先讀取本地全檔內容以精確計算 LOC
            if Path(path).exists():
                try:
                    content = Path(path).read_text(encoding="utf-8")
                except (OSError, UnicodeDecodeError):
                    pass
            if content:
                all_findings.extend(check_python_file(path, content))

    return all_findings

import os
import re
from typing import Any

from check_rules import BANNED_PERMISSIONS, make_finding

SERVICE_MODULES = (
    "backend-iam-service",
    "backend-competency-service",
    "backend-job-service",
    "backend-alert-service",
    "backend-external-api-service",
    "backend-gateway",
)


def _detect_module(path: str) -> str:
    norm_path = path.replace("\\", "/").lower()
    for mod in SERVICE_MODULES:
        if mod in norm_path:
            return mod
    return ""


def _check_cross_module(
    path: str, line: str, idx: int, mod: str
) -> dict[str, Any] | None:
    if not mod or not line.strip().startswith("import "):
        return None
    # 檢查是否跨服務直接引用其他微服務的 Entity 或 Repository
    # 例：在 IAM 中引用 Competency 的 Entity/Repository，或在 Competency 中引用 IAM 的 Entity/Repository
    is_entity_or_repo = ".Entity." in line or ".Repository." in line
    if not is_entity_or_repo:
        return None

    if mod == "backend-iam-service":
        if any(
            target in line
            for target in (
                "SkillEntity", "ProjectEntity", "JobPostingEntity", "Competency",
                "SkillRepository", "ProjectRepository", "CompensationOutbox",
            )
        ):
            return make_finding(
                path, idx, "HIGH", "ARCHITECTURE",
                "開發規範 §1.1 微服務資料庫與實體隔離規範",
                "IAM 服務中直接引用業務微服務之 Entity 或 Repository",
                line.strip(),
                "破壞微服務資料庫獨立性與資料存取邊界",
                "微服務間嚴禁共用或直接操作 Entity，應透過 Feign Client 呼叫 API",
            )
    elif mod == "backend-competency-service":
        if any(
            target in line
            for target in (
                "UserEntity", "RoleEntity", "PermissionEntity",
                "UserRepository", "RoleRepository", "PermissionRepository"
            )
        ):
            return make_finding(
                path, idx, "HIGH", "ARCHITECTURE",
                "開發規範 §1.1 微服務資料庫與實體隔離規範",
                "業務微服務中直接引用 IAM 之 Entity 或 Repository",
                line.strip(),
                "破壞微服務資料庫獨立性與資料存取邊界",
                "微服務間嚴禁共用或直接操作 Entity，應透過 Feign Client 呼叫 API",
            )
    return None


def _check_self_feign(
    path: str, line: str, idx: int, mod: str
) -> dict[str, Any] | None:
    if not mod or "@FeignClient" not in line:
        return None
    # 檢測同微服務內部是否宣告指向自身的 Feign Client
    if f'"{mod}"' in line or f"'{mod}'" in line or f":{mod}" in line:
        return make_finding(
            path, idx, "HIGH", "ARCHITECTURE",
            "開發規範 §1.2 禁止同服務自我 Feign 呼叫規範",
            f"微服務 {mod} 內部宣告了指向自身的 Feign Client",
            line.strip(),
            "自我 Feign 呼叫增加非必要網路開銷並繞過事務隔離",
            "移除自我 Feign，改用 Spring @Lazy 介面自我代理處理 @Transactional/@Caching",
        )
    return None


def _check_iam_reverse_dependency(
    path: str, line: str, idx: int, mod: str
) -> dict[str, Any] | None:
    if mod != "backend-iam-service" or not line.strip().startswith("import "):
        return None
    # IAM 為基礎服務，禁止反向依賴業務微服務之 Feign Client 或 Service
    if any(
        business in line
        for business in ("CompetencyFeignClient", "SkillFeignClient", "AlertFeignClient")
    ):
        return make_finding(
            path, idx, "HIGH", "ARCHITECTURE",
            "開發規範 §1.1 IAM 單向依賴原則",
            "IAM 基礎服務中反向引用業務微服務之 FeignClient",
            line.strip(),
            "違反 IAM 作為基礎核心服務的單向依賴架構，造成微服務循環依賴",
            "移除 IAM 對業務微服務的依賴，由業務微服務單向依賴 IAM",
        )
    return None


def check_java_file(
    path: str,
    content: str,
    changed_lines: set[int] | None = None,
) -> list[dict[str, Any]]:
    """針對 Java Controller、Service 與架構分層進行確定性規範檢查。"""
    findings: list[dict[str, Any]] = []
    filename = os.path.basename(path)
    lines = content.splitlines()
    is_test = "src/test/java/" in path.replace("\\", "/")
    is_controller = "Controller" in filename or "@RestController" in content
    is_service_impl = "ServiceImpl" in filename or "/Service/Impl/" in path.replace("\\", "/")
    current_module = _detect_module(path)

    for idx, raw_line in enumerate(lines, start=1):
        if changed_lines is not None and idx not in changed_lines:
            continue
        line = raw_line.lstrip("+- ")

        # 1. 跨模組資料隔離與依賴檢查
        cross_res = _check_cross_module(path, line, idx, current_module)
        if cross_res:
            findings.append(cross_res)

        # 2. 自我 Feign 檢查
        feign_res = _check_self_feign(path, line, idx, current_module)
        if feign_res:
            findings.append(feign_res)

        # 3. IAM 單向依賴檢查
        iam_res = _check_iam_reverse_dependency(path, line, idx, current_module)
        if iam_res:
            findings.append(iam_res)

        # 4. 權限字串檢查
        for banned in BANNED_PERMISSIONS:
            if banned in line and ("@RequirePermission" in line or "hasAuthority" in line):
                findings.append(make_finding(
                    path, idx, "HIGH", "SECURITY",
                    "開發規範 §2 權限字典與禁用字串規範",
                    f"使用了已被廢棄或禁用的權限字串: {banned}",
                    raw_line.strip(),
                    "使用非標準權限名稱將導致 RBAC 權限失效或鑑權失敗",
                    "依據《開發規範.md》§2 權限字典替換為標準權限命名",
                ))

        # 5. 生產環境 @Autowired 檢查
        if not is_test and re.search(r"@Autowired\b", line):
            findings.append(make_finding(
                path, idx, "HIGH", "COMPLIANCE",
                "開發規範 §1.4 依賴注入規範",
                "生產程式碼中嚴禁使用 @Autowired 進行欄位注入",
                raw_line.strip(),
                "欄位注入隱藏依賴且不利於單元測試",
                "採用 Lombok @RequiredArgsConstructor 搭配 private final",
            ))

        # 6. Controller 分層與 OpenAPI 規範檢查
        if is_controller:
            if re.search(r"@Operation\(", line):
                findings.append(make_finding(
                    path, idx, "MEDIUM", "COMPLIANCE", "開發規範 §3 OpenAPI 標註規範",
                    "Controller 應使用專案封裝之 OpenApi 註解取代原生 @Operation",
                    raw_line.strip(), "缺少統一的 API 響應結構與錯誤碼說明",
                    "使用 @OpenApiCommonResponse 或專案標準註解封裝",
                ))
            if re.search(r"private\s+final\s+.*EntityManager\b", line):
                findings.append(make_finding(
                    path, idx, "HIGH", "ARCHITECTURE", "開發規範 §1.3 Controller 與資料層隔離規範",
                    "Controller 嚴禁直接注入 EntityManager", raw_line.strip(),
                    "破壞 Controller/Service/DataAccess 分層架構",
                    "移除 EntityManager，僅透過 Service 介面操作",
                ))
            if re.search(r"private\s+final\s+.*(?:Repository|Mapper)\b", line):
                findings.append(make_finding(
                    path, idx, "HIGH", "ARCHITECTURE", "開發規範 §1.3 Controller 依賴規範",
                    "Controller 嚴禁直接注入 Repository 或 Mapper", raw_line.strip(),
                    "違反 MVC 分層職責與資料隔離", "Controller 僅可注入 Service 介面，透過 Vo 進行互動",
                ))
            if re.search(r"public\s+(?:ResponseEntity<)?\w+Entity(?:>)?\s+\w+\(", line):
                findings.append(make_finding(
                    path, idx, "HIGH", "ARCHITECTURE", "開發規範 §1.3 Entity 使用規範",
                    "Controller 方法回傳型別包含 Entity", raw_line.strip(),
                    "Entity 洩漏至 API 對外介面，破壞封裝", "將回傳型別轉換為 Vo",
                ))

        # 7. Service Impl EntityManager 操作檢查
        if is_service_impl and re.search(r"private\s+final\s+.*EntityManager\b", line):
            findings.append(make_finding(
                path, idx, "HIGH", "ARCHITECTURE", "開發規範 §1.1 Service 禁止操作 EntityManager",
                "Service Impl 禁止直接注入 EntityManager", raw_line.strip(),
                "違反資料存取抽象化規範", "將資料庫操作封裝至 DataAccess 或 Repository",
            ))

    return findings

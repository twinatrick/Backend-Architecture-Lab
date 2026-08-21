import os
import re
from typing import Any

from check_rules import (
    BANNED_PERMISSIONS,
    RULE_CONTROLLER_DATA_ISOLATION,
    RULE_CONTROLLER_DEPENDENCY,
    RULE_DEPENDENCY_INJECTION,
    RULE_ENTITY_USAGE,
    RULE_IAM_UNIDIRECTIONAL,
    RULE_MICROSERVICE_ISOLATION,
    RULE_OPENAPI_ANNOTATION,
    RULE_PERMISSION_DICTIONARY,
    RULE_PROHIBIT_SELF_FEIGN,
    RULE_SERVICE_ENTITY_MANAGER,
    make_finding,
)

SERVICE_MODULES = (
    "backend-iam-service",
    "backend-competency-service",
    "backend-job-service",
    "backend-alert-service",
    "backend-external-api-service",
    "backend-gateway",
)

FOREIGN_ENTITIES = {
    "backend-iam-service": (
        "SkillEntity", "ProjectEntity", "JobPostingEntity", "JobApplicationEntity",
        "CompetencyEntity", "CompensationOutbox", "AlertRuleEntity", "AlarmHistoryEntity",
        "ExternalApiConfigEntity", "BotConfigEntity", "SkillRepository", "ProjectRepository",
        "CompetencyRepository", "JobPostingRepository", "AlertRuleRepository",
        "AlarmHistoryRepository",
    ),
    "backend-competency-service": (
        "UserEntity", "RoleEntity", "PermissionEntity", "GroupEntity", "SystemUserEntity",
        "JobPostingEntity", "JobApplicationEntity", "AlertRuleEntity", "AlarmHistoryEntity",
        "ExternalApiConfigEntity", "BotConfigEntity", "UserRepository", "RoleRepository",
        "PermissionRepository", "JobPostingRepository", "AlertRuleRepository",
    ),
    "backend-job-service": (
        "UserEntity", "RoleEntity", "PermissionEntity", "GroupEntity", "SystemUserEntity",
        "SkillEntity", "ProjectEntity", "CompensationOutbox", "AlertRuleEntity",
        "AlarmHistoryEntity", "ExternalApiConfigEntity", "UserRepository", "SkillRepository",
        "ProjectRepository", "AlertRuleRepository",
    ),
    "backend-alert-service": (
        "UserEntity", "RoleEntity", "PermissionEntity", "GroupEntity", "SkillEntity",
        "ProjectEntity", "JobPostingEntity", "CompensationOutbox", "ExternalApiConfigEntity",
        "UserRepository", "SkillRepository", "JobPostingRepository",
    ),
    "backend-external-api-service": (
        "UserEntity", "RoleEntity", "PermissionEntity", "GroupEntity", "SkillEntity",
        "ProjectEntity", "JobPostingEntity", "CompensationOutbox", "AlertRuleEntity",
        "UserRepository", "SkillRepository", "ProjectRepository",
    ),
    "backend-gateway": (
        "Entity", "Repository",
    ),
}


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


def _check_self_feign_block(
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


def _check_iam_reverse_dependency(
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

    # 檢查自我 Feign（支援跨多行註解）
    findings.extend(_check_self_feign_block(path, content, current_module, changed_lines))

    for idx, raw_line in enumerate(lines, start=1):
        if changed_lines is not None and idx not in changed_lines:
            continue
        line = raw_line.lstrip("+- ")

        # 1. 跨模組資料隔離與依賴檢查
        cross_res = _check_cross_module(path, line, idx, current_module)
        if cross_res:
            findings.append(cross_res)

        # 2. IAM 單向依賴檢查
        iam_res = _check_iam_reverse_dependency(path, line, idx, current_module)
        if iam_res:
            findings.append(iam_res)

        # 3. 權限字串檢查
        for banned in BANNED_PERMISSIONS:
            if banned in line and ("@RequirePermission" in line or "hasAuthority" in line):
                findings.append(make_finding(
                    path, idx, "HIGH", "SECURITY",
                    RULE_PERMISSION_DICTIONARY,
                    f"使用了已被廢棄或禁用的權限字串: {banned}",
                    raw_line.strip(),
                    "使用非標準權限名稱將導致 RBAC 權限失效或鑑權失敗",
                    "依據《開發規範.md》§2 權限字典替換為標準權限命名",
                ))

        # 4. 生產環境 @Autowired 檢查
        if not is_test and re.search(r"@Autowired\b", line):
            findings.append(make_finding(
                path, idx, "HIGH", "COMPLIANCE",
                RULE_DEPENDENCY_INJECTION,
                "生產程式碼中嚴禁使用 @Autowired 進行欄位注入",
                raw_line.strip(),
                "欄位注入隱藏依賴且不利於單元測試",
                "採用 Lombok @RequiredArgsConstructor 搭配 private final",
            ))

        # 5. Controller 分層與 OpenAPI 規範檢查
        if is_controller:
            if re.search(r"@Operation\(", line):
                findings.append(make_finding(
                    path, idx, "MEDIUM", "COMPLIANCE",
                    RULE_OPENAPI_ANNOTATION,
                    "Controller 應使用專案封裝之 OpenApi 註解取代原生 @Operation",
                    raw_line.strip(),
                    "缺少統一的 API 響應結構與錯誤碼說明",
                    "使用 @OpenApiCommonResponse 或專案標準註解封裝",
                ))
            if re.search(r"private\s+final\s+.*EntityManager\b", line):
                findings.append(make_finding(
                    path, idx, "HIGH", "ARCHITECTURE",
                    RULE_CONTROLLER_DATA_ISOLATION,
                    "Controller 嚴禁直接注入 EntityManager",
                    raw_line.strip(),
                    "破壞 Controller/Service/DataAccess 分層架構",
                    "移除 EntityManager，僅透過 Service 介面操作",
                ))
            if re.search(r"private\s+final\s+.*(?:Repository|Mapper)\b", line):
                findings.append(make_finding(
                    path, idx, "HIGH", "ARCHITECTURE",
                    RULE_CONTROLLER_DEPENDENCY,
                    "Controller 嚴禁直接注入 Repository 或 Mapper",
                    raw_line.strip(),
                    "違反 MVC 分層職責與資料隔離",
                    "Controller 僅可注入 Service 介面，透過 Vo 進行互動",
                ))
            if re.search(r"public\s+(?:ResponseEntity<)?\w+Entity(?:>)?\s+\w+\(", line):
                findings.append(make_finding(
                    path, idx, "HIGH", "ARCHITECTURE",
                    RULE_ENTITY_USAGE,
                    "Controller 方法回傳型別包含 Entity",
                    raw_line.strip(),
                    "Entity 洩漏至 API 對外介面，破壞封裝",
                    "將回傳型別轉換為 Vo",
                ))

        # 6. Service Impl EntityManager 操作檢查
        if is_service_impl and re.search(r"private\s+final\s+.*EntityManager\b", line):
            findings.append(make_finding(
                path, idx, "HIGH", "ARCHITECTURE",
                RULE_SERVICE_ENTITY_MANAGER,
                "Service Impl 禁止直接注入 EntityManager",
                raw_line.strip(),
                "違反資料存取抽象化規範",
                "將資料庫操作封裝至 DataAccess 或 Repository",
            ))

    return findings

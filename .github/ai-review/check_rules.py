from typing import Any

from redaction import STATIC_SECRET_REGEXES

# 規則名稱與真源常數（對齊《開發規範.md》）
RULE_MICROSERVICE_ISOLATION = "開發規範 §1.1 微服務資料庫與實體隔離規範"
RULE_IAM_UNIDIRECTIONAL = "開發規範 §1.1 IAM 單向依賴原則"
RULE_PROHIBIT_SELF_FEIGN = "開發規範 §1.2 禁止同服務自我 Feign 呼叫規範"
RULE_CONTROLLER_DATA_ISOLATION = "開發規範 §1.3 Controller 與資料層隔離規範"
RULE_CONTROLLER_DEPENDENCY = "開發規範 §1.3 Controller 依賴規範"
RULE_ENTITY_USAGE = "開發規範 §1.3 Entity 使用規範"
RULE_SERVICE_ENTITY_MANAGER = "開發規範 §1.3 Service 禁止操作 EntityManager"
RULE_DEPENDENCY_INJECTION = "開發規範 §1.4 依賴注入規範"
RULE_CONSTRUCTOR_INJECTION = "開發規範 §1.4/§5.7 依賴注入與建構子規範"
RULE_PERMISSION_DICTIONARY = "開發規範 §2 權限字典與禁用字串規範"
RULE_SECRET_PROTECTION = "開發規範 §2 敏感資訊與金鑰保護規範"
RULE_CI_TRUST_BOUNDARY = "開發規範 §2 CI 信任邊界防護"
RULE_CI_EXPRESSION_INJECTION = "開發規範 §2 CI 腳本表達式注入防護"
RULE_CI_ACTION_PINNING = "開發規範 §2 CI Action 版本鎖定規範"
RULE_CI_LEAST_PRIVILEGE = "開發規範 §2 CI 最小權限原則"
RULE_OPENAPI_ANNOTATION = "開發規範 §3 OpenAPI 標註規範"
RULE_PYTHON_NO_NOQA = "開發規範 §4.1 集中規則管理（禁止 # noqa 行內抑制註解）"
RULE_PYTHON_IMPORT_TOP = "開發規範 §4.1 Import 置頂規範"
RULE_PYTHON_SINGLE_LETTER = "開發規範 §4.2 禁止單字母變數規範"
RULE_PYTHON_LINE_LENGTH = "開發規範 §4.2 程式碼格式與行長規範"
RULE_PYTHON_MODULE_LOC = "開發規範 §4.3 單一職責與單檔行數限制"
RULE_PYTHON_TYPE_HINTS = "開發規範 §4.3 型別標註規範"
RULE_PYTHON_SPECIFIC_EXCEPTION = "開發規範 §4.4 具體例外處理規範"
RULE_PYTHON_ERROR_HANDLING = "開發規範 §4.4 錯誤處理與安全規範"
RULE_PROHIBIT_FQN = "開發規範 §5.12 禁寫完全限定名稱規範"
RULE_DEAD_CODE_REPOSITORY = "開發規範 §5.8 程式碼精簡與死代碼防護（YAGNI 原則）"
RULE_DERIVED_DELETE_TRANSACTIONAL = "開發規範 §1.3/§5 資料存取衍生刪除交易邊界規範"
RULE_WEAK_TYPED_COLLECTION = "開發規範 §1.3/§5.3 契約型別安全性（禁止弱型別 Map 回傳）"
RULE_UNIMPLEMENTED_METHOD_STUB = "開發規範 §6.4/§5.3 未實作存根守門門禁（禁止未實作例外或虛假殘留）"
RULE_FORBIDDEN_GETALL_ENDPOINT = "開發規範 §6.1 根除無界端點原則（嚴禁對外暴露無分頁 getAll 端點）"
RULE_MISSING_DUAL_RAIL_RETRIEVAL = "開發規範 §6.2 雙軌實體獲取標準（必須同時提供 /search 與 /stream 端點）"

PENDING_DUAL_RAIL_MIGRATION = frozenset({
    "AlertCheckLimitController",
    "CompanyController",
    "SkillController",
    "SkillLevelController",
    "RoleController",
    "FunctionController",
    "ConfigController",
    "DiscordManagementController",
    "DiaryController",
    "UsageController",
    "VoiceDiaryController",
    "UsageLogController",
})

NON_AGGREGATE_CONTROLLERS = frozenset({
    "AuthController",
    "GatewayFallbackController",
    "OpenApiAggregatorController",
    "CacheStatsController",
    "LineWebhookController",
    "UserInternalController",
    "PermissionInternalController",
    "AiInternalController",
    "SttController",
    "LearnController",
    "AiStreamController",
    "VoiceUploadController",
    "TtsRefAudioController",
    "UserJobBindingController",
    "UserBindingController",
    "ProjectManagementController",
    "ProjectAdminController",
    "PersonalSkillController",
    "PersonalProjectController",
})

ALLOWED_STUBS = frozenset({
    "ExternalSyncServiceImpl.java",
})

JPA_BUILTIN_METHODS = frozenset({
    "save", "saveAll", "saveAndFlush", "findById", "existsById",
    "findAll", "findAllById", "count", "deleteById", "delete",
    "deleteAllById", "deleteAll", "deleteAllInBatch", "deleteInBatch",
    "flush", "getOne", "getById", "getReferenceById", "hashCode", "equals", "toString",
})

BANNED_PERMISSIONS = (
    "PersonalEdit",
    "EditAll",
    "SkillManagement",
    "RolePermission",
    "AquarkDataAvg",
    "LimitSetting",
    "ViewPersonal",
    "DeleteAll",
    "SkillView",
    "UserManagement",
)

SECRET_REGEXES = STATIC_SECRET_REGEXES

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


def make_finding(
    path: str,
    line: int,
    severity: str,
    category: str,
    rule: str,
    problem: str,
    evidence: str,
    risk: str,
    recommendation: str,
) -> dict[str, Any]:
    return {
        "location": f"{path}:{line}",
        "severity": severity,
        "confidence": "HIGH",
        "category": category,
        "rule": rule,
        "problem": problem,
        "evidence": evidence[:100],
        "risk": risk,
        "recommendation": recommendation,
    }

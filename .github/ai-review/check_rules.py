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
RULE_PERMISSION_DICTIONARY = "開發規範 §2 權限字典與禁用字串規範"
RULE_SECRET_PROTECTION = "開發規範 §2 敏感資訊與金鑰保護規範"
RULE_CI_TRUST_BOUNDARY = "開發規範 §2 CI 信任邊界防護"
RULE_CI_EXPRESSION_INJECTION = "開發規範 §2 CI 腳本表達式注入防護"
RULE_CI_ACTION_PINNING = "開發規範 §2 CI Action 版本鎖定規範"
RULE_CI_LEAST_PRIVILEGE = "開發規範 §2 CI 最小權限原則"
RULE_OPENAPI_ANNOTATION = "開發規範 §3 OpenAPI 標註規範"
RULE_PYTHON_IMPORT_TOP = "開發規範 §4.1 Import 置頂規範"
RULE_PYTHON_SINGLE_LETTER = "開發規範 §4.2 禁止單字母變數規範"
RULE_PYTHON_LINE_LENGTH = "開發規範 §4.2 程式碼格式與行長規範"
RULE_PYTHON_MODULE_LOC = "開發規範 §4.3 單一職責與單檔行數限制"
RULE_PYTHON_TYPE_HINTS = "開發規範 §4.3 型別標註規範"
RULE_PYTHON_SPECIFIC_EXCEPTION = "開發規範 §4.4 具體例外處理規範"
RULE_PYTHON_ERROR_HANDLING = "開發規範 §4.4 錯誤處理與安全規範"

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

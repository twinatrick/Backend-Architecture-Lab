import sys
from pathlib import Path

AI_REVIEW_DIR = Path(__file__).resolve().parents[1]
if str(AI_REVIEW_DIR) not in sys.path:
    sys.path.insert(0, str(AI_REVIEW_DIR))

import check_java
import static_checks


def test_check_java_cross_module_data_access():
    content = (
        "package com.example.BackendArchitectureLab.Service.Impl;\n"
        "import com.example.BackendArchitectureLab.Repository.CompetencyRepository;\n"
        "public class UserServiceImpl {\n"
        "    private final CompetencyRepository repo;\n"
        "}\n"
    )
    findings = check_java.check_java_file(
        "backend-iam-service/src/main/java/com/example/UserServiceImpl.java",
        content,
    )
    rules = [finding_item["rule"] for finding_item in findings]
    assert any("微服務資料庫與實體隔離規範" in rule_text for rule_text in rules)


def test_check_java_self_feign_prohibition():
    content = (
        "package com.example.BackendArchitectureLab.Feign;\n"
        "@FeignClient(name = \"backend-competency-service\", path = \"/api/competency\")\n"
        "public interface CompetencySelfFeignClient {\n"
        "}\n"
    )
    findings = check_java.check_java_file(
        "backend-competency-service/src/main/java/com/example/CompetencySelfFeignClient.java",
        content,
    )
    rules = [finding_item["rule"] for finding_item in findings]
    assert any("禁止同服務自我 Feign 呼叫" in rule_text for rule_text in rules)


def test_check_java_iam_dependency_inversion():
    content = (
        "package com.example.BackendArchitectureLab.Service.Impl;\n"
        "import com.example.BackendArchitectureLab.Feign.CompetencyFeignClient;\n"
        "public class IamServiceImpl {\n"
        "    private final CompetencyFeignClient feignClient;\n"
        "}\n"
    )
    findings = check_java.check_java_file(
        "backend-iam-service/src/main/java/com/example/IamServiceImpl.java",
        content,
    )
    rules = [finding_item["rule"] for finding_item in findings]
    assert any("IAM 單向依賴原則" in rule_text for rule_text in rules)


def test_check_java_openapi_native_tag_and_import():
    content = (
        "package com.example.BackendArchitectureLab.Controller;\n"
        "import io.swagger.v3.oas.annotations.tags.Tag;\n"
        "@Tag(name = \"Gateway Fallback\")\n"
        "@RestController\n"
        "public class GatewayFallbackController {\n"
        "}\n"
    )
    findings = check_java.check_java_file(
        "backend-gateway/src/main/java/com/example/GatewayFallbackController.java",
        content,
    )
    rules = [finding_item["rule"] for finding_item in findings]
    assert any("OpenAPI 標註規範" in rule_text for rule_text in rules)


def test_check_java_constructor_injection_overloaded():
    content = (
        "package com.example.BackendArchitectureLab.Aop;\n"
        "public class DefaultPermissionValidator {\n"
        "    public DefaultPermissionValidator(String a) {\n"
        "        this(a, null);\n"
        "    }\n"
        "    public DefaultPermissionValidator(String a, String b) {}\n"
        "}\n"
    )
    findings = check_java.check_java_file(
        "backend-common/src/main/java/com/example/Aop/DefaultPermissionValidator.java",
        content,
    )
    rules = [finding_item["rule"] for finding_item in findings]
    assert any("依賴注入與建構子規範" in rule_text for rule_text in rules)


def test_check_java_controller_handwritten_constructor():
    content = (
        "package com.example.BackendArchitectureLab.Controller;\n"
        "@RestController\n"
        "public class UserController {\n"
        "    public UserController(String repo) {}\n"
        "}\n"
    )
    findings = check_java.check_java_file(
        "backend-iam-service/src/main/java/com/example/Controller/UserController.java",
        content,
    )
    rules = [finding_item["rule"] for finding_item in findings]
    assert any("依賴注入與建構子規範" in rule_text for rule_text in rules)


def test_check_java_dead_code_repository_isolated():
    content = (
        "package com.example.BackendArchitectureLab.Repository;\n"
        "public interface DiscordGfMessageRepository extends "
        "JpaRepository<DiscordGfMessage, UUID> {\n"
        "    List<DiscordGfMessage> findTop20BySessionIdOrderByCreatedTimeDesc(UUID sessionId);\n"
        "}\n"
    )
    findings = check_java.check_java_file(
        "backend-external-api-service/src/main/java/com/example/Repository/"
        "DiscordGfMessageRepository.java",
        content,
        repo_searcher=lambda method, path: [],
    )
    rules = [item["rule"] for item in findings]
    assert any("死代碼防護" in rule for rule in rules)


def test_check_java_dead_code_repository_has_callers():
    content = (
        "package com.example.BackendArchitectureLab.Repository;\n"
        "public interface DiscordGfMessageRepository extends "
        "JpaRepository<DiscordGfMessage, UUID> {\n"
        "    List<DiscordGfMessage> findBySessionIdOrderByCreatedTimeDesc("
        "UUID sessionId, Pageable pageable);\n"
        "}\n"
    )
    findings = check_java.check_java_file(
        "backend-external-api-service/src/main/java/com/example/Repository/"
        "DiscordGfMessageRepository.java",
        content,
        repo_searcher=lambda method, path: ["DiscordGfMessageDataAccessImpl.java"],
    )
    rules = [item["rule"] for item in findings]
    assert not any("死代碼防護" in rule for rule in rules)


def test_check_java_derived_delete_transactional_missing():
    content = (
        "package com.example.BackendArchitectureLab.DataAccess.Impl;\n"
        "public class DiscordGfMessageDataAccessImpl {\n"
        "    public int deleteBySessionId(UUID sessionId) {\n"
        "        return 1;\n"
        "    }\n"
        "}\n"
    )
    findings = check_java.check_java_file(
        "backend-external-api-service/src/main/java/com/example/DataAccess/Impl/"
        "DiscordGfMessageDataAccessImpl.java",
        content,
    )
    rules = [item["rule"] for item in findings]
    assert any("衍生刪除交易邊界" in rule for rule in rules)


def test_check_java_derived_delete_transactional_present():
    content = (
        "package com.example.BackendArchitectureLab.DataAccess.Impl;\n"
        "public class DiscordGfMessageDataAccessImpl {\n"
        "    @Transactional\n"
        "    public int deleteBySessionId(UUID sessionId) {\n"
        "        return 1;\n"
        "    }\n"
        "}\n"
    )
    findings = check_java.check_java_file(
        "backend-external-api-service/src/main/java/com/example/DataAccess/Impl/"
        "DiscordGfMessageDataAccessImpl.java",
        content,
    )
    rules = [item["rule"] for item in findings]
    assert not any("衍生刪除交易邊界" in rule for rule in rules)


def test_check_java_weak_typed_map_prohibited():
    content = (
        "package com.example.BackendArchitectureLab.Controller;\n"
        "@RestController\n"
        "public class CacheStatsController {\n"
        "    public ResponseType<Map<Object, Object>> getStats() {\n"
        "        return null;\n"
        "    }\n"
        "}\n"
    )
    findings = check_java.check_java_file(
        "backend-alert-service/src/main/java/com/example/Controller/CacheStatsController.java",
        content,
    )
    rules = [item["rule"] for item in findings]
    assert any("禁止弱型別 Map 回傳" in rule for rule in rules)


def test_check_java_strong_typed_map_allowed():
    content = (
        "package com.example.BackendArchitectureLab.Controller;\n"
        "@RestController\n"
        "public class CacheStatsController {\n"
        "    public ResponseType<Map<String, CacheMetricsVo>> getStats() {\n"
        "        Map<String, Object> localVariable = new HashMap<>();\n"
        "        return null;\n"
        "    }\n"
        "}\n"
    )
    findings = check_java.check_java_file(
        "backend-alert-service/src/main/java/com/example/Controller/CacheStatsController.java",
        content,
    )
    rules = [item["rule"] for item in findings]
    assert not any("禁止弱型別 Map 回傳" in rule for rule in rules)


def test_run_static_checks_resolves_repository_callers_from_pr_files():
    repo_path = "backend-iam-service/src/main/java/com/example/Repository/DemoRepository.java"
    dao_path = (
        "backend-iam-service/src/main/java/com/example/DataAccess/Impl/DemoDataAccessImpl.java"
    )
    files = [
        {
            "filename": repo_path,
            "status": "modified",
            "patch": (
                "@@ -1,2 +1,4 @@\n"
                " public interface DemoRepository {\n"
                "+    void deleteByCustomIdsInPrMemory(List<String> ids);\n"
                " }\n"
            ),
        },
        {
            "filename": dao_path,
            "status": "modified",
            "patch": (
                "@@ -1,2 +1,4 @@\n"
                " public class DemoDataAccessImpl {\n"
                "+    public void clean(List<String> ids) {\n"
                "+        demoRepository.deleteByCustomIdsInPrMemory(ids);\n"
                "+    }\n"
                " }\n"
            ),
        },
    ]
    findings = static_checks.run_static_checks(files)
    repo_dead_findings = [
        item for item in findings if "deleteByCustomIdsInPrMemory" in item.get("problem", "")
    ]
    assert repo_dead_findings == []


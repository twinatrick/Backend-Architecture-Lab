import sys
from pathlib import Path

AI_REVIEW_DIR = Path(__file__).resolve().parents[1]
if str(AI_REVIEW_DIR) not in sys.path:
    sys.path.insert(0, str(AI_REVIEW_DIR))

import check_java


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


def test_check_java_fqn_prohibited():
    content = (
        "package com.example.BackendArchitectureLab.Service.Impl;\n"
        "public class DemoService {\n"
        "    public void run() {\n"
        "        java.util.Date now = new java.util.Date();\n"
        "    }\n"
        "}\n"
    )
    findings = check_java.check_java_file(
        "backend-iam-service/src/main/java/com/example/DemoService.java",
        content,
    )
    rules = [finding_item["rule"] for finding_item in findings]
    assert any("禁寫完全限定名稱規範" in rule_text for rule_text in rules)


def test_check_java_fqn_allowed_imports_and_comments():
    content = (
        "package com.example.BackendArchitectureLab.Service.Impl;\n"
        "import java.util.Date;\n"
        "// java.util.Date in comment is allowed\n"
        "/* java.util.Date in block comment */\n"
        "public class DemoService {\n"
        "    public void run() {\n"
        "        Date now = new Date();\n"
        "    }\n"
        "}\n"
    )
    findings = check_java.check_java_file(
        "backend-iam-service/src/main/java/com/example/DemoService.java",
        content,
    )
    rules = [finding_item["rule"] for finding_item in findings]
    assert not any("禁寫完全限定名稱規範" in rule_text for rule_text in rules)

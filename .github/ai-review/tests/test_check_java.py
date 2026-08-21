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

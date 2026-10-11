import sys
from pathlib import Path

AI_REVIEW_DIR = Path(__file__).resolve().parents[1]
if str(AI_REVIEW_DIR) not in sys.path:
    sys.path.insert(0, str(AI_REVIEW_DIR))

import check_java


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


def test_check_java_fqn_prohibited_for_reactive_and_third_party():
    content = (
        "package com.example.BackendArchitectureLab.Service.Impl;\n"
        "public class DemoService {\n"
        "    public void run() {\n"
        "        Flux.just(1).subscribeOn("
        "reactor.core.scheduler.Schedulers.boundedElastic());\n"
        "    }\n"
        "}\n"
    )
    findings = check_java.check_java_file(
        "backend-iam-service/src/main/java/com/example/DemoService.java",
        content,
    )
    rules = [finding_item["rule"] for finding_item in findings]
    assert any("禁寫完全限定名稱規範" in rule_text for rule_text in rules)


def test_check_java_fqn_prohibited_in_test_files():
    content = (
        "package com.example.BackendArchitectureLab.Service.Impl;\n"
        "public class DemoServiceTest {\n"
        "    public void testRun() {\n"
        "        java.util.function.Supplier<?> supplier = () -> null;\n"
        "    }\n"
        "}\n"
    )
    findings = check_java.check_java_file(
        "backend-iam-service/src/test/java/com/example/DemoServiceTest.java",
        content,
    )
    rules = [finding_item["rule"] for finding_item in findings]
    assert any("禁寫完全限定名稱規範" in rule_text for rule_text in rules)


def test_check_java_fqn_allowed_in_strings():
    content = (
        "package com.example.BackendArchitectureLab.Config;\n"
        "public class DemoConfig {\n"
        "    public void run() {\n"
        "        String pkg = \"com.example.BackendArchitectureLab.Util\";\n"
        "    }\n"
        "}\n"
    )
    findings = check_java.check_java_file(
        "backend-iam-service/src/main/java/com/example/DemoConfig.java",
        content,
    )
    rules = [finding_item["rule"] for finding_item in findings]
    assert not any("禁寫完全限定名稱規範" in rule_text for rule_text in rules)

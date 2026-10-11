import sys
from pathlib import Path

AI_REVIEW_DIR = Path(__file__).resolve().parents[1]
if str(AI_REVIEW_DIR) not in sys.path:
    sys.path.insert(0, str(AI_REVIEW_DIR))

import check_java


def test_unimplemented_stub_exception_prohibited():
    content = (
        "package com.example.BackendArchitectureLab.Service.Impl;\n"
        "public class OrderServiceImpl {\n"
        "    public void processOrder() {\n"
        "        throw new UnsupportedOperationException(\"Not yet implemented\");\n"
        "    }\n"
        "}\n"
    )
    findings = check_java.check_java_file(
        "backend-job-service/src/main/java/com/example/OrderServiceImpl.java",
        content,
    )
    rules = [item["rule"] for item in findings]
    assert any("未實作存根守門門禁" in rule for rule in rules)


def test_unimplemented_stub_todo_prohibited():
    content = (
        "package com.example.BackendArchitectureLab.Service.Impl;\n"
        "public class OrderServiceImpl {\n"
        "    public void processOrder() {\n"
        "        // TODO: implement order processing\n"
        "    }\n"
        "}\n"
    )
    findings = check_java.check_java_file(
        "backend-job-service/src/main/java/com/example/OrderServiceImpl.java",
        content,
    )
    rules = [item["rule"] for item in findings]
    assert any("未實作存根守門門禁" in rule for rule in rules)


def test_unimplemented_stub_allowed_for_test_files():
    content = (
        "package com.example.BackendArchitectureLab.Service.Impl;\n"
        "public class OrderServiceImplTest {\n"
        "    public void testThrows() {\n"
        "        throw new UnsupportedOperationException(\"Expected\");\n"
        "    }\n"
        "}\n"
    )
    findings = check_java.check_java_file(
        "backend-job-service/src/test/java/com/example/OrderServiceImplTest.java",
        content,
    )
    rules = [item["rule"] for item in findings]
    assert not any("未實作存根守門門禁" in rule for rule in rules)


def test_unimplemented_stub_allowed_for_whitelisted_adapters():
    content = (
        "package com.example.BackendArchitectureLab.Service.Impl;\n"
        "public class ExternalSyncServiceImpl {\n"
        "    public void syncProject(UUID projectId) {\n"
        "        throw new UnsupportedOperationException(\"External sync adapter\");\n"
        "    }\n"
        "}\n"
    )
    findings = check_java.check_java_file(
        "backend-competency-service/src/main/java/com/example/ExternalSyncServiceImpl.java",
        content,
    )
    rules = [item["rule"] for item in findings]
    assert not any("未實作存根守門門禁" in rule for rule in rules)


def test_forbidden_getall_endpoint_prohibited():
    content = (
        "package com.example.BackendArchitectureLab.Controller;\n"
        "@RestController\n"
        "public class OrderController {\n"
        "    @GetMapping(\"/get\")\n"
        "    public ResponseType<List<OrderVo>> getAllOrders() {\n"
        "        return null;\n"
        "    }\n"
        "}\n"
    )
    findings = check_java.check_java_file(
        "backend-job-service/src/main/java/com/example/OrderController.java",
        content,
    )
    rules = [item["rule"] for item in findings]
    assert any("根除無界端點原則" in rule for rule in rules)


def test_forbidden_getall_endpoint_allowed_for_sub_resource():
    content = (
        "package com.example.BackendArchitectureLab.Controller;\n"
        "@RestController\n"
        "public class OrderController {\n"
        "    @GetMapping(\"/{id}/items\")\n"
        "    public ResponseType<List<OrderItemVo>> getOrderItems(@PathVariable UUID id) {\n"
        "        return null;\n"
        "    }\n"
        "}\n"
    )
    findings = check_java.check_java_file(
        "backend-job-service/src/main/java/com/example/OrderController.java",
        content,
    )
    rules = [item["rule"] for item in findings]
    assert not any("根除無界端點原則" in rule for rule in rules)


def test_missing_dual_rail_endpoints_prohibited():
    content = (
        "package com.example.BackendArchitectureLab.Controller;\n"
        "@RestController\n"
        "public class OrderController {\n"
        "    @PostMapping(\"/create\")\n"
        "    public ResponseType<OrderVo> createOrder(@RequestBody OrderVo vo) {\n"
        "        return null;\n"
        "    }\n"
        "}\n"
    )
    findings = check_java.check_java_file(
        "backend-job-service/src/main/java/com/example/OrderController.java",
        content,
    )
    rules = [item["rule"] for item in findings]
    assert any("雙軌實體獲取標準" in rule for rule in rules)


def test_missing_dual_rail_endpoints_allowed_when_search_and_stream_present():
    content = (
        "package com.example.BackendArchitectureLab.Controller;\n"
        "@RestController\n"
        "public class OrderController {\n"
        "    @PostMapping(\"/search\")\n"
        "    public ResponseType<PageResult<OrderVo>> searchOrders(\n"
        "            @RequestBody OrderSearchQuery query) {\n"
        "        return null;\n"
        "    }\n"
        "    @GetMapping(value = \"/stream\", produces = MediaType.TEXT_EVENT_STREAM_VALUE)\n"
        "    public Flux<ServerSentEvent<List<OrderVo>>> streamOrders(\n"
        "            @RequestParam(defaultValue = \"250\") int chunkSize) {\n"
        "        return null;\n"
        "    }\n"
        "}\n"
    )
    findings = check_java.check_java_file(
        "backend-job-service/src/main/java/com/example/OrderController.java",
        content,
    )
    rules = [item["rule"] for item in findings]
    assert not any("雙軌實體獲取標準" in rule for rule in rules)


def test_missing_dual_rail_endpoints_allowed_for_migration_list():
    content = (
        "package com.example.BackendArchitectureLab.Controller;\n"
        "@RestController\n"
        "public class DiscordManagementController {\n"
        "    @GetMapping(\"/get\")\n"
        "    public ResponseType<List<DiscordGfSessionVo>> getSession() {\n"
        "        return null;\n"
        "    }\n"
        "}\n"
    )
    findings = check_java.check_java_file(
        "backend-external-api-service/src/main/java/com/example/DiscordManagementController.java",
        content,
    )
    rules = [item["rule"] for item in findings]
    assert not any("雙軌實體獲取標準" in rule for rule in rules)
    assert not any("根除無界端點原則" in rule for rule in rules)


def test_missing_dual_rail_endpoints_allowed_for_non_aggregate_controllers():
    content = (
        "package com.example.BackendArchitectureLab.Controller;\n"
        "@RestController\n"
        "public class AuthController {\n"
        "    @PostMapping(\"/login\")\n"
        "    public ResponseType<AuthTokenVo> login(@RequestBody LoginRequestVo vo) {\n"
        "        return null;\n"
        "    }\n"
        "}\n"
    )
    findings = check_java.check_java_file(
        "backend-iam-service/src/main/java/com/example/AuthController.java",
        content,
    )
    rules = [item["rule"] for item in findings]
    assert not any("雙軌實體獲取標準" in rule for rule in rules)

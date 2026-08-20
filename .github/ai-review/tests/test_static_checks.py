import sys
from pathlib import Path

AI_REVIEW_DIR = Path(__file__).resolve().parents[1]
if str(AI_REVIEW_DIR) not in sys.path:
    sys.path.insert(0, str(AI_REVIEW_DIR))

import static_checks


def test_static_checks_github_workflow_pull_request_target():
    files = [
        {
            "filename": ".github/workflows/bad_pr.yml",
            "patch": """@@ -0,0 +1,10 @@
+on:
+  pull_request_target:
+jobs:
+  build:
+    runs-on: ubuntu-latest
+    steps:
+      - uses: actions/checkout@v4
+        with:
+          ref: ${{ github.event.pull_request.head.sha }}
+""",
        }
    ]
    findings = static_checks.run_static_checks(files)
    assert len(findings) >= 1
    rules = [f["rule"] for f in findings]
    assert any("CI 信任邊界防護" in r for r in rules)
    assert any(f["severity"] == "HIGH" for f in findings)


def test_static_checks_github_workflow_expression_injection():
    files = [
        {
            "filename": ".github/workflows/inject.yml",
            "patch": """@@ -0,0 +1,5 @@
+      - name: Echo title
+        run: |
+          echo "${{ github.event.pull_request.title }}"
+""",
        }
    ]
    findings = static_checks.run_static_checks(files)
    rules = [f["rule"] for f in findings]
    assert any("CI 腳本表達式注入防護" in r for r in rules)


def test_static_checks_java_controller_entity_manager_and_entity():
    files = [
        {
            "filename": "backend-iam/src/main/java/com/example/Controller/UserController.java",
            "patch": """@@ -1,5 +1,12 @@
+@RestController
+public class UserController {
+    private final EntityManager em;
+    private final UserRepository repo;
+
+    public UserEntity getUser() {
+        return null;
+    }
+}
+""",
        }
    ]
    findings = static_checks.run_static_checks(files)
    rules = [f["rule"] for f in findings]
    assert any("Controller 與資料層隔離規範" in r for r in rules)
    assert any("Controller 依賴規範" in r for r in rules)
    assert any("Entity 使用規範" in r for r in rules)


def test_static_checks_java_service_entity_manager():
    files = [
        {
            "filename": "backend-iam/src/main/java/com/example/Service/Impl/UserServiceImpl.java",
            "patch": """@@ -1,5 +1,8 @@
+@Service
+public class UserServiceImpl implements IUserService {
+    private final EntityManager entityManager;
+}
+""",
        }
    ]
    findings = static_checks.run_static_checks(files)
    rules = [f["rule"] for f in findings]
    assert any("Service 禁止操作 EntityManager" in r for r in rules)


def test_static_checks_python_generic_exception():
    files = [
        {
            "filename": "scripts/tool.py",
            "patch": """@@ -1,5 +1,7 @@
+try:
+    do_work()
+except Exception:
+    pass
+""",
        }
    ]
    findings = static_checks.run_static_checks(files)
    rules = [f["rule"] for f in findings]
    assert any("具體例外處理規範" in r for r in rules)


def test_static_checks_java_banned_permissions_and_autowired():
    files = [
        {
            "filename": "backend-iam/src/main/java/com/example/Controller/RoleController.java",
            "patch": """@@ -1,5 +1,9 @@
+@RestController
+public class RoleController {
+    @Autowired
+    private RoleService roleService;
+
+    @RequirePermission("PersonalEdit")
+    public void edit() {}
+}
+""",
        }
    ]
    findings = static_checks.run_static_checks(files)
    rules = [f["rule"] for f in findings]
    assert any("權限字典與禁用字串規範" in r for r in rules)
    assert any("依賴注入規範" in r for r in rules)


def test_static_checks_java_controller_raw_operation():
    files = [
        {
            "filename": "backend-iam/src/main/java/com/example/Controller/TestController.java",
            "patch": """@@ -1,5 +1,8 @@
+@RestController
+public class TestController {
+    @Operation(summary = "test")
+    public void test() {}
+}
+""",
        }
    ]
    findings = static_checks.run_static_checks(files)
    rules = [f["rule"] for f in findings]
    assert any("OpenAPI 標註規範" in r for r in rules)


def test_static_checks_python_line_length_and_loc():
    long_line = "a = '" + ("x" * 120) + "'"
    files = [
        {
            "filename": "scripts/long.py",
            "patch": f"+{long_line}\n",
        }
    ]
    findings = static_checks.run_static_checks(files)
    rules = [f["rule"] for f in findings]
    assert any("程式碼格式與行長規範" in r for r in rules)


def test_static_checks_python_full_content_loc_exceeded():
    fake_full_content = "\n".join([f"x_{i} = {i}" for i in range(305)])
    files = [
        {
            "filename": "scripts/large_module.py",
            "patch": "+x_0 = 0\n",
            "full_content": fake_full_content,
        }
    ]
    findings = static_checks.run_static_checks(files)
    rules = [f["rule"] for f in findings]
    assert any("單檔行數限制" in r for r in rules)


def test_static_checks_github_workflow_unpinned_action():
    files = [
        {
            "filename": ".github/workflows/unpinned.yml",
            "patch": """@@ -0,0 +1,5 @@
+    steps:
+      - uses: actions/checkout@main
+""",
        }
    ]
    findings = static_checks.run_static_checks(files)
    rules = [f["rule"] for f in findings]
    assert any("Action 版本鎖定規範" in r for r in rules)


def test_static_checks_secret_exposure():
    # 建立敏感金鑰測試變數 (分段拼接以避免自身的正則檢測)
    fake_secret = "g" + "sk_" + ("a" * 48)
    files = [
        {
            "filename": "backend-common/src/main/resources/application.yml",
            "patch": f"+api_key: \"{fake_secret}\"\n",
        }
    ]
    findings = static_checks.run_static_checks(files)
    rules = [f["rule"] for f in findings]
    assert any("敏感資訊與金鑰保護規範" in r for r in rules)


def test_static_checks_self_compliance():
    py_files = list(AI_REVIEW_DIR.glob("*.py"))
    assert len(py_files) >= 10
    file_payloads = []
    for py_file in py_files:
        content = py_file.read_text(encoding="utf-8")
        file_payloads.append(
            {
                "filename": f".github/ai-review/{py_file.name}",
                "patch": "",
                "full_content": content,
            }
        )
    findings = static_checks.run_static_checks(file_payloads)
    assert findings == []

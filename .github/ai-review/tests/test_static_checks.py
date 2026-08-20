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

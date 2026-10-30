from pathlib import Path
import sys
from unittest.mock import MagicMock

AI_REVIEW_DIR = Path(__file__).resolve().parents[1]
if str(AI_REVIEW_DIR) not in sys.path:
    sys.path.insert(0, str(AI_REVIEW_DIR))

import claim_verifier
import review_snapshot
import rule_registry
import verdict_manager


def _make_finding(
    location: str,
    rule: str,
    problem: str,
    evidence: str,
    category: str = "ARCHITECTURE",
    severity: str = "HIGH",
) -> dict[str, str]:
    return {
        "location": location,
        "category": category,
        "severity": severity,
        "confidence": "HIGH",
        "rule": rule,
        "problem": problem,
        "evidence": evidence,
        "risk": "阻擋風險",
        "recommendation": "修正建議",
    }


def test_partial_patch_without_full_content_returns_inconclusive():
    files = [{"filename": "src/A.java", "patch": "@@ -100,1 +100,1 @@\n+int x = 1;"}]
    snap = review_snapshot.ReviewSnapshot(files=files, head_sha="sha1")
    reg = rule_registry.RuleRegistry()
    finding = _make_finding("src/A.java:10", "開發規範.md (禁止 FQN)", "FQN", "x")
    res = reg.get_inspector(finding["rule"], finding["problem"]).inspect(finding, snap)
    assert res.status == review_snapshot.InspectionStatus.INCONCLUSIVE


def test_fqn_inspector_ignores_fabricated_evidence_and_checks_actual_code():
    files = [
        {
            "filename": "src/ServiceA.java",
            "full_content": (
                "package com.example;\n"
                "public class ServiceA {\n"
                "    private String normalizedParent = \"root\";\n"
                "}\n"
            ),
            "patch": "@@ -1,4 +1,4 @@\n+    private String normalizedParent = \"root\";",
        }
    ]
    snap = review_snapshot.ReviewSnapshot(files=files, head_sha="sha1")
    reg = rule_registry.RuleRegistry()
    finding = _make_finding(
        "src/ServiceA.java:3",
        "開發規範.md (禁止使用完全限定名稱 FQN)",
        "疑似使用 FQN",
        "java.util.Date fakeEvidence = new java.util.Date();",
    )
    res = reg.get_inspector(finding["rule"], finding["problem"]).inspect(finding, snap)
    assert res.status == review_snapshot.InspectionStatus.COMPLIANT


def test_aspect_inspector_ignores_lazy_in_comments_and_strings():
    files = [
        {
            "filename": "src/AlertService.java",
            "full_content": (
                "package com.example;\n"
                "public class AlertService {\n"
                "    // 已移除 @Lazy 自我注入\n"
                "    private String note = \"@Lazy this.save()\";\n"
                "    public void save() {}\n"
                "}\n"
            ),
            "patch": "@@ -1,6 +1,6 @@\n+    public void save() {}",
        }
    ]
    snap = review_snapshot.ReviewSnapshot(files=files, head_sha="sha1")
    reg = rule_registry.RuleRegistry()
    finding = _make_finding(
        "src/AlertService.java:3",
        "開發規範.md (嚴禁 @Lazy 與切面自調用)",
        "疑似使用 @Lazy 或自調用",
        "// 已移除 @Lazy",
    )
    res = reg.get_inspector(finding["rule"], finding["problem"]).inspect(finding, snap)
    assert res.status == review_snapshot.InspectionStatus.INCONCLUSIVE


def test_deterministic_inspector_returns_inconclusive_on_unknown_rule():
    files = [
        {
            "filename": "src/Unknown.java",
            "full_content": "package com.example;\npublic class Unknown {}\n",
            "patch": "@@ -1,2 +1,2 @@\n+public class Unknown {}",
        }
    ]
    snap = review_snapshot.ReviewSnapshot(files=files, head_sha="sha1")
    reg = rule_registry.RuleRegistry()
    finding = _make_finding("src/Unknown.java:1", "UNKNOWN_RULE_XYZ", "未知規則", "class Unknown")
    assert reg.get_inspector(finding["rule"], finding["problem"]) is None
    res = rule_registry.DeterministicFileRuleInspector("UNKNOWN_RULE_XYZ").inspect(finding, snap)
    assert res.status == review_snapshot.InspectionStatus.INCONCLUSIVE


def test_eligible_for_human_verdict_preserves_high_blocking_severity():
    files = [
        {
            "filename": "src/OrderService.java",
            "full_content": (
                "package com.example;\n"
                "public class OrderService {\n"
                "    public void cancelOrder(String orderId) {\n"
                "        repository.deleteById(orderId);\n"
                "    }\n"
                "}\n"
            ),
            "patch": "@@ -1,6 +1,6 @@\n+        repository.deleteById(orderId);",
        }
    ]
    snap = review_snapshot.ReviewSnapshot(files=files, head_sha="sha1")
    reg = rule_registry.RuleRegistry()
    finding = {
        "location": "src/OrderService.java:4",
        "category": "SECURITY",
        "severity": "HIGH",
        "confidence": "HIGH",
        "rule": "業務授權閉環檢查",
        "problem": "入口方法 cancelOrder 對資源 repository 缺少擁有者預期保護條件檢查",
        "evidence": "repository.deleteById(orderId);",
        "risk": "越權取消他人訂單；缺少跨服務授權上下文需人工核對",
        "recommendation": "補齊資源擁有者校驗",
    }
    claims_with_res = claim_verifier.inspect_findings_as_claims([finding], snap, reg)
    enforced = claim_verifier.enforce_claim_gate(claims_with_res, snap, reg)
    assert len(enforced) == 1
    assert enforced[0]["severity"] == "HIGH"
    assert enforced[0]["confidence"] == "HIGH"
    assert "[ELIGIBLE_FOR_HUMAN_VERDICT:" in enforced[0]["problem"]


def test_multi_turn_locks_inconclusive_and_splits_multi_rule_claims():
    files = [
        {
            "filename": "src/OrderService.java",
            "full_content": (
                "package com.example;\n"
                "public class OrderService {\n"
                "    public void cancelOrder(String id) { repo.delete(id); }\n"
                "}\n"
            ),
            "patch": (
                "@@ -1,4 +1,4 @@\n"
                "+    public void cancelOrder(String id) { repo.delete(id); }"
            ),
        }
    ]
    snap = review_snapshot.ReviewSnapshot(files=files, head_sha="sha1")
    reg = rule_registry.RuleRegistry()
    f_multi = _make_finding(
        "src/OrderService.java:3",
        "RULE_ENTITY_USAGE; 業務越權風險",
        "入口方法 cancelOrder 對資源 repo 缺少預期保護條件",
        "repo.delete(id);",
    )
    mock_caller = MagicMock(
        return_value=(
            '{"batch": "b-1", "files_reviewed": ["src/OrderService.java"], '
            '"coverage": "COMPLETE", "findings": []}'
        )
    )
    mock_caller.supports_rule_dialogue = True
    refined = claim_verifier.verify_and_refine_batch_findings(
        scope="business",
        index=1,
        paths=["src/OrderService.java"],
        initial_findings=[f_multi],
        snapshot=snap,
        registry=reg,
        caller=mock_caller,
        original_prompt="prompt",
        gate_mode="enforce",
    )
    assert len(refined) == 2
    assert all(item["severity"] == "HIGH" for item in refined)
    assert mock_caller.call_count == 1


def test_verdict_manager_fail_closed_auth_and_conflict_resolution():
    comments = [
        {
            "id": 101,
            "user": {"login": "dev_user"},
            "body": (
                "/ai-verdict claim_id=abc1234567890123 decision=FALSE_POSITIVE "
                "head_sha=sha_head base_sha=sha_base reason=確認為局部Diff誤判"
            ),
        }
    ]
    unverified_getter = MagicMock(return_value={"permission": "write"})
    verdicts_denied = verdict_manager.collect_verified_verdicts_from_comments(
        "owner/repo",
        comments,
        expected_head_sha="sha_head",
        expected_base_sha="sha_base",
        gh_get_fn=unverified_getter,
    )
    assert verdicts_denied == {}

    def _admin_getter(url: str, params=None):
        if url.endswith("/repos/owner/repo"):
            return {"default_branch": "master"}
        if "/collaborators/" in url:
            return {"permission": "admin", "user": {"permissions": {"push": True, "admin": True}}}
        if url.endswith("/protection"):
            return {"required_status_checks": None, "restrictions": None}
        if "/rules/branches/" in url:
            return []
        return {}

    verdicts_ok = verdict_manager.collect_verified_verdicts_from_comments(
        "owner/repo",
        comments,
        expected_head_sha="sha_head",
        expected_base_sha="sha_base",
        gh_get_fn=_admin_getter,
    )
    assert "abc1234567890123" in verdicts_ok
    assert verdicts_ok["abc1234567890123"].decision == "FALSE_POSITIVE"

def deduplicate(findings):
    result = []
    seen = set()
    for finding in findings:
        key = (finding.get("location"), finding.get("problem"), finding.get("rule"))
        if key not in seen:
            seen.add(key)
            result.append(finding)
    return result


def test_duplicate_findings_are_removed():
    finding = {"location": "a.java:10", "problem": "問題", "rule": "規範"}
    assert len(deduplicate([finding, dict(finding)])) == 1


def test_distinct_findings_are_kept():
    first = {"location": "a.java:10", "problem": "問題一", "rule": "規範"}
    second = {"location": "a.java:20", "problem": "問題二", "rule": "規範"}
    assert len(deduplicate([first, second])) == 2

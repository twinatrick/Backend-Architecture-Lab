import json


def format_markdown_report(
    decision: str,
    changed_files: list[str],
    results: list[dict],
    unique_findings: list[dict],
    blocking_findings: list[dict],
    passed_checks: list[str],
) -> str:
    """產出 Markdown 格式之 AI Review 報告全文。"""
    report = [
        "# AI Code Review",
        "",
        f"## 審查結果\n{decision}",
        "",
        f"已審查 {len(changed_files)} 個變更檔案、{len(results)} 個批次；"
        f"共 {len(unique_findings)} 個 Finding，其中 {len(blocking_findings)} 個阻擋項目。",
        "",
    ]
    if unique_findings:
        report.append("## Findings")
        for finding in unique_findings:
            report.extend([
                "",
                f"### [{finding['severity']}] {finding['problem']}",
                f"**位置**：`{finding['location']}`",
                f"**規範依據**：{finding['rule']}",
                f"**證據**：{finding['evidence']}",
                f"**風險**：{finding['risk']}",
                f"**修正建議**：{finding['recommendation']}",
                f"**信心度**：{finding['confidence']}",
            ])
    else:
        report.extend(["## Findings", "無。"])

    report.extend([
        "",
        "## 已通過檢查",
    ])
    for item in sorted(set(passed_checks)):
        report.append(f"- {item}")
    report.extend([
        "",
        "## 審查結論",
        "本次 Review 由分批 AI 分析，並由 deterministic engine 與 policy 統一計算阻擋條件。",
    ])

    return "\n".join(report)


def format_json_report(
    decision: str,
    unique_findings: list[dict],
    blocking_findings: list[dict],
    batch_count: int,
    changed_files: list[str],
) -> str:
    """產出 JSON 格式之審查元數據。"""
    return json.dumps(
        {
            "decision": decision,
            "findings": unique_findings,
            "blocking_findings": blocking_findings,
            "batches": batch_count,
            "files_reviewed": changed_files,
        },
        ensure_ascii=False,
        indent=2,
    )

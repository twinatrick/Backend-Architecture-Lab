import json
from typing import Any

from engine import has_high_risk_scope
from redaction import redact_secrets


def format_markdown_report(
    decision: str,
    changed_files: list[str],
    results: list[dict[str, Any]],
    unique_findings: list[dict[str, Any]],
    blocking_findings: list[dict[str, Any]],
    passed_checks: list[str],
    audit_info: dict[str, Any] | None = None,
    requires_human_review: bool = False,
    high_risk_files: list[str] | None = None,
) -> str:
    """產出 Markdown 格式之 AI Review 報告全文。"""
    is_human_required = requires_human_review or has_high_risk_scope(changed_files)
    report = [
        "# AI Code Review",
        "",
        f"## 審查結果\n{decision}",
        "",
        f"已審查 {len(changed_files)} 個變更檔案、{len(results)} 個批次；"
        f"共 {len(unique_findings)} 個 Finding，其中 {len(blocking_findings)} 個阻擋項目。",
        "",
    ]
    if is_human_required:
        risk_list = high_risk_files or [
            file_path for file_path in changed_files if has_high_risk_scope([file_path])
        ]
        sample_files = ", ".join(f"`{file_item}`" for file_item in risk_list[:3])
        suffix_text = " 等" if len(risk_list) > 3 else ""
        report.extend([
            "> ⚠️ **安全閘門策略提示 (Mandatory Human Architecture & Security Review Required)**：",
            "> 本次 PR 變更涉及核心安全、微服務架構或全域配置範疇（如 GitHub Workflows、",
            "> IAM 權限、AOP/Filter、Controller/Feign 或全域依賴 pom.xml 等）。",
            f"> 涉及檔案：{sample_files}{suffix_text}。",
            "> 依專案架構規範，**要求資深架構師或安全負責人進行最終人工審查與簽署**。",
            "",
        ])
    elif decision == "COMMENT":
        report.extend([
            "> ℹ️ **審查提示**：本次審查未發現嚴重阻擋項目，請參考相關建議。",
            "",
        ])
    if audit_info:
        trigger = redact_secrets(str(audit_info.get("trigger_type", "unknown")))
        actor = redact_secrets(str(audit_info.get("actor", "unknown")))
        head_sha = redact_secrets(str(audit_info.get("head_sha", "unknown")))
        report.extend([
            "## 審查稽核軌跡",
            f"- **觸發來源**：`{trigger}`",
            f"- **執行人員 (Actor)**：`{actor}`",
            f"- **目標 Commit SHA**：`{head_sha}`",
            "",
        ])
    if unique_findings:
        report.append("## Findings")
        for finding in unique_findings:
            sev = redact_secrets(str(finding.get("severity", "")))
            prob = redact_secrets(str(finding.get("problem", "")))
            loc = redact_secrets(str(finding.get("location", "")))
            rule = redact_secrets(str(finding.get("rule", "")))
            evi = redact_secrets(str(finding.get("evidence", "")))
            risk = redact_secrets(str(finding.get("risk", "")))
            rec = redact_secrets(str(finding.get("recommendation", "")))
            conf = redact_secrets(str(finding.get("confidence", "")))
            report.extend([
                "",
                f"### [{sev}] {prob}",
                f"**位置**：`{loc}`",
                f"**規範依據**：{rule}",
                f"**證據**：{evi}",
                f"**風險**：{risk}",
                f"**修正建議**：{rec}",
                f"**信心度**：{conf}",
            ])
    else:
        report.extend(["## Findings", "無。"])

    report.extend([
        "",
        "## 已通過檢查",
    ])
    for item in sorted(set(passed_checks)):
        report.append(f"- {redact_secrets(str(item))}")
    report.extend([
        "",
        "## 審查結論",
        "本次 Review 由分批 AI 分析，並由 deterministic engine 與 policy 統一計算阻擋條件。",
    ])

    return "\n".join(report)


def format_json_report(
    decision: str,
    unique_findings: list[dict[str, Any]],
    blocking_findings: list[dict[str, Any]],
    batch_count: int,
    changed_files: list[str],
    audit_info: dict[str, Any] | None = None,
    requires_human_review: bool = False,
    high_risk_files: list[str] | None = None,
) -> str:
    """產出 JSON 格式之審查元數據。"""
    sanitized_findings = []
    for finding_item in unique_findings:
        sanitized_findings.append({
            key_name: redact_secrets(str(val_obj)) if isinstance(val_obj, str) else val_obj
            for key_name, val_obj in finding_item.items()
        })
    sanitized_blocking = []
    for finding_item in blocking_findings:
        sanitized_blocking.append({
            key_name: redact_secrets(str(val_obj)) if isinstance(val_obj, str) else val_obj
            for key_name, val_obj in finding_item.items()
        })
    payload: dict[str, Any] = {
        "decision": decision,
        "findings": sanitized_findings,
        "blocking_findings": sanitized_blocking,
        "batches": batch_count,
        "files_reviewed": changed_files,
        "requires_human_review": requires_human_review,
        "high_risk_files": high_risk_files or [],
    }
    if audit_info:
        payload["audit"] = {
            key_name: redact_secrets(str(val_obj)) if isinstance(val_obj, str) else val_obj
            for key_name, val_obj in audit_info.items()
        }
    return json.dumps(
        payload,
        ensure_ascii=False,
        indent=2,
    )



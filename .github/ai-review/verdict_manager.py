import base64
from dataclasses import replace
import json
import logging
import re
import shlex
from typing import Any

import requests

from github_client import gh_get
from review_snapshot import HumanClaimVerdict
from reviewer_authorization import verify_approved_reviewer

VERDICT_CMD_RE = re.compile(r"^\s*/ai-verdict\s+(.+)$", re.MULTILINE)
VALID_DECISIONS = {"CONFIRMED_VIOLATION", "FALSE_POSITIVE", "RETRACT", "SUPERSEDE"}
COMMAND_KEYS = {
    "claim_id", "decision", "head_sha", "base_sha", "reason", "rule_version", "supersedes",
    "replacement_decision",
}


def parse_verdict_commands_from_comment(
    comment_body: str, author: str, comment_id: int = 0
) -> list[HumanClaimVerdict]:
    """確定性解析 PR 留言中的結構化 /ai-verdict 指令（不使用 LLM 解析）。"""
    verdicts: list[HumanClaimVerdict] = []
    if not comment_body or not author:
        return verdicts

    for match in VERDICT_CMD_RE.finditer(comment_body):
        args_line = match.group(1).strip()
        kv_map: dict[str, str] = {}
        try:
            for token in shlex.split(args_line, comments=False):
                key, separator, value = token.partition("=")
                if not separator or key not in COMMAND_KEYS or key in kv_map or not value.strip():
                    raise ValueError("裁定指令含重複、未知或無效參數。")
                kv_map[key] = value.strip()
        except ValueError:
            continue

        claim_id = kv_map.get("claim_id", "")
        decision = kv_map.get("decision", "").upper()
        head_sha = kv_map.get("head_sha", "")
        base_sha = kv_map.get("base_sha", "")
        reason = kv_map.get("reason", "")
        rule_version = kv_map.get("rule_version", "v1")
        supersedes = kv_map.get("supersedes", "")
        replacement = kv_map.get("replacement_decision", "").upper()

        if (
            not claim_id or decision not in VALID_DECISIONS or not head_sha or not base_sha
            or not reason or (decision in ("RETRACT", "SUPERSEDE") and not supersedes)
            or (decision == "SUPERSEDE" and replacement not in (
                "CONFIRMED_VIOLATION", "FALSE_POSITIVE",
            ))
        ):
            continue

        verdicts.append(
            HumanClaimVerdict(
                claim_id=claim_id,
                decision=decision,
                head_sha=head_sha,
                reason=reason,
                reviewer=author,
                base_sha=base_sha,
                rule_version=rule_version,
                supersedes_verdict_id=supersedes,
                comment_id=comment_id,
                auth_verified=False,
                replacement_decision=replacement,
            )
        )
    return verdicts


def verify_main_branch_merge_authorization(
    repo_name: str,
    username: str,
    gh_get_fn: Any = None,
) -> tuple[bool, str]:
    """歷史合併資格檢查；非人工裁定授權入口，裁定改由核准名單驗證。"""
    if not repo_name or not username:
        return False, "缺少儲存庫名稱或使用者帳號，授權驗證未完成（Fail-Closed）。"

    getter = gh_get_fn or gh_get
    try:
        perm_url = f"https://api.github.com/repos/{repo_name}/collaborators/{username}/permission"
        perm_data = getter(perm_url)
        if not isinstance(perm_data, dict):
            return False, "GitHub 權限 API 回傳格式異常，授權驗證未完成（Fail-Closed）。"

        perm_level = str(perm_data.get("permission", "")).lower()
        user_info = perm_data.get("user") or {}
        user_perms = user_info.get("permissions", {}) if isinstance(user_info, dict) else {}
        if not isinstance(user_perms, dict):
            return False, "使用者權限欄位格式異常，授權驗證未完成。"
        if perm_level not in ("write", "admin", "maintain") and user_perms.get("push") is not True:
            return False, f"使用者 {username} 權限為 {perm_level}，不具備合併主要分支權限。"

        repo_info = getter(f"https://api.github.com/repos/{repo_name}")
        default_branch = repo_info.get("default_branch") if isinstance(repo_info, dict) else None
        if not default_branch:
            return False, "無法確認主要分支，授權驗證未完成。"
        prot_url = (
            f"https://api.github.com/repos/{repo_name}/branches/{default_branch}/protection"
        )
        try:
            prot_data = getter(prot_url)
        except requests.HTTPError as exc:
            if exc.response is None or exc.response.status_code != 404:
                raise
            branch_info = getter(
                f"https://api.github.com/repos/{repo_name}/branches/{default_branch}"
            )
            if (not isinstance(branch_info, dict)
                    or branch_info.get("name") != default_branch
                    or not isinstance(branch_info.get("protected"), bool)):
                return False, "保護設定不存在但未能確認分支狀態，拒絕授權。"
            prot_data = {"required_status_checks": None, "restrictions": None}

        if not isinstance(prot_data, dict) or "required_status_checks" not in prot_data:
            return False, "主要分支保護設定回傳不可解析，授權驗證未完成（Fail-Closed）。"

        rules_url = f"https://api.github.com/repos/{repo_name}/rules/branches/{default_branch}"
        active_rules = getter(rules_url)
        harmless_rules = {"deletion", "non_fast_forward"}
        if not isinstance(active_rules, list) or any(
            not isinstance(rule, dict) or rule.get("type") not in harmless_rules
            for rule in active_rules
        ):
            return False, "主要分支含尚未支援的 Ruleset，無法可靠證明合併授權。"
        restrictions = prot_data.get("restrictions")
        if restrictions is not None:
            if not isinstance(restrictions, dict):
                return False, "主要分支限制格式不完整，無法確認合併授權。"
            allowed_users = [
                str(u_item.get("login", "")).lower()
                for u_item in (restrictions.get("users") or [])
                if isinstance(u_item, dict)
            ]
            if username.lower() not in allowed_users:
                return False, f"使用者 {username} 不在主要分支 {default_branch} 允許合併名單內。"

        return True, f"已確認使用者 {username} 具備主要分支 {default_branch} 合併授權。"
    except (requests.RequestException, ValueError, KeyError, TypeError) as exc:
        logging.warning("驗證主要分支合併權限失敗 (%s): %s", username, exc)
        return False, f"呼叫 GitHub API 驗證主要分支合併權限失敗（{exc}），授權驗證未完成。"


def resolve_effective_verdicts(
    records: list[HumanClaimVerdict],
    expected_head_sha: str,
    expected_base_sha: str = "",
) -> dict[str, HumanClaimVerdict]:
    """依 Append-only 紀錄解析有效裁定：處理 RETRACT、SUPERSEDE、SHA 綁定與同主張矛盾偵測。"""
    by_claim: dict[str, list[HumanClaimVerdict]] = {}
    for rec in records:
        if not rec.is_valid_for(expected_head_sha, expected_base_sha):
            continue
        by_claim.setdefault(rec.claim_id, []).append(rec)

    effective: dict[str, HumanClaimVerdict] = {}
    for claim_id, history in by_claim.items():
        active_decisions: list[HumanClaimVerdict] = []
        for item in history:
            if item.decision == "RETRACT":
                active_decisions = [
                    prev for prev in active_decisions
                    if str(prev.comment_id) != item.supersedes_verdict_id
                ]
            elif item.decision == "SUPERSEDE":
                if not any(str(prev.comment_id) == item.supersedes_verdict_id
                           for prev in active_decisions):
                    continue
                active_decisions = [
                    prev
                    for prev in active_decisions
                    if str(prev.comment_id) != item.supersedes_verdict_id
                ]
                active_decisions.append(replace(item, decision=item.replacement_decision))
            elif item.decision in ("CONFIRMED_VIOLATION", "FALSE_POSITIVE"):
                active_decisions.append(item)

        if not active_decisions:
            continue
        distinct = {act.decision for act in active_decisions}
        if len(distinct) > 1:
            last_item = active_decisions[-1]
            effective[claim_id] = HumanClaimVerdict(
                claim_id=claim_id,
                decision="CONFLICTING",
                head_sha=last_item.head_sha,
                reason="存在未撤回或未取代之矛盾人工裁定，維持待人工處理。",
                reviewer=last_item.reviewer,
                base_sha=last_item.base_sha,
                auth_verified=True,
                reviewer_authorized=True,
                reviewer_id=last_item.reviewer_id,
            )
        else:
            effective[claim_id] = active_decisions[-1]
    return effective


def fetch_ledger_verdicts(
    repo_name: str,
    pr_number: int,
    ledger_ref: str = "refs/ai-review/verdicts",
    gh_get_fn: Any = None,
) -> list[HumanClaimVerdict]:
    """自同儲存庫專用受保護裁定紀錄分支讀取 Append-only 人工裁定紀錄。"""
    if not repo_name or not pr_number:
        return []
    getter = gh_get_fn or gh_get
    path = f"verdicts/pr-{pr_number}.jsonl"
    url = f"https://api.github.com/repos/{repo_name}/contents/{path}"
    try:
        data = getter(url, params={"ref": ledger_ref})
        if isinstance(data, dict) and data.get("encoding") == "base64" and data.get("content"):
            raw_text = base64.b64decode(data["content"]).decode("utf-8", errors="strict")
            records: list[HumanClaimVerdict] = []
            for line in raw_text.splitlines():
                if not line.strip():
                    continue
                obj = json.loads(line)
                if not isinstance(obj, dict):
                    raise ValueError("裁定紀錄必須為物件。")
                record = HumanClaimVerdict(**obj)
                if not record.is_valid_for(record.head_sha, record.base_sha):
                    raise ValueError("裁定紀錄欄位或授權證明無效。")
                records.append(record)
            return records
    except (requests.RequestException, ValueError, KeyError, TypeError) as exc:
        raise RuntimeError("裁定紀錄讀取或驗證失敗，不得視為空紀錄。") from exc
    raise RuntimeError("裁定紀錄格式不完整，不得視為空紀錄。")


def collect_verified_verdicts_from_comments(
    repo_name: str,
    comments: list[dict[str, Any]],
    expected_head_sha: str,
    expected_base_sha: str = "",
    gh_get_fn: Any = None,
) -> dict[str, HumanClaimVerdict]:
    """自 API 留言清單解析指令，經核准名單與固定 ID 驗證後產出有效裁定表。"""
    auth_cache: dict[tuple[str, int], tuple[bool, str, int]] = {}
    verified_records: list[HumanClaimVerdict] = []

    for c_item in comments:
        if not isinstance(c_item, dict):
            continue
        user = c_item.get("user")
        if not isinstance(user, dict) or type(user.get("id")) is not int:
            continue
        author = str(user.get("login", "")).strip()
        body = str(c_item.get("body", ""))
        comment_id = int(c_item.get("id", 0) or 0)
        parsed_cmds = parse_verdict_commands_from_comment(body, author, comment_id=comment_id)
        for cmd in parsed_cmds:
            identity = (author, user["id"])
            if identity not in auth_cache:
                auth_cache[identity] = verify_approved_reviewer(
                    repo_name, author, gh_get_fn, expected_user_id=user["id"],
                )
            if auth_cache[identity][0]:
                verified_records.append(
                    HumanClaimVerdict(
                        claim_id=cmd.claim_id,
                        decision=cmd.decision,
                        head_sha=cmd.head_sha,
                        reason=cmd.reason,
                        reviewer=cmd.reviewer,
                        base_sha=cmd.base_sha,
                        rule_version=cmd.rule_version,
                        supersedes_verdict_id=cmd.supersedes_verdict_id,
                        comment_id=cmd.comment_id,
                        auth_verified=True,
                        reviewer_authorized=True,
                        reviewer_id=auth_cache[identity][2],
                        replacement_decision=cmd.replacement_decision,
                    )
                )

    return resolve_effective_verdicts(verified_records, expected_head_sha, expected_base_sha)

"""受保護裁定分支的追加寫入；不建立分支、不變更保護設定。"""

import base64
from dataclasses import asdict, replace
import json
import os
from typing import Any

import requests

from review_snapshot import HumanClaimVerdict
from reviewer_authorization import approved_reviewer_ids, verify_approved_reviewer
from verdict_manager import (
    parse_verdict_commands_from_comment,
    resolve_effective_verdicts,
)

LEDGER_BRANCH = "ai-review-verdicts"


def require_ledger_protection(repo: str, getter: Any, writer: str) -> None:
    """僅接受明確限定寫入身分、禁止刪除與強推的分支；未知設定拒絕。"""
    protection = getter(
        f"https://api.github.com/repos/{repo}/branches/{LEDGER_BRANCH}/protection"
    )
    if not isinstance(protection, dict) or not writer:
        raise RuntimeError("裁定紀錄分支保護未完成。")
    admins = protection.get("enforce_admins")
    if not isinstance(admins, dict) or admins.get("enabled") is not True:
        raise RuntimeError("裁定紀錄分支保護必須同樣約束管理員。")
    for key in ("allow_force_pushes", "allow_deletions"):
        setting = protection.get(key)
        if not isinstance(setting, dict) or setting.get("enabled") is not False:
            raise RuntimeError("裁定紀錄分支必須禁止強推與刪除。")
    restrictions = protection.get("restrictions")
    if not isinstance(restrictions, dict):
        raise RuntimeError("裁定紀錄分支必須限制寫入身分。")
    if restrictions.get("teams") or restrictions.get("apps"):
        raise RuntimeError("尚未支援裁定分支的團隊／App 寫入授權，拒絕推測。")
    users = restrictions.get("users")
    if (not isinstance(users, list) or len(users) != 1
            or not isinstance(users[0], dict) or users[0].get("login") != writer):
        raise RuntimeError("裁定紀錄分支寫入身分與設定不符。")


def decode_ledger(data: Any) -> tuple[str, str]:
    """驗證完整舊紀錄；損毀時不得以空內容覆蓋。"""
    if not isinstance(data, dict) or data.get("encoding") != "base64" or not data.get("sha"):
        raise ValueError("裁定紀錄 API 回應不完整。")
    content = base64.b64decode(data.get("content", ""), validate=False).decode("utf-8")
    for line in content.splitlines():
        if not line.strip():
            continue
        record = HumanClaimVerdict(**json.loads(line))
        if not record.is_valid_for(record.head_sha, record.base_sha):
            raise ValueError("既有裁定紀錄無效。")
    return content, data["sha"]


def append_comment_verdicts(
    repo: str, pr_number: int, comment: dict[str, Any], getter: Any, putter: Any,
    writer: str,
) -> int:
    """驗證目前 PR 快照與作者授權後追加；API SHA 比對失敗交由工作流程重跑。"""
    require_ledger_protection(repo, getter, writer)
    pr = getter(f"https://api.github.com/repos/{repo}/pulls/{pr_number}")
    if pr.get("state") != "open":
        raise ValueError("僅接受開啟中的 PR。")
    author = comment.get("user", {}).get("login")
    comment_id = comment.get("id")
    if not isinstance(author, str) or not isinstance(comment_id, int) or comment_id <= 0:
        raise ValueError("留言身分資訊不完整。")
    commands = parse_verdict_commands_from_comment(comment.get("body", ""), author, comment_id)
    if not commands:
        raise ValueError("未找到有效裁定指令。")
    allowed, _, reviewer_id = verify_approved_reviewer(
        repo, author, getter, expected_user_id=comment.get("user", {}).get("id", 0),
    )
    if not allowed:
        raise PermissionError("審核者核准名單身分驗證未完成。")
    records = [replace(command, auth_verified=True, reviewer_authorized=True,
                       reviewer_id=reviewer_id)
               for command in commands]
    if any(not record.is_valid_for(pr["head"]["sha"], pr["base"]["sha"])
           for record in records):
        raise ValueError("裁定與目前 PR 快照或規則版本不符。")
    url = f"https://api.github.com/repos/{repo}/contents/verdicts/pr-{pr_number}.jsonl"
    try:
        previous, sha = decode_ledger(getter(url, params={"ref": LEDGER_BRANCH}))
    except requests.HTTPError as exc:
        if exc.response is None or exc.response.status_code != 404:
            raise
        previous, sha = "", ""
    old_ids = {json.loads(line)["comment_id"] for line in previous.splitlines() if line.strip()}
    if comment_id in old_ids:
        return 0
    text = previous + ("\n" if previous and not previous.endswith("\n") else "")
    text += "".join(json.dumps(asdict(record), ensure_ascii=False) + "\n" for record in records)
    payload = {
        "message": f"追加 PR #{pr_number} 人工裁定（留言 {comment_id}）",
        "branch": LEDGER_BRANCH, "content": base64.b64encode(text.encode("utf-8")).decode(),
    }
    if sha:
        payload["sha"] = sha
    # 重新讀取快照，避免審查期間的 push 被當成同一版本裁定。
    current = getter(f"https://api.github.com/repos/{repo}/pulls/{pr_number}")
    if current["head"]["sha"] != pr["head"]["sha"] or current["base"]["sha"] != pr["base"]["sha"]:
        raise ValueError("裁定寫入前 PR 快照已更新。")
    putter(url, payload)
    return len(records)


def read_protected_verdicts(repo: str, pr_number: int, getter: Any, writer: str) -> list:
    """消費端只需唯讀權限；沒有分支保護時不信任紀錄中的授權布林值。"""
    require_ledger_protection(repo, getter, writer)
    url = f"https://api.github.com/repos/{repo}/contents/verdicts/pr-{pr_number}.jsonl"
    try:
        content, _ = decode_ledger(getter(url, params={"ref": LEDGER_BRANCH}))
    except requests.HTTPError as exc:
        if exc.response is not None and exc.response.status_code == 404:
            return []
        raise
    identities = approved_reviewer_ids(repo)
    records = [HumanClaimVerdict(**json.loads(line))
               for line in content.splitlines() if line.strip()]
    return [record for record in records if record.reviewer_id in identities]


def configured_verdicts(repo: str, pr_number: int, head: str, base: str) -> dict:
    """影子部署可不配置；一旦配置，讀取／保護錯誤必須傳遞而非當成沒有裁定。"""
    writer = os.environ.get("AI_REVIEW_VERDICT_LEDGER_WRITER", "").strip()
    if not writer:
        return {}
    token = os.environ.get("AI_REVIEW_LEDGER_READ_TOKEN", "")
    if not token:
        raise RuntimeError("裁定消費端缺少獨立唯讀憑證。")

    def getter(url: str, params: dict | None = None) -> Any:
        response = requests.get(url, params=params, timeout=30, headers={
            "Authorization": f"Bearer {token}", "Accept": "application/vnd.github+json",
        })
        response.raise_for_status()
        return response.json()

    records = read_protected_verdicts(repo, pr_number, getter, writer)
    return resolve_effective_verdicts(records, head, base)

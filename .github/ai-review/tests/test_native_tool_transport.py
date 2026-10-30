import sys
from pathlib import Path
from unittest.mock import MagicMock

import pytest
import requests

AI_REVIEW_DIR = Path(__file__).resolve().parents[1]
if str(AI_REVIEW_DIR) not in sys.path:
    sys.path.insert(0, str(AI_REVIEW_DIR))

from native_tool_transport import NativeToolTransport, validate_tool_call
from verdict_manager import verify_main_branch_merge_authorization


@pytest.mark.parametrize("provider", ["groq", "gemini"])
def test_native_transport_keeps_structured_tool_history(provider):
    response = MagicMock()
    if provider == "groq":
        response.json.return_value = {"choices": [{"message": {"content": "done"}}]}
    else:
        response.json.return_value = {
            "candidates": [{"content": {"parts": [{"text": "done"}]}}],
        }
    post = MagicMock(return_value=response)
    history = [
        {"role": "user", "text": "查證"},
        {"role": "assistant", "text": "", "calls": [{
            "id": "one", "name": "inspect_rule_claim",
            "arguments": {"claim_id": "abc", "rule_key": "FQN"},
        }]},
        {"role": "tool", "id": "one", "result": {"status": "COMPLIANT"}},
    ]
    result = NativeToolTransport(provider, "model", "secret", post).exchange(history, 12)
    assert result["text"] == "done"
    assert history[-1]["result"]["status"] == "COMPLIANT"
    assert post.call_args.kwargs["timeout"] == 12
    assert "tools" in post.call_args.kwargs["json"]


@pytest.mark.parametrize("arguments", [
    {}, {"claim_id": "abc", "rule_key": "OTHER"},
    {"claim_id": "new", "rule_key": "FQN"},
    {"claim_id": "abc", "rule_key": "FQN", "path": ".env"},
])
def test_tool_call_cannot_expand_authority(arguments):
    with pytest.raises(ValueError):
        validate_tool_call({"name": "inspect_rule_claim", "arguments": arguments}, {"abc": "FQN"})


def test_ruleset_only_branch_authorizes_without_classic_protection():
    missing = requests.Response()
    missing.status_code = 404

    def getter(url):
        if url.endswith("/permission"):
            return {"permission": "write"}
        if url.endswith("/protection"):
            raise requests.HTTPError(response=missing)
        if "/rules/branches/" in url:
            return [{"type": "deletion"}, {"type": "non_fast_forward"}]
        if "/branches/" in url:
            return {"name": "master", "protected": True}
        return {"default_branch": "master"}

    allowed, _ = verify_main_branch_merge_authorization("owner/repo", "reviewer", getter)
    assert allowed


def test_transport_failure_preserves_history():
    history = [{"role": "user", "text": "查證"}]
    post = MagicMock(side_effect=requests.Timeout())
    with pytest.raises(requests.Timeout):
        NativeToolTransport("groq", "model", "secret", post).exchange(history)
    assert history == [{"role": "user", "text": "查證"}]

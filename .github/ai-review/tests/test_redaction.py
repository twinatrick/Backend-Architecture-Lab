import os
import sys
from pathlib import Path
from unittest.mock import patch

AI_REVIEW_DIR = Path(__file__).resolve().parents[1]
if str(AI_REVIEW_DIR) not in sys.path:
    sys.path.insert(0, str(AI_REVIEW_DIR))

import redaction


def test_redact_secrets():
    env_vars = {
        "GROQ_API_KEY": "gsk_mock1234567890abcdef1234567890abcdef",
        "GEMINI_API_KEY": "AIzaSyMockKey1234567890abcdef1234567890",
        "GH_TOKEN": "ghp_mock1234567890abcdef1234567890abcdef",
    }
    with patch.dict(os.environ, env_vars, clear=True):
        raw_msg = (
            "Error with key gsk_mock1234567890abcdef1234567890abcdef "
            "and Gemini AIzaSyMockKey1234567890abcdef1234567890 "
            "and GitHub ghp_mock1234567890abcdef1234567890abcdef "
            "and pat github_pat_mock11ABCD1234567890123456_abcdef"
        )
        redacted = redaction.redact_secrets(raw_msg)
        assert "gsk_" not in redacted
        assert "AIza" not in redacted
        assert "ghp_" not in redacted
        assert "github_pat_" not in redacted
        assert "[REDACTED]" in redacted


def test_sanitize_diff_various_secret_patterns():
    mock_raw_pk = (
        "-----BEGIN RSA PRIVATE KEY-----\n"
        "MIIEowIBAAKCAQEA0\n"
        "-----END RSA PRIVATE KEY-----"
    )
    mock_raw_cert = (
        "-----BEGIN CERTIFICATE-----\n"
        "MIIDXTCCAkWgAwIBAgIJ\n"
        "-----END CERTIFICATE-----"
    )
    assert redaction.sanitize_diff(mock_raw_pk) == "[REDACTED_PRIVATE_KEY]"
    assert redaction.sanitize_diff(mock_raw_cert) == "[REDACTED_CERTIFICATE]"

    raw_groq = "key is gsk_mockabcdef12345678901234567890 in config"
    raw_gemini = "gemini key AIzaDummy123456789012345678901234567890 used"
    raw_ghp = "github token ghp_mock123456789012345678901234567890"
    raw_pat = "github pat github_pat_mock12345678901234567890_12345"
    raw_aws = "aws key AKIAIOSFODNN7EXAMPLE and ASIAIOSFODNN7EXAMPLE"
    raw_slack = "slack token xoxb-1234567890-123456789012-abcdef123456"
    raw_jwt = (
        "jwt eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9."
        "eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIn0."
        "SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c token"
    )
    assert redaction.sanitize_diff(raw_groq) == "key is [REDACTED] in config"
    assert redaction.sanitize_diff(raw_gemini) == "gemini key [REDACTED] used"
    assert redaction.sanitize_diff(raw_ghp) == "github token [REDACTED]"
    assert redaction.sanitize_diff(raw_pat) == "github pat [REDACTED]"
    assert redaction.sanitize_diff(raw_aws) == "aws key [REDACTED] and [REDACTED]"
    assert redaction.sanitize_diff(raw_slack) == "slack token [REDACTED]"
    assert "[REDACTED_JWT]" in redaction.sanitize_diff(raw_jwt)

    raw_assign = 'api_key = "super_secret_value_123"\npassword: \'topsecretpass456\''
    sanitized_assign = redaction.sanitize_diff(raw_assign)
    assert 'api_key = "[REDACTED]"' in sanitized_assign
    assert "password: '[REDACTED]'" in sanitized_assign

    raw_url = "clone https://admin:superSecretPass@github.com/org/repo.git"
    assert "https://admin:[REDACTED]@github.com/org/repo.git" in redaction.sanitize_diff(raw_url)

    raw_jdbc = "jdbc:postgresql://dbuser:dbpass123@postgres-host:5432/mydb"
    sanitized_jdbc = redaction.sanitize_diff(raw_jdbc)
    assert "jdbc:postgresql://dbuser:[REDACTED]@postgres-host:5432/mydb" in sanitized_jdbc

    raw_redis = "redis://:redisSecretPass@redis-server:6379/0"
    sanitized_redis = redaction.sanitize_diff(raw_redis)
    assert "redis://:[REDACTED]@redis-server:6379/0" in sanitized_redis

    raw_yaml = "spring.datasource.password: rawSecretPassword456"
    sanitized_yaml = redaction.sanitize_diff(raw_yaml)
    assert "spring.datasource.password: [REDACTED]" in sanitized_yaml

    raw_env = "SPRING_SECURITY_PASSWORD=envSecretPassword789"
    sanitized_env = redaction.sanitize_diff(raw_env)
    assert "SPRING_SECURITY_PASSWORD=[REDACTED]" in sanitized_env

    # 驗證合法函數調用與變數引用不會被誤判遮蔽
    func_call = "api_key = build_runtime_api_key(config)"
    var_assign = "password = user_input_password"
    assert redaction.sanitize_diff(func_call) == func_call
    assert redaction.sanitize_diff(var_assign) == var_assign


def test_sanitize_diff_known_env_vars():
    with patch.dict(os.environ, {"MY_TEST_SECRET_KEY": "super_hidden_env_token_999"}):
        raw_text = "Here is the token: super_hidden_env_token_999 in diff"
        assert redaction.sanitize_diff(raw_text) == "Here is the token: [REDACTED] in diff"


def test_safe_print_redacts_tokens(capsys):
    raw_secret = "gsk_secrettoken12345678901234567890"
    redaction.safe_print("Processing key:", raw_secret)
    captured = capsys.readouterr()
    assert "gsk_" not in captured.out
    assert "[REDACTED]" in captured.out

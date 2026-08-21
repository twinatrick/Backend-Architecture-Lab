import sys
from pathlib import Path
from unittest.mock import MagicMock

AI_REVIEW_DIR = Path(__file__).resolve().parents[1]
if str(AI_REVIEW_DIR) not in sys.path:
    sys.path.insert(0, str(AI_REVIEW_DIR))

import retry_utils


def test_parse_retry_after_from_header():
    mock_resp = MagicMock()
    mock_resp.headers = {"retry-after": "8.5"}
    assert retry_utils.parse_retry_after(mock_resp) == 8.5


def test_parse_retry_after_from_text_seconds():
    mock_resp = MagicMock()
    mock_resp.headers = {}
    mock_resp.text = '{"error":{"message":"Rate limit reached. Please try again in 10.4175s."}}'
    assert retry_utils.parse_retry_after(mock_resp) == 10.4175


def test_parse_retry_after_from_text_milliseconds():
    mock_resp = MagicMock()
    mock_resp.headers = {}
    mock_resp.text = '{"error":{"message":"Rate limit reached. Please try again in 500ms."}}'
    assert retry_utils.parse_retry_after(mock_resp) == 0.5


def test_parse_retry_after_fallback():
    mock_resp = MagicMock()
    mock_resp.headers = {}
    mock_resp.text = "Internal error without retry hints"
    assert retry_utils.parse_retry_after(mock_resp) == 5.0


def test_calculate_backoff_delay_exponential_growth():
    delay_1 = retry_utils.calculate_backoff_delay(
        attempt=1, retry_after=0.0, base_delay=2.5, jitter_range=(0.0, 0.0)
    )
    delay_2 = retry_utils.calculate_backoff_delay(
        attempt=2, retry_after=0.0, base_delay=2.5, jitter_range=(0.0, 0.0)
    )
    delay_3 = retry_utils.calculate_backoff_delay(
        attempt=3, retry_after=0.0, base_delay=2.5, jitter_range=(0.0, 0.0)
    )
    assert delay_1 == 2.5
    assert delay_2 == 5.0
    assert delay_3 == 10.0


def test_calculate_backoff_delay_respects_retry_after():
    delay = retry_utils.calculate_backoff_delay(
        attempt=1, retry_after=12.5, base_delay=2.5, jitter_range=(0.0, 0.0)
    )
    assert delay == 12.5


def test_calculate_backoff_delay_capped_at_max_delay():
    delay = retry_utils.calculate_backoff_delay(
        attempt=10, retry_after=100.0, max_delay=90.0, jitter_range=(0.0, 0.0)
    )
    assert delay == 90.0


def test_parse_retry_limit_valid():
    assert retry_utils.parse_retry_limit("10", 3) == 10
    assert retry_utils.parse_retry_limit("1", 3) == 1
    assert retry_utils.parse_retry_limit("30", 3) == 12


def test_parse_retry_limit_invalid_or_empty():
    assert retry_utils.parse_retry_limit("invalid", 5) == 5
    assert retry_utils.parse_retry_limit(None, 4) == 4
    assert retry_utils.parse_retry_limit("-5", 3) == 3

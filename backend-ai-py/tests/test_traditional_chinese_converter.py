from unittest.mock import MagicMock
from unittest.mock import patch

import pytest

from config import settings
from services.traditional_chinese_converter import TraditionalChineseConverter


def test_convert_returns_same_text_when_conversion_disabled():
    converter = TraditionalChineseConverter()
    with patch.object(settings, "stt_opencc_convert", False):
        assert converter.convert("你好世界") == "你好世界"


def test_convert_returns_same_text_when_text_empty():
    converter = TraditionalChineseConverter()
    with patch.object(settings, "stt_opencc_convert", True):
        assert converter.convert("") == ""
        assert converter.convert(None) is None


def test_convert_returns_same_text_when_opencc_is_mocked():
    converter = TraditionalChineseConverter()
    with (
        patch.object(settings, "stt_opencc_convert", True),
        patch("services.traditional_chinese_converter.opencc", MagicMock()),
    ):
        assert converter.convert("你好世界") == "你好世界"


def test_convert_applies_opencc_conversion():
    class _FakeConverter:
        def convert(self, text: str) -> str:
            return "台灣繁體"

    class _FakeOpenCC:
        def OpenCC(self, config: str) -> _FakeConverter:  # noqa: N802
            return _FakeConverter()

    converter = TraditionalChineseConverter()
    with patch("services.traditional_chinese_converter.opencc", _FakeOpenCC()):
        result = converter.convert("台湾繁体")
    assert result == "台灣繁體"


def test_convert_returns_same_text_when_conversion_raises_type_error():
    converter = TraditionalChineseConverter()
    fake_opencc = MagicMock()
    fake_opencc.OpenCC.side_effect = TypeError("bad config")
    with patch("services.traditional_chinese_converter.opencc", fake_opencc):
        assert converter.convert("你好世界") == "你好世界"


def test_convert_does_not_swallow_unexpected_errors():
    class _FailingConverter:
        def convert(self, text: str) -> str:
            raise RuntimeError("programming bug")

    class _FailingOpenCC:
        def OpenCC(self, config: str) -> _FailingConverter:  # noqa: N802
            return _FailingConverter()

    converter = TraditionalChineseConverter()
    with (
        patch("services.traditional_chinese_converter.opencc", _FailingOpenCC()),
        pytest.raises(RuntimeError),
    ):
        converter.convert("你好世界")

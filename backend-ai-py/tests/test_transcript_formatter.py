from types import SimpleNamespace
from unittest.mock import MagicMock

from config import settings
from services.transcript_formatter import TranscriptFormatter
from services.transcript_formatter import traditional_chinese_converter


def _make_aligner(speaker_names):
    aligner = MagicMock()
    aligner.parse_speaker_names.return_value = speaker_names
    aligner.align_segments_to_speakers.return_value = [
        {"speaker": "游先生", "text": "你好"},
        {"speaker": "張小姐", "text": "世界"},
    ]
    aligner.merge_same_speaker.return_value = [
        {"speaker": "游先生", "text": "你好"},
        {"speaker": "張小姐", "text": "世界"},
    ]
    return aligner


def _make_converter():
    converter = MagicMock()
    converter.convert.side_effect = lambda text: f"繁:{text}"
    return converter


def test_default_instance_reuses_shared_converter():
    formatter = TranscriptFormatter()
    assert formatter.converter is traditional_chinese_converter


def test_dependency_injection_uses_provided_instances():
    aligner = _make_aligner(["游先生"])
    converter = _make_converter()
    formatter = TranscriptFormatter(aligner=aligner, converter=converter)
    assert formatter.aligner is aligner
    assert formatter.converter is converter


def test_format_diarized_dialogue_mode():
    aligner = _make_aligner(["游先生", "張小姐"])
    converter = _make_converter()
    formatter = TranscriptFormatter(aligner=aligner, converter=converter)
    with SettingsUpdate("stt_dialogue_mode", True), SettingsUpdate("stt_timestamps", False):
        result = formatter.format_diarized([], None)
    assert result == "游先生說：「繁:你好」\n張小姐說：「繁:世界」"
    converter.convert.assert_called()


def test_format_diarized_tag_mode():
    aligner = _make_aligner(["游先生", "張小姐"])
    converter = _make_converter()
    formatter = TranscriptFormatter(aligner=aligner, converter=converter)
    with SettingsUpdate("stt_dialogue_mode", False):
        result = formatter.format_diarized([], None)
    assert result == "[游先生]: 繁:你好\n[張小姐]: 繁:世界"


def _seg(text, start, end):
    return SimpleNamespace(text=text, start=start, end=end)


def test_format_plain_with_timestamps():
    converter = _make_converter()
    formatter = TranscriptFormatter(aligner=_make_aligner([]), converter=converter)
    segments = [_seg(" 你好 ", 0.0, 5.0), _seg("世界", 5.0, 10.3)]
    with SettingsUpdate("stt_timestamps", True):
        result = formatter.format_plain(segments)
    assert result == "繁:[00:00 -> 00:05] 你好\n[00:05 -> 00:10] 世界"


def test_format_plain_without_timestamps():
    converter = _make_converter()
    formatter = TranscriptFormatter(aligner=_make_aligner([]), converter=converter)
    segments = [_seg("你好", 0.0, 5.0), _seg("世界", 5.0, 10.3)]
    with SettingsUpdate("stt_timestamps", False):
        result = formatter.format_plain(segments)
    assert result == "繁:你好世界"


class SettingsUpdate:
    """暫時覆蓋 settings 欄位的 context manager。"""

    def __init__(self, key, value):
        self.key = key
        self.value = value
        self.original = None

    def __enter__(self):
        self.original = getattr(settings, self.key)
        setattr(settings, self.key, self.value)
        return self

    def __exit__(self, exc_type, exc_value, traceback):
        setattr(settings, self.key, self.original)
        return False

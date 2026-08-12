from config import settings
from services.speaker_aligner import SpeakerAligner
from services.traditional_chinese_converter import TraditionalChineseConverter


class TranscriptFormatter:
    """轉錄排版器：負責語者分離對話框格式與純文字／時間戳記格式輸出。"""

    def __init__(self) -> None:
        self.aligner = SpeakerAligner()
        self.converter = TraditionalChineseConverter()

    @staticmethod
    def _seconds_to_mmss(seconds: float) -> str:
        """將秒數轉換為 MM:SS 格式字串"""
        minutes = int(seconds) // 60
        remaining_seconds = int(seconds) % 60
        return f"{minutes:02d}:{remaining_seconds:02d}"

    def format_diarized(self, segments, diarization_result) -> str:
        """語者分離排版：時間軸對齊 + 同語者合併 + 中文小說體對話框或標籤格式"""
        speaker_names = self.aligner.parse_speaker_names()
        aligned = self.aligner.align_segments_to_speakers(
            segments, diarization_result, speaker_names
        )
        merged = self.aligner.merge_same_speaker(aligned)

        parts = []
        for item in merged:
            # 僅對辨識文字做繁體轉換，speaker 名稱（如「游先生」）保持原樣
            text = self.converter.convert(item["text"])
            if settings.stt_dialogue_mode:
                parts.append(f"{item['speaker']}說：「{text}」")
            else:
                parts.append(f"[{item['speaker']}]: {text}")

        return "\n".join(parts)

    def format_plain(self, segments) -> str:
        """無語者分離：純文字合併（可選繪式時間戳記）"""
        if settings.stt_timestamps:
            parts = []
            for seg in segments:
                text = seg.text.strip()
                if text:
                    parts.append(
                        f"[{self._seconds_to_mmss(seg.start)} -> "
                        f"{self._seconds_to_mmss(seg.end)}] {text}"
                    )
            raw_text = "\n".join(parts)
        else:
            raw_text = "".join(seg.text for seg in segments)
        return self.converter.convert(raw_text)


transcript_formatter = TranscriptFormatter()

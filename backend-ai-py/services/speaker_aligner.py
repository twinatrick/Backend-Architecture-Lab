import json
import logging
from typing import Protocol

from config import settings

logger = logging.getLogger(__name__)


class Segment(Protocol):
    """語音辨識分段的最小介面（相容 whisper segment）。"""

    text: str
    start: float
    end: float


class SpeakerAligner:
    """語者對齊器：解析語者名稱、依時間軸重疊度對齊語音分段並合併連續同語者。"""

    def parse_speaker_names(self) -> list[str]:
        """解析設定的語者名稱 JSON，格式異常時回傳空清單。"""
        if not settings.stt_speaker_names:
            return []
        try:
            return json.loads(settings.stt_speaker_names)
        except (json.JSONDecodeError, TypeError) as exc:
            logger.warning("[STT] 語者名稱解析失敗: %s", exc)
            return []

    def align_segments_to_speakers(
        self,
        segments: list[Segment],
        diarization_result: list[dict[str, float | str]],
        speaker_names: list[str],
    ) -> list[tuple[str, str, float, float]]:
        """智慧時間軸重疊度對齊：將每個語音分段歸類到最重疊或最近的語者"""
        aligned = []
        unique_speakers_ordered = []

        for seg in segments:
            speaker_overlaps = {}
            for turn in diarization_result:
                overlap = max(0.0, min(seg.end, turn["end"]) - max(seg.start, turn["start"]))
                if overlap > 0:
                    speaker_overlaps[turn["speaker"]] = (
                        speaker_overlaps.get(turn["speaker"], 0) + overlap
                    )

            if speaker_overlaps:
                assigned_speaker = max(speaker_overlaps, key=speaker_overlaps.get)
            else:
                closest_speaker = None
                min_dist = float("inf")
                for turn in diarization_result:
                    dist = min(abs(turn["start"] - seg.end), abs(turn["end"] - seg.start))
                    if dist < min_dist:
                        min_dist = dist
                        closest_speaker = turn["speaker"]
                assigned_speaker = closest_speaker if closest_speaker else "Unknown"

            if assigned_speaker not in unique_speakers_ordered and assigned_speaker != "Unknown":
                unique_speakers_ordered.append(assigned_speaker)

            speaker_label = assigned_speaker
            if assigned_speaker != "Unknown" and speaker_names:
                try:
                    idx = unique_speakers_ordered.index(assigned_speaker)
                    speaker_label = (
                        speaker_names[idx] if idx < len(speaker_names) else assigned_speaker
                    )
                except ValueError as exc:
                    # 語者名稱清單可能不完整，找不到時退回原始標籤；記錄診斷資訊以便後續補齊。
                    logger.warning("[STT] 語者名稱查無對應: %s", exc)

            aligned.append((speaker_label, seg.text.strip(), seg.start, seg.end))

        return aligned

    def merge_same_speaker(
        self, aligned: list[tuple[str, str, float, float]]
    ) -> list[dict[str, float | str]]:
        """智慧合併相同語者的連續說話段落（不產生碎裂換行）"""
        merged = []
        for speaker, text, start, end in aligned:
            if not text:
                continue
            if merged and merged[-1]["speaker"] == speaker:
                prev_text = merged[-1]["text"]
                punct = "，。？！、!?,"
                if prev_text and prev_text[-1] not in punct:
                    merged[-1]["text"] += "，" + text
                else:
                    merged[-1]["text"] += text
                merged[-1]["end"] = max(merged[-1]["end"], end)
            else:
                merged.append({"speaker": speaker, "text": text, "start": start, "end": end})
        return merged

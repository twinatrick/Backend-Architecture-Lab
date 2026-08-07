import json

import opencc

from config import settings
from services.common import _is_mock

_opencc_converter = None


def _seconds_to_mmss(seconds: float) -> str:
    """將秒數轉換為 MM:SS 格式字串"""
    m = int(seconds) // 60
    s = int(seconds) % 60
    return f"{m:02d}:{s:02d}"


def _parse_speaker_names() -> list:
    if not settings.stt_speaker_names:
        return []
    try:
        return json.loads(settings.stt_speaker_names)
    except Exception:
        return []


def _align_segments_to_speakers(segments, diarization_result, speaker_names):
    """智慧時間軸重疊度對齊：將每個語音分段歸類到最重疊或最近的語者"""
    aligned = []
    unique_speakers_ordered = []

    for seg in segments:
        speaker_overlaps = {}
        for turn in diarization_result:
            overlap = max(0, min(seg.end, turn["end"]) - max(seg.start, turn["start"]))
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
                speaker_label = speaker_names[idx] if idx < len(speaker_names) else assigned_speaker
            except ValueError:
                pass

        aligned.append((speaker_label, seg.text.strip(), seg.start, seg.end))

    return aligned


def _merge_same_speaker(aligned):
    """智慧合併相同語者的連續說話段落（不產生碎裂換行）"""
    merged = []
    for speaker, text, start, end in aligned:
        if not text:
            continue
        if merged and merged[-1]["speaker"] == speaker:
            prev_text = merged[-1]["text"]
            if prev_text and prev_text[-1] not in (
                "，",
                "。",
                "？",
                "！",
                "、",
                " ",
                "!",
                "?",
                ",",
            ):
                merged[-1]["text"] += "，" + text
            else:
                merged[-1]["text"] += text
            merged[-1]["end"] = max(merged[-1]["end"], end)
        else:
            merged.append({"speaker": speaker, "text": text, "start": start, "end": end})
    return merged


def _format_diarized(segments, diarization_result) -> str:
    """語者分離排版：時間軸對齊 + 同語者合併 + 中文小說體對話框或標籤格式"""
    speaker_names = _parse_speaker_names()
    aligned = _align_segments_to_speakers(segments, diarization_result, speaker_names)
    merged = _merge_same_speaker(aligned)

    parts = []
    for item in merged:
        # 僅對辨識文字做繁體轉換，speaker 名稱（如「游先生」）保持原樣
        text = _convert_tw_traditional(item["text"])
        if settings.stt_dialogue_mode:
            parts.append(f"{item['speaker']}說：「{text}」")
        else:
            parts.append(f"[{item['speaker']}]: {text}")

    return "\n".join(parts)


def _format_plain(segments) -> str:
    """無語者分離：純文字合併（可選繪式時間戳記）"""
    if settings.stt_timestamps:
        parts = []
        for seg in segments:
            text = seg.text.strip()
            if text:
                parts.append(
                    f"[{_seconds_to_mmss(seg.start)} -> {_seconds_to_mmss(seg.end)}] {text}"
                )
        raw_text = "\n".join(parts)
    else:
        raw_text = "".join(seg.text for seg in segments)
    return _convert_tw_traditional(raw_text)


def _convert_tw_traditional(text: str) -> str:
    """動態台灣繁體 OpenCC 轉換（s2twp）"""
    global _opencc_converter
    if not text or not settings.stt_opencc_convert:
        return text
    try:
        if _is_mock(opencc):
            return text
        if _opencc_converter is None:
            _opencc_converter = opencc.OpenCC("s2twp.json")
        return _opencc_converter.convert(text)
    except Exception as e:
        print(f"[STT] OpenCC conversion failed: {e}")
        return text

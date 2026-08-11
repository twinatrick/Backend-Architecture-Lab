from utils.audio import convert_to_wav
from utils.audio import get_audio_duration


class AudioConverter:
    """音訊格式處理：負責轉換為 WAV 與取得音訊時長。"""

    def convert_to_wav(self, input_path: str) -> str:
        """將輸入音訊轉為 16kHz mono WAV，回傳轉檔後路徑。"""
        return convert_to_wav(input_path)

    def get_duration(self, file_path: str) -> float:
        """取得音訊檔案時長（秒）。"""
        return get_audio_duration(file_path)

class TtsTimeoutError(Exception):
    """GptSoVits 外部服務請求逾時。"""


class GptSovitsError(Exception):
    """GptSoVits 外部服務錯誤（連線失敗或 HTTP 錯誤回應）。"""

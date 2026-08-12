from config import settings
from utils.file_adapter import download_from_minio


class AudioStorage:
    """MinIO 音訊儲存操作：負責下載原始音訊與組裝對外 URL。"""

    def download(self, object_key: str, bucket: str = None) -> str:
        """自 MinIO 下載指定物件，回傳暫存檔路徑。"""
        return download_from_minio(object_key, bucket)

    def build_audio_url(self, object_key: str) -> str:
        """組裝對外可存取的音訊 URL。"""
        endpoint = settings.minio_endpoint.rstrip("/")
        return f"{endpoint}/{settings.minio_bucket_audio}/{object_key}"

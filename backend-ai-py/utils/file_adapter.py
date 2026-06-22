import io
import tempfile
import uuid
from datetime import datetime
from pathlib import Path

from minio import Minio

from config import settings


def _get_client() -> Minio:
    return Minio(
        endpoint=settings.minio_endpoint.replace("http://", "").replace("https://", ""),
        access_key=settings.minio_access_key,
        secret_key=settings.minio_secret_key,
        secure=settings.minio_endpoint.startswith("https"),
        region=settings.minio_region,
    )


def generate_object_key(prefix: str, ext: str) -> str:
    today = datetime.now().strftime("%Y/%m/%d")
    uid = uuid.uuid4().hex
    return f"{prefix}/{today}/{uid}{ext}"


def download_from_minio(object_path: str) -> str:
    client = _get_client()
    response = client.get_object(settings.minio_bucket_audio, object_path)
    suffix = Path(object_path).suffix
    tmp = tempfile.NamedTemporaryFile(delete=False, suffix=suffix)
    tmp.write(response.read())
    tmp.close()
    response.close()
    response.release_conn()
    return tmp.name


def upload_to_minio(data: bytes, object_key: str, content_type: str) -> str:
    client = _get_client()
    if not client.bucket_exists(settings.minio_bucket_audio):
        client.make_bucket(settings.minio_bucket_audio)
    data_stream = io.BytesIO(data)
    length = len(data)
    client.put_object(
        bucket_name=settings.minio_bucket_audio,
        object_name=object_key,
        data=data_stream,
        length=length,
        content_type=content_type,
    )
    endpoint = settings.minio_endpoint.rstrip("/")
    return f"{endpoint}/{settings.minio_bucket_audio}/{object_key}"

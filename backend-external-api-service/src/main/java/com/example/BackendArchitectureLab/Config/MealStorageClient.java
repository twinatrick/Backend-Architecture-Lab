package com.example.BackendArchitectureLab.Config;

import io.minio.MinioClient;

/** 隔離餐點媒體的連線期限，不改變既有語音上傳的 HTTP 設定。 */
public record MealStorageClient(MinioClient client) {
}

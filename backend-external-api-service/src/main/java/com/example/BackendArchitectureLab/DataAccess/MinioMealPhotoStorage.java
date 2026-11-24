package com.example.BackendArchitectureLab.DataAccess;

import com.example.BackendArchitectureLab.Config.MealProperties;
import com.example.BackendArchitectureLab.Config.MealStorageClient;
import com.example.BackendArchitectureLab.Exception.AppException;
import com.example.BackendArchitectureLab.Service.MealPhotoStorage;
import io.minio.GetObjectArgs;
import io.minio.GetBucketPolicyArgs;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.errors.ErrorResponseException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
@RequiredArgsConstructor
public class MinioMealPhotoStorage implements MealPhotoStorage {
    private final MealStorageClient storageClient;
    private final MealProperties properties;

    @Override
    public void put(String objectKey, Path file, long size, String contentType) {
        try (InputStream input = Files.newInputStream(file)) {
            requirePrivateBucket();
            storageClient.client().putObject(PutObjectArgs.builder().bucket(properties.getBucket()).object(objectKey)
                    .stream(input, size, -1).contentType(contentType).build());
        } catch (Exception exception) {
            throw new AppException("MEAL_STORAGE_UNAVAILABLE", "照片儲存暫時不可用，餐點已保留，可重試補傳", 503);
        }
    }

    @Override
    public InputStream open(String objectKey) {
        try {
            requirePrivateBucket();
            return storageClient.client().getObject(GetObjectArgs.builder().bucket(properties.getBucket()).object(objectKey).build());
        } catch (Exception exception) {
            throw new AppException("MEAL_STORAGE_UNAVAILABLE", "照片讀取暫時不可用", 503);
        }
    }

    @Override
    public void delete(String objectKey) {
        try {
            storageClient.client().removeObject(RemoveObjectArgs.builder().bucket(properties.getBucket()).object(objectKey).build());
        } catch (Exception exception) {
            throw new AppException("MEAL_STORAGE_UNAVAILABLE", "照片清理暫時不可用", 503);
        }
    }

    private void requirePrivateBucket() throws Exception {
        if (!storageClient.client().bucketExists(BucketExistsArgs.builder().bucket(properties.getBucket()).build())) {
            try {
                storageClient.client().makeBucket(MakeBucketArgs.builder().bucket(properties.getBucket()).build());
            } catch (ErrorResponseException exception) {
                if (!"BucketAlreadyOwnedByYou".equals(exception.errorResponse().code())) throw exception;
            }
        }
        String policy = storageClient.client().getBucketPolicy(GetBucketPolicyArgs.builder().bucket(properties.getBucket()).build());
        // 專用 bucket 不接受既有政策，避免誤用公開 bucket；不擅自覆寫管理者政策。
        if (policy != null && !policy.isBlank()) throw new AppException("MEAL_BUCKET_NOT_PRIVATE", "餐點照片需要無公開政策的專用儲存桶", 503);
    }
}

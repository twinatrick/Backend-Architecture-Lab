package com.example.BackendArchitectureLab.DataAccess;

import com.example.BackendArchitectureLab.Entity.MealReceipt;
import com.example.BackendArchitectureLab.Entity.MealReceiptPhoto;
import com.example.BackendArchitectureLab.Exception.AppException;
import com.example.BackendArchitectureLab.Repository.MealReceiptPhotoRepository;
import com.example.BackendArchitectureLab.Repository.MealReceiptRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class MealReceiptDataAccess {
    private final MealReceiptRepository receiptRepository;
    private final MealReceiptPhotoRepository receiptPhotoRepository;

    public MealReceipt owned(UUID userId, UUID receiptId, boolean lock) {
        MealReceipt receipt = (lock
                ? receiptRepository.lockOwned(receiptId, userId)
                : receiptRepository.findByIdAndOwnerUserId(receiptId, userId))
                .orElseThrow(() -> new AppException("MEAL_RECEIPT_NOT_FOUND", "消費明細不存在或不屬於目前使用者", 404));
        if (receipt.isDeleted()) {
            throw new AppException("MEAL_RECEIPT_DELETED", "此消費明細已刪除", 410);
        }
        return receipt;
    }

    public Optional<MealReceipt> byIdempotencyKey(UUID userId, String idempotencyKey) {
        return receiptRepository.findByOwnerUserIdAndIdempotencyKey(userId, idempotencyKey);
    }

    public MealReceipt saveReceipt(MealReceipt receipt) {
        return receiptRepository.saveAndFlush(receipt);
    }

    public Optional<MealReceiptPhoto> photoByIdempotencyKey(UUID receiptId, UUID userId, String idempotencyKey) {
        return receiptPhotoRepository.findByReceiptIdAndOwnerUserIdAndIdempotencyKey(receiptId, userId, idempotencyKey);
    }

    public MealReceiptPhoto ownedPhoto(UUID userId, UUID receiptId, UUID photoId) {
        return receiptPhotoRepository.findByIdAndReceiptIdAndOwnerUserId(photoId, receiptId, userId)
                .orElseThrow(() -> new AppException("MEAL_RECEIPT_PHOTO_NOT_FOUND", "明細圖片不存在或不屬於此明細", 404));
    }

    public List<MealReceiptPhoto> listPhotos(UUID userId, UUID receiptId) {
        return receiptPhotoRepository.findByReceiptIdAndOwnerUserIdOrderBySortOrderAscCreatedAtAsc(receiptId, userId);
    }

    public long countPhotos(UUID receiptId) {
        return receiptPhotoRepository.countByReceiptId(receiptId);
    }

    public MealReceiptPhoto savePhoto(MealReceiptPhoto photo) {
        return receiptPhotoRepository.saveAndFlush(photo);
    }

    @Transactional
    public void deletePhotosByReceipt(UUID receiptId) {
        receiptPhotoRepository.deleteByReceiptId(receiptId);
    }
}

package com.example.BackendArchitectureLab.DataAccess;

import com.example.BackendArchitectureLab.Entity.MealMediaCleanup;
import com.example.BackendArchitectureLab.Entity.MealPhoto;
import com.example.BackendArchitectureLab.Repository.MealMediaCleanupRepository;
import com.example.BackendArchitectureLab.Repository.MealPhotoRepository;
import com.example.BackendArchitectureLab.Repository.MealReceiptPhotoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class MealMediaDataAccess {
    private final MealPhotoRepository photos;
    private final MealReceiptPhotoRepository receiptPhotos;
    private final MealMediaCleanupRepository cleanups;

    public Optional<MealPhoto> byOperation(UUID mealId, UUID operationId) {
        return photos.findByMealIdAndOperationId(mealId, operationId);
    }

    public Optional<MealPhoto> find(UUID mealId, UUID photoId) {
        return photos.findByIdAndMealId(photoId, mealId);
    }

    public List<MealPhoto> ready(UUID mealId) {
        String state = "READY";
        return photos.findByMealIdAndState(mealId, state);
    }

    public long occupied(UUID mealId) {
        Date now = new Date();
        return photos.countOccupied(mealId, now);
    }

    public MealPhoto save(MealPhoto photo) {
        return photos.saveAndFlush(photo);
    }

    public void schedule(String objectKey, Date due) {
        MealMediaCleanup cleanup = cleanups.findByObjectKey(objectKey).orElseGet(MealMediaCleanup::new);
        cleanup.setObjectKey(objectKey);
        cleanup.setNextAttemptAt(due);
        cleanups.save(cleanup);
    }

    public void deleteAll(UUID mealId) {
        for (MealPhoto photo : photos.findByMealId(mealId)) {
            photo.setState("DELETED");
            // 延遲清理可涵蓋刪除當下仍在傳送中的物件寫入。
            schedule(photo.getObjectKey(), new Date(System.currentTimeMillis() + 3_600_000));
        }
    }

    public Optional<MealMediaCleanup> nextCleanup() {
        Date now = new Date();
        Pageable pageable = PageRequest.of(0, 1);
        return cleanups.findByNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(now, pageable).stream().findFirst();
    }

    public boolean isReferenced(String objectKey) {
        String state = "READY";
        return photos.existsByObjectKeyAndState(objectKey, state)
                || receiptPhotos.existsByObjectKey(objectKey);
    }

    public void completeCleanup(MealMediaCleanup cleanup) {
        cleanups.delete(cleanup);
    }

    public void retryCleanup(MealMediaCleanup cleanup, String errorCode) {
        cleanup.setAttempts(Math.min(Integer.MAX_VALUE - 1, cleanup.getAttempts()) + 1);
        cleanup.setLastError(errorCode);
        long delay = Math.min(86_400_000L, 60_000L * (1L << Math.min(cleanup.getAttempts(), 10)));
        cleanup.setNextAttemptAt(new Date(System.currentTimeMillis() + delay));
        cleanups.save(cleanup);
    }
}

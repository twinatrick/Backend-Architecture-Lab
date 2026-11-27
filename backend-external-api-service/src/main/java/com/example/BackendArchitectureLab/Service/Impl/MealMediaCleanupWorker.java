package com.example.BackendArchitectureLab.Service.Impl;

import com.example.BackendArchitectureLab.DataAccess.MealMediaDataAccess;
import com.example.BackendArchitectureLab.Entity.MealMediaCleanup;
import com.example.BackendArchitectureLab.Service.MealPhotoStorage;
import com.example.BackendArchitectureLab.Util.TransactionExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class MealMediaCleanupWorker {
    private final MealMediaDataAccess media;
    private final MealPhotoStorage storage;
    private final TransactionExecutor transactions;

    @Scheduled(fixedDelayString = "${meals.cleanup-delay-ms:60000}")
    public void cleanNext() {
        transactions.runWritable(() -> {
            MealMediaCleanup task = media.nextCleanup().orElse(null);
            if (task == null) return;
            try {
                if (!media.isReferenced(task.getObjectKey())) storage.delete(task.getObjectKey());
                media.completeCleanup(task);
            } catch (RuntimeException exception) {
                media.retryCleanup(task, exception.getClass().getSimpleName());
                log.warn("餐點媒體清理失敗，工作 {} 已保留重試，累計 {} 次", task.getId(), task.getAttempts());
            }
        });
    }
}

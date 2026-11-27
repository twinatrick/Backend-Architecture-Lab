package com.example.BackendArchitectureLab.Service.Impl;

import com.example.BackendArchitectureLab.Config.MealProperties;
import com.example.BackendArchitectureLab.DataAccess.MealMediaDataAccess;
import com.example.BackendArchitectureLab.DataAccess.MealRecordDataAccess;
import com.example.BackendArchitectureLab.Entity.MealPhoto;
import com.example.BackendArchitectureLab.Exception.AppException;
import com.example.BackendArchitectureLab.Mapper.MealPhotoMapper;
import com.example.BackendArchitectureLab.Service.IMealPhotoService;
import com.example.BackendArchitectureLab.Service.MealPhotoStorage;
import com.example.BackendArchitectureLab.Util.MealImageValidator;
import com.example.BackendArchitectureLab.Util.MealImageValidator.ValidatedImage;
import com.example.BackendArchitectureLab.Util.TransactionExecutor;
import com.example.BackendArchitectureLab.Vo.MealPhotoVo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MealPhotoService implements IMealPhotoService {
    private final MealRecordDataAccess meals;
    private final MealMediaDataAccess media;
    private final MealPhotoMapper mapper;
    private final MealPhotoStorage storage;
    private final MealImageValidator validator;
    private final MealProperties properties;
    private final TransactionExecutor transactions;

    @Override
    public MealPhotoVo upload(UUID userId, UUID mealId, UUID operationId, MultipartFile file) throws IOException {
        transactions.runReadOnly(() -> meals.owned(userId, mealId, false));
        if (operationId == null) throw new AppException("INVALID_OPERATION", "必須提供操作識別碼", 400);
        try (ValidatedImage image = validator.validate(file)) {
            MealPhoto reserved = transactions.executeWritable(() -> reserve(userId, mealId, operationId, image));
            if ("READY".equals(reserved.getState())) return mapper.toVo(reserved);
            try {
                storage.put(reserved.getObjectKey(), image.file(), image.byteSize(), image.contentType());
                return transactions.executeWritable(() -> {
                    meals.owned(userId, mealId, true);
                    MealPhoto current = media.find(mealId, reserved.getId()).orElseThrow();
                    if (!"UPLOADING".equals(current.getState()) || !Objects.equals(current.getObjectKey(), reserved.getObjectKey())
                            || current.getLeaseUntil().before(new Date())) {
                        throw new AppException("PHOTO_ATTEMPT_EXPIRED", "此照片上傳已失效，請重新操作", 409);
                    }
                    current.setState("READY");
                    return mapper.toVo(media.save(current));
                });
            } catch (RuntimeException exception) {
                transactions.runWritable(() -> {
                    meals.owned(userId, mealId, true);
                    media.find(mealId, reserved.getId()).ifPresent(current -> {
                        if ("UPLOADING".equals(current.getState()) && current.getObjectKey().equals(reserved.getObjectKey())) {
                            current.setState("FAILED");
                            media.save(current);
                        }
                    });
                });
                throw exception;
            }
        }
    }

    private MealPhoto reserve(UUID userId, UUID mealId, UUID operationId, ValidatedImage image) {
        meals.owned(userId, mealId, true);
        MealPhoto photo = media.byOperation(mealId, operationId).orElseGet(MealPhoto::new);
        if (photo.getId() != null) {
            if (!photo.getContentHash().equals(image.hash())) throw new AppException("OPERATION_CONFLICT", "相同操作識別碼不能上傳不同圖片", 409);
            if ("READY".equals(photo.getState())) return photo;
            if ("DELETED".equals(photo.getState())) throw new AppException("PHOTO_DELETED", "這次操作的照片已刪除，不會重新建立", 410);
            if ("UPLOADING".equals(photo.getState()) && photo.getLeaseUntil().after(new Date())) throw new AppException("PHOTO_BUSY", "此照片仍在上傳，請稍後重試", 409);
        }
        if (media.occupied(mealId) >= properties.getMaxPhotos()) throw new AppException("PHOTO_LIMIT", "餐點照片已達數量上限", 400);
        photo.setMealId(mealId);
        photo.setOperationId(operationId);
        photo.setContentHash(image.hash());
        photo.setObjectKey("meals/" + UUID.randomUUID());
        photo.setState("UPLOADING");
        photo.setContentType(image.contentType());
        photo.setByteSize(image.byteSize());
        photo.setLeaseUntil(new Date(System.currentTimeMillis() + 600_000));
        media.save(photo);
        // 在外部寫入之前保存清理責任，涵蓋程序中斷與成功上傳後資料庫失敗。
        media.schedule(photo.getObjectKey(), new Date(System.currentTimeMillis() + 3_600_000));
        return photo;
    }

    @Override
    public List<MealPhotoVo> list(UUID userId, UUID mealId) {
        return transactions.executeReadOnly(() -> {
            meals.owned(userId, mealId, false);
            return media.ready(mealId).stream().map(mapper::toVo).toList();
        });
    }

    private MealPhoto owned(UUID userId, UUID mealId, UUID photoId) {
        meals.owned(userId, mealId, false);
        return media.find(mealId, photoId).filter(photo -> "READY".equals(photo.getState()))
                .orElseThrow(() -> new AppException("PHOTO_NOT_FOUND", "照片不存在或無法存取", 404));
    }

    @Override
    public MealPhotoVo info(UUID userId, UUID mealId, UUID photoId) {
        return transactions.executeReadOnly(() -> mapper.toVo(owned(userId, mealId, photoId)));
    }

    @Override
    public void copyTo(UUID userId, UUID mealId, UUID photoId, OutputStream output) throws IOException {
        String key = transactions.executeReadOnly(() -> owned(userId, mealId, photoId).getObjectKey());
        try (InputStream input = storage.open(key)) {
            input.transferTo(output);
        }
    }

    @Override
    public void delete(UUID userId, UUID mealId, UUID photoId) {
        transactions.runWritable(() -> {
            meals.owned(userId, mealId, true);
            MealPhoto photo = owned(userId, mealId, photoId);
            photo.setState("DELETED");
            media.save(photo);
            media.schedule(photo.getObjectKey(), new Date(System.currentTimeMillis() + 3_600_000));
        });
    }
}

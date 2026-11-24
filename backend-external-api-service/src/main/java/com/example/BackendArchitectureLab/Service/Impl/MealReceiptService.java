package com.example.BackendArchitectureLab.Service.Impl;

import com.example.BackendArchitectureLab.Config.MealProperties;
import com.example.BackendArchitectureLab.DataAccess.MealLineDataAccess;
import com.example.BackendArchitectureLab.DataAccess.MealMediaDataAccess;
import com.example.BackendArchitectureLab.DataAccess.MealReceiptDataAccess;
import com.example.BackendArchitectureLab.DataAccess.MealRecordDataAccess;
import com.example.BackendArchitectureLab.Entity.MealReceipt;
import com.example.BackendArchitectureLab.Entity.MealReceiptPhoto;
import com.example.BackendArchitectureLab.Entity.MealRecord;
import com.example.BackendArchitectureLab.Exception.AppException;
import com.example.BackendArchitectureLab.Mapper.MealPhotoMapper;
import com.example.BackendArchitectureLab.Service.IMealReceiptService;
import com.example.BackendArchitectureLab.Service.MealLocalOcrEngine;
import com.example.BackendArchitectureLab.Service.MealPhotoStorage;
import com.example.BackendArchitectureLab.Util.MealImageValidator;
import com.example.BackendArchitectureLab.Util.MealImageValidator.ValidatedImage;
import com.example.BackendArchitectureLab.Util.TransactionExecutor;
import com.example.BackendArchitectureLab.Vo.MealPhotoVo;
import com.example.BackendArchitectureLab.Vo.MealReceiptOcrCandidateVo;
import com.example.BackendArchitectureLab.Vo.MealReceiptVo;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MealReceiptService implements IMealReceiptService {
    private final MealReceiptDataAccess receipts;
    private final MealRecordDataAccess meals;
    private final MealLineDataAccess lineDataAccess;
    private final MealMediaDataAccess mediaDataAccess;
    private final MealPhotoStorage storage;
    private final MealImageValidator imageValidator;
    private final MealLocalOcrEngine ocrEngine;
    private final MealPhotoMapper photoMapper;
    private final MealProperties properties;
    private final TransactionExecutor transactions;
    private final ObjectMapper objectMapper;

    @Override
    public MealReceiptVo createReceipt(UUID userId, UUID operationId) {
        if (operationId == null) {
            throw new AppException("INVALID_OPERATION_ID", "必須提供操作識別碼", 400);
        }
        String opKey = operationId.toString();
        String fingerprint = "create-receipt:" + opKey;
        return transactions.executeWritable(() -> {
            MealReceipt existing = receipts.byIdempotencyKey(userId, opKey).orElse(null);
            if (existing != null) {
                if (!Objects.equals(existing.getRequestFingerprint(), fingerprint)) {
                    throw new AppException("OPERATION_CONFLICT", "相同操作識別碼不能提交不同內容", 409);
                }
                if (existing.isDeleted()) {
                    throw new AppException("MEAL_RECEIPT_DELETED", "此消費明細已刪除", 410);
                }
                return toVo(userId, existing);
            }
            MealReceipt created = new MealReceipt();
            created.setOwnerUserId(userId);
            created.setIdempotencyKey(opKey);
            created.setRequestFingerprint(fingerprint);
            created.setOcrStatus("PENDING");
            created.setOcrCandidatesJson("[]");
            created.setDeleted(false);
            return toVo(userId, receipts.saveReceipt(created));
        });
    }

    @Override
    public MealPhotoVo uploadReceiptPhoto(UUID userId, UUID receiptId, UUID operationId, MultipartFile file) throws IOException {
        if (operationId == null) {
            throw new AppException("INVALID_OPERATION_ID", "必須提供圖片操作識別碼", 400);
        }
        String opKey = operationId.toString();
        try (ValidatedImage image = imageValidator.validate(file)) {
            MealPhotoVo existingVo = transactions.executeReadOnly(() ->
                    receipts.photoByIdempotencyKey(receiptId, userId, opKey)
                            .map(existing -> {
                                if (!Objects.equals(existing.getRequestFingerprint(), image.hash())) {
                                    throw new AppException("OPERATION_CONFLICT", "相同操作識別碼不能上傳不同明細圖片", 409);
                                }
                                return photoMapper.toReceiptPhotoVo(existing);
                            })
                            .orElse(null));
            if (existingVo != null) {
                return existingVo;
            }

            UUID photoId = UUID.randomUUID();
            String ext = "image/png".equals(image.contentType()) ? ".png" : ".jpg";
            String objectKey = "receipts/" + userId + "/" + receiptId + "/" + photoId + ext;
            transactions.runWritable(() -> {
                MealReceipt receipt = receipts.owned(userId, receiptId, true);
                long currentCount = receipts.countPhotos(receipt.getId());
                if (currentCount >= properties.getMaxReceiptPhotos()) {
                    throw new AppException("MEAL_RECEIPT_PHOTO_LIMIT",
                            "單一消費明細最多上傳 " + properties.getMaxReceiptPhotos() + " 張圖片", 400);
                }
                mediaDataAccess.schedule(objectKey,
                        new Date(System.currentTimeMillis() + 15 * 60_000L));
            });

            storage.put(objectKey, image.file(), image.byteSize(), image.contentType());

            return transactions.executeWritable(() -> {
                MealReceipt receipt = receipts.owned(userId, receiptId, true);
                int sortOrder = (int) receipts.countPhotos(receipt.getId());
                MealReceiptPhoto photo = new MealReceiptPhoto();
                photo.setReceiptId(receipt.getId());
                photo.setOwnerUserId(userId);
                photo.setIdempotencyKey(opKey);
                photo.setRequestFingerprint(image.hash());
                photo.setBucketName(properties.getBucket());
                photo.setObjectKey(objectKey);
                photo.setOriginalFilename(file.getOriginalFilename());
                photo.setContentType(image.contentType());
                photo.setFileSizeBytes(image.byteSize());
                photo.setWidthPx(1);
                photo.setHeightPx(1);
                photo.setSha256Hex(image.hash());
                photo.setSortOrder(sortOrder);
                return photoMapper.toReceiptPhotoVo(receipts.savePhoto(photo));
            });
        }
    }

    @Override
    public MealReceiptVo runReceiptOcr(UUID userId, UUID receiptId) {
        List<MealReceiptPhoto> photoList = transactions.executeWritable(() -> {
            MealReceipt receipt = receipts.owned(userId, receiptId, true);
            receipt.setOcrStatus("PROCESSING");
            receipts.saveReceipt(receipt);
            return receipts.listPhotos(userId, receiptId);
        });

        List<Path> stagedFiles = new ArrayList<>();
        try {
            for (MealReceiptPhoto photo : photoList) {
                Path temp = Files.createTempFile("meal-receipt-ocr-", ".img");
                stagedFiles.add(temp);
                try (InputStream in = storage.open(photo.getObjectKey())) {
                    Files.copy(in, temp, StandardCopyOption.REPLACE_EXISTING);
                }
            }
            MealLocalOcrEngine.OcrExecutionResult result = ocrEngine.recognizeReceipt(stagedFiles);
            return transactions.executeWritable(() -> {
                MealReceipt receipt = receipts.owned(userId, receiptId, true);
                receipt.setOcrStatus(result.succeeded() ? "SUCCEEDED" : "FAILED");
                receipt.setOcrRawText(result.rawText());
                receipt.setOcrErrorMessage(result.errorMessage());
                try {
                    receipt.setOcrCandidatesJson(objectMapper.writeValueAsString(result.candidates()));
                } catch (Exception ex) {
                    receipt.setOcrCandidatesJson("[]");
                }
                return toVo(userId, receipts.saveReceipt(receipt));
            });
        } catch (AppException ex) {
            throw ex;
        } catch (Exception ex) {
            return transactions.executeWritable(() -> {
                MealReceipt receipt = receipts.owned(userId, receiptId, true);
                receipt.setOcrStatus("FAILED");
                receipt.setOcrErrorMessage("讀取明細圖片進行本機 OCR 失敗，請手動輸入金額與說明");
                receipt.setOcrCandidatesJson("[]");
                return toVo(userId, receipts.saveReceipt(receipt));
            });
        } finally {
            for (Path staged : stagedFiles) {
                try {
                    Files.deleteIfExists(staged);
                } catch (Exception ignored) {
                    // 忽略暫存檔清理失敗
                }
            }
        }
    }

    @Override
    public MealReceiptVo getReceipt(UUID userId, UUID receiptId) {
        return transactions.executeReadOnly(() -> {
            MealReceipt receipt = receipts.owned(userId, receiptId, false);
            return toVo(userId, receipt);
        });
    }

    @Override
    public MealPhotoVo receiptPhotoInfo(UUID userId, UUID receiptId, UUID photoId) {
        return transactions.executeReadOnly(() -> {
            receipts.owned(userId, receiptId, false);
            return photoMapper.toReceiptPhotoVo(receipts.ownedPhoto(userId, receiptId, photoId));
        });
    }

    @Override
    public void copyReceiptPhotoTo(UUID userId, UUID receiptId, UUID photoId, OutputStream output) throws IOException {
        String objectKey = transactions.executeReadOnly(() -> {
            receipts.owned(userId, receiptId, false);
            return receipts.ownedPhoto(userId, receiptId, photoId).getObjectKey();
        });
        try (InputStream in = storage.open(objectKey)) {
            in.transferTo(output);
        }
    }

    @Override
    public void deleteReceipt(UUID userId, UUID receiptId) {
        transactions.runWritable(() -> {
            MealReceipt receipt = receipts.owned(userId, receiptId, true);
            long activeMeals = meals.countActiveByReceipt(userId, receiptId);
            long activeDrafts = lineDataAccess.countActiveDraftsForReceipt(userId, receiptId, new Date());
            if (activeMeals > 0 || activeDrafts > 0) {
                throw new AppException("MEAL_RECEIPT_IN_USE", "此消費明細仍被正式餐點紀錄或有效草稿引用，無法刪除", 409);
            }
            List<MealReceiptPhoto> photos = receipts.listPhotos(userId, receiptId);
            Date now = new Date();
            for (MealReceiptPhoto photo : photos) {
                mediaDataAccess.schedule(photo.getObjectKey(), now);
            }
            receipts.deletePhotosByReceipt(receiptId);
            receipt.setDeleted(true);
            receipt.setDeletedAt(now);
            receipts.saveReceipt(receipt);
        });
    }

    public List<MealReceiptOcrCandidateVo> parseCandidatesJson(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<MealReceiptOcrCandidateVo>>() {
            });
        } catch (Exception ex) {
            return List.of();
        }
    }

    private MealReceiptVo toVo(UUID userId, MealReceipt receipt) {
        MealReceiptVo vo = new MealReceiptVo();
        vo.setId(receipt.getId());
        vo.setOcrStatus(receipt.getOcrStatus());
        vo.setOcrRawText(receipt.getOcrRawText());
        vo.setOcrErrorMessage(receipt.getOcrErrorMessage());
        vo.setCandidates(parseCandidatesJson(receipt.getOcrCandidatesJson()));
        vo.setPhotos(receipts.listPhotos(userId, receipt.getId()).stream()
                .map(photoMapper::toReceiptPhotoVo)
                .toList());
        vo.setRecordedItemIndexes(meals.findActiveByReceipt(userId, receipt.getId()).stream()
                .map(MealRecord::getReceiptItemIndex)
                .filter(Objects::nonNull)
                .distinct()
                .toList());
        return vo;
    }
}

package com.example.BackendArchitectureLab.Service;

import com.example.BackendArchitectureLab.Vo.MealPhotoVo;
import com.example.BackendArchitectureLab.Vo.MealReceiptVo;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.OutputStream;
import java.util.UUID;

public interface IMealReceiptService {
    MealReceiptVo createReceipt(UUID userId, UUID operationId);
    MealPhotoVo uploadReceiptPhoto(UUID userId, UUID receiptId, UUID operationId, MultipartFile file) throws IOException;
    MealReceiptVo runReceiptOcr(UUID userId, UUID receiptId);
    MealReceiptVo getReceipt(UUID userId, UUID receiptId);
    MealPhotoVo receiptPhotoInfo(UUID userId, UUID receiptId, UUID photoId);
    void copyReceiptPhotoTo(UUID userId, UUID receiptId, UUID photoId, OutputStream output) throws IOException;
    void deleteReceipt(UUID userId, UUID receiptId);
}

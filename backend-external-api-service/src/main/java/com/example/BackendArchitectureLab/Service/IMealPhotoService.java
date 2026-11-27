package com.example.BackendArchitectureLab.Service;

import com.example.BackendArchitectureLab.Vo.MealPhotoVo;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.UUID;

public interface IMealPhotoService {
    MealPhotoVo upload(UUID userId, UUID mealId, UUID operationId, MultipartFile file) throws IOException;
    List<MealPhotoVo> list(UUID userId, UUID mealId);
    MealPhotoVo info(UUID userId, UUID mealId, UUID photoId);
    void copyTo(UUID userId, UUID mealId, UUID photoId, OutputStream output) throws IOException;
    void delete(UUID userId, UUID mealId, UUID photoId);
}

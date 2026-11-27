package com.example.BackendArchitectureLab.Mapper;

import com.example.BackendArchitectureLab.Entity.MealPhoto;
import com.example.BackendArchitectureLab.Entity.MealReceiptPhoto;
import com.example.BackendArchitectureLab.Vo.MealPhotoVo;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface MealPhotoMapper {
    MealPhotoVo toVo(MealPhoto entity);

    @Mapping(target = "mealId", source = "receiptId")
    @Mapping(target = "byteSize", source = "fileSizeBytes")
    MealPhotoVo toReceiptPhotoVo(MealReceiptPhoto entity);
}

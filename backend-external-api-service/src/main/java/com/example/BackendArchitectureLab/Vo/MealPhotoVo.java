package com.example.BackendArchitectureLab.Vo;

import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiDto;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiField;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@ApiDto(description = "餐點照片資訊，不公開儲存位置")
public class MealPhotoVo {
    @ApiField(description = "照片識別碼", format = "uuid", example = "00000000-0000-0000-0000-000000000001")
    private UUID id;
    @ApiField(description = "餐點識別碼", format = "uuid", example = "00000000-0000-0000-0000-000000000001")
    private UUID mealId;
    @ApiField(description = "實際圖片格式", example = "image/jpeg")
    private String contentType;
    @ApiField(description = "圖片位元組數", example = "1024")
    private Long byteSize;
}

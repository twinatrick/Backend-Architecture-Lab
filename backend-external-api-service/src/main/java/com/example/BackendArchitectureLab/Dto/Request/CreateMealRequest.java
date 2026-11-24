package com.example.BackendArchitectureLab.Dto.Request;

import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiDto;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiField;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@ApiDto(description = "建立餐點，同一操作識別碼可安全重試")
public class CreateMealRequest extends MealDetailsRequest {
    @ApiField(description = "由客戶端產生的操作識別碼", format = "uuid", example = "00000000-0000-0000-0000-000000000001", required = true)
    @NotNull
    private UUID operationId;
}

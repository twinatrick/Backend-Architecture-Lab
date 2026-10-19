package com.example.BackendArchitectureLab.Dto.Request;

import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiDto;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiField;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ApiDto(description = "批次 UUID 查詢請求物件")
public class BatchIdsRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotEmpty(message = "查詢 ID 列表不可為空")
    @Size(max = 100, message = "單次批次查詢 ID 數量上限為 100 筆")
    @ApiField(description = "待查詢實體 ID 列表", required = true, format = "uuid", example = "[\"00000000-0000-0000-0000-000000000001\"]")
    private List<UUID> ids;
}

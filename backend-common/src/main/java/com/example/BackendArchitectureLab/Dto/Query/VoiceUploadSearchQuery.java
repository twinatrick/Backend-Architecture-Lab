package com.example.BackendArchitectureLab.Dto.Query;

import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiDto;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiField;
import com.example.BackendArchitectureLab.Dto.Query.BaseSearchQuery;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ApiDto(description = "語音上傳搜尋查詢參數")
public class VoiceUploadSearchQuery extends BaseSearchQuery {

    @ApiField(description = "原始檔案名稱（模糊查詢）", example = "audio")
    private String fileName;

    @ApiField(description = "語音處理狀態", example = "PROCESSED")
    private String status;
}

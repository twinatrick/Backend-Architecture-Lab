package com.example.BackendArchitectureLab.Vo.Search;

import com.example.BackendArchitectureLab.Vo.Common.BaseSearchQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Schema(description = "語音上傳搜尋查詢參數")
public class VoiceUploadSearchQuery extends BaseSearchQuery {

    @Schema(description = "原始檔案名稱（模糊查詢）", example = "audio")
    private String fileName;

    @Schema(description = "語音處理狀態", example = "PROCESSED")
    private String status;
}

package com.example.BackendArchitectureLab.Vo;

import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiDto;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiField;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
@ApiDto(description = "共用消費明細與本機 OCR 辨識結果")
public class MealReceiptVo {
    @ApiField(description = "消費明細識別碼", format = "uuid", example = "00000000-0000-0000-0000-000000000020")
    private UUID id;

    @ApiField(description = "本機 OCR 狀態（PENDING/PROCESSING/SUCCEEDED/FAILED）", example = "SUCCEEDED")
    private String ocrStatus;

    @ApiField(description = "本機 OCR 原始辨識文字", example = "紅燒牛肉麵 150\n滷蛋 20")
    private String ocrRawText;

    @ApiField(description = "OCR 失敗或降級提示訊息", example = "本機 OCR 無法辨識單品金額，請手動填寫金額與說明")
    private String ocrErrorMessage;

    @ApiField(description = "OCR 辨識候選品項清單", example = "[]")
    private List<MealReceiptOcrCandidateVo> candidates;

    @ApiField(description = "明細圖片清單（最多 5 張）", example = "[]")
    private List<MealPhotoVo> photos;

    @ApiField(description = "此明細已建立正式餐點紀錄的候選品項序號", example = "[0]")
    private List<Integer> recordedItemIndexes;
}

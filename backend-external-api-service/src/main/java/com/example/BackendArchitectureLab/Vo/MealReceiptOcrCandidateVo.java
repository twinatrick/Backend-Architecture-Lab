package com.example.BackendArchitectureLab.Vo;

import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiDto;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiField;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ApiDto(description = "消費明細本機 OCR 候選品項與金額")
public class MealReceiptOcrCandidateVo {
    @ApiField(description = "候選品項序號（由 0 起算）", example = "0")
    private int itemIndex;

    @ApiField(description = "辨識出的品項名稱", example = "紅燒牛肉麵")
    private String itemName;

    @ApiField(description = "辨識出的單品新臺幣金額", example = "150.00")
    private BigDecimal amount;
}

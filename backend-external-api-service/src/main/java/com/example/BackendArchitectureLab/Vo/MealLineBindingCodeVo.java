package com.example.BackendArchitectureLab.Vo;

import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiDto;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiField;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Date;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ApiDto(description = "飲食專用 LINE Bot 一次性綁定碼")
public class MealLineBindingCodeVo {
    @ApiField(description = "10 分鐘內有效的一次性綁定碼", example = "M8K29Q")
    private String bindingCode;

    @ApiField(description = "綁定碼到期時間", example = "2026-11-23T04:40:00Z")
    private Date expiresAt;
}

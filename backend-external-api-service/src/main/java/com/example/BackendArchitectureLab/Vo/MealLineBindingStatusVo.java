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
@ApiDto(description = "飲食專用 LINE Bot 帳號綁定狀態")
public class MealLineBindingStatusVo {
    @ApiField(description = "是否已綁定 LINE 帳號", example = "true")
    private boolean bound;

    @ApiField(description = "已綁定之 LINE 使用者識別碼", example = "U1234567890abcdef")
    private String lineUserId;

    @ApiField(description = "是否開啟每日 19:00 店況彙整通知", example = "true")
    private boolean notificationEnabled;

    @ApiField(description = "綁定時間", example = "2026-11-23T04:30:00Z")
    private Date boundAt;
}

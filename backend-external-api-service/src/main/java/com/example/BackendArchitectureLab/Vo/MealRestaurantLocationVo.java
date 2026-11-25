package com.example.BackendArchitectureLab.Vo;

import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiDto;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiField;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.Date;
import java.util.UUID;

@Getter
@Setter
@ApiDto(description = "使用者私有店家據點資訊")
public class MealRestaurantLocationVo {
    @ApiField(description = "店家據點識別碼", format = "uuid", example = "00000000-0000-0000-0000-000000000001")
    private UUID id;

    @ApiField(description = "Google Place 識別碼（若有）", format = "uuid", example = "00000000-0000-0000-0000-000000000001")
    private String googlePlaceId;

    @ApiField(description = "Google 地圖網址", example = "https://maps.google.com/?cid=123456")
    private String googleMapsUrl;

    @ApiField(description = "目前顯示店名", example = "巷口麵店")
    private String displayName;

    @ApiField(description = "樓層", example = "1F")
    private String floorText;

    @ApiField(description = "位置補充說明", example = "市場入口右側")
    private String locationDescription;

    @ApiField(description = "緯度", example = "25.0339640")
    private BigDecimal latitude;

    @ApiField(description = "經度", example = "121.5644680")
    private BigDecimal longitude;

    @ApiField(description = "營業狀態（OPEN/TEMPORARILY_CLOSED/PERMANENTLY_CLOSED/MOVED/UNKNOWN）", example = "OPEN")
    private String businessStatus;

    @ApiField(description = "搬遷來源舊據點識別碼", format = "uuid", example = "00000000-0000-0000-0000-000000000009")
    private UUID movedFromLocationId;

    @ApiField(description = "搬遷目標新據點識別碼", format = "uuid", example = "00000000-0000-0000-0000-000000000011")
    private UUID movedToLocationId;

    @ApiField(description = "是否待使用者確認營業狀態", example = "false")
    private boolean pendingUserConfirmation;

    @ApiField(description = "最後檢查時間", example = "2026-11-23T04:30:00Z")
    private Date lastCheckedAt;

    @ApiField(description = "下次每季檢查到期時間", example = "2027-01-01T00:00:00Z")
    private Date nextCheckDueAt;
}

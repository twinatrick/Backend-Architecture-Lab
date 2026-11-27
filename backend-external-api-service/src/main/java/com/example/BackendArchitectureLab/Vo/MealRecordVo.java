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
@ApiDto(description = "個人餐點紀錄，不包含儲存物件位址")
public class MealRecordVo {
    @ApiField(description = "餐點識別碼", format = "uuid", example = "00000000-0000-0000-0000-000000000001")
    private UUID id;
    @ApiField(description = "更新時以 If-Match 傳回的修訂碼", example = "0")
    private Long revision;
    @ApiField(description = "餐點名稱", example = "牛肉麵")
    private String mealName;
    @ApiField(description = "用餐時間", example = "2026-11-23T04:30:00Z")
    private Date eatenAt;
    @ApiField(description = "實付新臺幣金額", example = "150.00")
    private BigDecimal amount;
    @ApiField(description = "金額依據", example = "現場付款，未取得收據")
    private String amountExplanation;
    @ApiField(description = "關聯之共用消費明細識別碼", format = "uuid", example = "00000000-0000-0000-0000-000000000020")
    private UUID receiptId;
    @ApiField(description = "關聯之消費明細候選品項序號", example = "0")
    private Integer receiptItemIndex;
    @ApiField(description = "關聯之私有店家據點識別碼", format = "uuid", example = "00000000-0000-0000-0000-000000000010")
    private UUID restaurantLocationId;
    @ApiField(description = "當時店名（歷史快照）", example = "巷口麵店")
    private String restaurantName;
    @ApiField(description = "當時位置說明（歷史快照）", example = "市場入口右側")
    private String locationDescription;
    @ApiField(description = "當時樓層（歷史快照）", example = "1F")
    private String floor;
    @ApiField(description = "Google 地圖網址", example = "https://maps.google.com/?cid=123456")
    private String googleMapsUrl;
    @ApiField(description = "緯度", example = "25.0339640")
    private BigDecimal latitude;
    @ApiField(description = "經度", example = "121.5644680")
    private BigDecimal longitude;
    @ApiField(description = "店家據點目前顯示名稱", example = "巷口麵店（新址）")
    private String currentRestaurantName;
    @ApiField(description = "店家據點目前營業狀態（OPEN/TEMPORARILY_CLOSED/PERMANENTLY_CLOSED/MOVED/UNKNOWN）", example = "OPEN")
    private String currentRestaurantStatus;
    @ApiField(description = "若店家已搬遷，指向的新據點識別碼", format = "uuid", example = "00000000-0000-0000-0000-000000000011")
    private UUID movedToLocationId;
    @ApiField(description = "評分", example = "4")
    private Integer rating;
    @ApiField(description = "心得", example = "湯頭清爽")
    private String review;
}

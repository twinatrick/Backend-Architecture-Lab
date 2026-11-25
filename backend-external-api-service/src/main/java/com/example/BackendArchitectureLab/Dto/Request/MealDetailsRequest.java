package com.example.BackendArchitectureLab.Dto.Request;

import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiDto;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiField;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@ApiDto(description = "餐點內容，金額單位為新臺幣")
public class MealDetailsRequest {
    @ApiField(description = "餐點名稱", example = "牛肉麵", required = true)
    @NotBlank
    @Size(max = 200)
    private String mealName;
    @ApiField(description = "含時區的用餐時間", example = "2026-11-23T12:30:00+08:00", required = true)
    @NotNull
    private OffsetDateTime eatenAt;
    @NotNull
    @DecimalMin("0")
    @DecimalMax("999999.99")
    @Digits(integer = 6, fraction = 2)
    @ApiField(description = "單一品項實付新臺幣金額", example = "150.00", required = true)
    private BigDecimal amount;
    @ApiField(description = "無消費明細或覆寫明細金額時的金額說明，與心得分開", example = "現場付款，未取得收據")
    @Size(max = 500)
    private String amountExplanation;
    @ApiField(description = "共用消費明細識別碼", format = "uuid", example = "00000000-0000-0000-0000-000000000001")
    private UUID receiptId;
    @ApiField(description = "對應之消費明細 OCR 候選品項序號", example = "0")
    @Min(0)
    private Integer receiptItemIndex;
    @ApiField(description = "若該收據品項已建立過餐點紀錄，是否確認再次建立", example = "false")
    private Boolean confirmDuplicateReceiptItem;
    @ApiField(description = "明確指定的私有店家據點識別碼", format = "uuid", example = "00000000-0000-0000-0000-000000000001")
    private UUID restaurantLocationId;
    @ApiField(description = "Google 地圖網址", example = "https://maps.google.com/?q=place_id:ChIJN1t_tDeuEmsRUsoyG83frY4")
    @Size(max = 1024)
    private String googleMapsUrl;
    @ApiField(description = "Google Place 識別碼", format = "uuid", example = "00000000-0000-0000-0000-000000000001")
    @Size(max = 255)
    private String googlePlaceId;
    @ApiField(description = "當時自填店名（歷史快照）", example = "巷口麵店")
    @Size(max = 200)
    private String restaurantName;
    @ApiField(description = "當時自填位置說明（歷史快照）", example = "市場入口右側")
    @Size(max = 500)
    private String locationDescription;
    @ApiField(description = "當時樓層（歷史快照）", example = "1F")
    @Size(max = 50)
    private String floor;
    @ApiField(description = "店家緯度", example = "25.0339640")
    @DecimalMin("-90.0")
    @DecimalMax("90.0")
    private BigDecimal latitude;
    @ApiField(description = "店家經度", example = "121.5644680")
    @DecimalMin("-180.0")
    @DecimalMax("180.0")
    private BigDecimal longitude;
    @ApiField(description = "遇到已歇業、已搬遷或多候選據點時之明確確認動作（BACKFILL/REOPENED/NEW_BUSINESS/CREATE_NEW）", example = "BACKFILL")
    @Size(max = 32)
    private String confirmLocationAction;
    @ApiField(description = "一至五星評分，空值代表未評分", example = "4")
    @Min(1)
    @Max(5)
    private Integer rating;
    @ApiField(description = "口味心得", example = "湯頭清爽")
    @Size(max = 4000)
    private String review;
}

package com.example.BackendArchitectureLab.Dto.Query;

import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiDto;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiField;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
@ApiDto(description = "當前使用者的餐點搜尋條件，日期採臺北時區")
public class MealSearchQuery extends BaseSearchQuery {
    public MealSearchQuery() {
        setSortBy("eatenAt");
    }

    @ApiField(description = "餐點名稱包含文字", example = "牛肉")
    @Size(max = 200)
    private String mealName;
    @ApiField(description = "當時店名包含文字", example = "麵店")
    @Size(max = 200)
    private String restaurantName;
    @ApiField(description = "指定私有店家據點識別碼", format = "uuid", example = "00000000-0000-0000-0000-000000000010")
    private UUID restaurantLocationId;
    @ApiField(description = "店家目前營業狀態（OPEN/TEMPORARILY_CLOSED/PERMANENTLY_CLOSED/MOVED/UNKNOWN）", example = "OPEN")
    @Size(max = 32)
    private String businessStatus;
    @ApiField(description = "用餐起日，包含當日", example = "2026-11-01")
    private LocalDate dateFrom;
    @ApiField(description = "用餐迄日，包含當日", example = "2026-11-30")
    private LocalDate dateTo;
    @ApiField(description = "實付金額下限，包含邊界", example = "0")
    private BigDecimal amountMin;
    @ApiField(description = "實付金額上限，包含邊界", example = "200")
    private BigDecimal amountMax;
    @ApiField(description = "評分下限", example = "1")
    private Integer ratingMin;
    @ApiField(description = "評分上限", example = "5")
    private Integer ratingMax;
    @ApiField(description = "只查未評分，不能與評分區間同用", example = "false")
    private Boolean unrated;
    @ApiField(description = "是否有可查看的餐點照片，空值代表不限", example = "true")
    private Boolean hasPhotos;
}

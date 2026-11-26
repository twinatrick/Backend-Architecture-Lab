package com.example.BackendArchitectureLab.Dto.Request;

import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiDto;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiField;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@ApiDto(description = "建立或更新私有店家據點請求")
public class ResolveRestaurantLocationRequest {
    @ApiField(description = "既有據點識別碼（指定更新或確認時使用）", format = "uuid", example = "00000000-0000-0000-0000-000000000001")
    private UUID locationId;

    @ApiField(description = "店家名稱", example = "巷口麵店", required = true)
    @NotBlank
    @Size(max = 200)
    private String displayName;

    @ApiField(description = "樓層", example = "1F")
    @Size(max = 64)
    private String floorText;

    @ApiField(description = "位置說明", example = "市場入口右側")
    @Size(max = 500)
    private String locationDescription;

    @ApiField(description = "Google 地圖網址", example = "https://maps.google.com/?q=place_id:ChIJN1t_tDeuEmsRUsoyG83frY4")
    @Size(max = 1024)
    private String googleMapsUrl;

    @ApiField(description = "Google Place 識別碼", example = "ChIJN1t_tDeuEmsRUsoyG83frY4")
    @Size(max = 255)
    private String googlePlaceId;

    @ApiField(description = "緯度", example = "25.0339640")
    @DecimalMin("-90.0")
    @DecimalMax("90.0")
    private BigDecimal latitude;

    @ApiField(description = "經度", example = "121.5644680")
    @DecimalMin("-180.0")
    @DecimalMax("180.0")
    private BigDecimal longitude;

    @ApiField(description = "手動確認或更新之營業狀態（OPEN/TEMPORARILY_CLOSED/PERMANENTLY_CLOSED/UNKNOWN）", example = "OPEN")
    @Size(max = 32)
    private String businessStatus;

    @ApiField(description = "衝突或歇業處置方式（USE_EXISTING/CREATE_NEW/REOPENED/NEW_BUSINESS）", example = "USE_EXISTING")
    @Size(max = 32)
    private String resolutionAction;
}

package com.example.BackendArchitectureLab.Config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@ConfigurationProperties(prefix = "meals")
@Validated
@Getter
@Setter
public class MealProperties {
    @NotBlank
    private String bucket = "meal-photos";
    @Min(1)
    @Max(10485760)
    private long maxPhotoBytes = 10_485_760;
    @Min(1)
    @Max(10)
    private int maxPhotos = 10;
    @Min(1)
    @Max(5)
    private int maxReceiptPhotos = 5;
    @Min(1)
    @Max(20000000)
    private long maxPixels = 20_000_000;
    @Min(1)
    @Max(20000)
    private int maxDimension = 12_000;
    @Min(1)
    @Max(30)
    private int draftExpirationDays = 7;
    @Min(1)
    @Max(60)
    private int lineBindingCodeMinutes = 10;
    private boolean googleEnabled = false;
    private boolean googleAuthorizedBackgroundTracking = false;
    private String googleApiKey = "";
    @Min(1)
    @Max(5000)
    private int googleMonthlyRequestCap = 1_000;
    private boolean ocrEnabled = false;
    @NotBlank
    private String ocrCommand = "tesseract";
    @NotBlank
    private String ocrLanguage = "chi_tra+eng";
    @Min(1)
    @Max(60)
    private int ocrTimeoutSeconds = 15;
    @Min(1)
    @Max(16)
    private int ocrMaxQueueSize = 4;
    private String lineChannelSecret = "";
    private String lineChannelToken = "";
}

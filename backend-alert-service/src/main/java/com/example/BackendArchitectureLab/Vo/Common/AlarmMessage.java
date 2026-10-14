package com.example.BackendArchitectureLab.Vo.Common;

import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiDto;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiField;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Date;

@Getter
@Setter
@NoArgsConstructor
@ApiDto(description = "告警事件訊息物件")
public class AlarmMessage {

    @ApiField(description = "告警等級 (ERROR, WARN, INFO)", example = "WARN")
    private String level;

    @ApiField(description = "來源識別 (Web, API, DB)", example = "API")
    private String source;

    @ApiField(description = "告警訊息內容", example = "Water level exceeded upper threshold")
    private String message;

    @ApiField(description = "發送時間戳記")
    private Date timestamp;
}

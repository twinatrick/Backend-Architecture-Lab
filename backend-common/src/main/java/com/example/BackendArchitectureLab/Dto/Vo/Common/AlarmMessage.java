package com.example.BackendArchitectureLab.Dto.Vo.Common;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Date;

@Getter
@Setter
@NoArgsConstructor
public class AlarmMessage {

    private String level;       // 告警等級，例如 ERROR, WARN, INFO
    private String source;      // 來源，例如 Web, API, DB
    private String message;     // 告警內容
    private Date timestamp;     // 發送時間
}

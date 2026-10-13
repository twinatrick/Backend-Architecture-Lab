package com.example.BackendArchitectureLab.Vo.AquarkUse;

import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiDto;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiField;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;

@Getter
@Setter
@ApiDto(description = "Aquark 站點原始感測資料")
public class RowData {
    @ApiField(description = "測站代碼", example = "station-001")
    private String station_id;
    @JsonProperty("CSQ")
    @ApiField(description = "信號品質指標", example = "28")
    private String CSQ;
    @ApiField(description = "觀測時間字串", example = "2026-10-14 12:00:00")
    private String obs_time;
    @ApiField(description = "傳輸時間戳記")
    private Date trans_time;
    @JsonProperty("rain_d")
    @ApiField(description = "雨量數值", example = "0.0")
    private float rain_d;
    @JsonProperty("sensor")
    @ApiField(description = "感測器明細資料")
    private Sensor sensor;

    public AquarkDataRaw transToDbData() {
        AquarkDataRaw data = new AquarkDataRaw();
        data.setStation_id(this.station_id);
        data.setCSQ(this.CSQ);
        data.setObs_time(this.obs_time);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        LocalDateTime dateTime = LocalDateTime.parse(this.obs_time, formatter);
        data.setTrans_time(Timestamp.valueOf(dateTime));
        data.setRain_d(this.rain_d);

        if (this.sensor == null) {
            return data;
        }
        if (this.sensor.getStickTxRh() != null) {
            data.setMoisture(this.sensor.getStickTxRh().getRh());
            data.setTemperature(this.sensor.getStickTxRh().getTx());
        }
        if (this.sensor.getUltrasonicLevel() != null)
            data.setEcho(this.sensor.getUltrasonicLevel().getEcho());
        if (this.sensor.getVolt() != null) {
            data.setV1(this.sensor.getVolt().getV1());
            data.setV2(this.sensor.getVolt().getV2());
            data.setV3(this.sensor.getVolt().getV3());
            data.setV4(this.sensor.getVolt().getV4());
            data.setV5(this.sensor.getVolt().getV5());
            data.setV6(this.sensor.getVolt().getV6());
            data.setV7(this.sensor.getVolt().getV7());
        }

        if (this.sensor.getWaterSpeedAquark() != null) {
            data.setWaterSpeedAquark(this.sensor.getWaterSpeedAquark().getSpeed());
        }
        return data;
    }
}

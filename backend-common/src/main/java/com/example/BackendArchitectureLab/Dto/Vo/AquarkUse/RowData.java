package com.example.BackendArchitectureLab.Dto.Vo.AquarkUse;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;

@Getter
@Setter
public class RowData {
    private String station_id;
    @JsonProperty("CSQ")
    private String CSQ;
    private String obs_time;
    private Date trans_time;
    @JsonProperty("rain_d")
    private float rain_d;
    @JsonProperty("sensor")
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
        if (this.sensor.getUltrasonic_Level() != null)
            data.setEcho(this.sensor.getUltrasonic_Level().getEcho());
        if (this.sensor.getVolt() != null) {
            data.setV1(this.sensor.getVolt().getV1());
            data.setV2(this.sensor.getVolt().getV2());
            data.setV3(this.sensor.getVolt().getV3());
            data.setV4(this.sensor.getVolt().getV4());
            data.setV5(this.sensor.getVolt().getV5());
            data.setV6(this.sensor.getVolt().getV6());
            data.setV7(this.sensor.getVolt().getV7());
        }

        if (this.sensor.getWater_speed_aquark() != null) {
            data.setWaterSpeedAquark(this.sensor.getWater_speed_aquark().getSpeed());
        }
        return data;
    }
}

package com.example.BackendArchitectureLab.Vo.AquarkUse;

import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiDto;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiField;
import lombok.Getter;
import lombok.Setter;

import java.util.Calendar;
import java.util.Date;

@Getter
@Setter
@ApiDto(description = "Aquark 感測器原始資料模型")
public class AquarkDataRaw {
    @ApiField(description = "資料唯一識別碼", example = "550e8400-e29b-41d4-a716-446655440000", format = "uuid")
    private String id;

    @ApiField(description = "觀測站點代碼", example = "ST001")
    private String station_id;//站點
    @ApiField(description = "訊號強度指標", example = "25")
    private String CSQ;
    @ApiField(description = "觀測時間字串", example = "2026-04-12 08:00:00")
    private String obs_time;
    @ApiField(description = "傳輸時間戳記", example = "2026-04-12T08:00:00Z")
    private Date trans_time;
    @ApiField(description = "日累積雨量", example = "12.5")
    private float rain_d;//日累積雨量

    @ApiField(description = "土壤或環境濕度", example = "65.2")
    private float moisture;//濕度
    @ApiField(description = "環境溫度", example = "26.4")
    private float temperature;//溫度

    @ApiField(description = "水位空高數值", example = "1.85")
    private float echo; //水位空高

    @ApiField(description = "水流速度數值", example = "0.75")
    private float waterSpeedAquark;//水流速


    @ApiField(description = "通道 1 電壓（鋰電池）", example = "12.6")
    private float v1;//鋰電池電壓
    @ApiField(description = "通道 2 電壓", example = "12.4")
    private float v2;
    @ApiField(description = "通道 3 電壓", example = "5.0")
    private float v3;
    @ApiField(description = "通道 4 電壓", example = "3.3")
    private float v4;
    @ApiField(description = "通道 5 電壓（太陽能板 1）", example = "18.2")
    private float v5;//太陽能板 1
    @ApiField(description = "通道 6 電壓（太陽能板 2）", example = "18.1")
    private float v6;//太陽能板 1
    @ApiField(description = "通道 7 電壓", example = "12.0")
    private float v7;
    @ApiField(description = "是否處於尖峰時段", example = "true")
    private boolean isPeak;//是否為尖峰


    private boolean isPeakCheck(){
        Calendar cal=Calendar.getInstance();
        cal.setTime(this.trans_time);
        int day=cal.getFirstDayOfWeek();
        int week=cal.get(Calendar.DAY_OF_WEEK)-day;
        if (week==4||week==5 ){
            return true;
        }else if (week==6||week==0) {
            return false;
        }
        int year=cal.get(Calendar.YEAR);
        int month=cal.get(Calendar.MONTH);
        int day1=cal.get(Calendar.DAY_OF_MONTH);

        Calendar start=Calendar.getInstance();
        Calendar end=Calendar.getInstance();
        start.set(year,month,day1,7,30,0);
        end.set(year,month,day1,17,30,0);
        long start1=start.getTimeInMillis();
        long end1=end.getTimeInMillis();
        long centerTime=cal.getTimeInMillis();
        return centerTime >= start1 && centerTime <= end1;
    }

    private String DateToString(Date date){
        Calendar cal=Calendar.getInstance();
        cal.setTime(date);
        int year=cal.get(Calendar.YEAR);
        int month=cal.get(Calendar.MONTH)+1;
        int day=cal.get(Calendar.DAY_OF_MONTH);
        return year+"-"+month+"-"+day;
    }
    private String DateHourToString(Date date){
        Calendar cal=Calendar.getInstance();
        cal.setTime(date);
        int year=cal.get(Calendar.YEAR);
        int month=cal.get(Calendar.MONTH)+1;
        int day=cal.get(Calendar.DAY_OF_MONTH);
        int hour=cal.get(Calendar.HOUR_OF_DAY);
        return year+"-"+month+"-"+day+" "+hour;
    }
    public AverageAquark toAverageAquark(){
        AverageAquark averageAquark=new AverageAquark();
        averageAquark.setStation_id(this.station_id);
        averageAquark.setDate(DateToString(this.trans_time));
        averageAquark.setDateHour(DateHourToString(this.trans_time));
        averageAquark.setRain_d(this.rain_d);
        averageAquark.setMoisture(this.moisture);
        averageAquark.setTemperature(this.temperature);
        averageAquark.setEcho(this.echo);
        averageAquark.setWaterSpeedAquark(this.waterSpeedAquark);
        averageAquark.setV1(this.v1);
        averageAquark.setV2(this.v2);
        averageAquark.setV3(this.v3);
        averageAquark.setV4(this.v4);
        averageAquark.setV5(this.v5);
        averageAquark.setV6(this.v6);
        averageAquark.setV7(this.v7);
        return averageAquark;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }
}

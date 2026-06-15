package com.example.BackendArchitectureLab.Dto.Vo.AquarkUse;

import lombok.Getter;
import lombok.Setter;

import java.util.Calendar;
import java.util.Date;

@Getter
@Setter
public class AquarkDataRaw {
    private String id;

    private String station_id;//站點
    private String CSQ;
    private String obs_time;
    private Date trans_time;
    private float rain_d;//日累積雨量

    private float moisture;//濕度
    private float temperature;//溫度

    private float echo; //水位空高

    private float waterSpeedAquark;//水流速


    private float v1;//鋰電池電壓
    private float v2;
    private float v3;
    private float v4;
    private float v5;//太陽能板 1
    private float v6;//太陽能板 1
    private float v7;
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

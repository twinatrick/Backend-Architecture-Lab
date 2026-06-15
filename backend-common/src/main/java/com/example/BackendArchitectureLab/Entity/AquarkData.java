package com.example.BackendArchitectureLab.Entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;

@Getter
@Setter
@Entity
@Table(name = "aquark_data")
public class AquarkData extends BaseEntity {
    @Column(name = "station_id")
    private String station_id;//站點
    @Column(name = "csq")
    private String CSQ;
    @Column(name = "trans_time")
    private Date trans_time;
    @Column(name = "rain_d")
    private float rain_d;//日累積雨量

    @Column(name = "moisture")
    private float moisture;//濕度
    @Column(name = "temperature")
    private float temperature;//溫度

    @Column(name = "echo")
    private float echo; //水位空高

    @Column(name = "water_speed_aquark")
    private float waterSpeedAquark;//水流速

    @Column(name = "is_peak")
    private boolean isPeak;//是否為尖峰

    @Column(name = "v1")
    private float v1;//鋰電池電壓
    @Column(name = "v2")
    private float v2;
    @Column(name = "v3")
    private float v3;
    @Column(name = "v4")
    private float v4;
    @Column(name = "v5")
    private float v5;//太陽能板 1
    @Column(name = "v6")
    private float v6;//太陽能板 1
    @Column(name = "v7")
    private float v7;

}

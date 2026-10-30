package com.example.BackendArchitectureLab.Vo.AquarkUse;

import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiDto;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiField;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;

@Getter
@Setter
@ApiDto(description = "Aquark 感測器平均統計資料模型")
public class AverageAquark {

        @ApiField(description = "觀測站點代碼", example = "ST001")
        private String station_id;//站點
        @ApiField(description = "資料傳輸時間", example = "2026-04-12T08:00:00Z")
        private Date trans_time;
        @ApiField(description = "統計日期字串", example = "2026-04-12")
        private String date;

        @ApiField(description = "統計日期與小時字串", example = "2026-04-12 08")
        private String dateHour;
        @ApiField(description = "平均日累積雨量", example = "10.2")
        private float rain_d;//日累積雨量

        @ApiField(description = "平均環境濕度", example = "68.0")
        private float moisture;//濕度
        @ApiField(description = "平均環境溫度", example = "25.8")
        private float temperature;//溫度

        @ApiField(description = "平均水位空高", example = "1.90")
        private float echo; //水位空高

        @ApiField(description = "平均水流速度", example = "0.65")
        private float waterSpeedAquark;//水流速


        @ApiField(description = "平均通道 1 電壓（鋰電池）", example = "12.5")
        private float v1;//鋰電池電壓
        @ApiField(description = "平均通道 2 電壓", example = "12.3")
        private float v2;
        @ApiField(description = "平均通道 3 電壓", example = "5.0")
        private float v3;
        @ApiField(description = "平均通道 4 電壓", example = "3.3")
        private float v4;
        @ApiField(description = "平均通道 5 電壓（太陽能板 1）", example = "17.8")
        private float v5;//太陽能板 1
        @ApiField(description = "平均通道 6 電壓（太陽能板 2）", example = "17.9")
        private float v6;//太陽能板 1
        @ApiField(description = "平均通道 7 電壓", example = "12.0")
        private float v7;



    }

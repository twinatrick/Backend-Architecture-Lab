package com.example.BackendArchitectureLab.Service.impl;

import com.example.BackendArchitectureLab.Dto.Vo.Common.AlarmMessage;
import com.example.BackendArchitectureLab.Service.IAlertCheckLimitService;
import com.example.BackendArchitectureLab.Service.IApiFetcher;
import com.example.BackendArchitectureLab.Service.IAquarkDataService;
import com.example.BackendArchitectureLab.Service.ICheckApiService;
import com.example.BackendArchitectureLab.Service.IAlarmService;
import com.example.BackendArchitectureLab.Dto.Vo.AquarkUse.RowData;
import com.example.BackendArchitectureLab.Dto.Vo.AquarkUse.aquarkApiReturnVo;
import com.example.BackendArchitectureLab.Dto.Vo.AquarkUse.AquarkDataRaw;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class CheckApiService implements ICheckApiService {
    @Autowired
    private IApiFetcher apiFetcher;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private IAlarmService alarmService;
    @Autowired
    private IAquarkDataService aquarkDataService;
    @Autowired
    private IAlertCheckLimitService alertCheckLimitService;

    @Override
    public String getApiOnlyUrl(String url) throws IOException {
        return apiFetcher.get(url);
    }

    @Override
    public void getAquarkApiData() throws IOException {
        List<String> urlList = List.of(
                "https://app.aquark.com.tw/api/raw/Angle2024/240627",
                "https://app.aquark.com.tw/api/raw/Angle2024/240706",
                "https://app.aquark.com.tw/api/raw/Angle2024/240708",
                "https://app.aquark.com.tw/api/raw/Angle2024/240709",
                "https://app.aquark.com.tw/api/raw/Angle2024/240710");
        log.debug("CheckApiService.getAquarkApiData");
        List<AquarkDataRaw> aquarkDataRawList = new ArrayList<>();
        for (String url : urlList) {
            String result = getApiOnlyUrl(url);
            aquarkApiReturnVo aquarkApiReturnVo =objectMapper.readValue(result, aquarkApiReturnVo.class);
            log.debug("aquarkApiReturnVo Raw size: {}", aquarkApiReturnVo.getRaw().size());
            aquarkDataRawList.addAll(aquarkApiReturnVo.getRaw().stream().map(RowData::transToDbData).toList());

        }
        List<AlarmMessage> alarmMessages = new ArrayList<>();
        for (AquarkDataRaw data : aquarkDataRawList) {
            AquarkDataRaw aquarkData = aquarkDataService.insertAquarkData(data);
            alarmMessages.addAll(checkValue(aquarkData)) ;
        }
        if (!alarmMessages.isEmpty()){
            alarmService.processAlarm(alarmMessages);
        }
    }
    List<AlarmMessage> checkValue(AquarkDataRaw data) {

        AlarmMessage alarmMessage = new AlarmMessage();
        alarmMessage.setLevel("ERROR");
        String message = data.getStation_id() + "Date:" + data.getTrans_time().toString();
        int alertLevel = 0;
        if (data.getV1() > alertCheckLimitService.getLimit("aquark_data", "v1").getLimitValue()) {
            message += "v1超出限制,";
            alertLevel++;

        }
        if (data.getV2() > alertCheckLimitService.getLimit("aquark_data", "v2").getLimitValue()) {
            message += "v2超出限制,";
            alertLevel++;

        }
        if (data.getV3() > alertCheckLimitService.getLimit("aquark_data", "v3").getLimitValue()) {
            message += "v3超出限制,";
            alertLevel++;

        }
        if (data.getV4() > alertCheckLimitService.getLimit("aquark_data", "v4").getLimitValue()) {
            message += "v4超出限制,";
            alertLevel++;

        }
        if (data.getV5() > alertCheckLimitService.getLimit("aquark_data", "v5").getLimitValue()) {
            message += "v5超出限制,";
            alertLevel++;


        }
        if (data.getV6() > alertCheckLimitService.getLimit("aquark_data", "v6").getLimitValue()) {
            message += "v6超出限制,";
            alertLevel++;


        }
        if (data.getV7() > alertCheckLimitService.getLimit("aquark_data", "v7").getLimitValue()) {
            message += "v7超出限制,";
            alertLevel++;

        }
        if (data.getTemperature() > alertCheckLimitService.getLimit("aquark_data", "temperature").getLimitValue()) {
            message += "temperature超出限制,";
            alertLevel++;


        }
        if (data.getMoisture() > alertCheckLimitService.getLimit("aquark_data", "moisture").getLimitValue()) {
            message += "moisture超出限制,";
            alertLevel++;


        }
        if (data.getRain_d() > alertCheckLimitService.getLimit("aquark_data", "rain_d").getLimitValue()) {
            message += "rain_d超出限制,";
            alertLevel++;

        }
        if (data.getWaterSpeedAquark() > alertCheckLimitService.getLimit("aquark_data", "water_speed_aquark").getLimitValue()) {
            message += "water_speed_aquark超出限制,";
            alertLevel++;

        }
        if (data.getEcho() > alertCheckLimitService.getLimit("aquark_data", "echo").getLimitValue()) {
            message += "echo超出限制,";
            alertLevel++;

        }
        if (alertLevel > 0) {
            alarmMessage.setMessage(message);
//            alarmService.processAlarm(alarmMessage);
            return List.of(alarmMessage);
        }
        return List.of();
    }




}

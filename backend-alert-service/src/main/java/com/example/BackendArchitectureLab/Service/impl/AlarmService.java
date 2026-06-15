package com.example.BackendArchitectureLab.Service.impl;

import com.example.BackendArchitectureLab.Dto.Vo.Common.AlarmMessage;
import com.example.BackendArchitectureLab.Service.IAlarmPublisher;
import com.example.BackendArchitectureLab.Service.IAlarmService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class AlarmService implements IAlarmService {

    @Autowired
    private IAlarmPublisher alarmPublisher;

    // 可以使用消息隊列、異步執行器等來處理

    @Override
    public void processAlarm(List<AlarmMessage> alarmMessage) {
        try {
            alarmPublisher.publish(alarmMessage);
        } catch (Exception e) {
            log.warn("告警消息發送失敗: {}", e.toString());
            alarmMessage.get(0).setMessage("告警消息發送失敗：" + e.getMessage());
        }

    }

    private void saveAlarm(AlarmMessage alarmMessage) {
        // 實作：保存至資料庫
//        System.out.println("保存告警：" + alarmMessage.getMessage());
    }

    private void logAlarm(AlarmMessage alarmMessage) {
        // 實作：記錄到日誌系統
        log.info("記錄告警：{}", alarmMessage.getMessage());
    }
}

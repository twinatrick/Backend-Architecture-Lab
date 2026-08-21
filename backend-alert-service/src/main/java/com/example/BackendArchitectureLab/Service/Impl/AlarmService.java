package com.example.BackendArchitectureLab.Service.Impl;

import com.example.BackendArchitectureLab.Vo.Common.AlarmMessage;
import com.example.BackendArchitectureLab.Service.IAlarmPublisher;
import com.example.BackendArchitectureLab.Service.IAlarmService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AlarmService implements IAlarmService {

    private final IAlarmPublisher alarmPublisher;

    // 可以使用消息隊列、異步執行器等來處理

    @Override
    public void processAlarm(List<AlarmMessage> alarmMessage) {
        try {
            alarmPublisher.publish(alarmMessage);
        } catch (Exception e) {
            log.warn("告警消息發送失敗: {}", e.toString());
            if (alarmMessage != null && !alarmMessage.isEmpty()) {
                alarmMessage.getFirst().setMessage("告警消息發送失敗：" + e.getMessage());
            }
        }
    }
}

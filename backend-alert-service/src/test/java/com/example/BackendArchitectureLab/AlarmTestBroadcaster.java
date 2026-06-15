package com.example.BackendArchitectureLab;

import com.example.BackendArchitectureLab.Dto.Vo.Common.AlarmMessage;
import com.example.BackendArchitectureLab.WebSocket.AlarmWebSocket;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class AlarmTestBroadcaster {
    @Scheduled(fixedRate = 10000) // 每 10 秒發送一次測試訊息
    public void sendTestMessage() {
        AlarmMessage message = new AlarmMessage();
        message.setLevel("INFO");
        message.setMessage("這是一條測試訊息");

        AlarmWebSocket.broadcast(message);
        System.out.println("已廣播測試訊息：" + message);
    }
}

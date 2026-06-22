package com.example.BackendArchitectureLab.Service;


import com.example.BackendArchitectureLab.Vo.Common.AlarmMessage;

import java.util.List;

public interface IKafkaConsumerService {
    void listen(List<AlarmMessage> messages);
}

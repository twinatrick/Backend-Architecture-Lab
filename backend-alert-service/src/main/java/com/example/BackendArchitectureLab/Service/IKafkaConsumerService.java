package com.example.BackendArchitectureLab.Service;


import com.example.BackendArchitectureLab.Event.AlarmEvent;

import java.util.List;

public interface IKafkaConsumerService {
    void listen(List<AlarmEvent> messages);
}

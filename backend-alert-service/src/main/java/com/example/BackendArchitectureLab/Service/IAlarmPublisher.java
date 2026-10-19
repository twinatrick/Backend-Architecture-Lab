package com.example.BackendArchitectureLab.Service;


import com.example.BackendArchitectureLab.Event.AlarmEvent;

import java.util.List;

public interface IAlarmPublisher {
    void publish(List<AlarmEvent> messages);
}

package com.example.BackendArchitectureLab.Service.Strategy;

import com.example.BackendArchitectureLab.Vo.Kafka.CompensationAction;
import com.example.BackendArchitectureLab.Vo.Kafka.CompensationEvent;

/**
 * CompensationStrategy - 補償執行策略（Strategy Pattern），
 * 依事件 action 分派到對應的回滾／復原實作。
 */
public interface CompensationStrategy {

    boolean supports(CompensationAction action);

    void compensate(CompensationEvent event);
}
package com.example.BackendArchitectureLab.Service.Strategy;

import com.example.BackendArchitectureLab.Vo.Kafka.CompensationAction;
import com.example.BackendArchitectureLab.Vo.Kafka.CompensationEvent;

/**
 * CompensationStrategy - 補償執行策略（Strategy Pattern），
 * 依事件 action 分派到對應的回滾／復原實作。
 */
public interface CompensationStrategy {

    boolean supports(CompensationAction action);

    /**
     * 執行補償
     *
     * @param event 補償事件
     * @param ownerId 目前認領此事件的處理者唯一識別碼（fencing token 的一部份）
     * @param fencingVersion 目前認領的樂觀鎖代數（單調遞增），下游以此驗證是否為最新持有者
     */
    void compensate(CompensationEvent event, String ownerId, Long fencingVersion);
}

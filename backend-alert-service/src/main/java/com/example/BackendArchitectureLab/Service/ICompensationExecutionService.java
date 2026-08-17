package com.example.BackendArchitectureLab.Service;

import com.example.BackendArchitectureLab.Vo.Kafka.CompensationAction;
import com.example.BackendArchitectureLab.Vo.Kafka.CompensationEvent;

/**
 * ICompensationExecutionService - 補償執行（Strategy 委派）與重試分類。
 * <p>
 * 依事件 action 找到支援的 {@code CompensationStrategy} 執行補償；並提供
 * 「是否為不可重試的永久錯誤」分類，供處理核心決定 DEAD / FAILED。
 */
public interface ICompensationExecutionService {

    /**
     * 是否存在支援指定 action 的補償策略。
     *
     * @param action 補償動作
     * @return true 表示有對應策略可處理
     */
    boolean supports(CompensationAction action);

    /**
     * 執行補償：將事件分派至支援的策略；無支援策略時拋出
     * {@link com.example.BackendArchitectureLab.Exception.UnsupportedCompensationActionException}。
     *
     * @param event          補償事件
     * @param ownerId        目前認領此事件的處理者唯一識別碼（fencing token 的一部份）
     * @param fencingVersion 目前認領的樂觀鎖代數（單調遞增）
     */
    void execute(CompensationEvent event, String ownerId, Long fencingVersion);

    /**
     * 判斷例外是否為不可重試的永久錯誤（契約不相容、永久性業務衝突）。
     *
     * @param e 處理過程拋出的例外
     * @return true 表示重試亦不會成功，應直接標記 DEAD 隔離
     */
    boolean isNonRetryable(Throwable e);
}

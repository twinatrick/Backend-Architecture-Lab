package com.example.BackendArchitectureLab.Vo.Kafka;

/**
 * CompensationEventLogStatus - 補償事件的消費處理狀態機：
 * PROCESSING → PROCESSED / FAILED（可依事件重試，lease 到期後可被重新認領）。PROCESSED 為最終狀態。
 */
public final class CompensationEventLogStatus {

    public static final String PROCESSING = "PROCESSING";
    public static final String PROCESSED = "PROCESSED";
    public static final String FAILED = "FAILED";
    public static final String DEAD = "DEAD";

    private CompensationEventLogStatus() {
    }
}
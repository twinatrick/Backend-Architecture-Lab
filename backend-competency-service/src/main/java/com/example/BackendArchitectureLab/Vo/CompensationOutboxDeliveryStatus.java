package com.example.BackendArchitectureLab.Vo;

/**
 * CompensationOutboxDeliveryStatus - Outbox 事件的投遞狀態機：
 * PENDING → PROCESSING → SENT / FAILED（可重試）→ DEAD（超過最大重試次數）。
 */
public final class CompensationOutboxDeliveryStatus {

    public static final String PENDING = "PENDING";
    public static final String PROCESSING = "PROCESSING";
    public static final String SENT = "SENT";
    public static final String FAILED = "FAILED";
    public static final String DEAD = "DEAD";

    private CompensationOutboxDeliveryStatus() {
    }
}
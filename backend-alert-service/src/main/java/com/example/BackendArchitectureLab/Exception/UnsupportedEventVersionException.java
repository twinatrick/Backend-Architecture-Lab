package com.example.BackendArchitectureLab.Exception;

/**
 * UnsupportedEventVersionException - 消費者收到不支援的事件版本時拋出，
 * 由 CompensationConsumer 標記 FAILED 並 rethrow，交由 Kafka retry（最終 DEAD/DLQ）。
 */
public class UnsupportedEventVersionException extends RuntimeException {

    public UnsupportedEventVersionException(String message) {
        super(message);
    }
}

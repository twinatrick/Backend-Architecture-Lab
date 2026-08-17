package com.example.BackendArchitectureLab.Exception;

/**
 * UnsupportedEventVersionException - 消費者收到不支援的事件版本時拋出。
 * 此異常為不相容之永久型錯誤（Permanent Error），在 Kafka 錯誤處理器中已設定為 Non-retryable，
 * 拋出後將直接隔離至死信信箱（DLT/Quarantine）供人工介入排除，不進行重複嘗試。
 */
public class UnsupportedEventVersionException extends RuntimeException {

    public UnsupportedEventVersionException(String message) {
        super(message);
    }
}

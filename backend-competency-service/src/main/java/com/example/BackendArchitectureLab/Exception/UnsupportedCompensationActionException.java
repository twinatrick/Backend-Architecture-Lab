package com.example.BackendArchitectureLab.Exception;

/**
 * UnsupportedCompensationActionException - 當收到不支援的補償動作（無對應 strategy 實作）時拋出，
 * 視為永久性、不可重試錯誤，直接交由 Kafka DLT 處理，不寫入本地日誌。
 */
public class UnsupportedCompensationActionException extends RuntimeException {

    public UnsupportedCompensationActionException(String message) {
        super(message);
    }
}

package com.example.BackendArchitectureLab.Exception;

/**
 * CompensationConflictException - 當補償還原檢測到並發更新衝突時拋出（樂觀守衛失敗）。
 */
public class CompensationConflictException extends RuntimeException {

    public CompensationConflictException(String message) {
        super(message);
    }
}

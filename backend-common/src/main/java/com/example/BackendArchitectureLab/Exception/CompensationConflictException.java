package com.example.BackendArchitectureLab.Exception;

/**
 * CompensationConflictException - 當補償還原檢測到並發更新衝突時拋出（樂觀守衛失敗）。
 * 此類錯誤屬於永久性業務衝突，無法靠重試復原，應隔離至 DLT 供人工介入。
 */
public class CompensationConflictException extends RuntimeException {

    public CompensationConflictException(String message) {
        super(message);
    }
}

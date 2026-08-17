package com.example.BackendArchitectureLab.Exception;

/**
 * CompensationDeadEventException - 補償事件已進入 DEAD 隔離狀態時的永久性例外。
 * <p>
 * 當補償事件已達最大重試次數或重送時發現 DB 狀態已為 DEAD（先前已隔離），
 * 此例外向外拋出，由 Kafka 錯誤處理器識別為不可重試（non-retryable）並直接
 * 轉發至 DLT 供人工介入，避免對已隔離的事件進行無意義的重試。
 */
public class CompensationDeadEventException extends RuntimeException {

    public CompensationDeadEventException(String message) {
        super(message);
    }

    public CompensationDeadEventException(String message, Throwable cause) {
        super(message, cause);
    }
}

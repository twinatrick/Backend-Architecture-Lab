package com.example.BackendArchitectureLab.Exception;

public class AppException extends RuntimeException {
    private final String errorType;
    private final int httpStatus;

    public AppException(String errorType, String message, int httpStatus) {
        super(message);
        this.errorType = errorType;
        this.httpStatus = httpStatus;
    }

    public String getErrorType() {
        return errorType;
    }

    public int getHttpStatus() {
        return httpStatus;
    }
}

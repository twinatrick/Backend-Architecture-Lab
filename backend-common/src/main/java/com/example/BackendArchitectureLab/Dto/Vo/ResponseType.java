package com.example.BackendArchitectureLab.Dto.Vo;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ResponseType<T> {
    private Integer code;
    private T data;
    private String message;
    private String errorType;

    public ResponseType(int code) {
        this.code = normalizeCode(code);
    }

    public ResponseType(T data) {
        this.code = 200;
        this.data = data;
    }

    public ResponseType(int code, T data) {
        this.code = normalizeCode(code);
        this.data = data;
    }

    public ResponseType(int code, T data, String message) {
        this.code = normalizeCode(code);
        this.message = message;
        this.data = data;
    }

    public static ResponseType<?> CodeAndMessage(int code, String message) {
        ResponseType<?> response = new ResponseType<>();
        response.code = normalizeCode(code);
        response.message = message;
        return response;
    }

    public static <T> ResponseType<T> Success() {
        return Success(null, "");
    }

    public static <T> ResponseType<T> Success(T data) {
        return Success(data, "");
    }

    public static <T> ResponseType<T> Success(T data, String message) {
        return new ResponseType<>(200, data, message);
    }

    public static <T> ResponseType<T> Fail(String errorType, String message) {
        return Fail(errorType, message, 400, null);
    }

    public static <T> ResponseType<T> Fail(String errorType, String message, int httpStatus) {
        return Fail(errorType, message, httpStatus, null);
    }

    public static <T> ResponseType<T> Fail(String errorType, String message, int httpStatus, T data) {
        ResponseType<T> response = new ResponseType<>(httpStatus, data, message);
        response.errorType = errorType;
        return response;
    }

    private static int normalizeCode(int code) {
        if (code == 0) {
            return 200;
        }
        if (code == -1) {
            return 500;
        }
        return code;
    }
}

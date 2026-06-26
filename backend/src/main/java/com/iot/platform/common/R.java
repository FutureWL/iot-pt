package com.iot.platform.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 统一响应包装
 *
 * @author IoT Platform Team
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class R<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public static final int CODE_SUCCESS = 200;
    public static final int CODE_FAIL    = 500;
    public static final int CODE_UNAUTHORIZED = 401;
    public static final int CODE_FORBIDDEN    = 403;
    public static final int CODE_BAD_REQUEST  = 400;

    private int code;
    private String message;
    private T data;
    private long timestamp = System.currentTimeMillis();

    public static <T> R<T> ok() {
        return build(CODE_SUCCESS, "操作成功", null);
    }

    public static <T> R<T> ok(T data) {
        return build(CODE_SUCCESS, "操作成功", data);
    }

    public static <T> R<T> ok(String message, T data) {
        return build(CODE_SUCCESS, message, data);
    }

    public static <T> R<T> fail(String message) {
        return build(CODE_FAIL, message, null);
    }

    public static <T> R<T> fail(int code, String message) {
        return build(code, message, null);
    }

    public static <T> R<T> build(int code, String message, T data) {
        R<T> r = new R<>();
        r.setCode(code);
        r.setMessage(message);
        r.setData(data);
        return r;
    }
}

package com.iot.platform.common;

import lombok.Getter;

/**
 * 业务异常
 *
 * @author IoT Platform Team
 */
@Getter
public class BusinessException extends RuntimeException {

    private final int code;

    public BusinessException(String message) {
        super(message);
        this.code = R.CODE_FAIL;
    }

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    public BusinessException(String message, Throwable cause) {
        super(message, cause);
        this.code = R.CODE_FAIL;
    }
}

package com.factory.anomaly_service.exception;

import com.factory.common.core.exception.ErrorCode;

public enum AnomalyErrorCode implements ErrorCode {
    ;

    private final int status;
    private final String code;
    private final String message;

    AnomalyErrorCode(int status, String code, String message) {
        this.status = status;
        this.code = code;
        this.message = message;
    }

    @Override
    public int getStatus() {
        return this.status;
    }

    @Override
    public String getCode() {
        return this.code;
    }

    @Override
    public String getMessage() {
        return this.message;
    }
}

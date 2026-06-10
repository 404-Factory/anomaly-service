package com.factory.anomaly.exception;

import com.factory.common.core.exception.ErrorCode;

public enum AnomalyErrorCode implements ErrorCode {

    ANOMALY_LOG_NOT_FOUND(404, "ANOMALY404", "이상 로그를 찾을 수 없습니다."),
    INVALID_ANALYSIS_STATUS(400, "ANOMALY400", "유효하지 않은 이상 분석 상태입니다."),
    ANALYSIS_NOT_FOUND(404, "ANOMALY404", "이상 분석을 찾을 수 없습니다.");

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

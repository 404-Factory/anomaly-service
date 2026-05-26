package com.factory.anomaly_service.exception;

import com.factory.common.core.exception.BaseException;
import com.factory.common.core.exception.ErrorCode;

public class AnomalyException extends BaseException {

    public AnomalyException(ErrorCode errorCode) {
        super(errorCode);
    }

    public AnomalyException(ErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }

}

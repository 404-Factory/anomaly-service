package com.factory.anomaly.service;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface AnalysisSseService {

    SseEmitter subscribe(Long anomalyId);

    void send(Long anomalyId, Object event);

    void remove(Long id, SseEmitter emitter);
}

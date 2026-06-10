package com.factory.anomaly.service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class AnalysisSseServiceImpl implements AnalysisSseService {

    private final Map<Long, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

    @Override
    public SseEmitter subscribe(Long anomalyId) {

        SseEmitter emitter = new SseEmitter(0L);

        emitters.computeIfAbsent(anomalyId, k -> new CopyOnWriteArrayList<>())
            .add(emitter);

        emitter.onCompletion(() -> remove(anomalyId, emitter));
        emitter.onTimeout(() -> remove(anomalyId, emitter));

        return emitter;
    }

    @Override
    public void send(Long anomalyId, Object event) {

        List<SseEmitter> list = emitters.get(anomalyId);
        if (list == null) return;

        for (SseEmitter emitter : list) {
            try {
                emitter.send(SseEmitter.event()
                    .name("analysis")
                    .data(event));
            } catch (Exception e) {
                emitter.complete();
            }
        }
    }

    @Override
    public void remove(Long id, SseEmitter emitter) {
        List<SseEmitter> list = emitters.get(id);
        if (list != null) list.remove(emitter);
    }
}
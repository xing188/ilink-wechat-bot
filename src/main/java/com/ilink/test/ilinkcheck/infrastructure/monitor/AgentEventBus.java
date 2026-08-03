package com.ilink.test.ilinkcheck.infrastructure.monitor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Agent 运行时事件总线。
 * 持有最近一段时间的历史事件（新连接先回放），并通过 SSE 推送给所有监控台连接。
 */
@Slf4j
@Component
public class AgentEventBus {

    /** 保留的历史事件上限。 */
    private static final int MAX_HISTORY = 300;

    private final Deque<AgentEvent> history = new ArrayDeque<>();
    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    public synchronized void publish(AgentEvent event) {
        history.addLast(event);
        while (history.size() > MAX_HISTORY) {
            history.removeFirst();
        }
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name("agent").data(event));
            } catch (IOException | IllegalStateException e) {
                emitters.remove(emitter);
            }
        }
    }

    public synchronized List<AgentEvent> recentEvents() {
        return List.copyOf(history);
    }

    /**
     * 建立一个新的 SSE 连接：先回放历史事件，再持续接收新事件。
     */
    public SseEmitter connect() {
        SseEmitter emitter = new SseEmitter(0L);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitters.add(emitter);

        for (AgentEvent event : recentEvents()) {
            try {
                emitter.send(SseEmitter.event().name("agent").data(event));
            } catch (IOException | IllegalStateException e) {
                emitters.remove(emitter);
                break;
            }
        }
        return emitter;
    }
}

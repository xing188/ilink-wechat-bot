package com.ilink.test.ilinkcheck.adapter.inbound.web;

import com.ilink.test.ilinkcheck.infrastructure.monitor.AgentEvent;
import com.ilink.test.ilinkcheck.infrastructure.monitor.AgentEventBus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * Agent 运行监控台接口。
 * 通过 SSE 实时推送 Agent 工作流事件；历史事件用于页面加载时回放。
 */
@RestController
@RequestMapping("/api/agent")
public class AgentMonitorController {

    private final AgentEventBus agentEventBus;

    public AgentMonitorController(AgentEventBus agentEventBus) {
        this.agentEventBus = agentEventBus;
    }

    @GetMapping(value = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        return agentEventBus.connect();
    }

    @GetMapping(value = "/events/history", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<AgentEvent> history() {
        return agentEventBus.recentEvents();
    }
}

package com.ilink.test.ilinkcheck.application.skill;

import com.ilink.test.ilinkcheck.application.agent.orchestrator.AgentLoopService;
import org.springframework.stereotype.Component;

/**
 * 技术推送 Skill。
 * 入口保持不变，内部还是交给 AgentLoopService 走既有的 digest 工作流。
 */
@Component
public class DailyDigestSkill implements Skill {

    private final AgentLoopService agentLoopService;

    public DailyDigestSkill(AgentLoopService agentLoopService) {
        this.agentLoopService = agentLoopService;
    }

    @Override
    public String id() {
        return "daily-digest";
    }

    @Override
    public String name() {
        return "技术推送订阅";
    }

    @Override
    public String description() {
        return "订阅每周 GitHub 好项目或预览技术推送，底层复用统一定时逻辑。";
    }

    @Override
    public String execute(String input) {
        return agentLoopService.tryHandle("skill:" + id(), input)
                .orElse("""
                        如果你想订阅技术推送，可以直接这样说：
                        - 帮我订阅每周 GitHub 好项目
                        - 每周二上午 9 点给我推送
                        - 先帮我预览一份周报
                        """.trim());
    }
}

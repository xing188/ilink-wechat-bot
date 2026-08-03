package com.ilink.test.ilinkcheck.application.skill;

import com.ilink.test.ilinkcheck.application.agent.orchestrator.AgentLoopService;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class DailyDigestSkillTest {

    @Test
    void shouldExposeStableSkillMetadata() {
        DailyDigestSkill skill = new DailyDigestSkill(new StubAgentLoopService(Optional.of("mock")));

        assertThat(skill.id()).isEqualTo("daily-digest");
        assertThat(skill.name()).isNotBlank();
        assertThat(skill.description()).isNotBlank();
    }

    @Test
    void shouldDelegateToAgentLoopWhenWorkflowMatches() {
        DailyDigestSkill skill = new DailyDigestSkill(new StubAgentLoopService(Optional.of("已为你记录技术推送计划：每天 08:30")));

        String reply = skill.execute("每周二 08:30 给我推送技术周报");

        assertThat(reply).contains("08:30");
    }

    @Test
    void shouldReturnUsageHintWhenWorkflowDoesNotMatch() {
        DailyDigestSkill skill = new DailyDigestSkill(new StubAgentLoopService(Optional.empty()));

        String reply = skill.execute("你好");

        assertThat(reply).contains("帮我订阅每周 GitHub 好项目");
        assertThat(reply).contains("每周二上午 9 点");
    }

    private static final class StubAgentLoopService extends AgentLoopService {

        private final Optional<String> reply;

        private StubAgentLoopService(Optional<String> reply) {
            super(null, null, null, null);
            this.reply = reply;
        }

        @Override
        public Optional<String> tryHandle(String conversationId, String message) {
            return reply;
        }
    }
}

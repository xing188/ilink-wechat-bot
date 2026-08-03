package com.ilink.test.ilinkcheck.application.agent.orchestrator.phase.roadmap;

import com.ilink.test.ilinkcheck.application.agent.dto.roadmap.DevToArticle;
import com.ilink.test.ilinkcheck.application.agent.dto.roadmap.RoadmapPlan;
import com.ilink.test.ilinkcheck.application.agent.dto.roadmap.SearchResult;
import com.ilink.test.ilinkcheck.application.agent.feature.roadmap.RoadmapComposeService;
import com.ilink.test.ilinkcheck.application.agent.orchestrator.model.*;
import com.ilink.test.ilinkcheck.application.agent.orchestrator.support.AgentTextUtils;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 学习路线「确认是否要完整版」阶段。
 *
 * <p>在 {@link RoadmapComposePhaseExecutor} 给出精简版并等待用户输入后进入；
 * 根据用户回复决定下一步：</p>
 * <ul>
 *   <li>回复「详细 / 完整 / 要」等正向词，或「翻译成中文」，或首轮就要求中文版 →
 *       渲染带说明与资料链接的完整版（按标记决定是否翻译）；</li>
 *   <li>否则（如「不用了」）→ 礼貌结束，精简版已给出，不强制推送完整版。</li>
 * </ul>
 */
@Service
public class RoadmapConfirmDetailPhaseExecutor implements PhaseExecutor {

    private final RoadmapComposeService composeService;

    public RoadmapConfirmDetailPhaseExecutor(RoadmapComposeService composeService) {
        this.composeService = composeService;
    }

    @Override
    public boolean supports(AgentTaskType taskType, AgentPhase phase) {
        return taskType == AgentTaskType.TECH_ROADMAP && phase == AgentPhase.ROADMAP_CONFIRM_DETAIL;
    }

    @Override
    public PhaseExecutionResult execute(AgentState state) {
        String userMessage = state.getLatestUserMessage();
        List<DevToArticle> articles = state.getArtifact(AgentArtifacts.ROADMAP_ARTICLES);
        List<SearchResult> references = state.getArtifact(AgentArtifacts.ROADMAP_REFERENCES);
        String topic = state.getArtifact(AgentArtifacts.REQUEST_TOPIC, String.class);
        boolean translate = Boolean.TRUE.equals(state.getArtifact(AgentArtifacts.ROADMAP_TRANSLATE, Boolean.class));

        // 用户想要完整版：正向词 / 中文请求 / 首轮就要求中文版，任一命中即渲染完整版
        boolean wantsMore = AgentTextUtils.wantsDetailed(userMessage)
                || AgentTextUtils.wantsChinese(userMessage)
                || translate;
        if (!wantsMore) {
            return PhaseExecutionResult.finish(
                    "好的，完整版路线先不发了。想看资料链接或翻译成中文，随时回复我～",
                    Map.of()
            );
        }

        // 渲染完整版（带说明 + 资料链接），按用户/首轮要求决定是否翻译
        String reply = composeService.composeAndRender(topic, articles, references, translate);

        // 渐进式交互的第三屏：完整版之后并列询问是否需要 B 站网课视频链接与知识点思维导图。
        // 回复「视频/网课」进入 ROADMAP_CONFIRM_VIDEO；回复「思维导图」由该阶段识别后转发到
        // ROADMAP_CONFIRM_MINDMAP。两者独立可选，不必先经过网课环节。
        String askExtra = "\n\n还需要什么补充资料？任选其一回复我：\n"
                + "• 回复「视频」或「网课」→ 获取配套 B 站网课链接 📺\n"
                + "• 回复「思维导图」→ 生成 AI 总结的知识点网络图 🗺️\n"
                + "• 回复「都要」→ 先发视频链接，再发思维导图";
        return PhaseExecutionResult.waitForUser(
                AgentPhase.ROADMAP_CONFIRM_VIDEO,
                reply + askExtra,
                Map.of(AgentArtifacts.ROADMAP_TRANSLATE, translate)
        );
    }
}

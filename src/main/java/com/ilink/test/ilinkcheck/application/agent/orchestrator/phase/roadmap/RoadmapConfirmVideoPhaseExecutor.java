package com.ilink.test.ilinkcheck.application.agent.orchestrator.phase.roadmap;

import com.ilink.test.ilinkcheck.application.agent.dto.roadmap.SearchResult;
import com.ilink.test.ilinkcheck.application.agent.feature.roadmap.RoadmapComposeService;
import com.ilink.test.ilinkcheck.application.agent.orchestrator.model.*;
import com.ilink.test.ilinkcheck.application.agent.orchestrator.support.AgentTextUtils;
import com.ilink.test.ilinkcheck.application.agent.spi.mcp.WebSearchProvider;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 学习路线「确认是否要网课视频」阶段。
 *
 * <p>在 {@link RoadmapConfirmDetailPhaseExecutor} 给出完整版并询问后进入；
 * 根据用户回复决定下一步：</p>
 * <ul>
 *   <li>回复「视频 / 网课 / B站 / bilibili」等正向词 →
 *       用 {@link WebSearchProvider} 检索该主题的 B 站网课，
 *       只保留 bilibili 链接并渲染带来源标注的视频列表；</li>
 *   <li>否则（如「不用了」）→ 礼貌结束，不强制推送视频。</li>
 * </ul>
 */
@Service
public class RoadmapConfirmVideoPhaseExecutor implements PhaseExecutor {

    /** 检索关键词后缀：锚定 B 站，避免泛化到无关视频平台。 */
    private static final String VIDEO_QUERY_SUFFIX = " bilibili 网课 教程";

    private final WebSearchProvider webSearchProvider;
    private final RoadmapComposeService composeService;

    public RoadmapConfirmVideoPhaseExecutor(WebSearchProvider webSearchProvider,
                                            RoadmapComposeService composeService) {
        this.webSearchProvider = webSearchProvider;
        this.composeService = composeService;
    }

    @Override
    public boolean supports(AgentTaskType taskType, AgentPhase phase) {
        return taskType == AgentTaskType.TECH_ROADMAP && phase == AgentPhase.ROADMAP_CONFIRM_VIDEO;
    }

    @Override
    public PhaseExecutionResult execute(AgentState state) {
        String userMessage = state.getLatestUserMessage();
        String topic = state.getArtifact(AgentArtifacts.REQUEST_TOPIC, String.class);

        boolean wantsVideo = AgentTextUtils.wantsVideo(userMessage);
        boolean wantsMindmap = AgentTextUtils.wantsMindmap(userMessage);

        StringBuilder reply = new StringBuilder();

        // 用户想看视频：检索并渲染 B 站网课链接
        if (wantsVideo) {
            String query = (topic == null ? "" : topic) + VIDEO_QUERY_SUFFIX;
            List<SearchResult> videos = webSearchProvider.search(query, 5).stream()
                    .filter(v -> v.url() != null && v.url().toLowerCase().contains("bilibili"))
                    .limit(3)
                    .toList();
            reply.append(composeService.renderVideoLinks(topic, videos));
            reply.append(System.lineSeparator()).append(System.lineSeparator());
        }

        // 用户直接点了「思维导图」且不要视频：不等待二次输入，
        // 直接用 next() 推进到思维导图阶段，让主循环在同一轮请求中执行生成。
        if (!wantsVideo && wantsMindmap) {
            return PhaseExecutionResult.next(
                    AgentPhase.ROADMAP_CONFIRM_MINDMAP,
                    Map.of()
            );
        }

        // 用户不要视频也不要思维导图：礼貌结束
        if (!wantsVideo) {
            reply.append("好的，补充资料先不发了。需要时随时回复我～");
            return PhaseExecutionResult.finish(reply.toString().trim(), Map.of());
        }

        // 看了视频后，再询问是否需要知识点思维导图
        reply.append("需要我再为你生成一份「知识点思维导图」吗？回复「思维导图」即可 🗺️");

        return PhaseExecutionResult.waitForUser(
                AgentPhase.ROADMAP_CONFIRM_MINDMAP,
                reply.toString().trim(),
                Map.of()
        );
    }
}

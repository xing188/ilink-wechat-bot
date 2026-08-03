package com.ilink.test.ilinkcheck.application.agent.orchestrator.phase.roadmap;

import com.ilink.test.ilinkcheck.application.agent.dto.roadmap.DevToArticle;
import com.ilink.test.ilinkcheck.application.agent.dto.roadmap.RoadmapPlan;
import com.ilink.test.ilinkcheck.application.agent.dto.roadmap.SearchResult;
import com.ilink.test.ilinkcheck.application.agent.feature.roadmap.MindmapGenerationService;
import com.ilink.test.ilinkcheck.application.agent.feature.roadmap.MindmapImageCache;
import com.ilink.test.ilinkcheck.application.agent.orchestrator.model.*;
import com.ilink.test.ilinkcheck.application.agent.orchestrator.support.AgentTextUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 学习路线「确认是否要知识点思维导图」阶段。
 *
 * <p>在 {@link RoadmapConfirmVideoPhaseExecutor} 询问后进入；
 * 根据用户回复决定下一步：</p>
 * <ul>
 *   <li>回复「思维导图 / 脑图 / 导图 / mindmap」等正向词 →
 *       基于抓取到的资料由 AI 重新总结知识点网络，渲染成<b>图片版思维导图</b>；</li>
 *   <li>否则（如「不用了」）→ 礼貌结束，不强制推送思维导图。</li>
 * </ul>
 *
 * <p>思维导图是<b>独立生成、按需发送</b>的，内容来自资料总结，不会混入完整版路线。</p>
 */
@Slf4j
@Service
public class RoadmapConfirmMindmapPhaseExecutor implements PhaseExecutor {

    private static final String MINDMAP_IMAGE_MARKER = "[MINDMAP_IMAGE]:";

    private final MindmapGenerationService mindmapGenerationService;
    private final MindmapImageCache mindmapImageCache;

    public RoadmapConfirmMindmapPhaseExecutor(MindmapGenerationService mindmapGenerationService,
                                              MindmapImageCache mindmapImageCache) {
        this.mindmapGenerationService = mindmapGenerationService;
        this.mindmapImageCache = mindmapImageCache;
    }

    @Override
    public boolean supports(AgentTaskType taskType, AgentPhase phase) {
        return taskType == AgentTaskType.TECH_ROADMAP && phase == AgentPhase.ROADMAP_CONFIRM_MINDMAP;
    }

    @Override
    public PhaseExecutionResult execute(AgentState state) {
        String userMessage = state.getLatestUserMessage();
        log.info("[思维导图阶段] 收到用户消息: '{}', 当前阶段: {}", userMessage, state.getCurrentPhase());

        // 用户不想要思维导图：礼貌结束，不强制推送
        if (!AgentTextUtils.wantsMindmap(userMessage)) {
            return PhaseExecutionResult.finish(
                    "好的，思维导图先不发了～ 需要的时候随时喊我 🗺️",
                    Map.of()
            );
        }

        String topic = state.getArtifact(AgentArtifacts.REQUEST_TOPIC, String.class);
        if (topic == null) {
            topic = "技术主题";
        }

        // 取出之前抓取的文章与参考，作为 AI 总结知识点的输入
        List<DevToArticle> articles = state.getArtifact(AgentArtifacts.ROADMAP_ARTICLES);
        List<SearchResult> references = state.getArtifact(AgentArtifacts.ROADMAP_REFERENCES);

        try {
            MindmapGenerationService.MindmapArtifacts artifacts =
                    mindmapGenerationService.generateMindmap(topic, articles, references);
            String imageKey = mindmapImageCache.put(artifacts.pngImage());
            String fileKey = mindmapImageCache.put(artifacts.xmindFile());
            // 合并标记：[MINDMAP_IMAGE]:<图片key>:<xmind文件key>，由发送层分别取出图片与文件
            return PhaseExecutionResult.finish(
                    MINDMAP_IMAGE_MARKER + imageKey + ":" + fileKey,
                    Map.of()
            );
        } catch (Exception e) {
            log.error("生成思维导图失败", e);
            return PhaseExecutionResult.finish(
                    "思维导图生成失败了，请稍后再试～",
                    Map.of()
            );
        }
    }
}

package com.ilink.test.ilinkcheck.application.agent.orchestrator;

import com.ilink.test.ilinkcheck.application.agent.dto.HotSearchResult;
import com.ilink.test.ilinkcheck.application.agent.dto.KnowledgeItem;
import com.ilink.test.ilinkcheck.application.agent.dto.SentimentReport;
import com.ilink.test.ilinkcheck.application.agent.dto.WritingDraft;
import com.ilink.test.ilinkcheck.application.agent.spi.content.SentimentAnalyzer;
import com.ilink.test.ilinkcheck.application.agent.spi.content.WriterAgent;
import com.ilink.test.ilinkcheck.application.agent.spi.mcp.HotSearchProvider;
import com.ilink.test.ilinkcheck.application.agent.spi.rag.KnowledgeRetriever;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class AgentLoopServiceTest {

    @Test
    void shouldStartWorkflowAndWaitForUserSelection() {
        AgentLoopService agentLoopService = createAgentLoopService();

        Optional<String> reply = agentLoopService.tryHandle("conv-1", "看下今天关于AI副业的热点，给我三个选题");

        assertThat(reply).isPresent();
        assertThat(reply.get()).contains("下面是 3 个可继续展开的选题");
        assertThat(reply.get()).contains("1.");
        assertThat(reply.get()).contains("2.");
        assertThat(reply.get()).contains("3.");
    }

    @Test
    void shouldResumeWorkflowAndGenerateDraftAfterSelection() {
        AgentLoopService agentLoopService = createAgentLoopService();

        agentLoopService.tryHandle("conv-2", "看下今天关于AI副业的热点，给我三个选题");
        Optional<String> finalReply = agentLoopService.tryHandle("conv-2", "2");

        assertThat(finalReply).isPresent();
        assertThat(finalReply.get()).contains("已根据你选择的方向生成第一版草稿");
        assertThat(finalReply.get()).contains("标题：");
        assertThat(finalReply.get()).contains("正文：");
    }

    @Test
    void shouldReturnEmptyWhenMessageIsNotAgentIntent() {
        AgentLoopService agentLoopService = createAgentLoopService();

        Optional<String> reply = agentLoopService.tryHandle("conv-3", "北京今天天气怎么样");

        assertThat(reply).isEmpty();
    }

    private AgentLoopService createAgentLoopService() {
        HotSearchProvider hotSearchProvider = new HotSearchProvider() {
            @Override
            public List<HotSearchResult> fetchHotSearch(String source) {
                return searchByKeyword("AI副业");
            }

            @Override
            public List<HotSearchResult> searchByKeyword(String keyword) {
                return List.of(
                        new HotSearchResult("weibo", keyword + " 热度持续升温", "机会与焦虑并存", "https://example.com/1", 91, List.of(keyword)),
                        new HotSearchResult("weibo", keyword + " 门槛争议加剧", "普通人担心跟不上", "https://example.com/2", 87, List.of(keyword)),
                        new HotSearchResult("weibo", keyword + " 创作者转型提速", "大家关注变现路径", "https://example.com/3", 84, List.of(keyword))
                );
            }
        };
        SentimentAnalyzer sentimentAnalyzer = text -> new SentimentReport(
                "争议偏高",
                0.5,
                0.3,
                List.of("担心门槛变高"),
                List.of("红利期是否结束")
        );
        KnowledgeRetriever knowledgeRetriever = new KnowledgeRetriever() {
            @Override
            public List<KnowledgeItem> search(String query) {
                return List.of(new KnowledgeItem("1", "模板", "内容", "writing-template", "理性拆解", 0.9f));
            }

            @Override
            public List<KnowledgeItem> searchByCategory(String category) {
                return search(category);
            }
        };
        WriterAgent writerAgent = (topic, platform, style) -> new WritingDraft(
                topic,
                "这是围绕“" + topic + "”生成的测试草稿。",
                platform,
                List.of("热点分析", "草稿生成"),
                80
        );

        List<PhaseExecutor> executors = List.of(
                new HotspotFetchPhaseExecutor(hotSearchProvider),
                new SentimentAnalyzePhaseExecutor(sentimentAnalyzer),
                new TopicGeneratePhaseExecutor(),
                new WaitUserSelectionPhaseExecutor(),
                new DraftGeneratePhaseExecutor(writerAgent, knowledgeRetriever)
        );

        return new AgentLoopService(
                new IntentRouter(),
                new WorkflowTemplateRegistry(),
                new PhaseExecutorRegistry(executors),
                new InMemoryAgentStateStore()
        );
    }
}

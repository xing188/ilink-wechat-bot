package com.ilink.test.ilinkcheck.application.agent.feature.roadmap;

import com.ilink.test.ilinkcheck.application.agent.dto.roadmap.DevToArticle;
import com.ilink.test.ilinkcheck.application.agent.dto.roadmap.SearchResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MindmapGenerationServiceTest {

    @Mock
    private ChatClient chatClient;
    @Mock
    private ChatClient.ChatClientRequestSpec requestSpec;
    @Mock
    private ChatClient.CallResponseSpec callSpec;

    @Test
    void shouldGeneratePngImageBytesWithoutLlm() throws Exception {
        MindmapGenerationService service = new MindmapGenerationService();
        List<DevToArticle> articles = List.of(
                new DevToArticle("RAG Basics", "https://dev.to/rag", "rag", 100, "RAG intro")
        );
        List<SearchResult> references = List.of(
                new SearchResult("RAG Tutorial", "https://example.com/rag", "A tutorial about RAG")
        );

        byte[] bytes = service.generateMindmapImage("RAG", articles, references);

        assertThat(bytes).isNotNull();
        assertThat(bytes.length).isGreaterThan(1000);
        // 验证是合法 PNG
        assertThat(ImageIO.read(new ByteArrayInputStream(bytes))).isNotNull();
    }

    @Test
    void fallbackStructureShouldContainBranchesAndSubTopics() {
        MindmapGenerationService service = new MindmapGenerationService();

        MindmapGenerationService.MindmapStructure structure = service.summarizeStructure(
                "RAG", List.of(), List.of());

        assertThat(structure.topic()).isEqualTo("RAG");
        assertThat(structure.branches()).isNotEmpty();
        // 三层结构：分支 → 子主题 → 细节
        assertThat(structure.branches().get(0).subTopics()).isNotEmpty();
        assertThat(structure.branches().get(0).subTopics().get(0).details()).isNotEmpty();
        // 验证细节内容含标签格式（如"1.1定义:"）
        String firstDetail = structure.branches().get(0).subTopics().get(0).details().get(0);
        assertThat(firstDetail).contains(":");
    }

    @Test
    void shouldParseLlmStructureWhenPlannerClientReturns() {
        // 模拟 LLM 返回三层结构化 JSON：验证成功路径可正确解析
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callSpec);
        when(callSpec.content()).thenReturn("""
                {"topic":"RAG","branches":[{"title":"检索器","subTopics":[{"title":"1.向量检索","details":["1.1定义: 将查询转为向量在高维空间中找相似文档","1.2核心: 余弦相似度与近似最近邻"]}]}]}
                """);

        MindmapGenerationService service = new MindmapGenerationService();
        service.setPlannerClient(chatClient);

        MindmapGenerationService.MindmapStructure structure = service.summarizeStructure("RAG", List.of(), List.of());

        assertThat(structure.topic()).isEqualTo("RAG");
        assertThat(structure.branches()).hasSize(1);
        assertThat(structure.branches().get(0).title()).isEqualTo("检索器");
        assertThat(structure.branches().get(0).subTopics()).hasSize(1);
        assertThat(structure.branches().get(0).subTopics().get(0).title()).isEqualTo("1.向量检索");
        assertThat(structure.branches().get(0).subTopics().get(0).details()).hasSize(2);
    }

    @Test
    void shouldGenerateXmindFileBytes() throws Exception {
        // 验证 .xmind 产物：合法 ZIP、含 content.json、三层结构正确（分支→子主题→细节叶子节点）
        MindmapGenerationService service = new MindmapGenerationService();
        List<DevToArticle> articles = List.of(
                new DevToArticle("RAG Basics", "https://dev.to/rag", "rag", 100, "RAG intro"));
        List<SearchResult> references = List.of(
                new SearchResult("RAG Tutorial", "https://example.com/rag", "A tutorial about RAG"));

        byte[] bytes = service.generateXmindFile("RAG", articles, references);

        assertThat(bytes).isNotNull();
        assertThat(bytes.length).isGreaterThan(200);

        // 1) 合法 ZIP 且包含必要条目
        Set<String> entryNames = new java.util.HashSet<>();
        String contentJson;
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            StringBuilder sb = new StringBuilder();
            while ((entry = zis.getNextEntry()) != null) {
                entryNames.add(entry.getName());
                if ("content.json".equals(entry.getName())) {
                    sb.append(new String(zis.readAllBytes(), StandardCharsets.UTF_8));
                }
            }
            contentJson = sb.toString();
        }
        assertThat(entryNames).contains("content.json", "metadata.json", "manifest.json");
        assertThat(contentJson).isNotBlank();

        // 2) content.json 结构正确：三层 topic 树
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(contentJson);
        assertThat(root.isArray()).isTrue();
        JsonNode rootTopic = root.get(0).get("rootTopic");
        assertThat(rootTopic.get("title").asText()).isEqualTo("RAG");
        assertThat(rootTopic.get("structureClass").asText()).isEqualTo("org.xmind.ui.map.balanced");
        JsonNode branches = rootTopic.get("children").get("attached");
        assertThat(branches).isNotNull();
        assertThat(branches.size()).isGreaterThan(0);
        // 分支下有子主题
        JsonNode subTopics = branches.get(0).get("children").get("attached");
        assertThat(subTopics).isNotNull();
        assertThat(subTopics.size()).isGreaterThan(0);
        // 子主题下有细节叶子节点
        JsonNode details = subTopics.get(0).get("children").get("attached");
        assertThat(details).isNotNull();
        assertThat(details.size()).isGreaterThan(0);
    }

    @Test
    void shouldFallbackWhenLlmTimesOut() {
        // plannerClient 故意阻塞超过超时阈值：应退回通用模板，而非抛错或卡死
        MindmapGenerationService service = new MindmapGenerationService();
        service.setPlannerClient(chatClient);
        service.mindmapTimeoutMs = 200;

        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callSpec);
        when(callSpec.content()).thenAnswer(invocation -> {
            Thread.sleep(5000); // 模拟慢模型
            return "{\"topic\":\"RAG\",\"branches\":[]}";
        });

        MindmapGenerationService.MindmapStructure structure = service.summarizeStructure("RAG", List.of(), List.of());

        // 超时后使用通用模板兜底（三层结构）
        assertThat(structure.topic()).isEqualTo("RAG");
        assertThat(structure.branches()).isNotEmpty();
        assertThat(structure.branches().get(0).subTopics()).isNotEmpty();
        assertThat(structure.branches().get(0).subTopics().get(0).details()).isNotEmpty();
    }

    @Disabled("仅用于本地生成预览图，常规 CI 不需要执行")
    @Test
    void generatePreviewImageForVisualCheck() throws Exception {
        // 模拟 LLM 返回三层详细知识结构（带定义/考点/举例），生成预览图供人工检查
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callSpec);
        when(callSpec.content()).thenReturn("""
                {"topic":"RAG 检索增强生成","branches":[
                {"title":"基础概念","subTopics":[
                  {"title":"1.RAG定义与背景","details":["1.1定义: Retrieval-Augmented Generation，检索增强生成范式","1.2作用: 解决大模型知识过时与幻觉问题，外挂知识库实时检索","1.3背景: 2020年Facebook提出，成为企业级LLM应用的核心架构"]},
                  {"title":"2.核心术语","details":["2.1Embedding: 文本向量化表示，语义相似度计算基础","2.2Vector DB: 专门存储和检索高维向量的数据库","2.3Chunk: 文档切分后的文本片段，检索的基本单元"]}]},
                {"title":"检索技术","subTopics":[
                  {"title":"1.向量检索原理","details":["1.1原理: query→embedding→ANN搜索→top-k召回","1.2算法: IVF-PQ、HNSW、LSH等近似最近邻算法","1.3优劣势: 召回率高但计算成本大，需索引优化"]},
                  {"title":"2.混合检索策略","details":["2.1方案: 向量检索+关键词检索(BM25)加权融合","2.2Rerank: 交叉编码器对初排结果精排，提升精度","2.3实践: Cohere Rerank、bge-reranker等主流模型"]}]},
                {"title":"数据Pipeline","subTopics":[
                  {"title":"1.文档处理","details":["1.1切分策略: 按段落/递归字符/语义边界切分","1.2元数据提取: 标题、来源、日期等结构化信息","1.3质量控制: 去重、去噪、敏感信息过滤"]},
                  {"title":"2.向量化管理","details":["2.1模型选择: OpenAI text-embedding-3 / BGE-M3等","2.2索引构建: 分段量化(PQ)降低存储与延迟","2.3增量更新: 新文档入库与过期数据淘汰"]}]},
                {"title":"框架与工具","subTopics":[
                  {"title":"1.编排框架","details":["1.1LangChain: 最流行的LLM应用开发框架，链式抽象","1.2LlamaIndex: 数据框架优先，擅长索引管理与查询路由","1.3Spring AI: Spring生态AI集成，自动配置与Boot对接"]},
                  {"title":"2.向量数据库","details":["2.1Milvus: 云原生向量数据库，支持十亿级规模","2.2Pinecone: 全托管向量数据库服务，零运维","2.3Chroma: 轻量级嵌入式向量库，适合本地开发"]}]},
                {"title":"实践指南","subTopics":[
                  {"title":"1.快速上手","details":["1.1环境准备: Python 3.10+ / JDK 21 + API Key配置","1.2最小Demo: 加载文档→切分→嵌入→存储→检索→生成的完整流程","1.3常见报错: embedding维度不匹配、API限流、连接超时"]},
                  {"title":"2.生产优化","details":["2.1性能优化: 缓存语义缓存、并行检索、流式输出","2.2质量评估: Faithfulness/Answer Relevance等指标","2.3安全考量: 提示注入防护、权限控制、审计日志"]}]
                }]}
                """);

        MindmapGenerationService service = new MindmapGenerationService();
        service.setPlannerClient(chatClient);

        byte[] bytes = service.generateMindmapImage("RAG",
                List.of(new DevToArticle("RAG Basics", "https://dev.to/rag", "rag", 100, "RAG intro")),
                List.of(new SearchResult("RAG Tutorial", "https://example.com/rag", "A tutorial about RAG")));

        String outputDir = "C:/Users/26573/WorkBuddy/2026-08-03-09-27-18/outputs";
        Files.createDirectories(Paths.get(outputDir));
        Files.write(Paths.get(outputDir, "mindmap-preview.png"), bytes);

        // 同时写出可编辑的 .xmind 文件（三层结构）
        byte[] xmind = service.generateXmindFile("RAG",
                List.of(new DevToArticle("RAG Basics", "https://dev.to/rag", "rag", 100, "RAG intro")),
                List.of(new SearchResult("RAG Tutorial", "https://example.com/rag", "A tutorial about RAG")));
        Files.write(Paths.get(outputDir, "mindmap-preview.xmind"), xmind);
    }
}

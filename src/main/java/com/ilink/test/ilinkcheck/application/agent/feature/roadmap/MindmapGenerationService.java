package com.ilink.test.ilinkcheck.application.agent.feature.roadmap;

import com.ilink.test.ilinkcheck.application.agent.dto.roadmap.DevToArticle;
import com.ilink.test.ilinkcheck.application.agent.dto.roadmap.SearchResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.font.TextLayout;
import java.awt.geom.CubicCurve2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 技术路线知识点思维导图生成服务。
 *
 * <p>与 {@link RoadmapComposeService} 渲染的「学习路线」不同，思维导图的内容由 AI
 * 基于抓取到的文章与搜索参考重新总结而来，聚焦「知识点网络」而非「学习步骤」。</p>
 *
 * <p>生成过程分两步：</p>
 * <ol>
 *   <li><b>结构提炼</b>：调用规划用 LLM 把资料总结成 {"topic","branches":[{"title","children":[]}]} 的 JSON；</li>
 *   <li><b>图片渲染</b>：用 Java2D 把结构渲染成 PNG 图片（居中主题 + 彩色渐变分支 + 圆角节点 + 平滑贝塞尔连线 + 阴影），用于微信内即时预览；</li>
 *   <li><b>文件渲染</b>：用标准 Xmind 文件格式（ZIP + content.json）生成可编辑的 .xmind 文件，无需任何第三方依赖，可被现代 Xmind / 百度脑图 / MindMaster 打开二次编辑。</li>
 * </ol>
 *
 * <p>无 LLM 时自动退回基于主题的通用模板结构，保证任何环境下都能产出图片与文件。</p>
 */
@Slf4j
@Service
public class MindmapGenerationService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** 规划用 ChatClient（可选）。无 Client 时退回通用模板。 */
    private ChatClient plannerClient;

    /** 思维导图 LLM 结构提炼的最长等待时间（毫秒）。三层 prompt 输出量大（~50+ 条 detail JSON），
     * qwen3.7-plus 生成约需 20~50 秒。超时即退回通用模板，避免同步阻塞 webhook 线程直到应用关闭才被中断。 */
    @Value("${app.agent.mindmap-timeout-ms:60000}")
    long mindmapTimeoutMs = 60000;

    @Autowired(required = false)
    public void setPlannerClient(@Qualifier("plannerChatClient") ChatClient plannerChatClient) {
        this.plannerClient = plannerChatClient;
    }

    /**
     * 生成思维导图产物：精美 PNG 图片（用于微信内即时预览）+ 可编辑 .xmind 文件（标准 Xmind 格式）。
     * 两者基于同一份 AI 总结结构，内容保持一致。
     *
     * @param topic      学习主题
     * @param articles   Dev.to 文章列表
     * @param references 网页参考列表
     * @return 包含 PNG 图片字节与 .xmind 文件字节的产物
     */
    public MindmapArtifacts generateMindmap(String topic,
                                            List<DevToArticle> articles,
                                            List<SearchResult> references) {
        log.info("[思维导图] 开始生成，topic={}, articles={}, references={}", topic,
                articles != null ? articles.size() : 0, references != null ? references.size() : 0);
        long t0 = System.currentTimeMillis();
        MindmapStructure structure = summarizeStructure(topic, articles, references);
        log.info("[思维导图] LLM结构提炼完成，耗时={}ms, branches={}", System.currentTimeMillis() - t0, structure.branches().size());

        long t1 = System.currentTimeMillis();
        byte[] png = renderImage(structure);
        log.info("[思维导图] PNG渲染完成，耗时={}ms, 大小={}KB", System.currentTimeMillis() - t1, png.length / 1024);

        long t2 = System.currentTimeMillis();
        byte[] xmind = renderXmind(structure);
        log.info("[思维导图] Xmind渲染完成，耗时={}ms, 大小={}KB", System.currentTimeMillis() - t2, xmind.length / 1024);

        return new MindmapArtifacts(png, xmind);
    }

    /**
     * 仅生成思维导图 PNG 图片字节（兼容旧调用与测试）。
     */
    public byte[] generateMindmapImage(String topic,
                                       List<DevToArticle> articles,
                                       List<SearchResult> references) {
        return generateMindmap(topic, articles, references).pngImage();
    }

    /**
     * 仅生成可编辑 .xmind 文件字节（兼容测试）。
     */
    public byte[] generateXmindFile(String topic,
                                    List<DevToArticle> articles,
                                    List<SearchResult> references) {
        MindmapStructure structure = summarizeStructure(topic, articles, references);
        return renderXmind(structure);
    }

    /**
     * 基于资料总结思维导图结构。有 LLM 时走 LLM，否则用模板兜底。
     */
    MindmapStructure summarizeStructure(String topic,
                                        List<DevToArticle> articles,
                                        List<SearchResult> references) {
        if (plannerClient != null) {
            try {
                log.info("[思维导图] 使用LLM提炼结构，timeout={}ms", mindmapTimeoutMs);
                return summarizeWithLlm(topic, articles, references);
            } catch (Exception e) {
                log.warn("[思维导图] LLM结构提炼失败，退回模板：{}", e.getMessage());
            }
        }
        log.info("[思维导图] 无plannerClient，使用fallback模板");
        return fallbackStructure(topic);
    }

    private MindmapStructure summarizeWithLlm(String topic,
                                              List<DevToArticle> articles,
                                              List<SearchResult> references) throws Exception {
        String context = buildContext(topic, articles, references);
        String raw;
        try {
            // 在独立线程跑 LLM 调用并加超时保护：避免模型/网络慢时同步阻塞 webhook 线程，
            // 直到应用关闭（JVM shutdown）才被中断——之前日志里的 "Thread interrupted while sleeping" 就是这个原因。
            raw = CompletableFuture.supplyAsync(() ->
                    plannerClient.prompt()
                            .system(MINDMAP_PROMPT)
                            .user(context)
                            .call()
                            .content()
            ).get(mindmapTimeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            log.warn("LLM 思维导图结构提炼超时（上限 {} ms），退回模板：{}", mindmapTimeoutMs, e.getMessage());
            throw new IllegalStateException("mindmap llm timeout", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("LLM 思维导图结构提炼被中断，退回模板：{}", e.getMessage());
            throw new IllegalStateException("mindmap llm interrupted", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            log.warn("LLM 思维导图结构提炼异常，退回模板：{}", cause.getMessage());
            throw new IllegalStateException("mindmap llm error", cause);
        }
        String json = extractJson(raw);
        MindmapStructureDto dto = OBJECT_MAPPER.readValue(json, MindmapStructureDto.class);
        return validateAndNormalize(topic, dto);
    }

    private static final String MINDMAP_PROMPT = """
            你是一位技术知识梳理专家。请基于用户给定的主题和资料，总结成一份结构清晰、内容详实的「完整知识点思维导图」。

            核心要求——展开到最小层级，每个分支附带完整知识点、定义、考点、举例：
            1. 输出严格 JSON，不要任何 markdown 代码块或额外解释；
            2. JSON 结构：{"topic":"中心主题","branches":[{"title":"分支标题（如：基础概念）","subTopics":[{"title":"编号.子主题名（如：1.JPA介绍）","details":["编号.编号 标签: 具体解释内容（如：1.1定义: Java持久化规范，ORM标准接口）","..."]}]}]}；
            3. 分支数量 5~8 个，覆盖该主题不同知识维度（基础概念、核心原理、关键组件、实践指南、进阶方向等）；
            4. 每个分支包含 2~4 个子主题（subTopics），每个子主题包含 2~4 条具体细节（details）；
            5. 每条 detail 必须是「标签+具体内容」格式，常用标签包括：
               - 定义: 它是什么、官方/学术定义
               - 作用/意义: 为什么需要它、解决什么问题
               - 核心/要点: 最关键的知识点或机制
               - 语法/用法: 怎么写、API/配置示例
               - 示例/案例: 一段简短的代码或场景举例
               - 注意/坑点: 易错、性能、安全等注意事项
            6. detail 内容必须来自或紧密结合提供的资料，禁止空泛套话；每条控制在 15~40 个汉字；
            7. 子主题标题控制在 4~16 个汉字；
            8. 主题若含英文术语，保持规范大小写（如 RAG、eBPF、Spring AI）。
            """;

    private String buildContext(String topic,
                                List<DevToArticle> articles,
                                List<SearchResult> references) {
        StringBuilder sb = new StringBuilder();
        sb.append("主题：").append(topic).append("\n\n");
        sb.append("可用资料：\n");
        boolean hasAny = false;
        if (articles != null) {
            for (DevToArticle a : articles) {
                sb.append("- [文章] ").append(a.title()).append(" | ").append(a.description()).append("\n");
                hasAny = true;
            }
        }
        if (references != null) {
            for (SearchResult r : references) {
                sb.append("- [参考] ").append(r.title()).append(" | ").append(r.snippet()).append("\n");
                hasAny = true;
            }
        }
        if (!hasAny) {
            sb.append("- （暂无可用资料，请基于通用技术常识总结）\n");
        }
        return sb.toString();
    }

    private MindmapStructure validateAndNormalize(String topic, MindmapStructureDto dto) {
        if (dto == null || dto.branches == null || dto.branches.isEmpty()) {
            return fallbackStructure(topic);
        }
        String center = (dto.topic == null || dto.topic.isBlank()) ? topic : dto.topic;
        List<MindmapBranch> branches = new ArrayList<>();
        for (MindmapStructureDto.MindmapBranchDto b : dto.branches) {
            if (b == null || b.title == null || b.title.isBlank()) {
                continue;
            }
            List<MindmapSubTopic> subTopics = new ArrayList<>();
            if (b.subTopics != null) {
                for (MindmapStructureDto.MindmapSubTopicDto st : b.subTopics) {
                    if (st == null || st.title == null || st.title.isBlank()) continue;
                    List<String> details = (st.details == null) ? List.of() : st.details.stream()
                            .filter(d -> d != null && !d.isBlank())
                            .limit(6)
                            .toList();
                    if (details.isEmpty()) details = List.of("核心要点：详见资料");
                    subTopics.add(new MindmapSubTopic(st.title.trim(), details));
                }
            }
            if (subTopics.isEmpty()) {
                // 兼容旧格式（LLM 返回 children 而非 subTopics 时）
                subTopics = List.of(
                        new MindmapSubTopic("核心概念", List.of("定义与背景", "关键术语")),
                        new MindmapSubTopic("实践要点", List.of("实现方式", "常见问题"))
                );
            }
            branches.add(new MindmapBranch(b.title.trim(), subTopics));
        }
        if (branches.isEmpty()) {
            return fallbackStructure(topic);
        }
        return new MindmapStructure(center, branches);
    }

    private MindmapStructure fallbackStructure(String topic) {
        return new MindmapStructure(topic, List.of(
                new MindmapBranch("基础概念", List.of(
                        new MindmapSubTopic("1.定义与背景", List.of(
                                "1.1定义: " + topic + "的核心技术定义与定位",
                                "1.2背景: 产生原因、解决什么业务问题",
                                "1.3发展: 演进历程与版本变迁"
                        )),
                        new MindmapSubTopic("2.核心术语", List.of(
                                "2.1关键术语: 最核心的技术名词解释",
                                "2.2相关概念: 与之关联的其他技术概念",
                                "2.3易混淆点: 常被误解或混用的地方"
                        ))
                )),
                new MindmapBranch("核心原理", List.of(
                        new MindmapSubTopic("1.工作原理", List.of(
                                "1.1整体流程: 端到端的处理流程概述",
                                "1.2关键机制: 内部运作的核心算法或协议",
                                "1.3数据流转: 数据在各组件间的流动路径"
                        )),
                        new MindmapSubTopic("2.设计思想", List.of(
                                "2.1架构理念: 设计哲学与架构原则",
                                "2.2权衡取舍: 在性能/一致性等方面的取舍",
                                "2.3适用边界: 适合与不适合的场景"
                        ))
                )),
                new MindmapBranch("关键组件", List.of(
                        new MindmapSubTopic("1.核心模块", List.of(
                                "1.1模块A职责: 核心模块的功能与接口",
                                "1.2模块B职责: 辅助模块的协作方式",
                                "1.3模块交互: 模块间的调用关系"
                        )),
                        new MindmapSubTopic("2.配置要点", List.of(
                                "2.1基础配置: 最小可用配置示例",
                                "2.2高级参数: 关键调优参数说明",
                                "2.3环境依赖: 运行所需的外部条件"
                        ))
                )),
                new MindmapBranch("实践指南", List.of(
                        new MindmapSubTopic("1.快速上手", List.of(
                                "1.1环境准备: 开发环境搭建步骤",
                                "1.2最小Demo: 可运行的Hello World级示例",
                                "1.3常见报错: 新手最容易遇到的问题"
                        )),
                        new MindmapSubTopic("2.实战要点", List.of(
                                "2.1最佳实践: 业界推荐的使用方式",
                                "2.2性能优化: 典型瓶颈与优化手段",
                                "2.3避坑指南: 生产环境踩过的坑"
                        ))
                )),
                new MindmapBranch("生态工具", List.of(
                        new MindmapSubTopic("1.官方工具", List.of(
                                "1.1CLI工具: 命令行工具的核心用法",
                                "1.2管理后台: 可视化管理界面功能",
                                "1.3监控诊断: 内置的监控与诊断能力"
                        )),
                        new MindmapSubTopic("2.社区生态", List.of(
                                "2.1主流框架: 社区主流集成方案",
                                "2.2周边工具: 第三方配套工具",
                                "2.3学习资源: 推荐文档与教程"
                        ))
                ))
        ));
    }

    /**
     * 用 Java2D 把思维导图结构渲染成 PNG（三层：分支 → 子主题节点[内含定义/考点/举例]）。
     */
    private byte[] renderImage(MindmapStructure structure) {
        int width = 3200;
        int height = 2800;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();

        try {
            setupHighQualityRendering(g);
            drawBackground(g, width, height);

            Font baseFont = resolveChineseFont(Font.PLAIN, 16);
            Font titleFont = baseFont.deriveFont(Font.BOLD, 30);
            Font branchFont = baseFont.deriveFont(Font.BOLD, 20);
            Font subTitleFont = baseFont.deriveFont(Font.BOLD, 16);
            Font detailFont = baseFont.deriveFont(Font.PLAIN, 14);

            int cx = width / 2;
            int cy = height / 2 - 60;
            int branchRadius = Math.min(520, (int) (Math.min(width, height) * 0.24));
            int subTopicRadius = Math.min(380, (int) (Math.min(width, height) * 0.15));

            // 预计算布局（三层：中心→分支→子主题[内含细节]）
            List<BranchLayout> layouts = computeLayouts(g, structure, cx, cy, branchRadius, subTopicRadius,
                    branchFont, subTitleFont, detailFont);

            // 预计算中心节点尺寸
            Dimension centerSize = measureCenterNode(g, structure.topic(), titleFont);

            // 先画所有连线
            for (BranchLayout layout : layouts) {
                drawSmoothConnection(g, cx, cy, centerSize.width, centerSize.height,
                        layout.bx, layout.by, layout.bw, layout.bh,
                        layout.color, 4f);
                for (SubTopicLayout st : layout.subTopics) {
                    drawSmoothConnection(g, layout.bx, layout.by, layout.bw, layout.bh,
                            st.x, st.y, st.w, st.h,
                            layout.color, 2f);
                }
            }

            // 画中心主题节点
            drawCenterNode(g, cx, cy, structure.topic(), titleFont);

            // 画分支与子主题节点
            for (BranchLayout layout : layouts) {
                drawBranchNode(g, layout, branchFont);
                for (SubTopicLayout st : layout.subTopics) {
                    drawSubTopicNode(g, st, layout.color, subTitleFont, detailFont);
                }
            }

            // 底部水印
            drawFooter(g, width, height, baseFont);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "png", baos);
            return baos.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("思维导图图片渲染失败", e);
        } finally {
            g.dispose();
        }
    }

    /**
     * 用标准 Xmind 文件格式（ZIP + content.json）生成可编辑的 .xmind 思维导图文件字节。
     *
     * <p>不依赖任何第三方库（博客推荐的 2021 年老依赖 {@code com.github.eljah:xmindjbehaveplugin}
     * 在离线构建环境下拉取不到且传递依赖陈旧），仅用 JDK 的 {@code java.util.zip} + Jackson 直接拼装，
     * 产物可被现代 Xmind / 百度脑图 / MindMaster 打开并二次编辑。</p>
     */
    private byte[] renderXmind(MindmapStructure structure) {
        try {
            Map<String, Object> sheet = new LinkedHashMap<>();
            sheet.put("id", "sheet-1");
            sheet.put("class", "sheet");
            sheet.put("title", "Sheet 1");
            sheet.put("rootTopic", buildTopic(structure.topic(), structure.branches()));

            List<Object> sheets = new ArrayList<>();
            sheets.add(sheet);

            String contentJson = OBJECT_MAPPER.writeValueAsString(sheets);
            String metadataJson = OBJECT_MAPPER.writeValueAsString(
                    Map.of("creator", Map.of("name", "iLink 技术路线规划", "version", "1.0")));
            String manifestJson = OBJECT_MAPPER.writeValueAsString(
                    Map.of("file-entries", Map.of(
                            "content.json", Map.of("media-type", "application/vnd.xmind.workbook"),
                            "metadata.json", Map.of("media-type", "application/json"))));

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (ZipOutputStream zos = new ZipOutputStream(baos)) {
                zos.setLevel(Deflater.DEFAULT_COMPRESSION);
                writeZipEntry(zos, "content.json", contentJson);
                writeZipEntry(zos, "metadata.json", metadataJson);
                writeZipEntry(zos, "manifest.json", manifestJson);
                writeZipEntry(zos, "META-INF/manifest.xml", buildManifestXml());
            }
            return baos.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("思维导图 .xmind 文件生成失败", e);
        }
    }

    private void writeZipEntry(ZipOutputStream zos, String name, String content) throws IOException {
        ZipEntry entry = new ZipEntry(name);
        zos.putNextEntry(entry);
        zos.write(content.getBytes(StandardCharsets.UTF_8));
        zos.closeEntry();
    }

    private String buildManifestXml() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>\n" +
                "<manifest xmlns=\"urn:xmind:xmap:xmlns:manifest:1.0\">\n" +
                "  <file-entry full-path=\"content.json\" media-type=\"application/vnd.xmind.workbook\"/>\n" +
                "  <file-entry full-path=\"metadata.json\" media-type=\"application/json\"/>\n" +
                "</manifest>\n";
    }

    /**
     * 把三层结构转成 Xmind 的 topic JSON 对象（分支 → 子主题 → 细节叶子节点）。
     */
    private Map<String, Object> buildTopic(String title, List<MindmapBranch> branches) {
        Map<String, Object> topic = new LinkedHashMap<>();
        topic.put("id", randomId());
        topic.put("class", "topic");
        topic.put("title", title);

        if (branches != null && !branches.isEmpty()) {
            topic.put("structureClass", "org.xmind.ui.map.balanced");

            List<Object> branchTopics = new ArrayList<>();
            for (MindmapBranch b : branches) {
                Map<String, Object> bt = new LinkedHashMap<>();
                bt.put("id", randomId());
                bt.put("class", "topic");
                bt.put("title", b.title());

                List<MindmapSubTopic> subTopics = b.subTopics();
                if (subTopics != null && !subTopics.isEmpty()) {
                    List<Object> stTopics = new ArrayList<>();
                    for (MindmapSubTopic st : subTopics) {
                        Map<String, Object> stMap = new LinkedHashMap<>();
                        stMap.put("id", randomId());
                        stMap.put("class", "topic");
                        stMap.put("title", st.title());

                        // 细节作为叶子节点
                        if (st.details() != null && !st.details().isEmpty()) {
                            List<Object> detailTopics = new ArrayList<>();
                            for (String detail : st.details()) {
                                Map<String, Object> dt = new LinkedHashMap<>();
                                dt.put("id", randomId());
                                dt.put("class", "topic");
                                dt.put("title", detail);
                                detailTopics.add(dt);
                            }
                            Map<String, Object> detailChildren = new LinkedHashMap<>();
                            detailChildren.put("attached", detailTopics);
                            stMap.put("children", detailChildren);
                        }
                        stTopics.add(stMap);
                    }
                    Map<String, Object> stChildren = new LinkedHashMap<>();
                    stChildren.put("attached", stTopics);
                    bt.put("children", stChildren);
                }
                branchTopics.add(bt);
            }
            Map<String, Object> childrenMap = new LinkedHashMap<>();
            childrenMap.put("attached", branchTopics);
            topic.put("children", childrenMap);
        }
        return topic;
    }

    private String randomId() {
        return "t" + UUID.randomUUID().toString().replace("-", "");
    }

    private void setupHighQualityRendering(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY);
    }

    private void drawBackground(Graphics2D g, int width, int height) {
        // 柔和浅灰背景
        g.setColor(new Color(248, 249, 252));
        g.fillRect(0, 0, width, height);

        // 极淡的径向渐变，突出中心
        RadialGradientPaint centerGlow = new RadialGradientPaint(
                width / 2f, height / 2f, Math.max(width, height) * 0.55f,
                new float[]{0f, 1f},
                new Color[]{new Color(255, 255, 255, 180), new Color(248, 249, 252, 0)}
        );
        g.setPaint(centerGlow);
        g.fillRect(0, 0, width, height);

        //  subtle 点状网格
        g.setColor(new Color(220, 225, 235, 80));
        int step = 40;
        for (int x = 0; x < width; x += step) {
            for (int y = 0; y < height; y += step) {
                g.fillOval(x, y, 2, 2);
            }
        }
    }

    private Dimension measureCenterNode(Graphics2D g, String text, Font font) {
        Font prev = g.getFont();
        try {
            g.setFont(font);
            FontMetrics fm = g.getFontMetrics();
            int paddingX = 52;
            int paddingY = 36;
            int titleW = Math.max(fm.stringWidth(text) + paddingX * 2, 180);
            int titleH = fm.getHeight() + paddingY * 2;
            return new Dimension(titleW, titleH);
        } finally {
            g.setFont(prev);
        }
    }

    private Dimension drawCenterNode(Graphics2D g, int cx, int cy, String text, Font font) {
        g.setFont(font);
        FontMetrics fm = g.getFontMetrics();
        int paddingX = 52;
        int paddingY = 36;
        int titleW = Math.max(fm.stringWidth(text) + paddingX * 2, 180);
        int titleH = fm.getHeight() + paddingY * 2;

        RoundRectangle2D node = new RoundRectangle2D.Double(
                cx - titleW / 2.0, cy - titleH / 2.0, titleW, titleH, 36, 36);

        // 外发光
        drawShadow(g, node, new Color(59, 130, 246, 60), 20, 0);
        // 阴影
        drawShadow(g, node, new Color(0, 0, 0, 45), 14, 8);

        // 渐变填充
        GradientPaint gradient = new GradientPaint(
                cx - titleW / 2f, cy - titleH / 2f, new Color(79, 70, 229),
                cx + titleW / 2f, cy + titleH / 2f, new Color(59, 130, 246));
        g.setPaint(gradient);
        g.fill(node);

        // 白色细边框
        g.setColor(new Color(255, 255, 255, 200));
        g.setStroke(new BasicStroke(2.5f));
        g.draw(node);

        g.setColor(Color.WHITE);
        drawCenteredString(g, text, cx, cy);

        return new Dimension(titleW, titleH);
    }

    private void drawBranchNode(Graphics2D g, BranchLayout layout, Font font) {
        g.setFont(font);
        FontMetrics fm = g.getFontMetrics();
        int paddingX = 28;
        int paddingY = 18;
        int bw = Math.max(fm.stringWidth(layout.branch.title()) + paddingX * 2, 120);
        int bh = fm.getHeight() + paddingY * 2;
        layout.bw = bw;
        layout.bh = bh;

        RoundRectangle2D node = new RoundRectangle2D.Double(
                layout.bx - bw / 2.0, layout.by - bh / 2.0, bw, bh, 20, 20);
        layout.nodeShape = node;

        drawShadow(g, node, new Color(0, 0, 0, 35), 10, 5);

        GradientPaint gradient = new GradientPaint(
                layout.bx - bw / 2f, layout.by - bh / 2f, layout.color,
                layout.bx + bw / 2f, layout.by + bh / 2f, layout.color.darker());
        g.setPaint(gradient);
        g.fill(node);

        g.setColor(layout.color.darker().darker());
        g.setStroke(new BasicStroke(1.5f));
        g.draw(node);

        g.setColor(Color.WHITE);
        drawCenteredString(g, layout.branch.title(), layout.bx, layout.by);
    }

    /**
     * 画子主题节点：标题 + 多行细节内容（定义/考点/举例），是三层结构的核心展示单元。
     */
    private void drawSubTopicNode(Graphics2D g, SubTopicLayout st, Color branchColor,
                                   Font subTitleFont, Font detailFont) {
        int cw = st.w;
        int ch = st.h;

        RoundRectangle2D node = new RoundRectangle2D.Double(
                st.x - cw / 2.0, st.y - ch / 2.0, cw, ch, 14, 14);
        st.nodeShape = node;

        // 阴影
        drawShadow(g, node, new Color(0, 0, 0, 30), 8, 4);

        // 白底填充
        g.setColor(new Color(255, 255, 255, 250));
        g.fill(node);

        // 双边框：外浅内深
        g.setColor(branchColor.brighter());
        g.setStroke(new BasicStroke(1f));
        g.draw(node);

        g.setColor(branchColor);
        g.setStroke(new BasicStroke(2f));
        RoundRectangle2D innerNode = new RoundRectangle2D.Double(
                st.x - cw / 2.0 + 2, st.y - ch / 2.0 + 2, cw - 4, ch - 4, 11, 11);
        g.draw(innerNode);

        // 绘制文字：标题（加粗）+ 细节列表
        g.setColor(branchColor.darker().darker());
        g.setFont(subTitleFont);
        FontMetrics titleFm = g.getFontMetrics();
        int textTop = st.y - ch / 2 + 16 + titleFm.getAscent();
        g.drawString(st.subTopic.title(), st.x - titleFm.stringWidth(st.subTopic.title()) / 2, textTop);

        // 分隔线
        g.setColor(new Color(220, 225, 235));
        g.setStroke(new BasicStroke(0.8f));
        int sepY = textTop + 8;
        g.drawLine(st.x - cw / 2 + 12, sepY, st.x + cw / 2 - 12, sepY);

        // 细节文本（左对齐）
        g.setFont(detailFont);
        g.setColor(new Color(50, 52, 65));
        FontMetrics detailFm = g.getFontMetrics();
        int lineH = detailFm.getHeight() + 3;
        int detailY = sepY + 10;
        int leftMargin = st.x - cw / 2 + 16;
        int maxTextW = cw - 32;

        for (String detail : st.subTopic.details()) {
            List<String> lines = wrapText(detail, detailFm, maxTextW);
            for (String line : lines) {
                if (detailY + detailFm.getAscent() > st.y + ch / 2 - 8) break; // 超出节点范围则截断
                g.drawString(line, leftMargin, detailY + detailFm.getAscent());
                detailY += lineH;
            }
        }
    }

    private void drawSmoothConnection(Graphics2D g,
                                      int x1, int y1, int w1, int h1,
                                      int x2, int y2, int w2, int h2,
                                      Color color, float strokeWidth) {
        g.setColor(color);
        g.setStroke(new BasicStroke(strokeWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

        // 计算两个矩形边缘的交点，使连线从节点边缘出发而非中心
        Point p1 = rectEdgePoint(x1, y1, w1, h1, x2 - x1, y2 - y1);
        Point p2 = rectEdgePoint(x2, y2, w2, h2, x1 - x2, y1 - y2);

        // 三次贝塞尔曲线，控制点沿连线方向偏移，形成自然弧度
        double dx = p2.x - p1.x;
        double dy = p2.y - p1.y;
        double dist = Math.hypot(dx, dy);
        if (dist < 1) {
            return;
        }
        double cpFactor = 0.5;
        int cp1x = p1.x + (int) (dx * cpFactor);
        int cp1y = p1.y + (int) (dy * cpFactor);
        int cp2x = p2.x - (int) (dx * cpFactor);
        int cp2y = p2.y - (int) (dy * cpFactor);

        CubicCurve2D curve = new CubicCurve2D.Double(p1.x, p1.y, cp1x, cp1y, cp2x, cp2y, p2.x, p2.y);
        g.draw(curve);
    }

    /**
     * 计算从矩形中心出发、沿 (dx,dy) 方向与矩形边界的交点。
     */
    private Point rectEdgePoint(int cx, int cy, int w, int h, double dx, double dy) {
        if (Math.abs(dx) < 0.001 && Math.abs(dy) < 0.001) {
            return new Point(cx, cy);
        }
        // 判断连线与矩形的左右边还是上下边相交
        if (Math.abs(dx) * h > Math.abs(dy) * w) {
            // 与左右边相交
            int x = cx + (int) (Math.signum(dx) * w / 2.0);
            int y = cy + (int) (dy / dx * Math.signum(dx) * w / 2.0);
            return new Point(x, y);
        } else {
            // 与上下边相交
            int y = cy + (int) (Math.signum(dy) * h / 2.0);
            int x = cx + (int) (dx / dy * Math.signum(dy) * h / 2.0);
            return new Point(x, y);
        }
    }

    private void drawShadow(Graphics2D g, RoundRectangle2D shape, Color shadowColor, int blur, int offsetY) {
        // 简单阴影：先画一个偏移、半透明的圆角矩形
        RoundRectangle2D shadow = new RoundRectangle2D.Double(
                shape.getX() + 4, shape.getY() + offsetY,
                shape.getWidth(), shape.getHeight(),
                shape.getArcWidth(), shape.getArcHeight());
        g.setColor(shadowColor);
        g.fill(shadow);
    }

    private void drawFooter(Graphics2D g, int width, int height, Font baseFont) {
        g.setFont(baseFont.deriveFont(Font.ITALIC, 13));
        g.setColor(new Color(150, 155, 170));
        String footer = "由 AI 基于资料总结生成 · 思维导图";
        FontMetrics fm = g.getFontMetrics();
        g.drawString(footer, width - fm.stringWidth(footer) - 30, height - 28);
    }

    private List<BranchLayout> computeLayouts(Graphics2D g, MindmapStructure structure, int cx, int cy,
                                              int branchRadius, int subTopicRadius,
                                              Font branchFont, Font subTitleFont, Font detailFont) {
        List<MindmapBranch> branches = structure.branches();
        int branchCount = branches.size();
        List<BranchLayout> layouts = new ArrayList<>();

        g.setFont(branchFont);
        FontMetrics branchFm = g.getFontMetrics();
        int branchPadX = 24;
        int branchPadY = 16;

        g.setFont(subTitleFont);
        FontMetrics subFm = g.getFontMetrics();
        g.setFont(detailFont);
        FontMetrics detailFm = g.getFontMetrics();

        int maxDetailTextW = 260; // 子主题节点最大宽度

        for (int i = 0; i < branchCount; i++) {
            double angle = 2 * Math.PI * i / branchCount - Math.PI / 2;
            Color color = BRANCH_COLORS[i % BRANCH_COLORS.length];
            int bx = cx + (int) (branchRadius * Math.cos(angle));
            int by = cy + (int) (branchRadius * Math.sin(angle));

            MindmapBranch branch = branches.get(i);
            BranchLayout layout = new BranchLayout(branch, bx, by, angle, color);
            layout.bw = Math.max(branchFm.stringWidth(branch.title()) + branchPadX * 2, 110);
            layout.bh = branchFm.getHeight() + branchPadY * 2;

            // 子主题沿分支外侧扇形展开
            List<MindmapSubTopic> subTopics = branch.subTopics();
            int stCount = subTopics.size();
            if (stCount > 0) {
                // 动态半径：子主题越多越远
                int dynamicRadius = subTopicRadius + (stCount - 3) * 22;
                dynamicRadius = Math.max(300, Math.min(dynamicRadius, 480));

                // 扇形角度
                double maxSpread = Math.toRadians(120);
                double minSpread = Math.toRadians(30);
                double spread = Math.min(maxSpread,
                        Math.max(minSpread, Math.toRadians(20) * stCount));
                double startAngle = angle - spread / 2;
                double step = stCount == 1 ? 0 : spread / (stCount - 1);

                for (int j = 0; j < stCount; j++) {
                    double stAngle = startAngle + step * j;
                    int six = bx + (int) (dynamicRadius * Math.cos(stAngle));
                    int siy = by + (int) (dynamicRadius * Math.sin(stAngle));

                    MindmapSubTopic st = subTopics.get(j);

                    // 计算子主题节点尺寸（标题 + 细节文本）
                    int titleW = subFm.stringWidth(st.title());
                    int detailBlockW = 0;
                    int totalDetailH = 0;
                    int lineH = detailFm.getHeight() + 3;
                    for (String detail : st.details()) {
                        List<String> lines = wrapText(detail, detailFm, maxDetailTextW);
                        for (String line : lines) {
                            detailBlockW = Math.max(detailBlockW, detailFm.stringWidth(line));
                            totalDetailH += lineH;
                        }
                    }
                    int contentW = Math.max(titleW, detailBlockW);
                    int sw = contentW + 32; // 左右 padding
                    int sh = 28 + 8 + totalDetailH + 14; // 标题区 + 分隔线 + 细节区 + 底部 padding

                    // 限制最大尺寸防止节点过大
                    sw = Math.min(sw, 340);
                    sh = Math.min(sh, 220);

                    layout.subTopics.add(new SubTopicLayout(st, six, siy, sw, sh));
                }
            }
            layouts.add(layout);
        }

        // 边界适配
        int minMargin = 60;
        int maxOutX = 0, maxOutY = 0;
        for (BranchLayout layout : layouts) {
            for (SubTopicLayout st : layout.subTopics) {
                int half = Math.max(st.w, st.h) / 2 + 20;
                maxOutX = Math.max(maxOutX, Math.abs(st.x - cx) + half);
                maxOutY = Math.max(maxOutY, Math.abs(st.y - cy) + half);
            }
        }

        int availableX = 3200 / 2 - minMargin;
        int availableY = 2800 / 2 - minMargin;
        double scaleX = maxOutX > availableX ? (double) availableX / maxOutX : 1.0;
        double scaleY = maxOutY > availableY ? (double) availableY / maxOutY : 1.0;
        double scale = Math.min(scaleX, scaleY);

        if (scale < 1.0) {
            for (BranchLayout layout : layouts) {
                layout.bx = cx + (int) ((layout.bx - cx) * scale);
                layout.by = cy + (int) ((layout.by - cy) * scale);
                for (SubTopicLayout st : layout.subTopics) {
                    st.x = cx + (int) ((st.x - cx) * scale);
                    st.y = cy + (int) ((st.y - cy) * scale);
                }
            }
        }

        return layouts;
    }

    private List<String> wrapText(String text, FontMetrics fm, int maxWidth) {
        if (fm.stringWidth(text) <= maxWidth) {
            return List.of(text);
        }
        List<String> lines = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (char c : text.toCharArray()) {
            if (fm.stringWidth(current.toString() + c) > maxWidth && !current.isEmpty()) {
                lines.add(current.toString());
                current = new StringBuilder();
            }
            current.append(c);
        }
        if (!current.isEmpty()) {
            lines.add(current.toString());
        }
        return lines.isEmpty() ? List.of(text) : lines;
    }

    private int linesWidth(List<String> lines, FontMetrics fm) {
        int max = 0;
        for (String line : lines) {
            max = Math.max(max, fm.stringWidth(line));
        }
        return max;
    }

    private void drawCenteredString(Graphics2D g, String text, int cx, int cy) {
        FontMetrics fm = g.getFontMetrics();
        int x = cx - fm.stringWidth(text) / 2;
        int y = cy + fm.getAscent() / 2 - fm.getDescent() / 2;
        g.drawString(text, x, y);
    }

    private Font resolveChineseFont(int style, int size) {
        String[] candidates = {
                "Microsoft YaHei",
                "SimHei",
                "SimSun",
                "Noto Sans CJK SC",
                "WenQuanYi Micro Hei",
                "PingFang SC",
                "Source Han Sans SC"
        };
        for (String name : candidates) {
            Font font = new Font(name, style, size);
            if (font.canDisplay('中')) {
                return font;
            }
        }
        return new Font(Font.SANS_SERIF, style, size);
    }

    private String extractJson(String raw) {
        if (raw == null) {
            return "{}";
        }
        String text = raw.trim();
        if (text.startsWith("```")) {
            int firstNewline = text.indexOf('\n');
            if (firstNewline >= 0) {
                text = text.substring(firstNewline + 1);
            }
            int lastFence = text.lastIndexOf("```");
            if (lastFence >= 0) {
                text = text.substring(0, lastFence);
            }
            text = text.trim();
        }
        return text;
    }

    private static final Color[] BRANCH_COLORS = {
            new Color(239, 68, 68),   // red
            new Color(245, 158, 11),  // amber
            new Color(16, 185, 129),  // emerald
            new Color(6, 182, 212),   // cyan
            new Color(59, 130, 246),  // blue
            new Color(139, 92, 246),  // violet
            new Color(236, 72, 153),  // pink
            new Color(249, 115, 22)   // orange
    };

    public record MindmapStructure(String topic, List<MindmapBranch> branches) {
    }

    /**
     * 思维导图一次生成的产物：PNG 图片字节（即时预览用）+ .xmind 文件字节（可编辑用）。
     */
    public record MindmapArtifacts(byte[] pngImage, byte[] xmindFile) {
    }

    public record MindmapBranch(String title, List<MindmapSubTopic> subTopics) {
    }

    /** 子主题：标题 + 具体细节列表（定义/考点/举例等） */
    public record MindmapSubTopic(String title, List<String> details) {
    }

    private static class BranchLayout {
        final MindmapBranch branch;
        int bx, by;
        final double angle;
        final Color color;
        int bw, bh;
        RoundRectangle2D nodeShape;
        final List<SubTopicLayout> subTopics = new ArrayList<>();

        BranchLayout(MindmapBranch branch, int bx, int by, double angle, Color color) {
            this.branch = branch;
            this.bx = bx;
            this.by = by;
            this.angle = angle;
            this.color = color;
        }
    }

    /** 子主题布局：位置 + 尺寸 + 对应的数据 */
    private static class SubTopicLayout {
        final MindmapSubTopic subTopic;
        int x, y;
        int w, h;
        RoundRectangle2D nodeShape;

        SubTopicLayout(MindmapSubTopic subTopic, int x, int y, int w, int h) {
            this.subTopic = subTopic;
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
        }
    }

    /**
     * LLM 返回的结构化中间对象（Jackson 直接映射）。
     */
    public static class MindmapStructureDto {
        public String topic;
        public List<MindmapBranchDto> branches;

        public static class MindmapBranchDto {
            public String title;
            public List<MindmapSubTopicDto> subTopics;
        }

        public static class MindmapSubTopicDto {
            public String title;
            public List<String> details;
        }
    }
}

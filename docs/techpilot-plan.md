# TechPilot：开发者技术学习与写作 Copilot — 项目方案与团队分工

## 项目定位

基于微信生态的**开发者技术学习与写作智能助手**。结合 GitHub / Dev.to 官方 Open API、动态 RAG 技术文档规范库与多 Agent 协同，实现"海外技术趋势感知 → 开源项目速读 → 体系化学习 → 规范化博客写作"的全流程闭环。

**核心优势：**
- 100% 合规零风险：全量数据对接国外官方 Open API，绝不触碰任何灰产或爬虫
- 极高工程可行性：基于现有 iLink Bot 底座扩展，微信卡片与 Markdown 交互

---

## 架构设计：MCP vs Skill

```
               [微信网关 / 用户指令]
                         │
                         ▼
             ┌───────────────────────┐
             │   TechPilot 核心路由   │
             └───────────────────────┘
                         │
      ┌──────────────────┴──────────────────┐
      ▼                                     ▼
【MCP 外部数据连接器】                 【Skill 内部业务处理链】
1. GitHub-MCP                         1. GithubRepoDigestSkill
2. DevTo-MCP                          2. TechRoadmapSkill
3. WebSearch-MCP                      3. DocReviewRAGSkill
                                      4. DailyDigestPushSkill
```

- **MCP（Model Context Protocol）**：对外数据连接（GitHub API、Dev.to API 抓取数据）
- **Skill（技能/Agent 流程）**：内部业务处理与 CoT 逻辑（生成学习路线、技术博文审稿、结构化总结）

必须保留的主链路：

`TextMessageProcessor -> ConversationService -> ConversationAiService -> AgentLoopService / ToolSelector / ToolOrchestrator -> WechatMessageGateway`

为了和 `agent.md` 里的计划一致，这里统一口径：

- **MCP**：对外数据接入层。代码落在 `infrastructure.client.*` 和可选的 `infrastructure.mcp.*`
- **Skill**：内部业务编排层。代码落在 `application.agent.feature.*` 和 `application.agent.orchestrator.*`
- **Tool**：暴露给模型或编排器调用的能力壳。代码落在 `application.tool.categories.*`

对应到这次升级：

- GitHub Repo 速读 = `GitHub MCP + RepoDigest Skill + GitHub Tool`
- 技术路线规划 = `Dev.to / WebSearch MCP + Roadmap Skill + Roadmap Tool`
- 博文审稿 = `RAG + Review Skill + Review Tool`
- 每日晨报 = `GitHub / Dev.to MCP + Digest Skill + Digest Tool + Scheduler`

---

## 功能模块

| 模块 | 说明 | 依赖 |
|---|---|---|
| GitHub 热点与开源速读 | 推送 GitHub Trending，发送链接快速提炼项目架构 | GitHub-MCP + GithubRepoDigestSkill |
| 技术路线与博文推荐 | 输入技术主题，抓取 Dev.to/GitHub 优质内容，输出学习路线 | DevTo-MCP + TechRoadmapSkill |
| 博客写作辅助与 RAG 审稿 | 依据中文技术文档规范对草稿进行审稿 | DocReviewRAGSkill + RAG 向量库 |
| 每日/每周技术晨报推送 | 定时推送 GitHub 热门与 Dev.to 趋势 | DailyDigestPushSkill + Cron 定时任务 |

---

## 现有代码复用骨架

| 现有代码 | 本次职责 |
|---|---|
| `ConversationAiService` | 总对话入口 |
| `ToolSelector` / `ToolOrchestrator` | 单轮工具筛选与编排 |
| `AgentLoopService` / `PhaseExecutorRegistry` | 多轮 Agent 状态推进 |
| `ToolRegistry` | Tool 元数据收集 |
| `DocumentExportService` | 导出审稿报告/学习路线/晨报 |
| `ReminderScheduler` / `CronTaskManager` | 定时任务参考实现 |

---

## 六人分工

### 人1：GitHub 数据源

**职责：** 对接 GitHub REST / GraphQL API，提供 repo 基本信息、README、release、star 趋势

| 项 | 内容 |
|---|---|
| 主包 | `infrastructure.client.github` |
| 建议接口 | `GitHubRepoProvider`、`GitHubTrendingProvider` |
| 依赖 | 被人3的 RepoDigest Skill、被人6的 DailyDigest 调用 |
| 交付 | 7/30 mock 可用 → 8/1 真实 client → 8/5 MCP 接入 |

---

### 人2：Dev.to + WebSearch 数据源

**职责：** 对接 Dev.to API 与 WebSearch 补充检索

| 项 | 内容 |
|---|---|
| 主包 | `infrastructure.client.devto`、`infrastructure.client.search` |
| 建议接口 | `DevToArticleProvider`、`WebSearchProvider` |
| 依赖 | 被人4的 TechRoadmap Skill、被人6的 DailyDigest 调用 |
| 交付 | 7/30 mock 可用 → 8/1 真实查询 → 8/5 稳定调用 |

---

### 人3：GitHub Repo 速读

**职责：** GithubRepoDigestSkill，输入 GitHub 链接输出结构化摘要

| 项 | 内容 |
|---|---|
| 主包 | `application.agent.feature.github`、`application.tool.categories.github` |
| 建议类 | `GitHubRepoDigestPhaseExecutor`、`GitHubRepoDigestTool`、`RepoDigestResult` |
| 依赖 | 依赖人1的 `GitHubRepoProvider` |
| 边界 | 不写 HTTP 请求，不改 ConversationAiService，不改 ToolRegistry |
| 交付 | 7/31 mock 输出 → 8/2 真实速读 |

---

### 人4：技术路线规划

**职责：** TechRoadmapSkill，输入技术主题输出体系化学习路线

| 项 | 内容 |
|---|---|
| 主包 | `application.agent.feature.roadmap`、`application.tool.categories.roadmap` |
| 建议类 | `RoadmapPlanPhaseExecutor`、`TechRoadmapTool`、`RoadmapResult` |
| 依赖 | 依赖人2的 `DevToArticleProvider`、`WebSearchProvider` |
| 边界 | 不直接写 API client，不改公共 scheduler |
| 交付 | 7/31 模板 + mock → 8/3 真实推荐 |

---

### 人5：博文审稿 + RAG

**职责：** DocReviewRAGSkill，依据技术文档规范知识库审稿

| 项 | 内容 |
|---|---|
| 主包 | `application.agent.feature.review`、`infrastructure.rag`、`infrastructure.persistence.review` |
| 建议接口 | `StyleGuideRetriever`、`DraftReviewService` |
| 建议类 | `DocReviewPhaseExecutor`、`DocReviewTool`、`ReviewReport` |
| 依赖 | 可复用 `DocumentExportService` |
| 边界 | 审稿逻辑在 feature.review，向量检索在 infrastructure.rag |
| 交付 | 8/1 mock 检索 + 模板 → 8/4 真实 RAG |

---

### 人6：总装 + Daily Digest（共享入口 owner）

**职责：** 维护共享入口和总流程，负责晨报推送，配置/依赖/联调收口

| 项 | 内容 |
|---|---|
| 主包 | `application.agent.orchestrator`、`application.agent.feature.digest`、`infrastructure.scheduler.digest` |
| 建议类 | `DailyDigestPhaseExecutor`、`DailyDigestTool`、`TechDigestScheduler` |
| 依赖 | 依赖人1~人5 所有接口（Mock 先行） |
| 交付 | 7/29 冻结接口 → 7/31 路由可用 → 8/5 全链路 → 8/8 联调 |

**统一维护的公共文件：**
- `pom.xml`、`application*.yml`
- `ConversationAiService`、`IntentRouter`
- `AgentTaskType`、`AgentPhase`、`WorkflowTemplateRegistry`

---

## 协同开发策略

### 接口先行 + Mock 并行

- 人3 先 mock `GitHubRepoProvider`
- 人4 先 mock `DevToArticleProvider` / `WebSearchProvider`
- 人5 先 mock `StyleGuideRetriever`
- 人6 先 mock 所有人，把主链路跑起来

### Git 规则

| 规则 | 说明 |
|---|---|
| 分支命名 | `feat/github-client`、`feat/devto-search-client`、`feat/github-digest`、`feat/roadmap`、`feat/doc-review-rag`、`feat/digest-integration` |
| 集成窗口 | 每天 12:30 和 20:30 各一次 |
| PR 原则 | 每次只做一件事，不超过 400 行 |

### 冲突控制

1. 新 Tool 必须自带 `metadata()`，不改 `ToolRegistry`
2. 新 `PhaseExecutor` 只新增类，不改 `PhaseExecutorRegistry`
3. 共享入口只允许人6改
4. HTTP client 走 `infrastructure.client.*`，不写进 `application`
5. 各 feature 的 DTO 放各自子包

---

## 任务类型与工作流（7/29 冻结）

### AgentTaskType

| 类型 | 用途 |
|---|---|
| `GITHUB_REPO_DIGEST` | GitHub repo 速读 |
| `TECH_ROADMAP` | 技术路线规划 |
| `DOC_REVIEW` | 博文审稿 |
| `DAILY_DIGEST_SUBSCRIBE` | 晨报订阅或推送配置 |

### AgentPhase

| 阶段 | 用途 |
|---|---|
| `GITHUB_FETCH` | 拉取 repo 信息 |
| `GITHUB_SUMMARIZE` | 生成 repo 速读结果 |
| `ROADMAP_FETCH` | 拉取推荐资源 |
| `ROADMAP_COMPOSE` | 生成学习路线 |
| `REVIEW_RETRIEVE_GUIDE` | 检索规范知识 |
| `REVIEW_GENERATE` | 生成审稿报告 |
| `DIGEST_FETCH` | 拉取晨报数据 |
| `DIGEST_COMPOSE` | 组装晨报内容 |
| `WAIT_USER_INPUT` | 等用户补充输入 |
| `FINISH` | 完成 |
| `FAILED` | 失败 |

---

## 时间安排

| 日期 | 里程碑 |
|---|---|
| 7/29 | 冻结包结构、接口、枚举、路由 |
| 7/30-7/31 | 人1/2 mock + 骨架，人3/4/5 基于 mock 开发，人6 打通路由 |
| 8/1-8/3 | GitHub 速读 + 技术路线跑通，第一轮联调 |
| 8/4-8/6 | 审稿/RAG + 晨报定时推送跑通 |
| 8/7-8/8 | 全链路回归，修 bug，补 demo |
| 8/9 | 功能冻结，只修阻断问题 |

---

## 演示场景

### 场景一：GitHub 开源项目速读
用户发 GitHub 链接 → 返回结构化摘要（定位/技术栈/场景/启动命令）

### 场景二：技术路线规划
用户输入技术主题 → 返回阶段学习路线 + 推荐博文

### 场景三：博文 RAG 审稿
用户发草稿 → 返回审稿报告（规范检查 + 修改建议）

### 场景四：每日技术晨报
定时推送 GitHub Trending + Dev.to 热门博文

# TechPilot 六人分工 — 包路径与接口对照

## 人1：GitHub 数据源

| 项 | 内容 |
|---|---|
| 主包 | `infrastructure.client.github` |
| MCP 可选 | `infrastructure.mcp.github` |
| 需实现的接口 | `GitHubRepoProvider` (`application.agent.spi.mcp`) |
| 接口方法 | `fetchRepo(owner, repo)` → `RepoInfo` |
| | `fetchTrending(language, since)` → `List<TrendingRepo>` |
| 消费方 | 人3 (RepoDigest)、人6 (DailyDigest) |
| 测试 Mock | `application.agent.spi.mcp.GitHubRepoProvider` |
| 交付 | 7/30 mock 可用 → 8/1 真实 client |

---

## 人2：Dev.to + WebSearch 数据源

| 项 | 内容 |
|---|---|
| 主包 | `infrastructure.client.devto`、`infrastructure.client.search` |
| MCP 可选 | `infrastructure.mcp.devto`、`infrastructure.mcp.search` |
| 需实现的接口 | `DevToArticleProvider` (`application.agent.spi.mcp`) |
| | `WebSearchProvider` (`application.agent.spi.mcp`) |
| 接口方法 | `DevToArticleProvider.fetchByTag(tag, limit)` → `List<DevToArticle>` |
| | `WebSearchProvider.search(query, limit)` → `List<SearchResult>` |
| 消费方 | 人4 (Roadmap)、人6 (DailyDigest) |
| 测试 Mock | `application.agent.spi.mcp.DevToArticleProvider`、`application.agent.spi.mcp.WebSearchProvider` |
| 交付 | 7/30 mock 可用 → 8/1 真实查询 |

---

## 人3：GitHub Repo 速读

| 项 | 内容 |
|---|---|
| 主包 | `application.agent.feature.github` |
| Tool 包 | `application.tool.categories.github` |
| DTO 包 | `application.agent.dto.github`（共用） |
| 需实现的 Skill | `GithubRepoDigestSkill` (`application.skill`) |
| 需创建的类 | `GitHubRepoDigestService`（业务逻辑） |
| | `GitHubRepoDigestPhaseExecutor`（工作流阶段） |
| | `GitHubRepoDigestTool`（`@Tool` 暴露给 AI） |
| 接口依赖 | 注入人1的 `GitHubRepoProvider` |
| 边界要求 | 不写 HTTP 请求，不改公共入口 |
| 交付 | 7/31 mock 输出 → 8/2 真实速读 |

---

## 人4：技术路线规划

| 项 | 内容 |
|---|---|
| 主包 | `application.agent.feature.roadmap` |
| Tool 包 | `application.tool.categories.roadmap` |
| DTO 包 | `application.agent.dto.roadmap`（共用） |
| 需实现的 Skill | `TechRoadmapSkill` (`application.skill`) |
| 需创建的类 | `RoadmapComposeService`（业务逻辑） |
| | `RoadmapPlanPhaseExecutor`（工作流阶段） |
| | `TechRoadmapTool`（`@Tool` 暴露给 AI） |
| 接口依赖 | 注入人2的 `DevToArticleProvider`、`WebSearchProvider` |
| 边界要求 | 不写 API client，不改公共 scheduler |
| 交付 | 7/31 模板 + mock → 8/3 真实推荐 |

---

## 人5：博文审稿 + RAG

| 项 | 内容 |
|---|---|
| 主包 | `application.agent.feature.review` |
| Tool 包 | `application.tool.categories.review` |
| 基础设施 | `infrastructure.rag`、`infrastructure.persistence.review` |
| DTO 包 | `application.agent.dto.review`（共用） |
| 需实现的接口 | `StyleGuideRetriever` (`application.agent.spi.rag`) |
| 接口方法 | `check(draftText)` → `List<ReviewSuggestion>` |
| 需实现的 Skill | `DocReviewSkill` (`application.skill`) |
| 需创建的类 | `DraftReviewService`（审稿逻辑） |
| | `DocReviewPhaseExecutor`（工作流阶段） |
| | `DocReviewTool`（`@Tool` 暴露给 AI） |
| | `StyleGuideRetrieverImpl`（接口实现，放 `infrastructure.rag`） |
| | `ReviewKnowledgeEntity`（JPA 实体，放 `infrastructure.persistence.review`） |
| 可复用 | `DocumentExportService` 导出审稿报告 |
| 交付 | 8/1 mock 检索 → 8/4 真实 RAG |

---

## 人6：通用定时 + GitHub 周报（共享入口 owner）

| 项 | 内容 |
|---|---|
| 主包 | `application.agent.orchestrator`、`application.agent.feature.digest` |
| Tool 包 | `application.tool.categories.digest` |
| 调度包 | `infrastructure.scheduler.digest` |
| DTO 包 | `application.agent.dto.digest`（共用） |
| 需实现的 Skill | `DailyDigestSkill` (`application.skill`) |
| 需创建的类 | `DailyDigestComposeService`（GitHub 周报聚合 + README 摘要） |
| | `DigestCollectSchedulePhaseExecutor`（收集推送时间） |
| | `DigestComposePhaseExecutor`（订阅并生成预览） |
| | `DailyDigestTool`（`@Tool` 暴露给 AI） |
| | `TechDigestScheduler`（通用定时触发） |
| 接口依赖 | 人1的 `GitHubRepoProvider`（必需）、人2的 `DevToArticleProvider`（后续扩展） |
| 统一维护 | `ConversationAiService`、`IntentRouter`、`AgentTaskType`、`AgentPhase`、`WorkflowTemplateRegistry` |
| | `pom.xml`、`application*.yml` |
| 交付 | 8/2 已完成：通用定时解析、周/日/月/间隔订阅、GitHub 周报最小版 |

---

## Skill 总览（核心业务封装）

每个功能以 `@Component implements Skill` 实现，由 `SkillManager` 自动注册，零硬编码。

| Skill                   | id                   | 执行人 | 底层依赖                                                  |
|-------------------------|----------------------|-----|-------------------------------------------------------|
| `GithubRepoDigestSkill` | `github-repo-digest` | 人3  | 人1的 `GitHubRepoProvider`                              |
| `TechRoadmapSkill`      | `tech-roadmap`       | 人4  | 人2的 `DevToArticleProvider` + `WebSearchProvider`      |
| `DocReviewSkill`        | `doc-review`         | 人5  | 自己的 `StyleGuideRetriever` + RAG                       |
| `DailyDigestSkill`      | `daily-digest`       | 人6  | 人1的 `GitHubRepoProvider`（周报）+ 人2的 `DevToArticleProvider`（预留） |

### Skill 调用链

```
AgentLoopService → PhaseExecutor → SkillManager.findById(id) → Skill.execute()
                                                                   ↓
                                                            底层调用 MCP / API / RAG
```

## 依赖关系总图

```
人1: infrastructure.client.github
     └── 提供 GitHubRepoProvider ──────→ 人3 (Repo 速读)
                                        → 人6 (DailyDigest)

人2: infrastructure.client.devto + .search
     └── 提供 DevToArticleProvider ────→ 人4 (Roadmap)
     └── 提供 WebSearchProvider ───────→ 人4 (Roadmap)
                                        → 人6 (DailyDigest)

人3: application.agent.feature.github   ← 消费人1
     └── 不提供接口，直接输出 PhaseExecutionResult

人4: application.agent.feature.roadmap   ← 消费人2
     └── 不提供接口，直接输出 PhaseExecutionResult

人5: application.agent.feature.review + infrastructure.rag
     └── 提供 StyleGuideRetriever ─────→ 自己消费（独立模块）

人6: application.agent.orchestrator + feature.digest
     └── 消费人1~人5 能力，Mock 先行
     └── 统一维护公共入口
```

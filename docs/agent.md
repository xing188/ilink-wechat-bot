
* **MCP（Model Context Protocol，模型上下文协议）**：用来做**对外数据连接**（比如接入 GitHub API、Dev.to API 抓取数据）。
* **Skill（技能/Agent 流程）**：用来做**内部业务处理与 CoT 逻辑**（比如“生成学习路线”、“技术博文审稿”、“结构化总结”）。

---

# 🚀 TechPilot：开发者技术学习与写作 Copilot 项目方案

## 📌 一、 项目定位与核心优势

`TechPilot` 是一款基于微信生态的**开发者技术学习与写作智能助手**。系统结合了 **GitHub / Dev.to 官方 Open API**、**动态 RAG 技术文档规范库** 与 **多 Agent 协同**，帮助开发者实现“海外技术趋势感知 $\rightarrow$ 开源项目速读 $\rightarrow$ 体系化学习 $\rightarrow$ 规范化博客写作”的全流程闭环。

* **100% 合规零风险**：全量数据对接国外官方 Open API，绝不触碰任何灰产或爬虫风控；
* **极高工程可行性**：无复杂网页前端，采用微信卡片与 Markdown 交互，注重轻量与实用性。

---

## 🛠️ 二、 核心架构设计：何时用 MCP，何时用 Skill？

在架构设计上，我们将功能清晰解耦为 **外部接口连接（MCP）** 与 **内部业务处理（Skill）**：

```text
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
   - Trending 列表                       - README 提炼与架构分析
   - Repo/Issue/Release 读取           2. TechRoadmapSkill
2. DevTo-MCP                             - 体系化学习路线规划
   - 官方开放博文 API                    3. DocReviewRAGSkill
3. WebSearch-MCP                         - 基于 RAG 的博文审稿与排版
   - 海外技术文档检索                  4. DailyDigestPushSkill
                                         - 定时晨报排版与推送

```

### 1. MCP 接入规划（对外数据 Fetch）

* **`GitHub-MCP`**：利用 GitHub REST API / GraphQL API，获取仓库 Meta 信息、README 内容、Star 趋势及最近 Release。
* **`DevTo-MCP`**：利用 Dev.to API，根据 Tag 获取海外最新高质量技术博文。
* **`WebSearch-MCP`**：通过联网搜索引擎获取补全资讯与技术文档。

### 2. Skill 技能规划（内部 Prompt & 逻辑链）

* **`GithubRepoDigestSkill`**：提取仓库的关键信息，自动总结“一句话定位 + 核心技术栈 + 适用场景 + 快速启动”。
* **`TechRoadmapSkill`**：结合检索到的技术概念，输出“预备知识 $\rightarrow$ 核心原理 $\rightarrow$ 实战项目 $\rightarrow$ 进阶博文”的结构化学习路线。
* **`DocReviewRAGSkill`**：读取内置的《中文技术文档编写规范》RAG 库，对用户的博文草稿进行格式纠错与参考文献排版。

---

## 📋 三、 详细功能模块与技术点映射

| 功能模块 | 详细功能说明                                                       | 依赖技术点 / 架构 | 选用方案 |
| --- |--------------------------------------------------------------| --- | --- |
| **1. GitHub 热点与开源速读** | 自动推送 GitHub Trending，支持发送 GitHub 链接快速提炼项目架构与启动方式。            | GitHub API (GraphQL), LLM 摘要总结 | **`GitHub-MCP`** + **`GithubRepoDigestSkill`** |
| **2. 技术路线与博文推荐** | 输入想学的技术，自动抓取 Dev.to / GitHub 上的优质内容，输出体系化学习路线。               | Dev.to API, 逻辑链 (CoT) Prompt | **`DevTo-MCP`** + **`TechRoadmapSkill`** |
| **3. 博客写作辅助与 RAG 审稿** | 搜集参考文献，并依据中文技术文档规范（如中英文空格、代码块标注）对草稿进行 Code Review 式审稿。       | RAG (ChromaDB/Milvus), 向量检索, Prompt Engine | **`DocReviewRAGSkill`** (结合 RAG 向量库) |
| **4. 每日/每周技术晨报推送** | 每日推送技术新闻与 Dev.to 趋势，每周定时推送 GitHub 热门项目（和一周总结？），支持 Cron 定时任务。 | 定时任务 (Cron), 微信模板卡片渲染 | **`DailyDigestPushSkill`** + 微信网关 |

---

## 💬 四、 使用场景与真实对话举例

### 场景一：GitHub 开源项目智能速读（深度利用 GitHub 接口）

> **适用场景**：在手机上收到一个 GitHub 项目链接，想快速了解是不是自己需要的库。

* **用户**：[发送链接] `[https://github.com/langchain-ai/langgraph](https://github.com/langchain-ai/langgraph)` “*帮我看看这个库是干嘛的，核心技术栈是什么？*”
* **TechPilot（后台流转）**：
1. 调用 `GitHub-MCP` 获取该 Repo 的 `README.md`、`package.json` / `pyproject.toml` 以及近期的 Star 变化；
2. 触发 `GithubRepoDigestSkill` 进行结构化提炼。


* **TechPilot（微信回复）**：
> 📦 **GitHub 项目速读：langchain-ai/langgraph**
> 💡 **一句话定位**：基于 LangChain 的多 Agent 状态循环流编排框架（支持有向有环图构架）。
> 🛠️ **核心技术栈**：Python / TypeScript, LangChain Core, Asyncio
> 🌟 **核心解决痛点**：
> * 解决传统 DAG 无法实现 Agent 循环（Loop）与自我修正（Self-Correction）的局限；
> * 提供持久化状态恢复与 Human-in-the-Loop 人工干预机制。
>
>
> 🚀 **快速启动命令**：
> ```bash
> pip install -U langgraph
> 
> ```
>
>
> 👉 *回复 “看代码” 可进一步提炼其核心目录架构。*



---

### 场景二：新技术路线规划与海外博文推荐

> **适用场景**：想学习一个新的硬核技术（如 `eBPF`），不知道从何学起。

* **用户**： “*我想学习 eBPF 技术，帮我规划一下学习路线，顺便推荐几篇海外优质入门博文。*”
* **TechPilot（后台流转）**：
1. 调用 `DevTo-MCP` 搜索 Tag 为 `ebpf` 的 Top 赞数文章；
2. 触发 `TechRoadmapSkill` 结合概念编排学习阶段。


* **TechPilot（微信回复）**：
> 🛣️ **eBPF 技术体系化学习路线**
> **阶段一：基础预备**（Linux 内核基础、C 语言指针、 Hook 机制）
> **阶段二：核心概念**（eBPF 字节码、Verifier 校验器、BPF Maps 数据共享）
> **阶段三：实战工具链**（BCC 框架、bpftrace 脚本开发）
> 📚 **为您筛选的海外精选博文（来自 Dev.to）**：
> 1. 📖 *“eBPF for Beginners: Building Your First Kernel Probe”* (点赞: 320)
> 2. 📖 *“Why eBPF is Revolutionizing Cloud Native Observability”* (点赞: 215)
>
>
> 👉 *回复“写博客”可基于以上内容为您生成写作提纲。*



---

### 场景三：技术博文 RAG 规范化审稿

> **适用场景**：自己写好了一篇技术博客草稿，让 Agent 帮忙检测排版与规范问题。

* **用户**：[发送文本草稿] “*帮我审一下这篇关于 Redis 延迟排查的博客草稿，看看规范和格式有没有问题。*”
* **TechPilot（后台流转）**：
1. 触发 `DocReviewRAGSkill`，检索 RAG 知识库中的《中文技术文档编写指南》与 Markdown 规范；
2. 对比文本，识别不合规部分。


* **TechPilot（微信回复）**：
> 📝 **技术博文审稿报告（Code Review 模式）**
> ✅ **表现优秀**：逻辑清晰，包含“故障现象 $\rightarrow$ 命令排查 $\rightarrow$ 原因分析”的完整链路。
> ⚠️ **排版建议修改（依据《中文技术排版规范》）**：
> 1. **中英文盘古之白**：第 2 段 `Redis延迟` 建议修改为 `Redis 延迟`（中英文交界处需加空格）；
> 2. **代码块标记**：第 4 段的 `INFO commandstats` 代码块未指定语言，建议写为 ```bash；
> 3. **专业术语大小写**：文中多次出现 `redis`，应统一规范大写为 `Redis`。
>
>
> 👉 *回复“一键优化”即可为您直接替换为排版完全合规的 Markdown 文本！*



---

### 场景四：GitHub Trending 定时晨报

> **适用场景**：每天早上 8:30 微信自动收到技术晨报。

* **TechPilot（微信定时推送）**：
> ☕ **TechPilot 每日技术晨报 (2026-03-01)**
> 🔥 **今日 GitHub 飙升项目 Top 3**：
> 1. **`browser-use/browser-use`** (⭐ +1,200)
> * 简述：让 AI 像人类一样操作浏览器的开源 Web 自动化工具。
>
>
> 2. **`virattt/ai-hedge-fund`** (⭐ +850)
> * 简述：基于多 Agent 协同的 Proof-of-Concept AI 对冲基金。
>
>
>
>
> 📰 **Dev.to 本周最火热帖**：
> * 《理解 Rust 中的所有权：给 Python 开发者的直观图解》
>
>
> 📲 *点击项目名称可直接发起深度速读对话！*



---

## 📅 五、 研发迭代计划

1. **Phase 1（核心 API 与速读）**：对接 GitHub API，跑通 `GitHub-MCP` 与 `GithubRepoDigestSkill`，实现微信端发送 GitHub 链接自动提炼 README 和架构。
2. **Phase 2（RAG 审稿与学习路线）**：引入向量库构建排版规范知识库，接入 `Dev.to` API，完成路线规划与草稿 Review 功能。
3. **Phase 3（定时推送与自动化）**：搭建 Cron 定时任务，实现 GitHub Trending 晨报推送，完善微信卡片效果。

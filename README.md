# ilink-check · 微信 iLink 智能对话 Bot

> 基于 **Spring Boot + Spring AI** 的微信智能助手，把 LLM 对话、**工具调用（Function Calling）**、**RAG 检索增强**、**定时任务调度**与**多模态（语音/图像/文档）** 串成一个可扩展的 Agent 系统。
> 作为 **Agent Engineer** 方向的实战项目，重点体现「让模型自主决策调用工具、多步任务编排、稳定可测试」的工程能力。

![Java](https://img.shields.io/badge/Java-21-ED8B00?logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.13-6DB33F?logo=springboot&logoColor=white)
![Spring AI](https://img.shields.io/badge/Spring%20AI-1.1.2-6DB33F?logo=spring&logoColor=white)
![License](https://img.shields.io/badge/license-学习/展示用途-lightgrey)

---

## 📌 项目简介

ilink-check 是一个运行在微信 iLink 生态里的智能对话机器人。它不只是「调一次大模型」，而是构建了一条**可编排、可扩展、可测试**的智能体链路：从消息接入、意图理解、工具调用、知识检索到多模态回复，每一环都职责清晰、可独立演进。

## 🚀 核心能力

- **LLM 多轮对话与记忆**：基于 Spring AI `ChatClient`，支持上下文记忆（Redis / 文件两种实现）。
- **工具调用 / Function Calling**：通过统一的 `ToolRegistry` 注册天气、IP 归属地、待办提醒等工具，由模型自主决定何时调用、如何组合。
- **Agent 编排**：ReAct 风格的 `AgentService` 负责意图理解与多步任务拆解（如提醒的 `ONCE / DAILY / CRON` 三类语义解析与调度）。
- **RAG 检索增强**：内置 `application/rag` 模块，支持文档检索与知识问答。
- **定时任务调度**：`ReminderScheduler` 到点主动给用户推送消息，配合自然语言时间解析。
- **多模态**：语音音色切换（TTS）、图片生成与识别、文件 / PDF / Office 解析与总结。
- **长文本回复**：超长内容自动导出为 docx 文档返回。

## 🧱 技术栈

| 领域 | 选型 |
| --- | --- |
| 语言 / 框架 | Java 21 · Spring Boot 3.5 · Spring AI 1.1（Alibaba DashScope + OpenAI） |
| 微信接入 | wechat-ilink-sdk |
| 存储 | Redis · MySQL（JPA / MyBatis-Plus）· SQLite |
| 文档处理 | Apache PDFBox · Apache POI |
| 测试 | JUnit 5 · Spring Boot Test（`feat/roadmap` 分支 **93 个测试用例全部通过**） |

## 🏗️ 架构

采用**严格的分层架构**，详见 [ARCHITECTURE.md](ARCHITECTURE.md)：

- `bootstrap`：启动与装配（`WechatBotService`、`ReminderScheduler`）
- `adapter`：微信消息入站 / 出站、消息类型识别、附件下载
- `application`：业务编排（`conversation` / `reply` / `voice` / `image` / `reminder` / `tool` / `agent` / `rag` / `skill` / `schedule`）
- `infrastructure`：具体实现（AI、文档解析、持久化、第三方 client、调度）
- `tool`：可被模型调用的能力包，按业务域分包（如 `weather`、`iplocation`、`reminder`）

**典型消息链路：**

```
WechatBotRuntime
  → MessageDispatcher
  → *MessageProcessor（文本 / 语音 / 图片 / 文件）
  → ConversationService（含 Agent 编排与工具调用）
  → ReplyStrategyService（文本 / 音频 / docx 决策）
  → WechatMessageGateway（出站）
```

## 📂 目录结构（节选）

```
src/main/java/com/ilink/test/ilinkcheck/
├── bootstrap/        # 启动入口、调度器
├── adapter/          # 微信入站/出站适配
├── application/      # 业务编排
│   ├── agent/        # Agent 编排（ReAct）
│   ├── conversation/  # 对话编排
│   ├── tool/          # 工具注册与能力包
│   ├── rag/           # 检索增强
│   ├── reminder/      # 定时提醒
│   ├── reply/         # 回复策略
│   └── voice/image/   # 多模态
├── infrastructure/   # AI / 文档 / 持久化 / 第三方 client
├── model/            # 跨层共享模型
└── config/           # 组件配置
```

## 🛠️ 快速开始

1. **环境**：JDK 21、Maven 3.9+、Redis、MySQL（可选）。
2. **配置**：在 `src/main/resources/application-*.yml` 中填入所需的 API Key。
   > ⚠️ 所有密钥均通过**环境变量注入**（如 `TIANAPI_KEY`、`DASHSCOPE_API_KEY`），仓库内不含任何明文凭据。
3. **启动**：
   ```bash
   mvn spring-boot:run
   # 或打包后运行
   mvn package && java -jar target/ilink-check-0.0.1-SNAPSHOT.jar
   ```
4. **接入微信**：按 iLink SDK 文档完成回调 / 登录配置。

## ✅ 测试

```bash
mvn test
```

核心链路（对话、工具调用、提醒解析、文档导出）均有单元测试覆盖，`feat/roadmap` 分支 93 个用例全绿。

## 💡 面试亮点（我负责 / 深入的部分）

- 设计落地 **ReAct 风格的多步 Agent 编排**与工具调用框架（`ToolRegistry` 模式，模型与具体工具解耦）。
- 实现**自然语言定时提醒**：`ONCE / DAILY / CRON` 三类语义解析 + 到点主动推送，保障消息可靠投递。
- 构建 **RAG 检索问答**与**多模态**（语音 / 图像 / 文档）能力，统一收口在 `ReplyStrategyService`。
- **严格分层架构 + 93 个单元测试**，保证核心链路在迭代中稳定可回归。

## ⚠️ 免责声明

本项目为**个人学习 / 面试作品**，仅用于技术展示。仓库不含任何生产密钥、个人数据或运行时状态文件。

---

> 更多架构细节见 [ARCHITECTURE.md](ARCHITECTURE.md) 与 [docs/](docs/)。

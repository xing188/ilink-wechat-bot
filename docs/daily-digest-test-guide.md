# 技术晨报测试说明

本文对应当前仓库中的 `DailyDigestSkill`、`DailyDigestTool`、`DigestComposePhaseExecutor`、`DailyDigestSubscriptionService` 与 `TechDigestScheduler`。

## 1. 自动化测试

先在项目根目录执行：

```powershell
mvn -q "-Dtest=DailyDigestComposeServiceTest,DailyDigestSubscriptionServiceTest,TechDigestSchedulerTest,DailyDigestToolTest,DigestComposePhaseExecutorTest,DailyDigestSkillTest,AgentLoopServiceTest,RoadmapChainDebugTest" test
```

预期结果：

- Maven 正常退出，退出码为 `0`
- `DailyDigestComposeServiceTest` 会校验晨报预览与推送文案
- `DailyDigestSubscriptionServiceTest` 会校验订阅保存与时间格式校验
- `TechDigestSchedulerTest` 会校验 cron 注册与实际推送触发
- `DailyDigestToolTest` 会校验 tool 直调时的订阅、预览与无用户上下文分支
- `DigestComposePhaseExecutorTest` 会校验 Agent 工作流中的订阅落库
- `DailyDigestSkillTest` 会校验 skill 元数据与 Agent 委托入口

## 2. 真实运行联调

### 2.1 启动前准备

- `src/main/resources/application.yml` 里当前 `app.ilink.enabled: true`
- 当前 Web 登录页端口是 `9090`
- 推荐准备 `GITHUB_TOKEN`
  - 目的不是让晨报功能“才能跑”，而是让 GitHub Trending 社区接口异常时，回退到 GitHub Search 更稳定

### 2.2 启动命令

```powershell
mvn spring-boot:run
```

预期现象：

- 控制台输出“请用微信扫码，打开浏览器访问：http://localhost:9090/”
- 微信扫码成功后，机器人进入可收消息状态

### 2.3 手工联调步骤

#### 场景 A：正常订阅

1. 在微信里发：`帮我订阅技术晨报`
2. 预期机器人回复：

```text
你希望我每天几点推送技术晨报？请直接回复 `08:30` 这样的时间。
```

3. 再发：`每天 08:30 推送`
4. 预期机器人回复中至少包含以下片段：

```text
已为你记录技术晨报订阅时间：08:30

晨报预览（2026-07-31）：
GitHub 热点：
- ...

技术文章：
- ...
```

补充校验：

- 发 `早上九点推送技术晨报` 也应该能直接识别，最终记录为 `09:00`
- 发 `9点推送技术晨报` 时，机器人应该继续追问上午/下午，而不是直接订错时间

说明：

- 如果 GitHub Trending 社区接口拿到了“今日新增 Star”，文案会是 `今日新增 Star: N`
- 如果社区接口失败、回退到了 GitHub Search，文案会是 `当前 Star: N，官方回退`
- 这两种都算符合预期，不需要改回旧实现

#### 场景 B：验证定时推送

1. 用“当前时间后 1 分钟”的时间订阅
2. 例如如果当前本地时间是 `2026-07-31 16:25`，就发：

```text
每天 16:26 推送技术晨报
```

3. 等到 `2026-07-31 16:26`
4. 预期机器人会主动推送一条新消息，开头类似：

```text
【技术晨报】2026-07-31

GitHub 热点：
- ...

技术文章：
- ...
```

如果没有收到，优先检查：

- 是否已经扫码并保持微信机器人在线
- 控制台是否出现 `Digest pushed. userId=...`
- 是否订阅到了正确的用户 id

## 3. 预期对话样例

### 3.1 Skill / Agent 多轮样例

用户：

```text
帮我订阅技术晨报
```

助手：

```text
你希望我每天几点推送技术晨报？请直接回复 `08:30` 这样的时间。
```

用户：

```text
每天 08:30 推送
```

助手：

```text
已为你记录技术晨报订阅时间：08:30

晨报预览（2026-07-31）：
GitHub 热点：
- spring-projects/spring-ai（今日新增 Star: 320）：Spring AI project

技术文章：
- AI article：article
```

### 3.2 Tool 直调样例

适合本地代码层面验证 `DailyDigestTool.subscribeDailyDigest(...)`。

输入：

```text
subscribeDailyDigest("08:30")
```

在“有用户上下文”时，预期返回：

```text
已为你记录技术晨报订阅时间：08:30
...
```

在“没有用户上下文”时，预期返回末尾还会追加：

```text
注意：当前没有可用的用户上下文，只生成了预览，尚未创建真实定时订阅。
```

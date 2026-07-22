# 项目架构说明

这份文档是给组员看的统一口径。目标只有一个：以后大家写代码时，能快速判断“这段代码该放哪一层、哪个包”。

## 总原则

- 按职责分层，不按类名分包。
- `application` 决定“做什么”。
- `infrastructure` 决定“怎么做”。
- `adapter` 负责和外部系统对接。
- `bootstrap` 只负责启动和装配。

## 当前包职责

### `bootstrap`

- 应用启动入口。
- 只放启动器、Runner、开关控制。
- 当前例子：`WechatBotService`。

### `adapter.inbound.wechat`

- 微信消息入口。
- 负责接收 iLink SDK 消息、识别消息类型、分发到应用层。
- 当前例子：`WechatBotRuntime`、`MessageDispatcher`、`TextMessageProcessor`、`VoiceMessageProcessor`、`ImageMessageProcessor`、`FileMessageProcessor`。
- 这里不写核心业务规则。

### `adapter.outbound.wechat`

- 微信消息出口。
- 负责发文本、图片、文件、typing，以及下载消息附件。
- 当前例子：`WechatMessageGateway`。
- 这里不决定业务策略，只负责把结果发出去。

### `application.conversation`

- 对话编排层。
- 输入文本，输出 `ConversationResult`。
- 当前例子：`ConversationService`、`ConversationAiService`、`ConversationResponseParser`。

### `application.reply`

- 回复策略层。
- 决定回复文本、音频文件，还是长文档。
- 当前例子：`ReplyStrategyService`、`ReplyDraft`。

### `application.voice`

- 音色选择层。
- 负责“换音色”的识别、菜单、确认、用户偏好保存。
- 当前例子：`VoiceSelectionService`、`VoicePreferenceService`、`VoicePreferenceRepository`。

### `application.image`

- 图片生成编排层。
- 负责把“要画图”变成一次图片生成任务。
- 当前例子：`ImageGenerationTaskService`。

### `application.tool`

- 未来的 function calling 工具层。
- 这里放“模型能调用的业务能力”。
- 推荐按能力域分包，比如 `weather`、`search`、`calendar`、`voice`。
- 一个简单能力可以只放一个 tool 类。
- 复杂后再拆 `request`、`result`、`executor`、`registry`。

当前例子：

- `application.tool.ToolRegistry`
- `application.tool.weather.WeatherQueryTool`
- `application.tool.weather.WeatherQueryService`
- `application.tool.weather.WeatherReport`

### `infrastructure.ai.tts`

- 语音合成的具体实现。
- 当前例子：`TtsService`。

### `infrastructure.document`

- 文件解析、文档导出等技术实现。
- 当前例子：`FileParseService`、`DocumentExportService`。

### `infrastructure.persistence.redis`

- Redis 持久化实现。
- 当前例子：`RedisVoicePreferenceRepository`。

### `infrastructure.persistence.file`

- 本地文件型持久化实现。
- 当前例子：`FileChatMemory`。

### `infrastructure.weather`

- 天气 API 的具体 HTTP 调用实现。
- 当前例子：`UapisWeatherClient`、`WeatherClientException`。

### `model`

- 跨层共享的小模型。
- 当前例子：`VoiceOption`、`ReplyMode`、`ConversationResult`。

### `config`

- Spring 和第三方组件配置。
- 当前例子：`DashScopeTtsConfig`、`DashScopeChatConfig`。

## 典型消息链路

### 1. 文本消息

流程：

`WechatBotRuntime` -> `MessageDispatcher` -> `TextMessageProcessor` -> `ConversationService` -> `ReplyStrategyService` -> `WechatMessageGateway`

判断：

- 如果是“换音色”意图，先走 `VoiceSelectionService`。
- 如果是普通聊天，走 `ConversationService`。
- 如果结果是长文本，`ReplyStrategyService` 会转成 docx。
- 如果结果是图像任务，转给 `ImageGenerationTaskService`。

### 2. 语音消息

流程：

`WechatBotRuntime` -> `MessageDispatcher` -> `VoiceMessageProcessor` -> `VoiceSelectionService` 或 `ConversationService` -> `ReplyStrategyService` -> `WechatMessageGateway`

特点：

- 语音先看是不是在做音色切换。
- 如果不是，就把语音转写内容当成文本对话处理。
- 音频回复时，`ReplyStrategyService` 会调用 `TtsService`。

### 3. 文件消息

流程：

`WechatBotRuntime` -> `MessageDispatcher` -> `FileMessageProcessor` -> `WechatMessageGateway` 下载文件 -> `FileParseService` 解析 -> `ConversationService` -> `ReplyStrategyService`

特点：

- 文件解析属于基础设施。
- “对文件做总结”属于应用层编排。
- 回复策略仍然统一走 `ReplyStrategyService`。

### 4. 图片消息

流程：

`WechatBotRuntime` -> `MessageDispatcher` -> `ImageMessageProcessor` -> `WechatMessageGateway` 下载图片 -> `ChatClient` 识图 -> `WechatMessageGateway`

特点：

- 微信下载和发送是 adapter。
- 模型调用是 application 里的流程决策。

## 已有功能怎么拆

### 文本对话

- 入站：`adapter.inbound.wechat.TextMessageProcessor`
- 编排：`application.conversation.ConversationService`
- 回复策略：`application.reply.ReplyStrategyService`
- 出站：`adapter.outbound.wechat.WechatMessageGateway`

### 换音色

- 入口：文本或语音都可以触发。
- 识别：`application.voice.VoiceSelectionService`
- 保存偏好：`application.voice.VoicePreferenceService`
- 持久化：`infrastructure.persistence.redis.RedisVoicePreferenceRepository`
- 播放音色：`infrastructure.ai.tts.TtsService`

### 图片生成

- 识别要画图：`application.conversation.ConversationService`
- 任务执行：`application.image.ImageGenerationTaskService`
- 微信发送：`adapter.outbound.wechat.WechatMessageGateway`

### 天气查询 function calling

- 工具暴露：`application.tool.weather.WeatherQueryTool`
- 业务编排：`application.tool.weather.WeatherQueryService`
- HTTP 调用：`infrastructure.weather.UapisWeatherClient`
- 对话接入：`application.tool.ToolRegistry` -> `application.conversation.ConversationAiService`

这条链路的关键点：

- `WeatherQueryTool` 只负责声明给模型看的工具签名。
- `WeatherQueryService` 负责默认规则、错误兜底、结果格式化。
- `UapisWeatherClient` 只负责调 UAPI 天气接口。
- `ConversationAiService` 不依赖具体天气类，只依赖 `ToolRegistry`。

### 长文档回复

- 判断是否过长：`application.reply.ReplyStrategyService`
- 导出 docx：`infrastructure.document.DocumentExportService`

### 文件总结

- 文件下载：`adapter.outbound.wechat.WechatMessageGateway`
- 内容解析：`infrastructure.document.FileParseService`
- 总结编排：`application.conversation.ConversationService`

## `tool` 包怎么分

`tool` 不是“杂物包”，而是“可被模型或编排层调用的能力包”。

推荐方式：

- 按业务域分包，不按 SDK 分包。
- 一个能力域通常一个包，比如 `application.tool.weather`。
- 一个简单功能可以就是一个类，比如 `WeatherQueryTool`。
- 如果后面能力变复杂，再在同包里加 `dto`、`executor`、`registry`。

示例：

- `application.tool.weather.WeatherQueryTool`
- `application.tool.voice.VoiceSwitchTool`
- `application.tool.search.WebSearchTool`

对应原则：

- `application` 决定调用哪个 tool。
- `tool` 决定暴露什么能力。
- `infrastructure` 决定具体怎么实现。

天气这个例子里就是：

- `queryWeather(...)` 是 tool 暴露给模型的能力。
- `WeatherQueryService` 是应用层的业务规则。
- `UapisWeatherClient` 是底层的 HTTP 实现。

## 写代码时判断

- 这是“接收/发送微信消息”吗？放 `adapter`。
- 这是“业务流程怎么走”吗？放 `application`。
- 这是“调用 Redis / 文件 / TTS / 第三方 SDK”吗？放 `infrastructure`。
- 这是“模型可以调用的功能”吗？放 `application.tool`。
- 这是“启动时做的事”吗？放 `bootstrap`。

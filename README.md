# ilink-check

这是一个微信 iLink Bot 项目。

先看这份：

- [项目架构说明](ARCHITECTURE.md)

## 你该怎么理解它

- `bootstrap` 只负责启动。
- `adapter` 负责和微信 SDK 交互。
- `application` 负责业务编排。
- `infrastructure` 负责具体技术实现。
- `tool` 负责未来的 function calling 能力。


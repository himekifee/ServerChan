# ServerChan

[![Server Tests](https://github.com/himekifee/ServerChan/actions/workflows/server-test.yml/badge.svg)](https://github.com/himekifee/ServerChan/actions/workflows/server-test.yml)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)
[![Modrinth](https://img.shields.io/modrinth/dt/frZpQL6O?logo=modrinth&label=Modrinth)](https://modrinth.com/mod/serverchan)
[![CurseForge](https://img.shields.io/curseforge/dw/1393571?logo=curseforge&label=CurseForge)](https://www.curseforge.com/minecraft/mc-mods/serverchan)
[![Minecraft](https://img.shields.io/badge/Minecraft-1.12--1.21-green.svg)](https://minecraft.net)

<p align="center">
  <img src="https://raw.githubusercontent.com/himekifee/ServerChan/main/common/src/main/resources/assets/serverchan/icon.png" alt="ServerChan 模组图标" width="200" />
</p>

> 欢迎来到 ServerChan！这是一款把现代 AI 助手带进 Minecraft 服务器的 Mod，能听懂聊天、回应事件，还能帮你执行命令。如果你想看英文版文档，可以前往 [README.md](README.md)。

## 主要特性

- **聊天机器人** - 使用兼容 OpenAI 的接口（OpenAI、Azure、本地 LLM 等）驱动对话
- **意图判断** - 可选的意图检查器帮助过滤无意义回复，避免刷屏
- **事件联动** - 关注玩家上下线、死亡等游戏事件并做出反应
- **命令执行** - 通过函数调用安全地执行服务器指令
- **多端支持** - Fabric、Forge、NeoForge、Spigot/Paper 一网打尽
- **版本覆盖广** - 支持 Minecraft 1.12 - 1.21
- **高度可配置** - 提供完整的提示词、模型、行为设置
- **多语言** - 内置中/英/日的消息翻译

## 兼容矩阵

| Minecraft 版本 | Java | Fabric | Forge | NeoForge | Spigot/Paper |
|----------------|------|--------|-------|----------|--------------|
| 1.12.x         | 8    | —      | ❌    | —        | ✅           |
| 1.13.x         | 8    | —      | ❌    | —        | ✅           |
| 1.14.x         | 8    | ✅     | ❌    | —        | ✅           |
| 1.15.x         | 8    | ✅     | ❌    | —        | ✅           |
| 1.16.x         | 8    | ✅     | ✅    | —        | ✅           |
| 1.17.x         | 16   | ✅     | ✅    | —        | ✅           |
| 1.18.x         | 17   | ✅     | ✅    | —        | ✅           |
| 1.19.x         | 17   | ✅     | ✅    | —        | ✅           |
| 1.20.x         | 21   | ✅     | ✅    | ✅       | ✅           |
| 1.21.x         | 21   | ✅     | ✅    | ✅       | ✅           |

`—` 表示对应加载器在该版本尚不存在（Fabric 从 1.14 起支持，NeoForge 从 1.20 起）。

## 安装指南

1. 从 [Modrinth](https://modrinth.com/mod/serverchan) 或 [CurseForge](https://www.curseforge.com/minecraft/mc-mods/serverchan) 下载与你服务器加载器匹配的 jar
2. 将 jar 放进 `mods/`（或 Spigot 的 `plugins/`）目录
3. 启动服务器生成配置文件
4. 填好 API Key 与想要的设置（见下方配置章节）
5. 重启或重载服务器

## 配置

配置文件位置：
- **Fabric/Forge/NeoForge**: `config/serverchan.yaml`
- **Spigot**: `plugins/ServerChan/config.yml`

### 必填项

| 选项 | 说明 |
|------|------|
| `openaiApiKey` | 你的 OpenAI 或其他兼容服务的 API Key |
| `openaiBaseUrl` | API 地址，默认为 `https://api.openai.com` |

### 常用可选项

| 选项 | 默认值 | 说明 |
|------|--------|------|
| `model` | `gpt-5.1` | 用于回复的模型 |
| `temperature` | `1.0` | 回答随机度 |
| `contextSize` | `20` | 聊天记忆长度 |
| `botColor` | `b` | 在聊天里的颜色代码 |
| `timeZone` | `UTC` | 用于时间戳的时区 |
| `locale` | `zh_cn` | Bot 的语言 |

### 意图检查器

| 选项 | 默认值 | 说明 |
|------|--------|------|
| `useIntentionChecker` | `true` | 启用智能过滤 |
| `intentionCheckerModel` | `qwen3-235b-a22b-2507` | 判断用模型 |
| `responseProbabilityThreshold` | `0.5` | 触发回复的最小概率 |
| `useFastPathIntentionChecker` | `false` | 允许提前开始生成回复 |
| `intentionCheckerApiKey` | (空) | 如果和主 key 不同可以单独设置 |
| `intentionCheckerBaseUrl` | (空) | 同上 |

### 事件设置

| 选项 | 默认值 | 说明 |
|------|--------|------|
| `enableGameEvents` | `true` | 关注游戏事件 |
| `enableJoinLeaveEvents` | `true` | 玩家上下线提醒 |
| `enableDeathEvents` | `true` | 玩家死亡提醒 |

### 权限设置

| 选项 | 默认值 | 说明 |
|------|--------|------|
| `inheritCmdSourcePermission` | `true` | AI 继承触发玩家的权限执行命令 |

### 自定义提示词

你可以通过 `intentionCheckingSystemMessage` 和 `responseGenerationSystemMessage` 完全定义 Bot 的性格与触发逻辑。`example/` 目录里放了 OnlyMyRedstone 等服务器的示例，复制后微调就能直接使用。

## 指令

所有指令需要管理员（op level 4）权限。

| 指令 | 说明 |
|------|------|
| `/serverchan reload` | 重载配置 |
| `/serverchan reset` | 清空会话上下文 |
| `/serverchan kill` | 重置 OpenAI 客户端连接 |
| `/serverchan disable` | 暂停 ServerChan 响应（不再处理消息） |
| `/serverchan enable` | 恢复 ServerChan 响应 |

## 工作流程

1. 玩家发送一条消息
2. （可选）意图检查器判断是否需要回复
3. 需要回复时主模型生成答案
4. 如有必要，AI 会通过函数调用执行命令
5. 结果广播给所有玩家

整个流程会保留最近 `contextSize` 条消息，方便延续对话。

## 环境需求

- Minecraft Server 1.12 - 1.21（详见兼容表）
- Fabric / Forge / NeoForge / Spigot-Paper 之一
- OpenAI 或其他兼容 API Key（例如 Azure、Ollama、Cerebras 等）

## 源码构建

```bash
git clone https://github.com/himekifee/ServerChan.git
cd ServerChan

# 构建指定版本
./gradlew build -PmcVer=1.21

# 构建聚合包（含所有加载器）
./gradlew build mergeJars -PmcVer=1.21
```

构建产物默认在 `build/libs/`（或 Forgix 的 `build/forgix/`）。

### 开发测试

`dev-test.sh` 可以配合 Docker 启动实际服务器做集成测试：

```bash
./dev-test.sh 1.21           # 构建 + 所有平台测试
./dev-test.sh --build-only 1.21
./dev-test.sh --fabric 1.21  # 仅测试指定平台
```

## 贡献方式

欢迎 PR 和 Issue！流程如下：

1. Fork 仓库
2. 新建分支 `git checkout -b feature/my-feature`
3. 提交改动 `git commit -m "Add my feature"`
4. 推送并发起 PR

## 授权协议

本项目使用 GNU GPL v3 - 详情见 [LICENSE](LICENSE)。

## 特别感谢

- [Universal Mod Template](https://github.com/thebuildcraft/Universal-Mod-Template)
- [ConfigLib](https://github.com/tomwmth/ConfigLib)
- [Architectury Loom](https://github.com/architectury/architectury-loom)、[Forgix](https://github.com/PacifistMC/Forgix)、[Manifold](https://github.com/manifold-systems/manifold)

## Cerebras

虽然没有赞助，但 CI 测试和自用服务器都会用到 [Cerebras](https://cerebras.ai/) 的推理 API：意图检查基本 1 秒内完成，完整回复也只需 2-5 秒，非常适合实时聊天的 ServerChan。如果你在 Cerebras 工作并愿意提供支持，欢迎 [开个 Issue](https://github.com/himekifee/ServerChan/issues/new) 聊聊 😊

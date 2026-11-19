# TinyChat 开发计划与交互设计文档

> 版本：v1.0 规划稿 · 2026-11-17
> 前置文档：[01-feasibility-and-plan.md](./01-feasibility-and-plan.md)（调研与可行性）
> 状态：**等待开工许可**，本文档只做规划，不含任何代码落地
> 阅读顺序：决策基线(§1) → 技术基线(§2) → 数据/网络/业务(§3–§5) → WBS(§6) → 交互设计(第二部分)

**目录**

- 第一部分 开发计划
  - [1. 版本范围与决策基线](#1-版本范围与决策基线)
  - [2. 技术基线](#2-技术基线)
  - [3. 数据层设计](#3-数据层设计)
  - [4. 网络层设计](#4-网络层设计)
  - [5. 业务逻辑设计](#5-业务逻辑设计)
  - [6. 任务拆解（WBS）](#6-任务拆解wbs)
  - [7. 测试计划](#7-测试计划)
  - [8. 风险登记册](#8-风险登记册)
  - [9. 排期](#9-排期)
- 第二部分 交互设计
  - [10. 设计原则与令牌](#10-设计原则与令牌)
  - [11. 信息架构与导航](#11-信息架构与导航)
  - [12. 页面规格](#12-页面规格)
  - [13. 消息组件族](#13-消息组件族)
  - [14. 状态矩阵](#14-状态矩阵)
  - [15. 文案表](#15-文案表)
  - [16. 动效与手势](#16-动效与手势)
  - [17. 无障碍与本地化](#17-无障碍与本地化)
  - [18. 交互验收清单](#18-交互验收清单)

---

# 第一部分 开发计划

## 1. 版本范围与决策基线

### 1.1 v1.0 包含

| 类别 | 内容 |
|---|---|
| 会话 | 多会话、自动标题、重命名、置顶、删除、编辑重发、重新生成、继续生成、消息分支切换 |
| 对话能力 | 流式输出、思维链折叠、服务端联网搜索、阅读链接、页内查找、引用角标、来源聚合、结构化错误与拒答 |
| 渲染 | Markdown（GFM 表格/任务列表/删除线）、代码高亮、行内与块级 LaTeX、Mermaid、图片 |
| 输入 | 文本、图片、文件（PDF/Word/PPT/纯文本类）、语音输入、停止生成 |
| 输出 | TTS 朗读（2 个官方音色）、分享为图片 |
| 数据 | v3 格式导入导出（兼容模式）、清空、附件本地存储 |
| 设置 | 服务商与 API Key（DeepSeek 官方 + 通用 OpenAI 兼容）、模型与思考强度、系统提示词（5 预设）、语言、外观、字号、朗读音色、用户元信息开关、用量显示开关 |
| 用量 | 上下文圆环、每轮 token/费用、账户余额（仅 DeepSeek） |

### 1.2 v1.0 明确**不含**（避免范围蔓延）

| 不做 | 原因 |
|---|---|
| 上下文压缩 / 摘要 | outline 明确要求保 Chat 一致性 |
| 客户端侧搜索工具循环 | 决策 5；非 DeepSeek 服务商置灰 |
| `temperature` / `top_p` 等采样参数 | 决策 11；扩展模式恒忽略 |
| 系统 TTS、第三方 TTS | 决策 3 |
| 内嵌 tokenizer | 决策 4 |
| 原生 JSON Output | 推迟 v1.1 |
| 提示词会话内编辑 | 决策 8 |
| OpenCode Go 服务商 | 推迟 v1.1 |
| 会话全文搜索、多设备同步、导图、插件 | 非核心 |

### 1.3 决策基线（速查，详见 01 §8.1）

`TinyChat+自绘图标` / `DeepSeek+OpenAI兼容` / `TTS仅2官方音色` / `启发式token估算` / `不做客户端搜索` / `GitHub Releases` / `导出兼容模式` / `提示词仅设置入口` / `5个预设` / `会话快照只读` / `不暴露temperature` / `角色扮演中性骨架` / `默认只读` / `附件用回形针` / `图标方案暂缓` / `无启动页` / `首页图标可省`

### 1.4 里程碑总览

| 里程碑 | 目标 | 出口标准 |
|---|---|---|
| **M0** | 工程骨架 + 设计系统 | 空壳 App 双主题可跑、冷启动直达主界面、CI 绿 |
| **M1** | 聊天内核 | 用 DeepSeek Key 连续对话、思维链、停止、重发生效 |
| **M2** | 渲染 | 与截图肉眼难辨 |
| **M3** | 联网 | 搜索/打开/查找步骤卡片 + 来源面板完整 |
| **M4** | 附件 | 传图问答、传文档问答 |
| **M5** | 语音 | 语音输入 + 朗读 |
| **M6** | 数据管理 | 3340 会话包 100% 往返 |
| **M7** | 用量与费用 | 圆环/每轮统计/余额/元信息 |
| **M7.5** | 提示词 | 5 预设 + 快照 |
| **M8** | 打磨 | 分享出图、i18n、OpenAI 兼容 provider 完整 |
| **M9** | 发布 | 签名 APK + 性能专项 |

---

## 2. 技术基线

### 2.1 构建环境（本机已核对）

| 项 | 值 | 备注 |
|---|---|---|
| JDK | 21.0.4（Dragonwell） | `JAVA_HOME` 已指向 |
| Gradle | **用 wrapper**，不依赖系统 8.4 | 建议 wrapper 8.9+（JDK21 友好） |
| Android SDK | `%LOCALAPPDATA%\Android\Sdk` | platforms 34 / 36；build-tools 到 36.1.0 |
| `local.properties` | `sdk.dir=...` | 必须本地生成，**不进 git** |
| compileSdk / targetSdk | 36 | 直发不依赖 Play 要求，但保持 36 |
| minSdk | 26 | Android 8.0；图片走 Photo Picker（API 33+ 原生，以下由 Play Services 回填），文件走 SAF |
| `applicationId` | `io.github.<user>.tinychat` | 与品牌一致，不含 deepseek 字样 |

### 2.2 依赖清单（版本在 M0 用 version catalog 锁定）

> **版本已由 Spike 实测钉死（2026-11-17，见 04 号报告 §5）**：
> Gradle **9.9.1** · AGP **9.5.1** · Kotlin **2.4.21** · **compileSdk 37** · Compose BOM **2026.09.00**（Compose 1.12.1）。
> 三条硬规定：① AGP 9 起**不可**再应用 `org.jetbrains.kotlin.android`（内置 Kotlin 支持）；
> ② 所有插件版本集中在 `settings.gradle.kts` 的 `pluginManagement.plugins {}`，根工程 `apply false` 统一解析；
> ③ Compose 1.12.1 **强制要求 compileSdk ≥ 37**，M0-1 必须用 `sdkmanager` 正规装 `platforms;android-37`。

> **M1-0 探针（2026-11-17 实测通过）**：`core:data` 接入 KSP **2.3.14** + Room **2.8.5**，AGP 9.5.1 内置 Kotlin + Kotlin 2.4.21 下首次即生成 `ProbeDatabase_Impl.kt`，无版本冲突。KSP 已与 Kotlin 版本解耦（2.3.x 为独立版本号，不再有 `<kotlin>-<ksp>` 双段格式）。

| 用途 | 坐标 | 选型理由 |
|---|---|---|
| UI | `androidx.compose:compose-bom` + material3 | 统一管理 Compose 版本 |
| 导航 | `androidx.navigation:navigation-compose` | 单 Activity + 类型安全路由 |
| 状态 | `androidx.lifecycle:lifecycle-viewmodel-compose` | 与 Compose 原生配合 |
| DI | **手写 `AppContainer`**（不引 Hilt，见决策 21） | M1 只有 3 个仓库，手写成本 0.2d 且无编译期插桩风险 |
| 序列化 | `org.jetbrains.kotlinx:kotlinx-serialization-json` | 请求体/碎片 JSON/预设文件 |
| 网络 | `com.squareup.okhttp3:okhttp` | SSE 手写解析（见 §4.4） |
| 数据库 | `androidx.room` **2.8.5**（`room-runtime/ktx/compiler` + KSP **2.3.14**） | 关系型 + 事务，适合消息树 |
| 偏好 | `androidx.datastore:datastore-preferences` **1.3.0** | 设置项；密钥另行加密 |
| 图片 | `io.coil-kt.coil3:coil-compose` | 附件缩略图/图片消息 |
| Markdown | `com.mikepenz:multiplatform-markdown-renderer:0.46.0` + `-m3` | **只用 `rememberStreamingMarkdownState`（实测 0 次高度回缩）** |
| 数学 | **WebView + 离线 KaTeX**（`assets/`） | 纯 Compose LaTeX 库依赖链断裂，已排除（04 §4） |
| 图表 | **同一个 WebView 宿主 + 离线 `mermaid.min.js`** | 公式与图表共用宿主，避免两套引擎 |
| 音频 | `androidx.media3:media3-exoplayer` | TTS 播放、audio focus |
| 测试 | junit + `kotlinx-coroutines-test` + `turbine` + `mockk` | 纯逻辑单测为主 |
| 静态检查 | `detekt` + `androidx.lint` | CI 门禁 |

> 版本策略：M0 建 `gradle/libs.versions.toml`，全部锁定；升级统一在里程碑边界做，不在功能开发中途升。

### 2.3 模块划分与依赖方向

```
app ──► feature:* ──► core:data ──► core:net ──► core:model
 │            │            │            │            ▲
 │            └──► core:ui ─┴────────────┴────────────┘
 └──► core:designsystem
```

| 模块 | 职责 | 禁止 |
|---|---|---|
| `core:model` | 纯 Kotlin：`Session`/`Message`/`Fragment`/`Usage`/`Provider`/`Capabilities` | 依赖 Android/Compose |
| `core:net` | OkHttp、SSE 解析、协议适配器、错误映射 | 依赖 Room/UI |
| `core:data` | Room、DataStore、附件仓库、ZIP 导入导出 | 依赖 Compose |
| `core:ui` | 通用 Compose 组件（按钮/菜单/弹窗/Toast/抽屉） | 含业务状态 |
| `core:designsystem` | 色板/字体/形状/图标 | 含业务 |
| `core:markdown` | Markdown/LaTeX/Mermaid 渲染与流式节流 | 含业务 |
| `feature:chat` | 会话页 | 直连 OkHttp |
| `feature:sessions` | 会话列表抽屉 + 会话管理 | — |
| `feature:settings` | 设置树 + 服务商 + 提示词 | — |
| `feature:data` | 导入导出 UI 与流程 | — |

---

## 3. 数据层设计

### 3.1 Room Schema

```sql
-- 会话
CREATE TABLE sessions (
  id            TEXT PRIMARY KEY NOT NULL,       -- UUID
  title         TEXT,                            -- null = 未命名（显示"新对话"）
  pinned        INTEGER NOT NULL DEFAULT 0,
  current_message_id INTEGER,                    -- 当前停留的消息（分支唯一依据）
  created_at    INTEGER NOT NULL,                -- epoch millis
  updated_at    INTEGER NOT NULL,
  provider_id   TEXT NOT NULL,                   -- 创建时的服务商快照
  model         TEXT NOT NULL,                   -- 创建时的模型快照
  thinking_enabled INTEGER NOT NULL DEFAULT 0,
  search_enabled   INTEGER NOT NULL DEFAULT 0,
  effort        TEXT,                            -- low/high/max，null 表示不支持
  system_prompt_snapshot TEXT,                   -- 提示词快照；null = 不传 system
  system_prompt_mode TEXT,                       -- replace/append/clear
  deleted       INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX idx_sessions_updated ON sessions(deleted, pinned DESC, updated_at DESC);

-- 消息（树）
CREATE TABLE messages (
  id            INTEGER PRIMARY KEY AUTOINCREMENT,  -- 会话内自增（与导出 v3 的 id 语义一致）
  session_id    TEXT NOT NULL,
  parent_id     INTEGER,                            -- null = 首条
  role          TEXT NOT NULL,                      -- USER / ASSISTANT
  status        TEXT NOT NULL,                      -- WIP/FINISHED/INCOMPLETE/FAILED/TIMEOUT/CONTENT_FILTER/CONTEXT_LENGTH_EXCEEDED
  thinking_enabled INTEGER NOT NULL DEFAULT 0,
  search_enabled   INTEGER NOT NULL DEFAULT 0,
  accumulated_token_usage INTEGER NOT NULL DEFAULT 0,
  inserted_at   INTEGER NOT NULL,
  -- 仅本地（不导出）
  model         TEXT,
  effort        TEXT,
  prompt_tokens INTEGER, cache_hit_tokens INTEGER, completion_tokens INTEGER,
  thinking_tokens INTEGER, search_calls INTEGER, fetch_calls INTEGER,
  cost_micros   INTEGER,                            -- 人民币微元，避免浮点
  error_kind    TEXT, error_message TEXT,
  FOREIGN KEY(session_id) REFERENCES sessions(id) ON DELETE CASCADE
);
CREATE INDEX idx_messages_session ON messages(session_id, inserted_at);

-- 碎片（与导出 v3 同构）
CREATE TABLE fragments (
  id         INTEGER PRIMARY KEY AUTOINCREMENT,
  message_id INTEGER NOT NULL,
  ord        INTEGER NOT NULL,
  type       TEXT NOT NULL,     -- REQUEST/RESPONSE/THINK/TOOL_SEARCH/TOOL_OPEN/TOOL_FIND/READ_LINK/FILE/TEMPLATE_RESPONSE/TOOL_IMAGE_SEARCH
  payload    TEXT NOT NULL,     -- kotlinx.serialization JSON
  FOREIGN KEY(message_id) REFERENCES messages(id) ON DELETE CASCADE
);
CREATE UNIQUE INDEX idx_fragments_ord ON fragments(message_id, ord);

-- 附件
CREATE TABLE attachments (
  id          TEXT NOT NULL,               -- 归档里的 file id
  message_id  INTEGER NOT NULL,
  file_name   TEXT NOT NULL,
  file_size   INTEGER NOT NULL,
  mime        TEXT,
  local_path  TEXT,                        -- 原件的本地路径；null = 只有元数据
  remote_id   TEXT,                        -- Files API 的 file_id；null = 未上传
  is_image    INTEGER NOT NULL,
  width INTEGER, height INTEGER,
  state       TEXT NOT NULL,               -- LOCAL / UPLOADING / UPLOADED / FAILED / MISSING
  PRIMARY KEY(id, message_id),             -- 同一附件会被同一会话的多条提问引用（真样本 20/124）
  FOREIGN KEY(message_id) REFERENCES messages(id) ON DELETE CASCADE
);
CREATE INDEX idx_attachments_msg ON attachments(message_id);

-- 提示词预设
CREATE TABLE prompt_presets (
  id         TEXT PRIMARY KEY NOT NULL,
  name       TEXT NOT NULL,
  content    TEXT NOT NULL,
  mode       TEXT NOT NULL,          -- replace/append/clear
  builtin    INTEGER NOT NULL,       -- 内置不可删改
  sort       INTEGER NOT NULL,
  updated_at INTEGER NOT NULL
);
```

> 为什么碎片单独建表：`TOOL_SEARCH` 要排序、要按 `stage_id` 分组、将来要检索；单列 JSON 会让这些都变难。代价是导入时多一次插入，可接受（分批 + 事务）。

### 3.2 关键列语义

| 列 | 语义 | 陷阱 |
|---|---|---|
| `current_message_id` | 当前分支的叶子；渲染只走这条链 | 删除分支后必须重算 |
| `messages.parent_id` | 同一 user 消息下的多个 assistant = 重新生成产生的兄弟 | 编辑提问 = 新建 user 兄弟，并把它的第一个 assistant 设为 current |
| `fragments.type` | 与 v3 导出完全一致的枚举 | 新增类型必须同步导出映射，否则往返丢数据 |
| `messages.id` | 全局自增（决策 22） | 不要按会话重算 `MAX(id)+1`；导入时按包内 id 重映射 |
| `system_prompt_snapshot` | 创建时固化 | **只读**；全局设置改动不得回写 |
| `cost_micros` | 见 §4.7 | 不用 Double，避免累加误差 |

### 3.3 设置存储（DataStore）

| Key | 类型 | 默认 |
|---|---|---|
| `locale` | `zh-CN` / `en-US` | 跟随系统 |
| `theme` | light/dark/system | system |
| `font_scale` | 0.9/1.0/1.15/1.3 | 1.0 |
| `active_provider_id` | String | 首个 DeepSeek |
| `tts_voice` | stella/tide | stella |
| `show_usage` | Bool（每轮统计开关） | true |
| `show_balance` | Bool | true |
| `user_meta_enabled` | Bool（latest_reminder） | true |
| `location` | 结构化（region[/province/city]） | 空 |
| `thinking_default` / `search_default` | Bool | false / false |
| `effort_default` | low/high/max | high |
| `model_default` | `deepseek-flash` | flash |
| `active_prompt_preset_id` | String | builtin.default |

**密钥**：不入 DataStore 明文。用 Android Keystore 生成 AES-256-GCM 主密钥，加密后以 `base64(iv||cipher||tag)` 存 DataStore（每个 provider 一把）。**不使用已 deprecated 的 `security-crypto`。**

### 3.4 附件目录与配额

```
filesDir/
  attachments/{messageId}/{fileId}_{safeName}    # 原件
  thumbs/{fileId}.webp                            # 图片缩略图（max 512px）
  exports/tmp-{uuid}.zip                          # 导出临时文件（结束后删除）
```

- 保存前用 `ContentResolver.openInputStream` 流式拷贝，**禁止** `readAllBytes`；
- 图片解码用 `BitmapFactory` + `inSampleSize`，缩略图存盘；
- 导入包内图片按 `fileId` 去重（同一图出现在多个会话目录只存一份）。

---

## 4. 网络层设计

### 4.1 分层

```
ChatRepository            （业务层：组装请求、落库、状态流转）
   └─ ChatProtocol        （协议层：encode / stream / capabilities）
        ├─ DeepSeekMessagesProtocol   （主链路）
        └─ OpenAICompatibleProtocol   （降级链路）
   └─ OkHttp + SseReader  （传输层）
```

```kotlin
interface ChatProtocol {
  val capabilities: Capabilities
  fun buildRequest(cmd: ChatCommand): HttpRequest      // URL/Header/Body
  fun parseEvents(raw: Flow<SseFrame>): Flow<WireEvent>
}

sealed interface WireEvent {
  data class BlockStart(val index: Int, val block: BlockContent) : WireEvent
  data class BlockDelta(val index: Int, val delta: Delta) : WireEvent
  data class BlockStop(val index: Int) : WireEvent
  data class MessageDelta(val stopReason: StopReason, val usage: Usage?, val title: String?) : WireEvent
  data object MessageStop : WireEvent
  data class Failed(val kind: ErrorKind, val httpStatus: Int?, val message: String) : WireEvent
}
```

### 4.2 DeepSeek Messages 适配（主链路）

| 请求字段 | v1 取值 | 说明 |
|---|---|---|
| `model` | 会话快照 | `deepseek-flash` / `deepseek-v4-pro` |
| `system` | 会话快照（可缺省） | 见 §5.6 |
| `messages` | 由碎片树重建（§5.2） | 含 `latest_reminder` 伪消息 |
| `thinking` | `{type: enabled/disabled}` | 会话开关 |
| `output_config` | `{effort: low/high/max}` | 仅 DeepSeek；仅 thinking=enabled 有意义 |
| `tools` | 恒含 `deepseek_read_url`；开关控制 `deepseek_web_search` | 与 chat-demo 一致 |
| `safety_check` | 恒 `{level:"high"}` | 合规必需 |
| `title` | 仅会话首次请求 | 拿到标题后不再开 |
| `stream` | `true` | 恒流式 |
| Header | `x-api-key`；有文件块时补 `anthropic-beta: files-api-2025-04-14` | 见 01 §2.2 |

**事件 → 碎片映射**（`server_tool_use` 与结果块需按 `tool_use_id` 配对；结果可能乱序到达）：

| 服务端块 | 客户端碎片 | 备注 |
|---|---|---|
| `thinking` + `thinking_delta` + `signature_delta` | `THINK` | signature 仅本轮续写需要 |
| `text` + `text_delta` | `RESPONSE` | 同一 `text` 块内追加 |
| `text.citations_delta` | `RESPONSE.citations[]` | 引用角标 |
| `server_tool_use(action=search)` → `web_search_tool_result` | `TOOL_SEARCH` | queries 取自调用块；`status` 由结果块决定；`stage_id` 取当前推理轮次 |
| `server_tool_use(action=open_page)` → `web_fetch_tool_result(kind=deep_search)` | `TOOL_OPEN` | 只有 url/title/published_at，**无正文** |
| `server_tool_use(action=find_in_page)` → `web_fetch_tool_result` | `TOOL_FIND` | 只有 pattern + 状态 |
| `server_tool_use(action=read_link)` → `web_fetch_tool_result(kind=read_link)` | `READ_LINK` | **必须原样保留 `signature`**，否则多轮丢网页上下文 |
| `message_delta.title` | 会话标题 | 仅当会话无标题且本轮请求开启了 title |
| `message_delta.usage` | 用量 | 以最后一个 `message_delta` 为准，**不要与 message_start 累加** |
| `message_delta.stop_reason=refusal` | 丢弃已下发的 thinking/text，只保留工具块 | 协议要求 |

### 4.3 OpenAI 兼容适配（降级链路）

| 项 | 映射 |
|---|---|
| 端点 | `{baseUrl}/chat/completions`（baseUrl 由用户填，默认 `https://api.openai.com/v1`） |
| 鉴权 | `Authorization: Bearer` |
| 流式 | `stream:true` + `stream_options:{include_usage:true}` |
| `reasoning_content` / `reasoning` | → `THINK`（无 signature） |
| `delta.content` | → `RESPONSE` |
| `finish_reason` | `stop`→FINISHED、`length`→INCOMPLETE、`content_filter`→CONTENT_FILTER |
| 标题 | **本地生成**：取首条用户消息前 24 字符（无 `title` 扩展） |
| 联网/文件/余额/TTS/ASR | **不支持**，UI 置灰 |

与模型无关的降级已在 `Capabilities` 中表达，UI 只读能力位，不做服务商特判。

### 4.4 SSE 解析器（**手写，不引库**）

必须处理的四种边界（chat-demo 已验证，照抄逻辑）：

1. `: keep-alive` 注释行 → 忽略，仅重置读超时；
2. **具名事件**（`event: message_delta`）→ `event` 名与 JSON `type` 一致，以 JSON `type` 为准；
3. **无名 data 错误帧**（只有 `data: {"error":{...}}`，无 `event`）→ 抛 `ApiError(kind=stream)`；
4. **没有 `[DONE]`**：以 `message_stop` 结束；若在 `message_stop` 前流结束 → `kind=interrupted`（**但若已收到 `message_delta.stop_reason`，按完成处理**）。

实现要点：

- 逐行读取，空行 = 帧结束；字段按 `event`/`data`/`id`/`retry` 累积；
- `data` 允许多行，按 `\n` 拼接；
- UTF-8 手动解码，防止多字节字符被 chunk 边界切断。

超时策略：连接 15s；**读超时 120s**（深度思考搜索可能长时间无输出，靠 `: keep-alive` 保活；服务端 10 分钟无推理才断连接）。

### 4.5 错误分类与重试

| 分类 | 触发 | UI | 重试 |
|---|---|---|---|
| `network` | 连接失败/DNS/超时 | "网络异常，请检查网络后重试" | 手动重试 |
| `http.401` | Key 无效 | "API Key 无效或已删除" + 跳设置 | 不自动 |
| `http.402` | 余额不足 | "账户余额不足，请充值" | 不自动 |
| `http.422` | 请求体不合法（如 latest_reminder 格式） | "请求参数有误" + 上报 | 不自动 |
| `http.429` | 并发/限流 | "请求过于频繁" | 读 `Retry-After`，**自动重试 1 次** |
| `http.5xx` | 服务端故障 | "服务器繁忙" | 指数退避**最多 2 次**（仅未收到内容时） |
| `stream` | 流中 error 帧 | 展示服务端 message | 手动 |
| `interrupted` | 流提前断开且无 stop_reason | "生成被中断" | 手动（可继续生成） |
| `refusal` | `stop_reason=refusal` | 替换为拒答文案，丢弃已下发正文 | 不可重试 |
| `upload` | 附件上传失败 | "附件上传失败" + 重试 | 手动 |
| `fileInvalid` | 服务端判 `file_id` 失效 | 自动重传后重发 | **自动最多 2 次**（退避 1s/2s） |

**铁律**：一旦收到过 `content_block_start`，**不再自动重试**（避免重复计费与内容错乱）。

### 4.6 取消与生命周期

- 每个会话持有 `Call` + `Job`；停止 = `cancel()`，状态落 `INCOMPLETE`，保留已生成内容；
- 页面离开 → 不停流（继续写入 DB）；v1 只在**前台**流式；
- 进程死亡 → `WIP` 消息在启动时归为 `INCOMPLETE`（显示"已停止"）。

### 4.7 用量与费用

```kotlin
data class Usage(
  val promptTokens: Int,        // input_tokens（未命中）
  val cacheHitTokens: Int,      // cache_read_input_tokens
  val completionTokens: Int,    // output_tokens
  val thinkingTokens: Int,      // output_tokens_details.thinking_tokens
  val safetyTokens: Int,
  val searchCalls: Int,         // server_tool_use.tool_search
  val fetchCalls: Int,
  val imageSearchCalls: Int
) {
  val totalInput get() = promptTokens + cacheHitTokens
  val contextUsed get() = totalInput + completionTokens
}

// 单位：人民币微元（1e-6 元），整数累加
fun costMicros(u: Usage, model: String, isPeak: Boolean): Long
```

**价格表**（元/百万 token，`idle` / `peak`）内置为可远程覆盖的 JSON；**峰值判定**：北京时间周一至周五 09:00–12:00、14:00–18:00（法定节假日近似忽略，UI 标注"按工作日时段估算"）。

工具费：`搜索 0.005 元/次`、`抓取 0.005 元/次` → `searchCalls * 5000` 微元。

**上下文圆环分母**：`Capabilities.maxContext`（DeepSeek 两模型 1,000,000）；分子 = 最近一次真实 `contextUsed` + 增量估算。

**增量估算**（决策 4）：

```
estimate(text) = 中文字符数 * 0.6 + 其他字符数 * 0.3
预估 = lastRealPromptTokens + estimate(新增 user 文本 + 附件文本估算)
```

UI 上带 `预估` 角标；一旦服务端返回真实值即覆盖。

---

## 5. 业务逻辑设计

### 5.1 消息树状态机

```
         ┌──────────── 编辑提问 ────────────┐
         ▼                                  │
  USER(a) ──► ASSISTANT(a1) ──► USER(b) ──► ASSISTANT(b1)   ← 当前分支
     │              ▲
     │              └── 重新生成（新建 a2，与原 a1 并列）
     └── 删除分支（current 回退到父）
```

| 操作 | 结果 |
|---|---|
| 发送 | 新 USER(parent = current) + 新 ASSISTANT(parent = 该 USER) |
| 编辑提问 | 新 USER(parent = 原 USER 的 parent)，并清空该分支下游 |
| 重新生成 | 新 ASSISTANT(parent = 该轮 USER)，`current_message_id` 指向新 |
| 切换版本 | `current_message_id` 指向另一个兄弟；UI 显示 `‹ 2/3 ›` |
| 继续生成 | 末尾 ASSISTANT 追加内容，状态回 `WIP` → `FINISHED` |
| 删除会话 | 软删 `deleted=1`；附件异步清理 |

**思考阶段被掐断怎么处理**（2026-11-18 定稿）：只显示「已停止」+ 折叠思维链，给「重新生成」，**不给「继续生成」**。

原因：续写靠 assistant 预填，而思考块带 `signature`（对整块内容签名）——
截断后回传签名必然不匹配，服务端会 400；且开思考时协议本就不允许预填 assistant。
所以「接着刚才那条思路继续想」在协议层做不到，能做的只有「再想一轮」（等价于重新生成），
不假装能续。签名已落 `messages.thinking_signature`（仅本地），留给「整块回放」用。

**继续生成的三个前置条件**（缺一不可，否则按钮置灰）：

1. 该回复不在"用过联网工具"的轮次（服务端会 400）；
2. `stop_reason` 为 `max_tokens` 或用户主动停止；
3. 回复不含服务端模板块、且非拒绝。

### 5.2 上下文重建（最容易出错的地方）

```kotlin
fun buildRequestMessages(session, currentLeaf): List<MessageParam>
```

规则（逐条对齐官方行为）：

1. 沿 `current_message_id` 回溯出**线性分支**；
2. 只保留「USER + 可用 ASSISTANT」**成对**的轮次；孤立的 USER、错误的 ASSISTANT 整轮跳过；
3. 「可用」= `status ∈ {FINISHED, INCOMPLETE}` 且 `stop_reason != refusal` 且不含 `TEMPLATE_RESPONSE` 且正文非空；
4. ASSISTANT 回传内容 = 正文文本块 + **仅 `kind=read_link` 的调用与结果**（含 `signature`）；**历史 thinking 一律不回传**；
5. 每个 USER 前，若其 `latest_reminder` 快照与本轮不同，插入一条 `{"role":"latest_reminder","content":...}`；
6. 末尾接本轮 USER（含图片/文件块，文件在前、文本在后）；
7. 继续生成时，末尾再补未完成的 ASSISTANT（保留本轮 thinking + signature）。

> 单测必覆盖：失败轮次跳过、read_link 签名回传、思考裁剪、reminder 去重、空附件轮次跳过。

### 5.3 标题生成

- 仅**会话首轮**请求带 `title:{enabled:true}`；
- 收到 `message_delta.title` 且会话**仍无标题**、未手动改名、未在导入中 → 写入；
- 缺失时兜底：取用户消息前 24 个字符（去换行）；
- 用户手动改名后，本会话不再接受服务端标题。

### 5.4 用户元信息（`latest_reminder`）

```
{date},{weekday},{location},{platform=App},{language}
例：2026-11-17,星期一,浙江,App,中文
```

- 开关关闭 → 不发送该角色消息；
- `location` 为空 → 该段为空字符串（保留逗号）；
- `language` 取官方 70+ 取值表（与 UI 语言联动，`{{language}}` 变量同源）；
- 快照随消息存储；导出**不保留**（v3 无此字段，与官方一致）。

### 5.5 导入流程（严格校验，失败即回滚）

```
1. 校验 ZIP 魔数 → 第一个条目必须是 manifest.json → export_format_version == 3
2. 逐条流式读取：
   manifest → user.json(跳过) → chat_sessions/*.json(逐个解析入库暂存)
   → chat_session_index.json → *_files/*(按 export_path 匹配)
   → 任何"未识别条目" → 立即失败整包拒绝（与官方实现一致）
3. 全部通过后：单事务替换 sessions/messages/fragments/attachments
4. 失败：清理暂存，保留原数据，提示"文件不完整/版本不支持"
```

**校验细则**：时间必须是 `Z` 结尾的 RFC3339；`parent_id < id`；角色交替；根消息为 `USER`；索引与会话一一对应；图片按 `file_id` 去重。

**性能目标**：87MB / 3340 会话包，中端机 ≤ 25s，内存峰值 ≤ 150MB（流式 + 分批事务 + 进度回调）。

### 5.6 系统提示词（对齐决策 8/10/12）

```kotlin
fun resolveSystem(session): String? = when (session.systemPromptMode) {
  CLEAR   -> null
  APPEND  -> OFFICIAL_LINE + "\n\n" + session.systemPromptSnapshot
  REPLACE -> session.systemPromptSnapshot
}
```

- 快照在**创建会话**时写入，之后只读；
- 变量替换（`{{date}} {{weekday}} {{time}} {{location}} {{language}} {{model}}`）在**发送时**求值（日期要实时），**只替换白名单**，未知变量保留原文并在设置页报错；
- `设置 → 应用 → 系统提示词`：预设列表（5 个内置 + 用户自定义）、编辑（变量插入按钮、字数与 token 估算、非法变量提示）、`恢复默认`。

### 5.7 分享为图片

1. 把选中的消息渲染（离屏 `ComposeView` → `Picture`/`Canvas`）；
2. 宽度固定 1080px，高度自适应，**超过 12000px 自动分段**；
3. 页脚固定："内容由 AI 生成，请仔细甄别" + TinyChat 标识（**不放 DeepSeek Logo**）；
4. 落地 `cacheDir/share/`，通过 `FileProvider` + `ACTION_SEND` 分享。

---

## 6. 任务拆解（WBS）

> 估时按单人、含自测；`依赖` 指必须先行完成的任务号。

### M0 工程骨架（3.1 人日 ≈ 0.6 周）

| ID | 任务 | 估时 | 依赖 | 验收 |
|---|---|---|---|---|
| M0-1 | Gradle wrapper + version catalog + 多模块骨架 | 0.5d | — | `./gradlew build` 通过 |
| M0-2 | `local.properties`、签名配置、`.gitignore` 校验 | 0.2d | — | 不泄露密钥/SDK 路径 |
| M0-3 | 设计令牌落地 `Color/Type/Shape`（01 附录 D） | 0.5d | M0-1 | 双主题色值与对照表一致 |
| M0-4 | 图标资产：`ImageVector` 骨架 + 占位（**定稿暂缓**，见附录 F） | 0.3d | M0-1 | 12 个占位可渲染、尺寸/色调统一 |
| M0-5 | 应用名、桌面图标（占位，非鲸鱼）、关于页骨架 | 0.3d | — | 桌面无 DeepSeek 标识；**不做启动页**（见 §11.1） |
| M0-6 | 导航图 + 空页面 | 0.5d | M0-1 | 五个目的地可跳转 |
| M0-7 | CI：build + lint + detekt | 0.5d | M0-1 | PR 必过 |
| M0-8 | `Capabilities` 与 `Provider` 模型定稿 | 0.3d | — | 两个 provider 的能力矩阵可单测断言 |

### M1 聊天内核（13 人日 ≈ 2.6 周）

| ID | 任务 | 估时 | 依赖 | 验收 |
|---|---|---|---|---|
| M1-1 | Room schema + DAO + 迁移框架 | 1d | M0-1 | schema 导出、CRUD 单测 |
| M1-2 | `SseReader` 手写解析器（4 类边界） | 1d | — | 用真实抓包样本跑单测 |
| M1-3 | DeepSeek 请求构建器（含 system/tools/thinking/effort/title） | 1d | M0-8 | 快照测试（golden JSON） |
| M1-4 | 事件→碎片归并器（含 tool_use 配对、乱序结果） | 1.5d | M1-2 | 覆盖 6 种块的乱序用例 |
| M1-5 | 流式落库与状态流转（WIP→FINISHED/INCOMPLETE/FAILED） | 1d | M1-1,4 | 杀进程后状态自愈 |
| M1-6 | 会话 CRUD + 列表（分组/置顶/改名/删除） | 1d | M1-1 | 与截图一致 |
| M1-7 | 发送/停止/重试 UI 接线 | 0.5d | M1-5 | 停止后内容保留 |
| M1-8 | 消息树操作：编辑重发、重新生成、版本切换 | 1.5d | M1-6 | 三个操作都可逆 |
| M1-9 | 继续生成（含三个前置条件判定） | 1d | M1-8 | 联网轮次按钮置灰 |
| M1-10 | 上下文重建器 + 单测 | 1.5d | M1-8 | §5.2 六条规则全覆盖 |
| M1-11 | 错误映射与重试策略 | 0.5d | M1-5 | 11 类错误各一例 |
| M1-12 | Files API 客户端（供 M4 用） | 1d | M1-3 | 上传返回 file_id |

### M2 渲染（11.5 人日 ≈ 2.3 周）

| ID | 任务 | 估时 | 依赖 | 验收 |
|---|---|---|---|---|
| M2-1 | Markdown 渲染接入 + 令牌映射（**必须用 `rememberStreamingMarkdownState`**，禁用 `Markdown(String)` 重载，见 04 §3） | 1d | M0-3 | 表格/列表/引用/代码/删除线 |
| M2-2 | 流式增量渲染 + 帧合并 | 1d | M2-1 | 3000 字流式：真机 ≥55fps、耗时 ≤1.3× 理论时长 |
| M2-3 | 抖动三坑修复（setext/key 稳定/未闭合公式） | 1.5d | M2-2 | 逐帧高度单调不减（Spike 的统计脚本可复用） |
| M2-4 | 代码块（高亮/语言标签/复制/长文折叠） | 1d | M2-1 | 20+ 语言；可试 `io.github.huarangmeng:codehighlight:1.0.7` |
| M2-5 | 公式：WebView 宿主 + 离线 KaTeX（**选型已定稿**，见 04 §4） | 2d | — | 20 条样例公式视觉对照；首帧 ≤300ms |
| M2-6 | Mermaid（**共用 M2-5 的 WebView 宿主** + 高度回传 + 深色主题 + 图/码切换） | 2d | M2-5 | 5 类图不溢出 |
| M2-7 | 思维链折叠（用时、展开态、流式展开） | 1d | M2-1 | 与截图一致 |
| M2-8 | 引用角标 + 来源聚合胶囊 | 1d | M2-1 | 点击定位到来源（与 M3-3 面板联调） |
| M2-9 | 消息操作栏（复制/朗读占位/分享/重新生成/继续生成） | 1d | M2-1 | 长按选中文本可用 |

### M3 联网（5 人日 = 1 周）

| ID | 任务 | 估时 | 依赖 | 验收 |
|---|---|---|---|---|
| M3-1 | 搜索步骤卡片（queries、结果数、站点图标堆叠） | 1d | M1-4 | 26 网页场景正确 |
| M3-2 | 打开页面 / 页内查找卡片 | 1d | M1-4 | 失败态有文案 |
| M3-3 | 搜索结果底部抽屉（站点名/日期/摘要/序号） | 1.5d | M3-1 | 手势可关闭 |
| M3-4 | 深度思考搜索多步分组的视觉串联 | 1d | M3-1,2 | stage 分组不错乱 |
| M3-5 | 搜索开关与计费提示 | 0.5d | M1-3 | 首开弹一次提示 |

### M4 附件（4.8 人日 ≈ 1 周）

| ID | 任务 | 估时 | 依赖 | 验收 |
|---|---|---|---|---|
| M4-1 | 图片选择（Photo Picker）+ 拍照 + 预览 | 1d | M1-12 | 权限拒绝有引导 |
| M4-2 | 文件选择（SAF）+ 大小/格式校验 | 1d | M1-12 | 超限即拒并提示 |
| M4-3 | 上传 + 状态（上传中/失败重试） | 1d | M1-12 | 断网恢复可续（官方也是转圈，不做百分比） |
| M4-4 | 消息内文件卡 / 图片缩略图 / 点击预览 | 1d | M4-1,2 | 与 chat-demo 版式一致（120dp，混排 64dp） |
| M4-5 | `purpose` 选择（解析型 vs user_data） | 0.5d | M4-3 | 按扩展名自动判定 |
| M4-6 | v4-pro 下禁用图片入口 | 0.3d | M0-8 | 能力位驱动 |

### M5 语音（5 人日 = 1 周）

| ID | 任务 | 估时 | 依赖 | 验收 |
|---|---|---|---|---|
| M5-1 | `AudioRecord` 录音 + WAV 封装 + 120s 上限 | 1d | — | 生成文件可被 ASR 接受 |
| M5-2 | 实时频谱（FFT + 竖条动画） | 1d | M5-1 | 与截图形态一致 |
| M5-3 | ASR 调用与结果回填（不自动发送） | 0.5d | M5-1 | 失败有 toast |
| M5-4 | TTS 朗读（2 音色）+ audio focus + 停止 | 1.5d | — | 切歌/来电正确暂停 |
| M5-5 | 长文按句切分播放 | 1d | M5-4 | 5000 字不卡顿 |

### M6 数据管理（8 人日 ≈ 1.6 周）

| ID | 任务 | 估时 | 依赖 | 验收 |
|---|---|---|---|---|
| M6-1 | 流式 ZIP Reader（STORE/DEFLATE、CRC、NFC 名） | 1.5d | — | 87MB 包不 OOM |
| M6-2 | 校验器（版本/首条目/父子关系/索应对应） | 1.5d | M6-1 | 20 个畸形样本被拒 |
| M6-3 | 导入入库（暂存 + 单事务替换 + 回滚） | 1.5d | M6-2 | 中断不损坏原数据 |
| M6-4 | 流式 ZIP Writer（官方条目顺序） | 1d | — | 输出可被 chat-demo 导入 |
| M6-5 | 导出 UI（SAF 保存 + 进度 + 成功提示） | 1d | M6-4 | 87MB 导出 ≤30s |
| M6-6 | 清空所有对话（含停止生成、清附件） | 0.5d | M1-1 | 二次确认 |
| M6-7 | 往返一致性测试（3340 会话） | 1d | M6-3,5 | 字段级 diff 为空 |

### M7 用量与费用（4 人日 ≈ 0.8 周）

| ID | 任务 | 估时 | 依赖 | 验收 |
|---|---|---|---|---|
| M7-1 | `Usage`/`CostCalculator` + 价格表 + 峰谷判定 | 1d | — | 与官方计价例一致 |
| M7-2 | 上下文圆环（含预估角标） | 1d | M7-1 | 点击展开明细 |
| M7-3 | 每轮统计条（可开关） | 0.5d | M7-1 | 命中/未命中/输出/费用 |
| M7-4 | 余额查询与侧栏展示（仅 DeepSeek） | 0.5d | M0-8 | 失败降级不报错 |
| M7-5 | `latest_reminder` 生成 + 开关 | 1d | — | 70+ 语言星期正确 |

### M7.5 提示词（2.2 人日 ≈ 0.4 周）

| ID | 任务 | 估时 | 依赖 | 验收 |
|---|---|---|---|---|
| M7-5-1 | `prompt_presets` 表 + 5 个内置预设种子 | 0.5d | M1-1 | 内置不可删 |
| M7-5-2 | 设置页列表 + 编辑器（变量插入/校验/字数） | 1d | M7-5-1 | 未知变量报错 |
| M7-5-3 | 快照写入与解析（replace/append/clear） | 0.5d | M1-3 | 老会话不受全局改动影响 |
| M7-5-4 | 恢复默认（一键） | 0.2d | M7-5-1 | 二次确认 |

### M8 打磨（6.3 人日 ≈ 1.3 周）

| ID | 任务 | 估时 | 依赖 | 验收 |
|---|---|---|---|---|
| M8-1 | OpenAI 兼容 provider（完整降级） | 2d | M1-3,4 | 能力位置灰正确 |
| M8-2 | 分享为图片（长图分段 + 页脚） | 1d | M2-1 | 12000px 上限 |
| M8-3 | i18n（zh/en） | 1d | — | 全量文案无硬编码 |
| M8-4 | 外观/字号/个性化设置 | 0.5d | M0-3 | 1.3 倍字号不破版 |
| M8-5 | **无 Key 行内引导**（提示条 + 发送拦截 + 底部抽屉进配置；**不做引导页**） | 0.3d | M8-1 | 冷启动直达主界面，无阻断弹窗 |
| M8-6 | 服务商与 API Key 页（增删改测） | 1d | M8-1 | Key 校验用 `GET /models` |
| M8-7 | 启动体验（系统 splash 最小化 + 冷启动 ≤1.2s） | 0.5d | M0-5 | 无自有启动页，只有系统图标一闪 |

### M9 发布（4.5 人日 ≈ 0.9 周）

| ID | 任务 | 估时 | 依赖 | 验收 |
|---|---|---|---|---|
| M9-1 | 性能专项：长会话/大图/导入/冷启动 | 2d | 全部 | 1 万条消息滚动 ≥55fps |
| M9-2 | 崩溃兜底、错误文案统一 | 1d | 全部 | 无英文堆栈外露 |
| M9-3 | README / LICENSE(MIT) / NOTICE（致谢 chat-demo） | 0.5d | — | 授权完整 |
| M9-4 | 签名 APK + GitHub Release 流程 | 0.5d | — | 可安装、可升级 |
| M9-5 | 发版前冒烟清单 | 0.5d | — | 清单 100% |

### 工时合计（按上表逐项加总）

| 里程碑 | 人日 |
|---|---|
| M0 | 3.1 |
| M1 | 13.0 |
| M2 | 11.5 |
| M3 | 5.0 |
| M4 | 4.8 |
| M5 | 5.0 |
| M6 | 8.0 |
| M7 | 4.0 |
| M7.5 | 2.2 |
| M8 | 6.3 |
| M9 | 4.5 |
| **合计** | **≈ 67.4 人日**（Spike 3 人日另计） |

换算口径：

- **全职 1 人（5 人日/周）** → **≈ 13.5 周**，加 Spike 与 15% 缓冲约 **16 周**；§9 排期表按此口径。
- **业余（15 小时/周 ≈ 2 人日/周）** → ≈ 34 周（**约 8 个月**）。若走这条路，建议砍范围：v1.0 只做 M0–M3 + M6（约 41 人日，20 周），M4/M5/M7 顺延到 v1.1。

> 两条口径差异极大，**请先确认每周可投入的人日**，否则排期没有意义。

---

## 7. 测试计划

### 7.1 单元测试（纯 JVM，CI 必跑）

| 模块 | 用例 |
|---|---|
| `SseReader` | keep-alive、无名 error 帧、无 DONE、多行 data、UTF-8 跨 chunk、提前 EOF |
| `RequestBuilder` | golden JSON（有/无 system、有无 tools、有无附件、续写、reminder 去重） |
| `ContextRebuilder` | 失败轮跳过、read_link 签名回传、thinking 裁剪、空附件轮、refusal 剔除 |
| `CostCalculator` | 峰/谷、缓存命中、工具费、微元不溢出 |
| `TokenEstimator` | 中英混排、emoji、代码块 |
| `ImportValidator` | 版本不符、首条目错、父 id 越界、索引不对应、未识别条目 |
| `PromptResolver` | 三模式、变量白名单、未知变量 |
| `Capabilities` | 两 provider 矩阵断言 |

### 7.2 手工测试矩阵

| 维度 | 覆盖 |
|---|---|
| 设备 | 小屏 5.0" / 主流 6.7" / 平板 10" / 折叠屏展开态 |
| 系统 | Android 8、10、12、14、16 |
| 网络 | WiFi、4G/5G、弱网（限速 100kbps）、断网即恢复、切网 |
| 长内容 | 3000 字流式、5000 字含 3 张表 2 个公式、20+ 轮会话 |
| 边界 | 无 Key、错误 Key、余额 0、超长输入、超大文件、拒答、限流 |
| 中断 | 生成中返回/杀进程/来电/锁屏/切后台 |

### 7.3 基准（M9 验收）

| 指标 | 目标 |
|---|---|
| 冷启动到可交互 | ≤ 1.2s |
| 1 万条消息会话滚动 | ≥ 55fps，无 ANR |
| 导入 87MB / 3340 会话 | ≤ 25s，峰值内存 ≤ 150MB |
| 导出 87MB | ≤ 30s |
| 流式渲染 | 掉帧 < 5% |

---

## 8. 风险登记册

| ID | 风险 | 概率 | 影响 | 缓解 |
|---|---|---|---|---|
| R1 | 商标/冒充争议 | 中 | 高 | 决策 1 已排除：自绘图标 + 自有名称 + 关于页声明 |
| R2 | 非 DeepSeek 服务商能力落差引发差评 | 高 | 中 | 能力位驱动置灰 + 文案说明；README 列明支持矩阵 |
| R3 | 流式渲染抖动/掉帧 | 中 | 中 | M2-3 专项 + 逐帧高度单调验收 |
| R4 | LaTeX/Mermaid 选型失败 | 中 | 中 | Spike 提前验证；兜底 WebView；最差降级为代码块 |
| R5 | 导入大包失败或损坏数据 | 低 | 高 | 暂存区 + 单事务 + 失败回滚 + 20 个畸形样本 |
| R6 | 提示词改动引发缓存费用暴涨 | 低 | 中 | 决策 10 已由快照规避 |
| R7 | 后台生成被系统杀 | 中 | 低 | v1 明确"仅前台流式"，UI 如实提示 |
| R8 | API 字段/价格变更 | 中 | 中 | 价格表远程可覆盖；协议层隔离；跟随官方文档 |
| R9 | 用户 Key 泄露 | 低 | 高 | Keystore 加密 + 不写日志 + 导出不含 Key |
| R10 | 大文件 120s 上传超时 | 中 | 低 | 压缩 + 进度 + 可重试 |

---

## 9. 排期

**口径**：全职 1 人 5 人日/周。**Spike 必须最先做**（丢弃型工程，不进主仓库），它验证不通过就要改方案，M0 之后再做等于白干。

| 阶段 | 周次 | 人日 | 累计周 |
|---|---|---|---|
| **Spike** | 第 1 周（前 3 天） | 3.0 | 0.6 |
| M0 工程骨架 | 第 1–2 周 | 3.1 | 1.2 |
| M1 聊天内核 | 第 2–4 周 | 13.0 | 3.8 |
| M2 渲染 | 第 4–7 周 | 11.5 | 6.1 |
| M3 联网 | 第 7–8 周 | 5.0 | 7.1 |
| M4 附件 | 第 8–9 周 | 4.8 | 8.1 |
| M5 语音 | 第 9–10 周 | 5.0 | 9.1 |
| M6 数据管理 | 第 10–11 周 | 8.0 | 10.7 |
| M7 用量费用 | 第 11–12 周 | 4.0 | 11.5 |
| M7.5 提示词 | 第 12 周 | 2.2 | 11.9 |
| M8 打磨 | 第 12–14 周 | 6.3 | 13.2 |
| M9 发布 | 第 14 周 | 4.5 | 14.1 |
| **合计** | | **70.4** | **≈ 14 周** |

**关键路径**：Spike → M1-10（上下文重建）→ M2-2/2-3（流式渲染）→ M6-3（导入事务）→ M9-1（性能）。任何一项延期，整体顺延。

**建议执行顺序**：**Spike（3 天）→ M0 → M1**。Spike 结论若与预期不符（尤其公式/图表选型、流式帧率），先改方案再继续，不要硬推。

---

# 第二部分 交互设计

## 10. 设计原则与令牌

### 10.1 三条原则

1. **复刻优先**：布局、间距、字号、圆角、动效曲线一律对齐官方；任何"改进"必须能单独关掉。
2. **增量克制**：新增功能只加在**不打断主流程**的位置（顶栏、侧栏底、设置内），不改动输入区与消息区的主结构。
3. **失败可恢复**：任何失败都要有明确的下一步（重试/换 Key/换服务商），且**已生成内容不丢**。

### 10.2 令牌 → Compose 映射

```kotlin
// Color.kt（浅色 / 深色）— 源自 chat-demo theme.css（MIT）
object TcColors {
  val Brand          = light(0xFF3964FE) to dark(0xFF5686FE)
  val Bubble         = light(0xFFEDF3FE) to dark(0xFF2C2C2E)
  val Bg             = light(0xFFFFFFFF) to dark(0xFF151517)
  val BgLayer2       = light(0xFFFFFFFF) to dark(0xFF2C2C2E)
  val LabelPrimary   = light(0xFF0F1115) to dark(0xFFF9FAFB)
  val LabelSecondary = light(0xFF61666B) to dark(0xFFCFD3D6)
  val LabelTertiary  = light(0xFF81858C) to dark(0xFFADB2B8)
  val BorderL2       = light(0x1A000000) to dark(0x1FFFFFFF)
  val CodeBg         = light(0xFFF9FAFB) to dark(0xFF1B1B1C)
  val InlineCodeBg   = light(0xFFEBEEF2) to dark(0xFF2C2C2E)
  val Danger         = light(0xFFEC1313) to dark(0xFFF25A5A)
}

// Type.kt — Markdown 语义字号
val mdH1    = TextStyle(fontSize = 24.sp, lineHeight = 34.sp, fontWeight = Bold)
val mdH2    = TextStyle(fontSize = 22.sp, lineHeight = 32.sp, fontWeight = Bold)
val mdH3    = TextStyle(fontSize = 20.sp, lineHeight = 30.sp, fontWeight = Bold)
val mdBody  = TextStyle(fontSize = 16.sp, lineHeight = 28.sp)
val mdTable = TextStyle(fontSize = 15.sp, lineHeight = 25.sp)
val mdSmall = TextStyle(fontSize = 14.sp, lineHeight = 24.sp)
val mdCode  = TextStyle(fontSize = 14.sp, lineHeight = 22.sp, fontFamily = Mono)

// Shape.kt
val BubbleShape = RoundedCornerShape(22.dp)   // 用户气泡
val InputShape  = RoundedCornerShape(24.dp)   // 输入框
val CardShape   = RoundedCornerShape(12.dp)   // 卡片/设置项/会话项
val SheetShape  = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
```

> 字号缩放：`font_scale` 作用于正文与列表，标题按比例缩放但**上限 1.3×**，避免破版。

---

## 11. 信息架构与导航

### 11.1 启动行为（**已定：无启动页，直接进主界面**）

```
[进程启动]
  └─► 会话页（主界面）   ← 唯一入口，没有引导页/欢迎页/启动页
        ├─ 有 Key：恢复上次会话 or 新会话
        └─ 无 Key：界面照常渲染，顶部出现「未配置服务商」提示条
                  点条 → 服务商与 API Key 页（以底部抽屉形式打开，不跳转）
```

**明确取消的东西**：

| 取消 | 替代方案 |
|---|---|
| 独立启动页 / 欢迎页 / Logo 淡入动画 | 无。冷启动直接渲染会话页 |
| 首次使用引导页（onboarding 分步） | 无 Key 时**行内提示**：输入区上方一条提示条 + 发送时 toast |
| 「选服务商」首次弹窗 | 从设置或提示条进入，不阻断主界面 |

**⚠️ 平台现实（必须知道，否则会返工）**：
Android 12+ 由系统强制绘制 **SplashScreen**（应用图标 + 窗口背景），第三方**无法完全移除**。我们能做的只有"让它最短、最不突兀"：

1. 用 `androidx.core:core-splashscreen` + 主题 `Theme.SplashScreen`，只设 `windowSplashScreenBackground = 背景色`、`windowSplashScreenAnimatedIcon = 应用图标`、`postSplashScreenTheme = 主主题`；
2. **不**做自定义动画、**不**加 `setKeepOnScreenCondition`（那会人为延长）；
3. Android 8–11 上则直接把启动主题设成与主界面同色 + 无 `windowBackground` 图，做到"无感"；
4. 验收口径：**冷启动到可交互 ≤ 1.2s**（§7.3），期间只有系统图标一闪，**不出现任何 TinyChat 自己的欢迎页**。

### 11.2 导航结构

```
会话页（唯一主界面）
  ├─ 左上「☰」──► 会话抽屉（列表/余额/设置入口）
  ├─ 右上「⊕」──► 新建会话
  ├─ 右上「🔊/🔇」──► 自动朗读开关
  └─ 输入区 ──► 附件 / 语音 / 模型与强度 / 搜索

设置（从抽屉进入）
  ├─ 账户：服务商与 API Key · 数据管理
  ├─ 应用：语言 · 外观 · 字体大小 · 个性化 · 系统提示词
  ├─ 语音：朗读音色
  └─ 关于：版本 · 服务协议 · 帮助与反馈
```

> 与官方差异：**仅两处**——「账号管理」→「服务商与 API Key」，以及应用分组里多一个「系统提示词」。其余保持一致。

---

## 12. 页面规格

### 12.1 首页 / 空会话（**这就是启动后看到的第一个界面**）

> 没有独立的启动页/欢迎页；冷启动后系统 splash 一闪即进入本页（见 §11.1）。
> 本页同时承担"首次使用"的所有引导职责——用**行内提示**，不用弹窗、不用分步引导。

| 元素 | 规格 |
|---|---|
| 顶栏 | 高 56dp；左「☰」40dp 触达；右「🔊」「⊕」各 40dp；无标题 |
| 主体 | 垂直居中：**图标 72dp（方案待定，可省略）** + 问候语 |
| 问候语 | 按时段：`早上好`(<12) / `下午好`(12–18) / `晚上好`(≥18) + `，我能帮什么忙吗？`；22sp / 600 |
| 无 Key 提示条 | 问候语上方，高 40dp，圆角 12dp，`Bubble` 底色 + 品牌色文字：`尚未配置服务商，点击设置 API Key`；点击以**底部抽屉**打开服务商页 |
| 输入区 | 悬浮卡片，距底 12dp、水平 12dp；圆角 24dp；白底 + 阴影 lv3 |
| 占位文案 | `发消息或按住说话`，`LabelTertiary` |
| 第二行 | `[模型与强度 pill] [搜索 icon pill] [回形针](40dp) [( •))](40dp)` |

> 图标位取舍：官方是鲸鱼 + 问候语。我们不能用鲸鱼，且图标方案暂缓 —— 因此 **M0–M2 先只显示问候语**（垂直居中），待图标定稿后可选加回 72dp 图标；布局用 `Column` 居中，加不加图标都不影响结构。

**输入区结构**（自上而下）：

1. 附件预览行（有附件时，56dp 缩略图横向滚动，右上角删除角标）；
2. **图片快捷提问行**（草稿里有图片、且非生成中时显示）：一排横向滚动的**描线圆角按钮**（不填充，`borderL2` 1dp 描边），文案后跟一个 `→`：`这是什么 →` `分析一下 →` `怎么办 →` `解释一下 →`。点一下就把这句话当作用户输入、连同当前全部附件直接发送（官方同款，省一次输入；点击会覆盖输入框里未发的文字）；
3. 语音频谱行（录音时显示，高 32dp；竖条 **2dp 宽 + 3dp 间距、根数按可用宽度自适应**——2026-11-19 按 chat-demo 更正，原先写的固定 20 根作废）；
4. 文本输入框（最多 8 行后内部滚动）；
5. 操作行。

**语音模式**（M5，2026-11-19 按官方 App 核对）：

| 状态 | 表现 |
|---|---|
| 进入 | 点右下角麦克风 → 按钮变成**键盘图标**；文本输入框位置整块换成居中的「按住说话」（`LabelTertiary`）；此时不弹输入法 |
| 按住 | 按下即开始录音，上方出现频谱行 + 录音秒数；主文案变「松开发送」（品牌色），下面挂一行 12sp「上滑取消」 |
| 松手（有内容） | 立即上传转写，**转写成功后直接发送**（不经过输入框、不退出语音模式） |
| 松手（无内容） | 弹提示「未识别到语音内容」，停在该状态 |
| **上滑取消** | 按住后竖向位移 > 56dp 且明显向上 → 文案变「松开取消」（`danger` 色）；松手时**丢弃**这段录音：不识别、不发送、不留文件。横向滑动不算 |
| 转写中 | 输入区显示「识别中…」，点键盘图标 = 取消本次（丢弃结果，回文字输入） |
| 退出 | 点键盘图标回文字输入，键盘弹出、焦点落输入框 |
| 权限 | 首次进入语音模式时申请 `RECORD_AUDIO`；拒绝则弹提示并留在文字模式 |
| 上限 | 录满 120s 自动停止，按有内容处理并提示「已达到单次录音时长上限」 |

> 生成中（`isStreaming`）不允许进入语音模式；切后台直接放弃本次录音。

**模型与强度 pill**：显示如 `Flash 高`（**不加中点**，决策 20），点击弹半屏选择器（模型列表 + 三档强度）；`deepseek-v4-pro` 旁标注"不支持联网/文件"。

**搜索 pill**：只显示图标。开启态 = 品牌色描边 + 品牌色图标；关闭态 = 灰色。**非 DeepSeek 服务商时置灰**，点击提示"当前服务商不支持服务端联网搜索"。

### 12.2 会话页

| 区域 | 规格 |
|---|---|
| 顶栏 | 左「☰」+ 标题（居中，1 行省略）+「🔊」；生成中标题不变 |
| 标题点击 | 弹重命名输入框（预填当前标题，空则"新对话"） |
| 消息区 | `LazyColumn(reverseLayout = true)`，上下 12dp，消息间距 20dp |
| 回到底部 | 40dp 圆钮，右下 16dp，带阴影；离底（末条底边低于视口底边）即显示 —— 原定 56dp / 离底 240dp，等视觉终稿再调 |
| 到底判定 | 末条**底边**对齐视口底边；底部 12dp 留白用 `Modifier.padding`，不进滚动内容（否则留白要单独再滚一次，看着像瞬移） |
| 自动跟随 | 生成中贴底跟随；用户自己拖动即停止跟随，拖回底部恢复；按钮点击用动画，流式跟随用无动画 |
| 输入法 | 上下划消息区时收起；点「回到底部」**不收起**（收起会让视口在动画中途变大，输入框仍停在旧位置，画面撕裂） |
| 输入区 | 同 §12.1；生成中「发送」变为「停止」（方形图标） |

**生成中**：

- 输入区仍可输入；发送按钮禁用并提示"正在生成"（v1 不排队）；
- 助手消息末尾显示闪烁光标（2dp 宽，600ms 呼吸）；
- 思维链默认**展开**，回答开始输出后自动折叠为「已思考（用时 N 秒）」。

### 12.3 会话抽屉

| 元素 | 规格 |
|---|---|
| 宽度 | 屏宽 × 0.82（最大 320dp）；左侧滑入 200ms |
| 顶部 | 「开启新对话」pill（44dp，圆角 100dp，品牌色描边）+ 关闭 |
| 列表分组 | 置顶 → 今天 → 昨天 → 7 天内 → 30 天内 → 更早；分组标题 12sp `LabelTertiary` |
| 会话项 | 高 44dp，圆角 12dp；标题 1 行；当前项底色 `selector`；右侧「⋯」出菜单（重命名/置顶/删除） |
| 底部 | ① 余额行（仅 DeepSeek）：`余额 ¥12.34 ↻` 12sp；② 设置入口（齿轮 +"设置"） |

**删除确认**：含附件的会话文案不同（"删除后该对话及其中上传的文件不可恢复"）。

### 12.4 搜索结果抽屉（底部）

- 高度 76% 屏高，顶部圆角 24dp；
- 标题「搜索结果」居中 + 右上「×」；
- 每条：站点图标 20dp + 站点名 + 日期 + 右上角序号（灰底圆角 8dp）；
- 标题 16sp/600，摘要 14sp/24sp `LabelSecondary`，最多 2 行；
- 点击 → 外部浏览器；
- 底部「全部」按钮（超过 5 条时）。

### 12.5 设置

分组与顺序（与截图一致，仅两处改动）：

```
账户
  服务商与 API Key        →
  数据管理               →
应用
  语言            中文（简体）  →
  外观                系统    →
  字体大小                 →
  个性化                  →
  系统提示词      默认（官方）  ← 新增
语音
  朗读音色         海星（女）  →
关于
  版本                1.0.0   →
  服务协议                 →
  帮助与反馈               →
```

设置项规格：高 56dp、左侧 24dp 图标、标题 16sp、右侧值 14sp `LabelTertiary` + `›`；分组卡片圆角 12dp、底色 `BgLayer2`、组间距 24dp。

### 12.6 服务商与 API Key

- 列表：每个服务商一行（名称 + 模型摘要 + 是否已填 Key + 选中态）；
- `＋ 添加服务商`：
  - **DeepSeek 官方**：只需 API Key（`GET /models` 校验）；
  - **OpenAI 兼容**：Base URL（默认 `https://api.openai.com/v1`）+ API Key + 模型名（手填或 `GET /models` 拉取）；
- 校验中：按钮转圈；失败：行内红字（401 → "Key 无效"）；
- 删除：二次确认（"该服务商下的会话仍保留，但无法继续对话"）。

### 12.7 系统提示词

```
[预设列表]（单选，右侧显示当前选中打勾）
  默认（官方）        只读
  无提示词
  角色扮演骨架
  翻译 / 润色
  学习辅导
  ＋ 新建
[当前选中的编辑区]（内置预设只读，需先"复制后编辑"）
  名称：______
  组合方式：[替换 ▾ | 追加 | 清空]
  内容：多行输入，右上角显示 字数 / 预估 token
  变量按钮：{{date}} {{weekday}} {{time}} {{location}} {{language}} {{model}}
  非法变量：行内红字提示，保存按钮禁用
[底部]  恢复默认
```

- **恢复默认**：二次确认"将把默认提示词恢复为官方原文，自定义内容不受影响"；
- 保存后提示"仅对新会话生效"（决策 10）；
- **设置列表里那一行的副标题显示当前状态**，格式 `当前预设：默认` / `当前预设：自定义`（决策 20），不写"5 个预设"之类的说明性文字。

### 12.8 数据管理

```
导出所有历史对话        →   （兼容模式，附说明文案）
导入历史对话            →
删除所有历史对话        红色
```

- **导出**：SAF 建文件 → 进度（百分比 + 当前条目数）→ 完成 toast（含文件大小）；
- **导入**：选择文件 → 校验（"正在校验…"）→ **二次确认"将覆盖当前全部对话"** → 进度 → 完成；失败给具体原因（版本不支持/文件不完整）；
- **删除**：二次确认（红色按钮），期间禁用其他交互。

### 12.9 分享为图片

- 入口：消息操作栏「↗」→ 底部抽屉；
- 选项：`生成图片` / `复制文本` / `分享`；
- 生成中显示进度；完成后可预览并分享；
- 图片页脚：`内容由 AI 生成，请仔细甄别` + `TinyChat`（**无 DeepSeek 标识**）。

---

## 13. 消息组件族

| 组件 | 规格 |
|---|---|
| **用户气泡** | 右对齐；底色 `Bubble`；圆角 22dp；内边距 12×10dp；最大宽 78% 屏宽；16sp/28sp；保留换行。**短按进入编辑重发**（输入框上方显示「正在编辑消息：」+「取消」），**长按复制本条** |
| **助手长按** | 菜单两项：`复制`（复制整个会话的纯文本）/ `选择文本`（底部面板，走系统选择器） |
| **用户附件** | **一列自动折行、右贴边、间距 10dp**；图片是正方形缩略图（**只有图片 120dp；与文档混排时缩到 64dp**），圆角 16dp、1dp 描边、`cover` 裁剪；上传中压 30% 黑蒙层 + 转圈，失败描红边 + 重试图标；文件卡（40dp 图标 + 名 + 「扩展名 大小」）。<br>⚠️ 2026-11-19 按参考实现 chat-demo（`UserMessage.module.css` / `MessageFile.module.css`）更正：**不是**「1 张 1:1、2 张 2 列、≥3 张 3 列」的九宫格 |
| **助手正文** | 左对齐，无气泡，满宽（左右 20dp）；Markdown 渲染；段落间距 12dp |
| **思维链** | 折叠头：`已思考（用时 N 秒）›`，14sp `LabelSecondary`；展开后左侧 2dp 竖线 + 24dp 缩进，15sp `LabelSecondary`；流式中显示 `正在思考` + 光标 |
| **搜索步骤** | 行：`搜索到 N 个网页` + 站点图标堆叠（最多 3 个 + 溢出圆）+ `›`；展开为结果列表；失败：`联网搜索暂不可用` |
| **打开页面** | `已浏览 N 个页面`；展开显示站点 + 标题 |
| **页内查找** | `查找关键词 "pattern"`；失败 `未查找到相关内容` |
| **引用角标** | 上标数字，10sp，圆角 4dp，底色 `InlineCodeBg`；点击定位到对应来源 |
| **来源聚合** | 正文末尾胶囊：图标堆叠 + `N 个网页`，点击打开搜索结果抽屉 |
| **免责声明** | **不做消息级免责声明**：输入框下方已有「回答由 AI 生成，仅供参考」，每条回答再挂一行属于重复刷屏（导出图片的页脚仍保留，见 §12.9） |
| **操作栏** | 固定两行，官方版式。第一行：`复制` `朗读` `分享` 靠左贴边、`重新生成` 靠右贴边；第二行：`‹ 2/3 ›` 靠左贴边、`继续生成` 靠右贴边。40dp 触达、18dp 图形、`LabelTertiary`，无赞踩。生成中不出栏；失败/超时不出栏（交给「重试」）。**手动停止只要已有正文，第一行照给**（内容可复制、可朗读），只有「思考到一半就被掐断、正文为空」才只剩重新生成。朗读在 M5-4 前为占位（点了无动作） |
| **每轮统计**（可关） | `↑1.2k(命中 0.8k) ↓0.6k | 思考 0.3k | 搜索 1 | ¥0.012`，12sp `LabelTertiary`（分隔用竖线，不用中点） |
| **错误态** | 红字 + 感叹号；正文与思考分别展示；下方「重试」 |
| **拒答态** | 正文替换为拒答文案；**不显示**已下发内容；不可重发 |
| **文件卡（消息内）** | 圆角 16dp，左侧 40dp 类型图标，名（1 行）+ 大小；解析失败显示红色角标 |
| **版本切换** | 助手消息底部居中：`‹ 2 / 3 ›`，12sp |

**引用与搜索的关系**：正文内角标是"精确到句子"的引用；末尾胶囊是"本轮总共访问了多少网页"。两者都指向同一个搜索结果集。

---

## 14. 状态矩阵

| 场景 | 输入区 | 消息区 | 顶栏 | 提示 |
|---|---|---|---|---|
| **冷启动** | — | — | — | 只有系统 splash 一闪，**无自有启动页**（§11.1） |
| **未配置 Key** | 发送禁用（可输入） | 问候语 + **提示条** | 正常 | 提示条「尚未配置服务商，点击设置 API Key」→ 底部抽屉 |
| 空会话 | 可用 | 问候语 | 正常 | — |
| 输入中 | 发送可用 | — | 正常 | — |
| 上传附件中 | 发送禁用 | 附件卡显示进度 | 正常 | 可取消 |
| 生成中 | 停止可用 | 流式 + 光标 | 标题不变 | — |
| 已停止 | 发送可用 | 保留内容 + `已停止` | 正常 | 可"继续生成" |
| 生成失败 | 发送可用 | 红字错误 + 重试 | 正常 | Toast 同类信息 |
| 无网络 | 发送禁用 | — | — | Toast「网络异常」 |
| Key 无效 | 发送禁用 | — | — | 顶部条「API Key 无效，去设置」 |
| 余额不足 | 发送禁用 | — | — | 顶部条「余额不足，去充值」 |
| 被限流 | 发送禁用 30s | — | — | 倒计时提示 |
| 拒答 | 发送可用 | 拒答文案 | 正常 | — |
| 非 DeepSeek 服务商 | 发送可用 | — | — | 搜索/TTS 置灰并说明 |
| v4-pro 模型 | 图片入口隐藏 | — | — | 模型选择器内已标注 |

---

## 15. 文案表

zh 主文案（en 在 M8 补齐）。**风格：短句、口语、不用感叹号、不卖萌。**

> ⚠️ **硬性文案规则（决策 20）**：界面文案**不使用「·」作分隔符**（AI 生成文案的高频口癖，读者已经生理性不适）。
> 需要分隔时用：全角逗号「，」、空格、竖线「|」、或直接拆成两行。文档正文里的「·」不在此限，但**凡是会显示在界面上的字符串**都要检查一遍。
> 已有单测 `PromptPresetTest.显示名非空且不含中点` 把这条规则钉住（预设名会直接上屏）。

| Key | 文案 |
|---|---|
| welcome.greeting.morning | 早上好，我能帮什么忙吗？ |
| welcome.greeting.afternoon | 下午好，我能帮什么忙吗？ |
| welcome.greeting.evening | 晚上好，我能帮什么忙吗？ |
| input.placeholder | 发消息或按住说话 |
| provider.missing | 尚未配置服务商，点击设置 API Key |
| prompt.current | 当前预设：{name} |
| input.stop | 停止 |
| input.generating | 正在生成 |
| disclaimer | 本回答由 AI 生成，内容仅供参考，请仔细甄别 |
| think.done | 已思考（用时 {n} 秒） |
| think.thinking | 正在思考 |
| search.results | 搜索到 {n} 个网页 |
| search.fail | 联网搜索暂不可用 |
| search.noResult | 未搜索到合适内容 |
| open.pages | 已浏览 {n} 个页面 |
| find.keyword | 查找关键词 |
| find.fail | 未查找到相关内容 |
| refusal | 你好，这个问题我暂时无法回答，让我们换个话题再聊聊吧。 |
| error.network | 网络异常，请检查网络后重试 |
| error.apiKey | API Key 无效或已被删除，请更换后重试 |
| error.balance | 账户余额不足，请充值后重试 |
| error.rateLimit | 请求过于频繁，请稍后再试 |
| error.busy | 服务器繁忙，请稍后再试 |
| error.interrupted | 生成被中断，可继续生成 |
| error.upload | 附件上传失败，请重试 |
| cap.noServerSearch | 当前服务商不支持服务端联网搜索 |
| cap.noVision | 当前模型不支持图片输入 |
| session.new | 开启新对话 |
| session.untitled | 新对话 |
| session.delete.title | 永久删除对话 |
| session.delete.desc | 删除后，该对话及其中上传的文件将不可恢复。确认删除吗？ |
| data.export.done | 导出完成（{size}） |
| data.import.confirm | 导入将覆盖当前全部对话，确认继续吗？ |
| data.import.failed | 导入失败：{reason} |
| prompt.saved | 已保存，仅对新会话生效 |
| prompt.reset.confirm | 将把默认提示词恢复为官方原文，自定义内容不受影响。确认吗？ |
| usage.estimated | 预估 |

---

## 16. 动效与手势

| 交互 | 规格 |
|---|---|
| 会话抽屉 | 左侧滑入 200ms `cubic-bezier(0.4,0,0.2,1)`；遮罩透明度 0→0.32 |
| 底部抽屉 | 上滑 240ms；下拉关闭（位移 > 25% 高度或速度 > 800dp/s） |
| 思维链展开 | 高度动画 200ms，内容淡入 150ms |
| 流式光标 | 2dp 竖条，透明度 0.6→0.1→0.6，600ms 循环 |
| 回到底部 | 出现/消失 150ms 淡入淡出，位移 8dp |
| 消息插入 | 新消息淡入 + 上移 8dp，180ms |
| 长按 | 文本选中菜单（复制/全选）；容器 200ms 轻缩放 0.98 |
| 下拉刷新 | **无**（会话内不存在下拉语义） |
| 双击 | **无**（避免误触） |

---

## 17. 无障碍与本地化

- **触达尺寸**：所有可点元素 ≥ 48×48dp（图标视觉可 24dp）；
- **对比度**：正文与背景 ≥ 4.5:1；`LabelTertiary` 仅用于非关键信息；
- **TalkBack**：图片需 `contentDescription`；搜索卡片读作"搜索到 26 个网页，双击展开"；流式过程中**不逐字播报**（按段节流）；
- **动态字号**：跟随系统缩放，上限 1.3×，需实测设置页与输入区不破版；
- **本地化**：中/英；日期星期按语言；`{{language}}` 与界面语言联动；
- **RTL**：v1 不做完整支持（官方亦无），但布局用 `start/end` 而非 `left/right`。

---

## 18. 交互验收清单

开工前请确认，逐条勾选即视为 v1.0 交互达标。

**会话与消息**

- [ ] **冷启动直接进入会话页，无自有启动页/欢迎页/引导页**（只有系统 splash 一闪）
- [ ] **未配置 Key 时主界面照常渲染**，只出现提示条，无阻断弹窗
- [ ] 空会话问候语按时段变化；图标位按方案决定"有/无"都不破版
- [ ] 用户气泡 22dp 圆角、`#EDF3FE`、右对齐、最宽 78%
- [ ] 助手消息无气泡、满宽、Markdown 正确
- [ ] 思维链默认展开→回答开始后自动折叠，用时统计正确
- [ ] 搜索/打开/查找三类步骤卡片文案与图标正确
- [ ] 正文引用角标可点击并定位来源
- [ ] 末尾来源胶囊显示"共 N 个网页"
- [ ] 操作栏各动作均可用（复制/分享为真，朗读为占位，无赞踩）
- [ ] 生成中可停止，停止后内容保留且可"继续生成"
- [ ] 重新生成产生新版本，`‹ 2/3 ›` 可切换
- [ ] 编辑提问后下游分支正确重建

**输入**

- [ ] 模型与强度 pill 显示当前值并可切换
- [ ] 切到 v4-pro 后图片入口消失、搜索提示不可用
- [ ] 搜索 pill 仅图标，开/关态视觉区分明显
- [ ] 非 DeepSeek 服务商时搜索置灰且有解释
- [ ] 语音录音时显示实时频谱
- [ ] 附件预览可删除、可取消上传

**长列表与流式**

- [ ] 3000 字流式输出不掉帧，底部不抖动
- [ ] 流式过程中不出现半截 setext 标题
- [ ] 块级公式在未闭合时不闪现源码
- [ ] 离底超过 240dp 出现"回到底部"按钮

**数据**

- [ ] 导出文件可被官方 chat-demo 正常导入
- [ ] 导入 3340 会话包成功且字段级一致
- [ ] 导入失败时原数据完好
- [ ] 删除会话/清空均有二次确认

**新增能力**

- [ ] 上下文圆环显示占用比例，预估态有角标
- [ ] 每轮统计可开关，数字与官方 usage 一致
- [ ] 余额仅 DeepSeek 显示，失败静默降级
- [ ] 系统提示词 5 个预设可用，默认预设只读
- [ ] 改全局提示词后，旧会话行为不变，新会话生效
- [ ] 恢复默认一键还原官方原文
- [ ] 分享图片无 DeepSeek 标识，含免责声明

---

## 附录 F：图标清单（M0-4 依据）

> **状态：图标方案暂缓定稿（2026-11-17）**。下表只锁定"用哪个图标、从哪来"，不锁定具体视觉；M0 阶段先用占位图标跑通，视觉在 M2/M8 统一收口。
> 事实依据：`chat-demo` 的 `src/components/base/icons.tsx` 共 **41 个内联 SVG 图标**，MIT 协议，**可直接把 path 数据转成 Compose `ImageVector`**。
> ⚠️ **chat-demo 不含 DeepSeek 鲸鱼 Logo**（`Welcome.tsx` 只有文字问候），所以桌面图标不能用鲸鱼；**首页中心图标位可直接省略**（决策 17，M0–M2 只放问候语）。

### F.1 核心 12 个（M0-4 的清单）

| # | 用途 | 建议命名 | 来源 | 备注 |
|---|---|---|---|---|
| 1 | 会话抽屉 | `IcMenu` | chat-demo `IconMenuOutline20` | ✅ 直接移植 |
| 2 | 新对话 | `IcNewChat` | chat-demo `IconNewChatOutline20` / `NewChatIcon` | ✅ 直接移植 |
| 3 | 发送 | `IcSend` | chat-demo `IconSendOutline16` | ✅ 直接移植 |
| 4 | 停止 | `IcStop` | chat-demo `IconStopFill16` | ✅ 直接移植 |
| 5 | 语音输入 | `IcMic` | chat-demo `IconMicOutline16` / `IconMicFill16`（录音态） | ✅ 直接移植 |
| 6 | 附件 | `IcAttach` | chat-demo `IconPaperclipOutline16` | ✅ **已定：用回形针**（不改成 ⊕） |
| 7 | 复制 | `IcCopy` | chat-demo `IconCopyOutline16` | ✅ 直接移植 |
| 8 | 朗读 | `IcSpeak` | **Material Symbols** `volume_up` | chat-demo 只有播放三角，不用 |
| 9 | 分享 | `IcShare` | **Material Symbols** `share` / `ios_share` | chat-demo 无 |
| 10 | 重发 | `IcRegenerate` | chat-demo `IconRefreshOutline16` | ✅ 直接移植 |
| 11 | 回到底部 | `IcArrowDown` | **Material Symbols** `arrow_downward` 或 `keyboard_arrow_down` | chat-demo 的 14px 箭头太小 |
| 12 | 设置 | `IcSettings` | chat-demo `IconSettingsOutline16` | ✅ 直接移植 |

**小结：9 个来自 chat-demo（MIT 可直接移植），3 个来自 Material Symbols（Apache-2.0）。**

### F.2 12 个之外还能大量借用（chat-demo，全部 MIT）

| 用途 | chat-demo 图标 |
|---|---|
| 深度思考 pill | `IconThinkOutline14`（对话区用 `IconThinkOutline16`） |
| 智能搜索 pill（地球） | `IconGlobeLanguageOutline14` ← 截图里那个地球就是它 |
| 搜索步骤 | `IconSearchOutline16` / 失败 `IconSearchWarningOutline16` |
| 打开页面 / 页内查找 | `IconBrowseOutline16` / 失败 `IconBrowseWarningOutline16` |
| 阅读链接行 | `IconLinkOutline16`（另有 14 变体 `IconLinkOutline14`） |
| 引用跳转 / 外链 | `IconRightUpOutline14` |
| 步骤成功 / 失败 | `IconCheckedOutline14` / `IconCloseFill14` |
| 折叠箭头 | `IconChevronDownOutline14` / `IconChevronRightOutline14` |
| 重命名 / 更多 | `IconEditOutline16` / `IconEllipsisOutline16` |
| 删除 / 警告 | `IconTrashOutline16` / `IconWarningOutline16` |
| 主题（浅/深/跟随） | `IconLightOutline16` / `IconDarkOutline16` / `IconFollowsystemOutline16` |
| 数据管理 / 上传 / 下载 | `IconDataOutline16` / `IconUploadOutline14` / `IconDownloadOutline16` |
| 关闭 / 勾选 | `IconCloseOutline16` / `IconCheckOutline16` |
| Toast 错误 / 图片失败占位 | `IconToastError16` / `IconPhotoErrorFill20` |
| 文件类型（独立一套） | `src/components/files/FileIcon.tsx` |
| 站点图标容器 | `src/components/search/SiteLogo.tsx` |

### F.3 剩余图标：去 Material Symbols 找

| 用途 | Material Symbols 候选 |
|---|---|
| 顶栏声音外放开关（开/关） | `volume_up` / `volume_off` |
| 点赞 / 点踩 | `thumb_up` / `thumb_down` |
| 朗读（若 `volume_up` 与顶栏开关撞车） | `headphones` 或 `record_voice_over` |
| 应用启动图标 | `chat_bubble` / `forum` / 自绘几何标记（**不可用鲸鱼**） |
| 首页中心图标 | 与启动图标同源，或直接**省略**（只留问候语，见 §12.1） |

> 风格统一手段：Material Symbols 默认线宽与 chat-demo 接近；统一取 **24dp viewBox、stroke/weight 400、圆头端点**，视觉上就能混用。**不要**试图复刻鲸鱼。

### F.4 移植做法（半天可完成）

1. 从 `icons.tsx` 取每个 `<path d="...">`（或 `<path>`/`<circle>`/`<rect>` 组合）；
2. 转成 `ImageVector.Builder(name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)`，逐条 `path { moveTo/lineTo/curveTo/close }`（或用社区工具把 `PathData` 字符串批量生成）；
3. `fill = null`、`stroke = SolidColor(CurrentColor)`、`strokeLineWidth = 1.5f`、`strokeLineCap = Round`，以支持主题色与深浅色；
4. **在文件头保留 MIT 归属注释**：`Derived from deepseek-ai/chat-demo (MIT). Copyright (c) DeepSeek.`（M9-3 的 NOTICE 一并致谢）；
5. Material Symbols 侧按 Apache-2.0 处理，无需逐个署名，但在 NOTICE 中列明来源。

---

> **下一步**：本文件与 01 号文档一起构成开工前基线。确认后按 §9 排期从 M0-1 开始；**Spike 必须先做**，其结论可能影响 §2.2 的 LaTeX/Mermaid 选型。

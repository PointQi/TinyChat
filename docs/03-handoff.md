# 交接与续作指引（上下文压缩后先读本文）

> 用途：**上下文一旦被压缩/新开 session，先读这一篇**，再按需跳转 01 / 02。
> 本文只写"不可丢失的事实 + 一手报文 + 下一步动作"，不重复论证。
> 最后更新：2026-11-17

---

## 1. 当前状态

> 这张表按 **2026-11-19 的代码扫描** 重写（不是按计划勾选）。判断依据是代码里有没有实体：能力位/枚举/注释不算「已做」。
> 上一版表格长期只记了 M1/M2，导致后续会话误判过两次（把已做完的引用角标、联网搜索当成待办）。

| 里程碑 | 状态 | 证据 / 缺口 |
|---|---|---|
| 调研 / 决策 / 规划 / Spike | ✅ | 01、02、04 号文档；19 项决策全部拍板 |
| **M0 骨架** | ✅ | 模块划分、设计 token、Gradle 版本目录；图标仍是自绘占位（决策 15 暂缓） |
| **M1 聊天内核** | ✅ | M1-1 ~ M1-13；真机验过发送/停止/编辑重发/重新生成/版本切换/继续生成/抽屉 |
| **M2 渲染** | ✅ 2026-11-19 | M2-1/2、M2-3（抖动三坑）、M2-4、M2-5/M2-6、M2-7、M2-8、M2-9 全部落地并真机对照；宽公式「自然宽度 + 左贴边 + 横向滚动」已对照（`6248c97`） |
| **M3 联网** | ✅ 已真机在用 | `server_tool_use` / `web_search_tool_result` 解析、搜索阶段碎片与卡片、来源胶囊 + 来源页、「N 个网页」聚合、`deepseek_web_search` / `deepseek_read_url` 声明；工具报文见 §6 |
| **M4 附件** | ⬜ **未接线** | 只有 `FilesApi` / `FileUpload` 能力位与数据结构；**没有**选择器（`ActivityResultContracts` / `GetContent` / `OpenDocument` 各 0 处）、没有上传调用（`uploadFile` 0 处）、没有消息内文件卡 |
| **M5 语音** | ⬜ 未实现 | `Asr` / `SpeechRecognizer` / 录音相关 0 处；TTS 只有 `Provider.kt` 的能力位与 `TtsVoice` 枚举（决策 3），**没有播放实现** |
| **M6 数据管理** | 🚧 基本在 | v3 导入导出已实现（导出 27 处、导入 8 处，流式 ZIP + SAF）；「3340 会话 100% 往返」的验收还没写进记录 |
| **M7 用量与费用** | 🚧 大半在 | `Usage` / `Cost` / 余额接口都在（`余额` 8 处）；**上下文饼图未做**（`PieChart` / `饼图` 0 处） |
| **M7.5 提示词** | 🚧 基本在 | 预设体系在（`Preset` 165 处、`内置预设` 4 处）；三种组合模式与会话快照固化待对验收 |
| **M8 打磨** | 🚧 部分 | 设置页在（`SettingsScreens.kt`）；**分享出图、i18n、引导页都没做**（`ShareImage` / `I18n` / `Onboarding` 各 0 处） |
| **M9 发布** | ⬜ 未开始 | 签名仍是 debug（决策 14，`M8-6` 才换 Keystore）；`NOTICE` 0 处 |
| 阻塞项 | 无 | Key 走 `.local/dev-key.txt`（debug 注入） |
| 下一步 | — | 建议顺序：M4 附件接线 → M5 语音 → M8 分享出图 / i18n → M9 发布 |


---

## 2. 文件地图（含一手参考物）

```
E:\code\TinyChat\
├── docs\
│   ├── 01-feasibility-and-plan.md     调研、可行性、API 能力、决策
│   ├── 02-dev-plan-and-ux-spec.md     WBS 60 项、SQL schema、协议映射、交互规格
│   ├── 03-handoff.md                  ← 本文
│   ├── 04-spike-report.md             ⭐ Spike 实测结论（工具链版本 / 渲染 API / 选型证据）
│   └── screenshots\                   M0 实拍：浅色/深色/抽屉/设置
├── app\                               M0：单 Activity + NavHost + 抽屉
├── core\model\                        纯 Kotlin 领域模型（Provider/Capabilities）
├── core\designsystem\                 色板 / 字体 / 形状 / 主题 / 占位图标
├── core\ui\                           通用 Compose 组件
├── feature\{chat,sessions,settings}\  M0 各一个页面
├── gradle\libs.versions.toml          版本目录（工具链版本已由 Spike 钉死）
├── .github\workflows\ci.yml           assemble + unit test + lint
├── .gitignore                         temp-doc/ 与构建产物已排除
├── temp-doc\                          ⚠️ 不进 git（outline 备注 4）
│   ├── outline.md                     原始需求（77 行）
│   ├── app-preview\                   10 张官方 App 截图（UI 唯一视觉基准）
│   ├── deepseek_data-2026-11-16.zip   官方导出样本：3340 会话 / 23580 消息 / 79 图 / 87MB
│   ├── base.apk                       官方 App 2.8.0(297)，**建议不用**（见 §5）
│   └── chat-demo-ref\                 ⭐ 官方演示客户端源码（MIT），最重要的实现参考
└── （仓库外）E:\code\TinyChat-spike\   Spike 丢弃型工程，含 out\sse.txt / out\zip.txt 原始输出
```

**必须钉住的版本**：`deepseek-ai/chat-demo` @ **`5d5dd754ebc1948e977ddb880a9eb5aa25f37b4c`**（`v0.1.1`, 2026-11-13, 155 个源文件）。
若该目录丢失，用一条命令重建：

```bash
git clone --depth 1 https://github.com/deepseek-ai/chat-demo.git temp-doc/chat-demo-ref
```

**查实现细节时按这个顺序看 chat-demo**：

| 要找什么 | 文件 |
|---|---|
| 请求体怎么拼 | `src/services/chat/store.ts`（`streamReply`，约 960–1010 行）+ `src/services/api/types.ts` |
| SSE 怎么解析 | `src/services/api/sse.ts`、`parseEvent.ts` |
| 事件怎么并成消息 | `src/services/chat/reply.ts`（`applyEvent` / `settleReply`） |
| 上下文怎么回传 | `src/services/chat/reply.ts`（`toRequestMessages` / `toAssistantParam`） |
| 导入导出 | `src/services/chat/history/*.ts`（`schema.ts` 是校验真值） |
| 文案 | `src/i18n/zh.ts`（172 行） |
| 设计令牌 | `src/app/theme.css`（12KB，浅/深两套完整色板） |
| 缩写/结构 | `src/services/chat/history/README.md`（讲清了导入导出的所有边界） |

---

## 3. 17 项决策（速查，勿重新讨论）

| # | 决策 |
|---|---|
| 1 | 品牌 **TinyChat** + 自绘图标；保留设计语言（色板/圆角/字号/布局），**不出现 DeepSeek 名称与鲸鱼** |
| 2 | 服务商：**DeepSeek 官方 + 通用 OpenAI 兼容**（后者降级）；OpenCode Go 推迟 v1.1 |
| 3 | TTS **只用官方 2 音色** `stella`/`tide`；不接系统 TTS、不接第三方 |
| 4 | Token **启发式估算**（中文×0.6 / 其他×0.3）+ 上轮真实值校准；**不内嵌 tokenizer** |
| 5 | **不做** 客户端侧搜索；非 DeepSeek 服务商把搜索置灰 |
| 6 | 发布：**GitHub Releases 直发 APK**；不上 Google Play |
| 7 | 导出默认 **兼容模式**（纯 v3，提示词/用量不随包走） |
| 8 | 提示词入口**只在** `设置 → 应用 → 系统提示词` |
| 9 | 预设 **5 个**：默认（官方）/ 无提示词 / 角色扮演骨架 / 翻译润色 / 学习辅导 |
| 10 | 全局改提示词 **不影响已有会话**（会话创建时快照，只读） |
| 11 | **不做**「纯对话模式」，**不暴露** `temperature` |
| 12 | 角色扮演预设 = **中性骨架**（人设/世界观/风格/禁止事项四槽位 + `{{}}` 占位） |
| 13 | `恢复默认` = 一键还原官方原文；默认预设只读、不可删改 |
| 14 | **附件图标用回形针**（不改成 ⊕ 圆加号） |
| 15 | **图标素材已齐，不再"暂缓"**：chat-demo 的 41 个图标全部机械移植到 `ChatDemoIcons.kt`（路径未改），只有分享、上箭头、返回三个是手绘；总览见 `docs/design/icons-contact-sheet.png` |
| 16 | **没有启动页**：冷启动直达主界面；无 Key 用行内提示条，不做引导页/onboarding |
| 17 | 首页中心图标**可省略**（鲸鱼不可用，自家图标未定）；M0–M2 先只放问候语 |
| 18 | Markdown 流式**只用** `rememberStreamingMarkdownState` + `append()`；禁用 `Markdown(String)` 重载与"逐帧换 state 驱动"（实测 42–61 次高度回缩，见 04 §3） |
| 19 | 公式与图表统一走 **WebView 宿主 + 离线 KaTeX / mermaid.js**（共用一套高度回传与深色主题；纯 Compose LaTeX 库已排除） |
| 20 | **界面文案禁用「·」作分隔符**（改用逗号/空格/竖线或拆行）；设置里系统提示词那行显示 `当前预设：默认` / `当前预设：自定义`，不写"5 个预设"这类说明 |
| 21 | 依赖注入用**手写 `AppContainer`**，不引 Hilt（AGP 9 内置 Kotlin 下 Hilt 插桩未验证，且 M1 只有 3 个仓库；将来要换 Hilt 只是改构造参数） |
| 22 | `messages.id` 用**全局 `AUTOINCREMENT`**，不做「会话内自增」；导入时按包内 id 重映射。v3 只要求会话内唯一且 parent 自洽，全局 id 满足 |

---

## 4. 证据分级（**防止续写时把推断当事实**）

### 4.1 一手证实（可直接照做）

| 结论 | 依据 |
|---|---|
| 官方演示客户端的 system prompt 只有一句：`该助手为DeepSeek，由深度求索公司创造。` | chat-demo `store.ts:38`（全仓库唯一 system 字符串） |
| 主链路端点 `POST https://api.deepseek.com/anthropic/v1/messages`，鉴权 `x-api-key` 或 `Bearer` | 官方文档 + chat-demo `client.ts` |
| 服务端工具：`tools:[{type:"deepseek_web_search",name:...},{type:"deepseek_read_url",name:...}]`；搜索与抓取**都在服务端执行** | 官方 [联网工具](https://api-docs.deepseek.com/zh-cn/guides/web_tools) |
| 扩展能力（搜索/读链接/文件/图片/标题）**仅 `deepseek-flash` 支持**，`deepseek-v4-pro` 不支持 | 官方文档两处均写"请使用 deepseek-flash" |
| 思考强度：Messages 用 `output_config:{effort:low/high/max}` | 官方 [思考模式](https://api-docs.deepseek.com/zh-cn/guides/thinking_mode) |
| 声明了工具 / `safety_check:high` / `title` 后，`temperature`、`top_p`、`stop_sequences` 等**被忽略** | 官方 [被忽略的参数](https://api-docs.deepseek.com/zh-cn/guides/build_a_chatbot#ignored-parameters) |
| 模型上下文 1M、最大输出 384K；价格与峰谷时段 | 官方 [模型 & 价格](https://api-docs.deepseek.com/zh-cn/quick_start/pricing) |
| 导出格式 `export_format_version: 3`，条目顺序与字段 | chat-demo `history/format.ts` + 样本 ZIP 实测 |
| 导入时 **任何未识别 ZIP 条目 → 整包失败**；**session JSON 内未知字段 → 静默丢弃** | chat-demo `importArchive.ts` / `schema.ts` 源码 |
| `pinned` 在 chat-demo 导入时被硬编码为 `false` | `schema.ts` 中 `readSessionIndex` / `readSessionFile` |
| OpenCode Go 必须带 `x-opencode-session`（缺失 400 `MissingSessionID`）、base URL `/inference/go`、UA 用自己的 | 官方 + AstrBot PR #10005 + dsh-opencode-go |

### 4.2 推断 / 未验证（**不要当结论用**）

| 事项 | 状态 |
|---|---|
| 官方 App 是否另有更长的 system prompt | **未知**。导出数据不含 system；只能逆向，不做 |
| 网传"DeepSeek 系统提示词全文"（繁体 20 条规则、V2/R1 英文版） | **不可信**，内容与当前事实矛盾（称 128K、知识截止 2024-07） |
| ~~LaTeX 渲染选型~~ | ✅ **已定稿（04 §4）**：纯 Compose 库依赖链断裂（`latex:latex-base:unspecified` → 404），改用 **WebView + 离线 KaTeX**；Mermaid 共用同一宿主 |
| Mermaid 纯 Kotlin 方案 `huarangmeng/diagram` | 仓库无 LICENSE，不可依赖（方案已改 WebView） |
| `io.github.huarangmeng:codehighlight:1.0.7` | 可用候选（Maven 上已到 1.0.7），**M2-4 开工时验证** |
| Messages 协议下原生 JSON Output 与联网工具的兼容性 | 文档未写，需实测（v1.1 议题） |

### 4.3 环境事实（已在本机核对 + Spike 实测修正）

**机器**：JDK 21.0.4 Dragonwell · **系统 Gradle 8.4（太旧，别用）** · Android SDK 在 `%LOCALAPPDATA%\Android\Sdk` · `ANDROID_HOME` 未设置（用 `local.properties`）· git 分支 `master` 无提交 · **没有 cmdline-tools / sdkmanager**（也无 Android Studio）· 有模拟器 `Pixel_3a_API_34_extension_level_7_x86_64` 与 system-image android-34。

**Spike 实测可用的一套工具链（M0 照此，见 04 §5）**：

```
Gradle 9.9.1 · AGP 9.5.1 · Kotlin 2.4.21 · compileSdk 37 · Compose BOM 2026.09.00
```

必须知道的四条：

1. **AGP 9 起内置 Kotlin 支持**，再应用 `org.jetbrains.kotlin.android` 会直接报错要求移除；
2. 插件版本全部放 `settings.gradle.kts → pluginManagement.plugins {}`，根工程 `apply false` 统一解析，否则报 `Could not generate a decorated class ... BaseVariant`；
3. **Compose 1.12.1 强制 compileSdk ≥ 37**；本机原来只有 34/36，`platforms\android-37.0` 是**手工装的**（zip 内顶层目录就叫 `android-37.0`，且 `source.properties` 需改成 `ApiLevel=37` + 补 `MinorApiLevel=0`）——**M0-1 必须补装 cmdline-tools 用 `sdkmanager` 正规装**；
4. Gradle 分发包下载不稳（`services.gradle.org` 超时、腾讯镜像 zip 被截断），wrapper 要设 `networkTimeout=120000` 并核对 sha256。

---

## 5. 硬约束（写代码时不可违反）

1. **不逆向 `base.apk`**。理由：官方 API 文档 + chat-demo MIT 源码已覆盖全部需求；逆向可能违反其用户协议，且 APK 内含他人账号残留风险。
2. **导出不得新增 ZIP 条目**，也不得在 session JSON 里塞扩展字段（v1 导出=兼容模式）。
3. **`READ_LINK.signature` 必须原样回传**，否则多轮丢网页上下文。
4. **历史 thinking 不回传**（除本轮续写）。
5. **一旦收到过 `content_block_start` 就不得自动重试**（防重复计费）。
6. **`stop_reason=refusal` 时丢弃已下发的 thinking/text**，只留工具块。
7. **不在导出中写 API Key / 设置 / 用量**。
8. `system` 快照只读；改全局提示词不得回写已有会话。
9. UI 不得出现 DeepSeek Logo/名称；分享图页脚放 TinyChat + 免责声明。
10. 不用 `androidx.security-crypto`（已 deprecated）。

---

## 6. 关键报文（原样留存，避免重新翻文档）

### 6.1 一次完整请求（Messages）

```http
POST https://api.deepseek.com/anthropic/v1/messages
Content-Type: application/json
x-api-key: ${DEEPSEEK_API_KEY}
```
```json
{
  "model": "deepseek-flash",
  "system": "该助手为DeepSeek，由深度求索公司创造。",
  "thinking": { "type": "enabled" },
  "output_config": { "effort": "high" },
  "tools": [
    { "type": "deepseek_read_url",   "name": "deepseek_read_url" },
    { "type": "deepseek_web_search", "name": "deepseek_web_search" }
  ],
  "safety_check": { "level": "high" },
  "title": { "enabled": true },
  "messages": [
    { "role": "latest_reminder", "content": "2026-11-17,星期一,浙江,App,中文" },
    { "role": "user", "content": "DeepSeek V4.1 有哪些更新？" }
  ],
  "stream": true
}
```

> `latest_reminder` 必须**紧挨在它所属于的 user 消息之前**，`content` 必须是字符串；格式错 → `400`，非字符串 → `422`。`language` 取值必须来自官方 70+ 语言表（**不要自造**，如 `中文`、`English`、`日本語`）。

### 6.2 流式事件顺序（官方示例）

```
message_start
content_block_start (thinking) → thinking_delta… → signature_delta → content_block_stop
content_block_start (server_tool_use)        → content_block_stop
content_block_start (web_search_tool_result) → content_block_stop
content_block_start (thinking) … → content_block_stop
content_block_start (text) → text_delta… → citations_delta → content_block_stop
content_block_start (text) → text_delta… → content_block_stop
message_delta
message_stop
```

### 6.3 各块的真实形状

```jsonc
// message_start（usage 只有第一步）
event: message_start
data: {"type":"message_start","message":{"id":"b161a9be-...","type":"message","role":"assistant",
  "model":"deepseek-flash","content":[],"stop_reason":null,"stop_sequence":null,
  "usage":{"input_tokens":40,"cache_creation_input_tokens":0,"cache_read_input_tokens":0,
           "output_tokens":0,"service_tier":"standard"}}}

// 服务端工具调用（原子下发，content_block_start 即带完整 input）
{"type":"content_block_start","index":1,"content_block":{
  "type":"server_tool_use","id":"srvtoolu_ds_00_b2baebaf...","name":"deepseek_web_search",
  "input":{"action":"search","queries":["DeepSeek V4.1 new features","..."]},
  "caller":{"type":"direct"},"kind":"deep_search"}}

// 搜索结果
{"type":"content_block_start","index":2,"content_block":{
  "type":"web_search_tool_result","tool_use_id":"srvtoolu_ds_00_...",
  "content":[{"type":"web_search_result","title":"...","url":"https://...",
    "encrypted_content":"...","page_age":"2026-10-16T16:00:00+00:00",
    "snippet":"...","site_name":"DeepSeek","site_icon":"https://cdn.deepseek.com/site-icons/deepseek.com"}],
  "kind":"deep_search"}}

// 正文 + 引用（引用走 citations_delta，引用后正文另起新 text 块）
{"type":"content_block_delta","index":15,"delta":{"type":"citations_delta",
  "citation":{"type":"web_search_result_location","url":"https://...","title":"...",
              "encrypted_index":"","cited_text":"..."}}}

// 结束（用量以这里为准，不要与 message_start 相加）
{"type":"message_delta","delta":{"stop_reason":"end_turn","stop_sequence":null,"title":"DeepSeek V4.1 更新"},
 "usage":{"input_tokens":9078,"cache_creation_input_tokens":0,"cache_read_input_tokens":4096,
   "output_tokens":1686,"service_tier":"standard",
   "server_tool_use":{"web_search_requests":1,"web_fetch_requests":0,
                      "tool_search":2,"tool_fetch":0,"tool_image_search":0}}}

// 失败（HTTP 仍是 200，之后不再有 message_delta / message_stop）
event: error
data: {"type":"error","error":{"type":"api_error","message":"The model failed to generate a complete response. Please retry."},"usage":{...}}

// 拒答
{"stop_reason":"refusal","stop_details":{"type":"refusal","category":null,"explanation":null}}
```

**action → kind 对照**：`search`→`search`/`deep_search`；`open_page`→`deep_search`；`find_in_page`→`deep_search`；`read_link`→`read_link`。图片搜索 kind 为 `image_search` / `deep_image_search`（无 query，结果多一个 `image_url`）。

### 6.4 阅读链接块（**必须回传 signature**）

```jsonc
{"type":"web_fetch_tool_result","tool_use_id":"srvtoolu_rl_01_9c1d...",
 "content":{"type":"web_fetch_result","url":"https://example.com/a",
   "content":{"type":"document","source":{"type":"text","media_type":"text/plain","data":""},
              "title":"页面标题"},
   "published_at":"2026-01-01"},
 "kind":"read_link","signature":"q3J0b1RmZ1c4cE1x..."}
```
失败时 `content` 为 `{"type":"web_fetch_tool_result_error","error_code":"url_not_accessible|url_not_allowed|unavailable"}`，且**无 signature**。

### 6.5 文件上传与引用

```http
POST https://api.deepseek.com/anthropic/v1/files
x-api-key: ...
anthropic-beta: files-api-2025-04-14
Content-Type: multipart/form-data   # purpose 必须排在 file 之前
```
`purpose`：`text_recognition`（仅文字）/ `rich_layout_recognition`（含图版式）/ `user_data`（txt/md/csv/json/代码/XLSX/EPUB，不计费）。
对话中引用（需带 `anthropic-beta: files-api-2025-04-14`）：

```json
{"role":"user","content":[
  {"type":"document","source":{"type":"file","file_id":"file-api-xxxxxxxx"}},
  {"type":"image","source":{"type":"file","file_id":"file-image-xxxxxxxx"}},
  {"type":"text","text":"总结这份文档的要点。"}
]}
```
纯文本也可不走上传：`{"type":"document","source":{"type":"text","media_type":"text/plain","data":"..."}}`。
限制：单文件 100MiB / 1000 页；上传 120s、解析 120s；每请求 ≤20 文档且合计 ≤8MiB 文本。

### 6.6 其他端点

| 用途 | 请求 | 要点 |
|---|---|---|
| 校验 Key | `GET /models`（`Authorization: Bearer`） | 不计费 |
| 余额 | `GET /user/balance` | 返回 `is_available` + `balance_infos[{currency,total_balance,granted_balance,topped_up_balance}]`，字符串金额 |
| ASR | `POST /v1/tools/asr`（multipart `file`） | 16-bit PCM WAV 或 Ogg Opus；≤8MiB、≤120s；返回 `{text,duration_ms}`；不支持流式 |
| TTS | `POST /v1/tools/tts` | `{text,voice_id:"stella"\|"tide",format:"wav"\|"pcm"}`；wav 或 24k/16bit/mono |
| TTS 流式 | `wss://api.deepseek.com/v1/tools/tts` | 语音边生成边播；会话 ≤30 分钟 |

### 6.7 计费口径（写 `CostCalculator` 时照此）

```
cost = input_tokens/1e6 * P_miss
     + cache_read_input_tokens/1e6 * P_hit
     + output_tokens/1e6 * P_out
     + (tool_search + tool_fetch + tool_image_search) * 0.005   // 元/次
```
价格（元/百万 token，空闲/高峰）：

| 模型 | 入(命中) | 入(未命中) | 出 |
|---|---|---|---|
| deepseek-flash | 0.02 / 0.04 | 1 / 2 | 4 / 8 |
| deepseek-v4-pro | 0.15 / 0.30 | 4.5 / 9 | 13.5 / 27 |

高峰 = 北京时间周一至周五 09:00–12:00、14:00–18:00（法定节假日除外）；其余为半价。
`cache_creation_input_tokens` **恒为 0**。搜索按**查询词条数**计费（3 个词 = 3 次）。

### 6.8 导出格式（v3）条目顺序

```
manifest.json                                   {"export_format_version": 3}   ← 必须是第一条
user.json                                       （导入时跳过）
chat_sessions/{session_id}.json                 会话（含消息树，id 会话内自增）
chat_session_index.json                         [{id,title,pinned,message_count,inserted_at,updated_at}]
chat_sessions/{session_id}_files/{file_id}_{name}
```
碎片枚举：`REQUEST` `RESPONSE` `THINK` `TEMPLATE_RESPONSE` `TIP` `CONTENT_RISK` `PARTIAL_FILE_READ` `TOOL_SEARCH` `TOOL_OPEN` `TOOL_FIND` `READ_LINK` `FILE` `TOOL_IMAGE_SEARCH`。
消息状态：`WIP` `FINISHED` `CONTENT_FILTER` `FAILED` `INCOMPLETE` `CONTEXT_LENGTH_EXCEEDED` `TIMEOUT`。
**样本实测分布**：`CONTENT_RISK` 4411 条全部是同一句英文免责声明（直接丢弃）；`TOOL_SEARCH` 1503 / `TOOL_OPEN` 2366 / `THINK` 13063。

---

## 7. 下一步动作（按顺序，勿跳步）

> ⚠️ 本节是 **历史执行记录**：按当时计划逐条勾选，覆盖 M0 → M2 阶段。**"现在做到哪"以 §1 为准**；
> M3 联网之后的工作没有回填到这里，别把这里的 ✅/⬜ 当成当前进度。

### Step 0 — Spike（3 天，丢弃型工程，不进主仓库）— ✅ 已完成 3/4

结果详见 **04-spike-report.md**。原始输出：`E:\code\TinyChat-spike\out\{sse,zip}.txt`。

| # | 验证 | 结果 |
|---|---|---|
| 1 | 手写 SSE 解析器 | ✅ 离线固件 **40/40 通过**；在线真打**待 API Key** |
| 2 | 流式 Markdown 渲染 | ✅ **决定性**：流式状态 API 0 次回缩/1.2× 时长，另两种写法 42–61 次回缩/2.0–2.3× |
| 3 | 公式 + 图表选型 | ✅ 定稿 **WebView + 离线 KaTeX/mermaid**（纯 Compose LaTeX 库依赖链断裂） |
| 4 | 87MB 导出包解析 | ✅ **PASS，余量大**：1.07s（限 25s）/ 43MB（限 150MB）/ 0 错误 |

> 唯一遗留：在线 SSE 需要用户提供 DeepSeek API Key，命令见 04 §1.2。

### Step 1 — M0（3.1 人日）— ✅ 已完成（2026-11-17）

产出结构（全部可编译，`./gradlew assembleDebug test lint` 全绿）：

```
app/                    单 Activity：enableEdgeToEdge + TcTheme + NavHost + 抽屉
core/model/              纯 Kotlin：Provider / Capabilities / ModelInfo / ThinkingEffort / TtsVoice（含 5 条单测）
core/designsystem/       色板 / 字体 / 形状 / 主题 / 12 个占位图标
core/ui/                 顶栏 / 图标按钮 / 列表行 / 分组标题 / 行内提示条
feature/chat/            会话页（空态 + 输入区占位 + 无 Key 行内提示）
feature:sessions/        会话抽屉（假数据）
feature/settings/        设置树 + 服务商 / 数据管理 / 关于 三个占位页
```

验收证据（Pixel_3a_API_34 模拟器实拍，见 `docs/screenshots/`）：

- 冷启动**直达会话页**，没有自有启动页/引导页（`m0-chat-light.png` / `m0-chat-dark.png`）
- 浅色/深色双主题均正常
- 抽屉可开、设置可进、返回可用（`m0-drawer-light.png` / `m0-drawer.png` / `m0-settings.png`）
- 未配置 Key 时**只出现行内提示条**，无弹窗、不阻断主界面
- `:core:model:test` **5/5 通过**；`lint` **0 error**；APK 安装启动无崩溃

**与计划的偏差（需知悉）**：

| 项 | 偏差 | 原因 |
|---|---|---|
| M0-7 的 detekt | **暂未接入**，CI 只有 assemble + test + lint | detekt 插件版本未核实，避免在骨架阶段引入不确定依赖；M8 质量门禁再补 |
| M0-4 图标 | 12 个**自绘占位**（简单几何），非最终视觉 | 决策 15：图标方案暂缓 |
| M0-5 桌面图标 | 一个对话气泡矢量占位 | 不可用鲸鱼；自家图标未定 |
| M0-2 签名 | 只有 debug 默认签名 | release 签名属 M9-4，密钥不进仓库 |

### Step 2 — M1（13 人日）← 下一步

顺序：`M1-1 Room` → `M1-2 SseReader` → `M1-3 请求构建` → `M1-4 碎片归并` → `M1-5 状态流转` → `M1-6 会话列表` → `M1-7 发送/停止` → `M1-8 消息树` → `M1-9 继续生成` → `M1-10 上下文重建` → `M1-11 错误映射` → `M1-12 Files 客户端`。

- **M1-2 可直接照 04 号报告 §1 的边界清单实现**（离线固件已有 40 条断言可复用）。
- 验收需要 Key：连续对话、思维链、停止、重发、版本切换。

**M1 进展（滚动更新）**

| 任务 | 状态 | 备注 |
|---|---|---|
| M1-0 工程补底 | 进行中 | KSP 2.3.14 + Room 2.8.5 + DataStore 1.3.0 已接；`core:data` 就绪；`core:net` 与 Key 存储待做 |
| M1-1 Room schema + DAO + 迁移框架 | **完成** | 5 张表、schema v1 已生成入库、28 个单测全绿 |
| M1-2 `SseReader` | **完成** | 9 个用例，Spike 固件原样搬入；四类边界全覆盖 |
| M1-3 请求构建器 | **完成** | 10 条 golden JSON（对照 03 §6.1 原始报文）；`stream` 漏发的坑已修 |
| M1-4 碎片归并 | **完成** | 12 个用例：6 种块、结果乱序配对、孤儿结果、未配对 WIP、拒答与错误帧 |
| M1-5 流式落库与状态流转 | **完成** | 4 个用例；FINISHED/INCOMPLETE/CONTENT_FILTER/FAILED 全覆盖，取消也落 INCOMPLETE |
| M1-6 会话 CRUD + 列表 | **完成** | 抽屉真实列表（置顶/今天/昨天/7 天/30 天/更早）、重命名、置顶、删除、当前项高亮。分组逻辑在 `core/model/SessionList.kt`，7 个单测。**冷启动不再自动进上次会话**（见下） |
| M1-7 发送/停止接线 | **完成** | 真机跑通：`hi` → 思考 + `Hi! How can I help you today?`（`m1-reply.png`） |
| M1-7b 输入区与思维链对齐官方 | **完成** | 底栏改为 `[Flash 高] [地球] [回形针] [麦克风/发送]`；思维链折叠头 `已思考（用时 N 秒）`；顶栏标题；免责声明 |
| M1-7c 消息区滚动与输入法 | **完成** | 到底判定、流式跟随、拖动收键盘（按钮不收）；真机验证，见下方约定 |
| M1-7d 消息操作 | **完成** | 短按用户消息=编辑重发；长按用户=复制；长按 AI=复制整段/选择文本；界面只渲染当前分支 |

**M1-7b 的界面取舍（后续别做回去）**：

1. 底栏第一格是**模型与强度**，显示 `Flash 高`，不是"深度思考"字样（大纲第 45 条）；点开是下拉菜单，正式版换成半屏选择器；
2. 搜索 pill **只有图标不带文字**（为底栏省空间），开=品牌色、关=灰；
3. 发送键只在「有输入」时出现，替换麦克风位置；生成中同一个位置变停止；
4. 思维链：流式中 `正在思考`（品牌色图标）、结束后 `已思考（用时 N 秒）`（灰图标），箭头 ˅/› 表示展开态；
5. 底部免责声明 `回答由 AI 生成，仅供参考` 只在有消息时显示；
6. 思维链左侧是**原子图标**，路径数据**原样移植**自 chat-demo `src/components/base/icons.tsx` 的 `IconThinkOutline14`（MIT），落在 `core/designsystem/AtomIcon.kt`。**不要再手画替代品**——四条轨道 + 中心点的图形，手绘近似在 16dp 下会糊成一团（已经踩过一次）。

**已补齐（M1-7c，真机验证过）**：思维链展开后的**圆点 + 左侧竖线时间轴**、`搜索到 N 个网页` 步骤卡片（站点堆叠最多 3 个 + 溢出圆，可展开结果列表）、`已浏览 N 个页面`（连续浏览合并为一条）、引用角标数字、回到底部悬浮钮、模型与强度**半屏选择器**。

**仍待办**：站点图标目前是**首字母色块**（真 favicon 要等 M2 接 Coil）、引用角标点击与来源抽屉属 M3-3、搜索结果里的日期/摘要还没展示。

**消息区滚动与输入法（M1-7c，真机验证过；这几条是踩坑换来的，别改回去）**：

1. **「到底」= 末条底边与视口底边（`viewportEndOffset`）对齐**，不是「末条顶边对齐视口顶边」。`animateScrollToItem(lastIndex)` 是后者 —— AI 回答普遍比一屏高，点完按钮会停在这条消息的开头，下面还差一大截。
2. **不能用 `canScrollForward` 当作「到底」的判定**：底部 `contentPadding` 也算可滚动量，补完正文余量后它仍是 true。按它判断会在「补余量」与「跳到末条」之间反复，表现为**画面上下横跳 5 次后回到原位**（已实际发生）。
3. **底部留白必须用 `Modifier.padding`，不能用 `contentPadding`**：后者属于滚动内容，滚到底后还剩 12dp 要再滚一次，动画被切成两段，末尾那一下看着像**瞬移**。放在 Modifier 上，留白照旧，位移只有一段。
4. **流式跟随观察 `layoutInfo` 变化**（正文变长、思考区展开、键盘弹起都会触发重新布局），不要只依赖 `messages.size` —— 那样一次回答生成期间只滚一次。
5. 跟随用**非动画** `scrollBy` 贴底（动画追不上文字）；用户点「回到底部」才用动画。
6. **跟随开关只由用户自己的拖动改变**（监听 `interactionSource` 的 `DragInteraction`），程序滚动不参与判定，否则一次抖动就会把自动跟随关掉。
7. **上下划消息区时收起输入法**（`DragInteraction.Start` → `clearFocus` + `hide`）；**「回到底部」按钮不收键盘** —— 收起键盘会让视口在动画中途变大，输入框仍停在旧位置，实测出现**画面撕裂**。

**冷启动行为**（2026-11-18 **改定**，与 M1-6 当时相反）：启动**永远停在空白新对话**（问候语页），
历史全部在抽屉里。M1-6 曾实现「读 `updated_at` 最大的会话自动打开」，用户明确不要这个体验
（每次重进都被拽进上一条对话，还要手动退出）。会话仍然只在**首次发送**时落库。

| M1-8 消息树 | **完成** | 编辑重发（提问多版本）、重新生成（回答多版本）、‹ 2/3 › 版本切换；切换后指针落到目标分支叶子。分支算法在 `core/model/MessageBranch.kt`，6 个单测 |
| M1-9 继续生成 | **完成** | 末尾 ASSISTANT 预填续写（thinking 关）；三个前置条件不满足就不给按钮；真机验证 3471→11761 字、ord 递增追加 |
| M1-11 错误映射与重试 | **完成** | 11 类错误固定文案（`ErrorKind.userMessage`）；429 补一次、网络/5xx 最多两次、退避 1s/2s；**收到过帧就不再重试**（4 个 runner 用例锁死）；401 文案可点进设置 |
| M1-13 签名回传 | **完成** | 思考签名落 `messages.thinking_signature`；`read_link` 的 `server_tool_use` 调用块整块进 `ReadLinkPayload.call`，重建历史时以 `[正文, 调用块, 结果块(含 signature)]` 成对回传（2 个重建用例） |
| M1-12 Files 客户端 | **完成** | `FilesRequests` 拼 multipart（purpose 在 file 前）+ beta 头；`FilesClient.upload` 拿 `file_id`。**真实上传已验证**（`TINYCHAT_LIVE_TEST=1` 跑通，`user_data` 不计费）；5 个离线用例 |

**M1-10 上下文重建**：`core/net/context/ContextRebuilder.kt` + `ReminderBuilder.kt`，8 个用例。

| 规则 | 状态 |
|---|---|
| ① 沿当前分支线性回溯 | 完成（调用方按 root→leaf 传入） |
| ② 只保留 USER+可用 ASSISTANT 成对轮次 | 完成（孤立 USER、失败轮次整轮跳过） |
| ③ 可用判定（状态/拒答/模板块/空正文） | 完成 |
| ④ 历史只回传正文、思考不回传 | 完成（M1-13）：正文 + `read_link` 调用/结果成对回传（含 signature），历史思考不回传 |
| ⑤ reminder 快照去重 | 完成（`messages.reminder_snapshot` 落库） |
| ⑥ 末尾接本轮 USER | 完成（附件块待 M4） |
| ⑦ 继续生成补未完成 ASSISTANT | 完成：预填 assistant 末尾续写（M1-9） |

> **规则 ④ 的缺口要补**：现在碎片只存了结果侧（url/title/signature 在 results 里），无法还原
> `server_tool_use` + `web_fetch_tool_result` 这一对。补法是给 `READ_LINK` 增加
> `tool_use_id` 与调用块字段（仅本地，导出时仍需与 v3 形状兼容），列为 **M1-13**。

**数据库 v2（已迁移验证）**：`messages` 加 `reminder_snapshot`、`thinking_signature` 两列（仅本地、不导出）。
`MIGRATION_1_2` 在**带旧数据的模拟器上**实测升库成功、无崩溃，schema `2.json` 已入库。

**M1-1 的实测发现（已同步进 02 §3.1/3.2）**：

1. 真实数据里存在 `TOOL_IMAGE_SEARCH`（3 条），§3.1 原枚举漏了它，**已补为第 10 类**，否则导入这 3 条会丢；
2. `READ_LINK` 全库仅 3 条且 `results` 均为空数组，`signature` 的字段形状无法从数据实测 → 该 payload **整块按原始 JSON 保留**，保证「取到什么样、发回什么样」；
3. `FILE.files[]` 在导出里带显式 `null`（`export_path`/`width`/`height`），我们编码时省略 null 字段，语义等价；
4. 工具碎片的 `status` 实测三种值：`FINISHED` / `FAILED` / `WIP`，按字符串存，不做枚举硬判；
5. `CONTENT_RISK`（4411 条）确认全部是同一句免责声明，**不入库**，导入时直接丢。

**M1-1 与计划的偏差**：DAO 测试用 **Robolectric 4.18 跑 JVM 单测**（`@Config(sdk = [34])`），而不是 `androidTest`，因为 CI 只跑 `testDebugUnitTest`；session 列表索引按 (deleted, pinned, updated_at) **升序**建，混合方向索引 Room 表达不了，几千量级无影响。

**M1-3/M1-4 的实现约定（后续别改坏）**：

1. `MessagesRequest.stream` **不能给默认值** —— 请求体序列化用 `encodeDefaults=false`，带默认值会整字段省掉，请求静默退化成非流式（已由 golden JSON 测试锁死）；
2. `WireMessage.content` 是 `JsonElement` 而非 `String`，M4 的图片/文件块直接走数组，不用改协议层；
3. 碎片 `ord` 在**调用块到达时**就分配（不是结果到达时），保证导出顺序 = 流顺序；
4. 结果块先于调用块到达的极端情况进 `orphanResults` 暂存，调用块到达后立即补配对，**不允许出现重复碎片**；
5. 未配对的调用在流结束时落成 `WIP` 碎片（用户中途停止的形态）；
6. `stage_id` 暂用「已出现的思考块数量」近似，只为 M3-4 分组服务，不是协议字段。

**M1-5/M1-7 的实测记录**：

| 事项 | 说明 |
|---|---|
| 真实接口 | `LiveSmokeTest` 跑通（1.46s）+ 文件上传跑通。**默认跳过**，要跑设 `TINYCHAT_LIVE_TEST=1`；注意 Gradle 常驻守护进程不带新环境变量，要加 `--no-daemon` 才会生效 |
| 权限 | M0 的 Manifest 没有 `INTERNET`，真机一发就崩（`SecurityException` 直接打死 OkHttp 线程）→ 已补 `INTERNET` + `ACCESS_NETWORK_STATE` |
| 开发期 Key | debug 构建从 `.local/dev-key.txt` 注入 `BuildConfig.DEV_API_KEY`；`.local/` 已 gitignore。M8-6 换成 Keystore+DataStore |
| 渲染妥协 | 思维链目前是一行灰字全文，M2-7 改成可折叠的「思考了 N 秒」 |
| 默认强度 | 默认 `Flash 低`（对齐官方 App）：日常问答上更高强度换不来质量，却多花时间与思考 token |
| 上下文 | `ChatRepository` 目前是**朴素线性版**（USER/可用 ASSISTANT 纯文本），M1-10 补六条规则 |

### Step 3 起

见 02 §6 的 M2–M9 表格（60 个任务，含依赖与验收）。

**M2-1/M2-2 的实现约定（踩坑换来的，别改回去）**：

| 事项 | 结论 |
|---|---|
| 模块 | 新建 `:core:markdown`（AGP library + Compose），对外只有 `TcMarkdown(content, modifier, streaming)` 一个入口；渲染库类型全部 internal，不泄漏到 feature 层 |
| 流式 | `rememberStreamingMarkdownState()` + 逐片 `append()`；内容不再是「当前全文的前缀」时（重新生成得到更短答案）用 `key(generation)` 重建解析状态 |
| 帧合并 | `FrameMerger`：一次渲染耗时 ×2 为最小间隔，封顶 2s；`streaming=false` 时立刻补完不做节流。6 个单测（虚拟时钟） |
| **库的 Spacer 陷阱** | 库给**每个顶层节点**前插 `Spacer(padding.block)`，而 GFM 的 AST 在每两块之间夹 **2 个 EOL 节点** → 设 8dp 实际是 24dp（真机实测标题下方 54dp）。**`block` 必须置 0**，间距由各组件自己加，且渲染时跳过 EOL 节点 |
| 块间距 | 段落上下各 6dp；标题上 12–14dp、下 6dp；列表 `list=8dp`、项间 3dp；引用上下 8dp；表格/分隔线 8–14dp。列表项内部的段落间距**置 0**（`LocalBlockSpacing`），否则每项之间多出 12dp |
| 字体内边距 | 全部 Markdown 样式 `includeFontPadding = false`。安卓默认按字体度量在首末行上下各加一段，中文 16sp 每段多出约 13px，是「标题与正文空得离谱」的另一半原因 |
| 库的动画 | 库默认给每段文字挂 `animateContentSize()`，流式时与帧合并互相打架（内容「慢慢长出来」并伴随回缩）→ 传 `markdownAnimations { this }` 关掉 |
| 行内代码底色 | **不用**库的 `RoundedCornerSpanPainter`：它拿「行盒」（= lineHeight，28sp）当矩形，再用 top/bottomMargin 硬裁，首行与续行裁切量不同 → 出现「首行太矮把 l 切了、续行高到吃下一行」。改用自研 `TcInlineCodePainter`：高度 = 字号 ×1.35、底边在基线下 0.24×字号，圆角 4dp，左右内边距 0（等宽字形自带 side bearing，再补 dp 就松）；跨行时只有首段左、末段右是圆角 |
| 行内代码字号 | 14sp（官方 0.875em），定义在 `MarkdownTheme.InlineCodeFontSize`，画笔与排版必须用同一个值 |
| 代码块 | 自绘（M2-4）：顶部栏 `inlineCodeBg` 底色、左侧语言名（``` 后原文，空则只留复制）、右侧「图标+复制」（点了变「已复制」1.5s）；代码区 `codeBg` + 12dp 内边距 + 横向滚动 + `softWrap = false`；超过 20 行折叠成「展开全部（共 N 行）」，**流式期间不折叠**（`collapsedCode` 纯函数 + 5 个单测）。不做语法高亮（库无可用 KMP 实现，自研 tokenizer 暂缓） |
| 任务列表 | 库默认把勾选框画成等宽文字 `[x]`，不是真的框 → 自绘 16dp 圆角方框（选中品牌色填充 + 白勾） |
| 引用 | 库把竖线与正文都画成正文色（黑）→ 自定义 `blockQuote`：竖线三级灰、引用内正文/列表/标题整体转三级灰，代码块保持原样 |
| 列表符号 | 库默认 `"• "`（带空格），宽度随字号浮动 → 自定义 `BulletHandler` 返回 `"•"`，用 `markerModifier` 的 6dp 尾距控制间隙 |
| 版本 | 渲染库 `com.mikepenz:multiplatform-markdown-renderer:0.46.0` + `-m3`（已实测可解析、API 与本文一致） |
| 引用角标（M2-8） | `citations_delta` **不写进正文**，但必然紧跟被引用的那段、且与该段同属一个 RESPONSE 碎片 → 拼正文时在**该碎片末尾的非空白字符之后**记锚点（`ChatSource.anchor`），不用改协议、不用给导出加字段。落位规则「贴在所属段落末尾」：锚点常落在段落间的 `\n\n` 里，按文本节点插会整批丢失（实测 6 个全丢） |
| 角标外形 | 三圆一直角（官方 `border-radius: 6px 6px 6px 0`，左下角带尖），15sp 占位符 + 9.5sp 数字，`InlineCodeBg` 底；用 `InlineTextContent` + 段落里 `appendInlineContent(tag)` 实现（**别传 altText**，否则会把数字当文字插进去） |
| 来源胶囊 | 正文末尾：站点首字母堆叠（同站去重、最多 3 个）+ `N 个网页`，点击打开来源列表（`SourceSheet` 是 M3-3 的临时壳，M3-3 换成搜索结果抽屉） |
| 操作栏布局（M2-9） | 固定两行、官方版式：第一行 `复制` `朗读` `分享` 靠左贴边 + `重新生成` 靠右贴边；第二行 `‹ 2/3 ›` 靠左贴边 + `继续生成` 靠右贴边。`ActionIcon` 统一 40dp 触达 / 18dp 图形 / `LabelTertiary` 无底色（相邻图形间距 22dp）；`VersionSwitcher` 的箭头盒子也统一成 40dp，否则第二行左缘比第一行缩进 6dp |
| 操作栏可见性 | 抽成纯函数 `resolveFooterState(status, hasText, versionCount, isLatest, canContinue)`（`MessageFooterState.kt`），9 个单测钉死矩阵：WIP/FAILED/TIMEOUT 整栏不出；**INCOMPLETE 只要已有正文照给左三件**（用户明确要求，手动停止后内容仍可复制/朗读）；只有「正文为空」才只剩重新生成。**别把判断写回 Composable** |
| 操作栏动作 | 复制=本条回答纯文本（`onCopyMessage`，图标变对勾 1.5s，不弹 Toast）；分享=`ACTION_SEND` text/plain 系统面板（M8-2 换成出图抽屉）；朗读=M5-4 前的空占位（`onClick = {}`，点了无动作）；赞踩官方已确认不做 |
| 公式/图表宿主（M2-5/6） | 全应用**一个不挂窗口的 WebView** 出位图（`TcRenderHost`，LRU 24MB），列表里只放 `Image`：这样没有 WebView 存量、也不吞竖向手势。资源离线 vendor 进 `core:markdown/src/main/assets/`（katex 0.19.1 + 20 个 woff2 + mermaid 11.12.0，共约 3.2MB） |
| 宿主四个坑（都实测过） | ① **CSS 像素 ≠ 物理像素**：下发前除以 density、回传后乘回来，否则公式被放大 3 倍只画出两个符号；② **WebView 必须 attach**（挂进 decor 后 translate 到屏幕外），完全离屏的实例只光栅化默认视口，`draw()` 拿到的永远是旧内容；③ **背景要透明**：`setBackgroundColor(TRANSPARENT)` + 页面 `background:transparent`，否则位图整块白底；④ **改完尺寸要再量一次**：先量再 resize 会让公式重排、被裁掉半行，`tcMeasure()` 只增不减补两轮 |
| mermaid 缩放 | `useMaxWidth: false`（否则 mermaid 默认把图拉满容器，字号被放大）；显示端按位图长宽比 `aspectRatio` 等比铺满，超宽图缩到正文宽度 |
| 渲染实验室 | `app/src/debug/`（release 不打包）：`adb shell am start -n io.tinychat/io.tinychat.debug.RenderLabActivity`，含 5 类图、错误态样例、`--ez dark true` 深色开关与「测耗时」（`TcRenderLab.benchMath` 冷启/稳态各一轮） |

**M2-3 抖动（2026-11-18 完成，真机 Pixel，density 3.0）**

**验收工具**：`StreamingLabActivity`（debug 专用）。把一份 2600 字样本按 6 字/帧喂给 `TcMarkdown`，
用 `onSizeChanged` 记录每次高度，统计「高度回缩次数」（验收标准 0 次），并把首个回缩的内容位置写进
`cacheDir/stream.txt`：

```bash
adb shell am start -n io.tinychat/io.tinychat.debug.StreamingLabActivity
adb shell run-as io.tinychat cat cache/stream.txt   # frames=… drops=… maxDrop=… + firstDrop@cursor
```

**实测**：修之前 `frames=62 drops=1 maxDrop=72`；修之后 **`frames=59 drops=0`**。

| # | 坑 | 现象与根因 | 修法 |
|---|---|---|---|
| 1 | **setext** | 正文下一行来了 `---` / `===`，GFM 把上一行变成标题 → 字号、字重、块间距全变，出现 -72px 回缩 | `stabilizeSetextMarkers()`：把这类标记行**原地换成等长的 `***`**（仍是分隔线，但不会被当 setext）。长度不变，引用角标偏移不受影响。**流式与结束都生效**，否则收尾时又跳一次 |
| 2 | **段尾空行** | 内容以 `\n\n` 结尾时，那一行空白先被解析成一个空行；下一段文字到达后空行消失 → -24dp 回缩 | 喂解析器前先**扣住末尾连续的换行**（`trimEnd('\n')`），下一片文字到达时连同换行一起补；画面末尾不会出现「空行闪一下」 |
| 3 | key 稳定 | 解析状态每帧重建会整篇重解析（Spike 已证 42–61 次回缩） | 仍只用 `rememberStreamingMarkdownState()` + `append()`；只有「内容不再是从前的前缀」才 `generation += 1` 重建 |

> **取舍**（需要知情）：坑 1 的代价是——若 model 真按 setext 写法输出 `标题\n===`，会被渲染成「段落 + 分隔线」而不是大标题。理由：模型输出里几乎只用 `#` 系列，这种两行写法基本都表示分隔；而它带来的是一条稳定的「高度只增不减」保证。

**同一轮真机发现并修掉的显示 bug**：暗色模式下**含公式的段落与列表项文字是黑的**（公式却是白的）。
根因是我自定义的 `TcParagraph` / `InlineMathParagraph` / `ParagraphSlice` 里的 `Text` **没有显式传颜色**，
回落到 `LocalContentColor`（此处是 Material 的默认黑），而公式位图用的是 `palette.labelPrimary`。
→ 三处都改成「样式里带颜色就用它（引用块三级灰），否则 `palette.labelPrimary`」。
**教训**：这个模块里凡是自己 new 的 `Text` 都必须显式给 `color`，别依赖环境色。



**M2-5 收尾：行内公式（2026-11-18 完成）**

四个毛病全部修掉，且都不是「位图路线」的硬限制：

| # | 现象 | 根因 | 修法 |
|---|---|---|---|
| 1 | 公式整体比正文高一截 | 用 `AboveBaseline` 让**位图底边**贴基线，而位图上下留白并不对称（公式自身还带 3px 下边距） | `host.js` 用**零宽 inline-block 探针**量出真实基线；原生层让占位框只占「基线以上」那段（`Placeholder(height = aboveBaseline)`），位图用 `requiredSize` 从框顶按原尺寸往下画，下伸（分母、下标）落进行距。`Text` 必须 `overflow = Visible`，否则会被裁 |
| 2 | 公式偏小 | KaTeX 行内按 1em 排，数学字体的 x-height 天生比中文正文小 | `InlineMathFontScale = 1.16`，**放大字号重排**而不是缩放位图（缩放会糊） |
| 3 | 行距不为公式让位（用户重点问的） | Compose 行高由 `TextStyle.lineHeight` 定死，内联内容再高也不撑行 | 逐段动态行高：`max(原行高, 最高公式的上半 + 最深的下伸 + 3dp)`，**只有含公式的段落**变高（`InlineMathLayout.kt`，8 个单测） |
| 4 | `\sqrt` 根号整条消失 | `render.html` 里 `#stage svg { height: auto }` 是 **id 选择器**，优先级压过 KaTeX 的 `.katex svg { height: inherit }`，把那条 400em 宽的根号 SVG 的高度算成 0.04px | 改成 `#stage > svg`（只作用于 mermaid 顶层图） |

**顺带挖出的两个幽灵**（都会伪装成「公式里多了/少了东西」）：

| 现象 | 真相 |
|---|---|
| 每个含上下标的公式下方有一撮暗红色小图案 | 它是被切掉的**巨大 💣 emoji**：KaTeX 在 `.vlist-s` 里塞了 U+200B 零宽空格，WebView 的字体回退把它当成缺字画成彩色 emoji。渲染后遍历文本节点删掉 `\u200B` 即可 |
| 同一位置出现「💣 Syntax error in text」 | mermaid 的 `render()` 会往 body 里塞临时容器，**报错时不自清理**，它的报错图就画在 stage 下方。每次出图前把 body 里除 `#stage` 之外的残留节点清掉 |

**宿主改造：不再相信 DOM 尺寸（这条是本次最大的经验）**

| 坑 | 结论 |
|---|---|
| DOM 盒子 ≠ 墨迹 | KaTeX 的盒子比墨迹**小**：块级公式盒子只有真实高度的一半（实测 41/84），矩阵右侧少 20px，根号尾巴画在盒外。**不能按 DOM 尺寸裁位图** |
| 「所有后代 rect 求并集」也不行 | `.pstrut` 是 3em 高的隐形占位，会把盒子撑大（矩阵被裁成 14px 高）。已试过并回滚 |
| 唯一可靠做法 | `view.draw()` 画到 Bitmap 后**扫像素 alpha 求墨迹框**，再按这个矩形裁剪；块级公式横向保留整宽（居中靠 `text-align:center`，裁窄就贴左），行内则连横向一起裁 |
| 视口别开太大 | 初始视口 4000px 高时每次重光栅都要重画整块，单条从 ~430ms 涨到 1.8s。改成 800px 起步 → 扫墨迹 → 贴边才放大重画（≤3 轮） |
| 改完视口不能立刻 draw | WebView 分块光栅：`\frac{1}{2}` 只剩一个分母、多行 `aligned` 只画一条 8px 横带。改尺寸后要等一次 JS 往返 + `postVisualStateCallback`（官方「视觉状态已就绪」信号）才算稳 |

**M2-5 补：宽公式横向滚动（2026-11-19 完成）**

期望行为只有一条规矩：**位图有多少像素，屏幕上就画多大**（不改字号、不改行高）。于是「填不满整行 → 居中；填满或超出 → 左边缘顶住、右侧裁掉、手指左右滑动查看」。

| # | 现象 | 根因 | 修法 |
|---|---|---|---|
| 1 | 公式被缩成小字（约 0.57 倍，看着像 1/3 高） | 用 `Modifier.size(w, h)` 时被父约束压缩，压缩后 `Image` 又按 `contentScale=Fit` 再适配一次 | `Modifier.requiredSize`（无视父约束）+ 不用 `Image`，直接 `Canvas { drawImage(bitmap) }` |
| 2 | 公式**居中**且两边溢出屏幕、完全拖不动 | `val image = Canvas(...)` —— `Canvas` 在**赋值那一行就地发射**，成了外层容器的子项，而不是 `Box`/`Row` 的子项：滚动容器里空无一物，画布位置由祖先决定 | 抽成独立 Composable `TcMathImage(...)`，在 `Box`/`Row` 的 content lambda 里调用（**这个坑踩过两次**） |
| 3 | 宽公式第一帧是被切断的残图、且每次多一轮渲染 | 「先按容器宽画、发现墨迹贴右边缘再切自然宽度重画」的两步走 | 直接**一律按自然宽度出图**（`host.js` 里 `display:inline-block;white-space:nowrap`），一次到位；`clippedRight` 只留作诊断字段 |

最终实现（`TcMath.kt`）：`bitmap.width <= widthPx` → `Box(contentAlignment = Center)`；否则 `Row(fillMaxWidth().horizontalScroll(rememberScrollState()))` —— 用标准组件，**别手搓位移**（手搓没有惯性和越界回弹，试过，退步了）。

**排障方法（这次靠它才定位到，比截图猜快得多）**

`TcRenderLab` 临时加了一个 `probeSink`，把每个公式的 `Box`/`Canvas` 的 `positionInRoot()`、尺寸、`scrollState.value/maxValue` 追加写到 `cacheDir/math_probe.txt`：

```bash
adb shell run-as io.tinychat cat cache/math_probe.txt
# 正确：row=988x63 root=(48,681) scroll=0/882 ; canvas=1870x63 root=(48,681)
# 错误：box=988x63  root=(48,681)                ; canvas=1870x63 root=(-393,681)  ← 差 441 = 居中
```

配合两个手段交叉验证：截图里画一圈**洋红描边**量「实际绘制矩形」（和位图尺寸一比就知道有没有被缩放），以及 `input swipe` 前后对同一 y 带做像素 diff（`changed=0` 就是没滚动）。改进后已删除全部探针。

**性能（`TcRenderLab.benchMath`，Pixel 3a API34 模拟器）**：8 条样例冷启 `4008/4239/700/516/814/985/315/330ms`，稳态 **`337–433ms`**。模拟器只有约 2fps，按帧折算真机约 50–100ms/条。行内公式仍在**消息结束后**批量出图（流式期间是源码文字），所以不影响打字时的流畅度。

**渲染实验室新增两个排障入口**（都不用点屏幕）：

```bash
# 取回渲染后的 DOM + 尺寸 + 基线（查「图里少了什么」）
adb shell am start -n io.tinychat/io.tinychat.debug.RenderLabActivity \
  --es dump '\sqrt[3]{x+1}'            # 行内；加 --ez block true 变块级
adb shell run-as io.tinychat head -c 300 cache/dom.html
# 出图耗时（冷启 + 稳态各一轮），结果进 logcat
adb shell am start -n io.tinychat/io.tinychat.debug.RenderLabActivity --ez bench true
adb logcat -d | Select-String "bench cold"
```

其他已知项：深色主题已验（`--ez dark true`，公式转浅色、根号完好）；M2-3 抖动压测排在之后。

**滚动性能：measurement 口径与被证伪的“优化”（2026-11-18）**

| 构建 | 渲染 | Janky | 50th | 95th |
|---|---|---|---|---|
| debug | Markdown | 23.6% | 18ms | 38ms |
| debug | 纯文本（A/B） | 22.4% | 18ms | 36ms |
| **benchmark** | 纯文本 | 0.6% | 6ms | — |
| **benchmark** | Markdown（一次性渲染 + LRU 缓存） | 20–35% | 21–25ms | 40ms+ |
| **benchmark** | Markdown（流式通道，最终版） | **3.8–9.0%** | **11–12ms** | 19–27ms |
| 对照：系统设置列表 | — | 0.7–2.3% | 8ms | 15ms |

1. **绝不能用 debug 包判断卡不卡**：同一份代码 debug 50th 18ms、release 语义 6–12ms，差 2–3 倍。为此新增 `benchmark` 构建变体（`initWith(release)` + debug 签名 + 注入 Key），`./gradlew :app:assembleBenchmark` 直接装机量帧率。**所有“流畅度”结论以它为准**；
2. 测量方法：`dumpsys gfxinfo io.tinychat` 看 `Janky frames` / `50th` / `95th`；`reset` 后跑固定手势（1900↔500、150ms、3 个来回）；对照组用系统设置列表。EMUI 关掉了 `framestats`，拿不到逐阶段拆解；
3. **被证伪的方案（别再做）**：把「已结束的消息」改成 `parseMarkdown()` + `Markdown(state=…)` 一次性渲染，并加 LRU + 后台预热。同包 A/B 反而更差（20–35% vs 4–9%）。原因：一次性渲染把整棵 AST 的组合压进正在滚动的帧；缓存用 `@Synchronized`，后台预热解析大消息时会挡住主线程。**结论：所有消息都走流式通道，让内容经 StateFlow 错开一帧出现**；
4. 保留下来的两处微优化：EOL 节点过滤、`toString()` 用 `remember(snapshot)` 缓存；行内代码画笔只在正文含 ``` ` ``` 时才挂（省掉整条 extend/onTextLayout/drawBehind）。


---

## 8. 压缩后的自检清单

新 session 开工前，逐条回答：

1. 当前有没有写过代码？（答案应：没有，只有 docs）
2. 主链路端点是哪个？（`/anthropic/v1/messages`，不是 `/chat/completions`）
3. 联网搜索是客户端做的吗？（不是，服务端 `deepseek_web_search`）
4. `deepseek-v4-pro` 能用联网/文件吗？（不能）
5. 导出能不能加自定义 ZIP 条目？（不能，会整包失败）
6. 改全局提示词会不会影响老会话？（不会，快照只读）
7. 有没有暴露 `temperature`？（没有，且不要加）
8. 品牌能不能用 DeepSeek 名字/鲸鱼？（不能）
9. 第一个动作是什么？（Spike 四项，不是 `gradle init`）
10. 还缺什么信息？（**一个 DeepSeek API Key**——Spike 第 1 项与 M1 验收要用；其余信息齐了）

---

## 9. 待用户确认

1. **是否有可用的 DeepSeek API Key**：有则 Spike 四项全跑；没有则先做第 2/3/4 项，第 1 项用 `parseEvent.ts` 离线样本兜底。
2. **首页中心图标位**：M0–M2 先只放问候语（决策 17），可接受则不必再问。

---

## 10. 官方文档索引（需要原文时直接取）

| 主题 | URL |
|---|---|
| 构建 Chatbot（总纲，含被忽略参数表） | `https://api-docs.deepseek.com/zh-cn/guides/build_a_chatbot` |
| 联网工具（搜索/读链接/块格式/计费） | `https://api-docs.deepseek.com/zh-cn/guides/web_tools` |
| 思考模式（effort 映射） | `https://api-docs.deepseek.com/zh-cn/guides/thinking_mode` |
| 文件解析（purpose/限制） | `https://api-docs.deepseek.com/zh-cn/guides/document_parsing` |
| 用户元信息（latest_reminder + 语言表） | `https://api-docs.deepseek.com/zh-cn/guides/latest_reminder` |
| 会话标题 | `https://api-docs.deepseek.com/zh-cn/guides/conversation_title` |
| 内容安全（拒答语义） | `https://api-docs.deepseek.com/zh-cn/guides/safety` |
| TTS / ASR | `.../guides/tts` · `.../guides/asr` |
| Anthropic 兼容（字段支持矩阵） | `https://api-docs.deepseek.com/zh-cn/guides/anthropic_api` |
| 模型与价格 | `https://api-docs.deepseek.com/zh-cn/quick_start/pricing` |
| 余额 | `https://api-docs.deepseek.com/zh-cn/api/get-user-balance` |
| 全家桶发布公告 | `https://api-docs.deepseek.com/zh-cn/news/news261113` |

> 小技巧：这些 docusaurus 页面支持直接取 Markdown —— 在 URL 后加 `.md`（如 `.../guides/web_tools.md`），比抓 HTML 干净得多。

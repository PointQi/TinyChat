# M8-1 / M8-6 服务商区域重设计（已定稿并实施，2026-11-19）

> 状态：**已实施并验完**（2026-11-19）。用户拍板：① 保存即校验、失败也保留配置；② 第三方的「视觉」本轮不做（不留死开关）；③ OpenCode Go 本轮进。
> 规格已并进 `02 §12.6`，决策编号 33–36 在 `03`。本文保留设计与理由，实施中的偏差见文末「实施记录」。
> **2026-11-20 更新**：P 阶段的决策 47–51 推翻了本文 §11「不做 / 延后」里的三条（第三方逐模型价格表、
> 服务商级别的「设为默认」、删除服务商后的「换一家继续」），并解除了「内置服务商只读」的假设。
> 以 `02 §12.6` 与 `03` 的决策为准，本文对应段落只作历史记录。
> 触发原因：现有「保存并验证 / 测试连接 / 清除」三个按钮职责重叠；输入框为空时「清除」仍可点，
> 用户看不出它清的是「已存的 Key」而不是输入框内容。

---

## 1. 现状与问题

现在这一页（`ProviderSettingsScreen`）是 M7-6 的临时形态：DeepSeek 独家、一个输入框、三个文字按钮并排。

| 现象 | 真实语义 | 用户看到的问题 |
|---|---|---|
| 保存并验证 | 先 `GET /models` 校验，通过才写盘 | 和「测试连接」差在哪？ |
| 测试连接 | 有输入就校验输入，没输入就校验已存的 Key | 同一件事的另一个名字，还改行为 |
| 清除 | 清除**已保存**的 Key（与输入框无关） | 输入框明明是空的，为什么能清？清的是什么？ |

根因不是文案，是**把「配置」和「校验」当成两个并列动作**，又把「删密钥」塞进同一行动作里。
所以这次不是改词，是重排信息架构。

---

## 2. 设计原则

1. **动作有层级**：一屏只有一个主按钮（保存），其余是文字动作或列表行的次级动作。
2. **状态不是动作**：Key 好不好是「状态」——列表上是点，编辑页是一行字；不要让用户点按钮去"得到"状态。
3. **动词必须有对象**：「清除」不写对象就没人知道清什么。改成「清除已保存的密钥」，且只在**存在**已保存密钥时出现。
4. **删除有边界**：删除服务商 ≠ 删除密钥。前者影响会话能否继续，后者只影响本机凭据。
5. **不新增概念**：不引入"默认服务商"这种第四种状态——新会话用哪家，由聊天页当前模型决定。
6. **界面文案沿用现有要求**：不用「·」分隔、不加脚注小字、不出现 DeepSeek 品牌（服务商名与模型名除外）。

---

## 3. 信息架构

```
设置
└─ 服务商与 API Key                     ← 列表页（A）
   ├─ DeepSeek 官方            ●  [编辑]          （内置，不可删）
   ├─ OpenCode Go              ●  [编辑][删除]
   ├─ Siliconflow              ●  [编辑][删除]
   └─ ＋ 添加模型提供商                            ← 打开添加页（B）

添加页（B）= 顶部两个 tab
   ├─ 第三方模型提供商   → 目录里选一家（DeepSeek / OpenCode Go），只填密钥
   └─ 自定义模型 API     → ID、显示名称、地址、协议、密钥、模型目录

编辑页（B'）= 同一个表单，带出已有值；不显示 tab
   └─ 模型目录（内置模型/已拉取的模型） → 行内展开或「添加模型」
```

页面 A 的文案取舍：顶部一行说明「填入各服务商的 API 密钥即可使用其模型。」（照 DSH 的写法，
是页面级说明，不是脚注）；列表行**只有**名称、可选「自定义」标签、状态点、编辑、删除，不堆副标题。

---

## 4. 页面规格

### 4.1 A 列表

- 卡片高 60dp、圆角 12dp、1dp 边框 `borderL2`、间距 10dp；
- 左：名称 16sp/600；自定义服务商加「自定义」小标签（12sp、`BgLayer2` 底、8dp 圆角）；
- 名称右侧 8dp 状态点：绿 = 最近一次校验通过，灰 = 未校验，红 = 校验失败；
- 右：`编辑`（14sp、`labelSecondary`）；非内置行再加 `删除`（14sp、`danger`）；
- 点整行 = 编辑，和点「编辑」等价（触达更大）；
- 底部虚线卡片「＋ 添加模型提供商」，圆角 12dp、文字 15sp、居中；
- 空态：只有内置 DeepSeek 一行 + 添加卡片，不显示空态插画。

DeepSeek 官方**不可删除**（它是 TTS/ASR/余额的唯一来源，删掉这些功能一起失效）；其余行均可删。

### 4.2 B 添加（两个 tab）

**tab 1 第三方模型提供商**

- 一行说明「从内置目录中选择 OpenCode Go 等提供商，填入其 API 密钥即可使用。」；
- 「提供商」：**下拉**（不是长列表），选项来自目录：`deepseek`、`opencode-go`；
- 「API 密钥 ⓘ」：占位「留空使用已保存凭据」；右侧「显示/隐藏」；
- 「› 自定义设置」可折叠：地址、协议、模型目录（目录服务商这里只读，仅 OpenCode Go 的模型可重新拉取）；
- 底部：`取消`（文字）/ `保存`（品牌色填充，占满剩余宽度）。

**tab 2 自定义模型 API**

字段顺序（照 DSH 截图的顺序，不另创）：

| 字段 | 规则 |
|---|---|
| Provider ID | 自动由显示名称生成 slug，可改；`^[a-z][a-z0-9-]{1,31}$`；唯一；页面下方一行灰色说明「以小写字母开头的标识，用于在本机唯一标识该提供商」 |
| 显示名称 | 必填，≤24 字 |
| API 地址 | 必填，`https`；默认占位 `https://gateway.example/v1` |
| API 协议 | 下拉：`OpenAI Chat Completions` / `Anthropic Messages` |
| API 密钥 | 同上，占位「输入 API 密钥」/「留空使用已保存凭据」 |
| 模型目录 | 见 4.4 |

**保存校验**：名称/地址/协议/至少一个模型；ID 冲突 → 行内红字（不用弹窗）。

### 4.3 B' 编辑

- 标题 = 服务商名称；表单同 4.2，但**不显示 tab**、ID 不可改（自定义服务商的 ID 是密钥的键，改了历史会话就断）；
- 内置服务商（DeepSeek）表单：地址/协议/模型目录只读，只有密钥可改；
- 密钥区下方：
  - 情况一：没有已保存密钥 → 什么都不显示；
  - 情况二：有已保存密钥 → 一行 `清除已保存的密钥`（13sp、`danger`，不描边、不填充），点击弹确认：
    「清除后需要重新填写才能继续对话，历史记录不受影响。仅删除本机保存的密钥，不影响服务商账户。」
  - 状态行右侧 `重新校验`（13sp、`labelSecondary`）——只在有已保存密钥时出现；
- 状态行：`可用` / `未校验` / `Key 无效` / `网络不通，请稍后再试`（失败红字，成功后回到 `labelSecondary`）；
- 底部固定条：`取消` / `保存`。

**核心行为变化——保存即校验，但校验不阻塞保存**：

1. 点「保存」→ 立即写本机（表和加密存储）；
2. 同一时刻起后台跑一次 `GET {base}/models`；期间状态显示「校验中…」；
3. 结果只影响状态点与状态行：`可用`（绿）/ `Key 无效`（红）/ `网络不通`（灰）；**不撤销已保存的配置**；
4. 返回列表后，状态点直接反映结果；下次进列表页时对「未校验 / 失败」的行做一次轻量复检（30 秒内不重复）。

这样「保存」只有一个含义（存配置），「测试连接」这个动词彻底消失——离线也能把配置存好。

### 4.4 模型目录（自定义服务商）

- 头部一行：`模型目录`；右侧两个文字动作 `恢复默认模型`（仅目录服务商）、`获取可用模型`；
- 搜索框：带放大镜，占位「搜索模型」；
- 每行一条模型：左 `模型 ID` 输入、右 `显示名称` 输入（等宽两列；显示名称留空 = 同 ID）；
- 行右侧：`视觉` 复选框 + `›` 展开；展开区放「上下文窗口」「最大输出」，单位 k；
- 行左下角小垃圾桶删除该模型；
- 底部 `＋ 添加模型`（描边按钮，全宽）；
- `获取可用模型`：用当前表单里的密钥 + 地址请求 `GET {base}/models`，成功 → 弹底部选择面板（搜索 + 勾选 + 全选），失败 → 行内灰字说明（401 Key 无效 / 404 该地址不提供模型列表，请手动添加）；
- 模型为空时不允许保存，且页脚提示 `至少添加一个模型`。

### 4.5 删除服务商

列表行 `删除` → 确认弹窗：
- 标题 `删除 {名称}？`
- 正文 `该服务商下的会话仍可查看，但无法继续对话。本机保存的密钥会一并删除。`
- 确认按钮红色。
删除 = 硬删除（表 + 密钥），不留墓碑。

### 4.6 文案替换表（避免以后又长出名字）

| 旧 | 新 |
|---|---|
| 保存并验证 | 保存 |
| 测试连接 | （删除；编辑页状态行 + `重新校验`） |
| 清除 / 清除 API Key？ | 清除已保存的密钥 |
| 只保存在本机，不会上传。 | 密钥只保存在本机（页脚一行，只在编辑页出现） |

---

## 5. 数据模型

### 5.1 `providers` 表（Room，新迁移）

```sql
CREATE TABLE providers (
  id            TEXT PRIMARY KEY NOT NULL,   -- deepseek / opencode-go / 用户 slug
  kind          TEXT NOT NULL,               -- deepseek | opencode_go | openai | anthropic
  display_name  TEXT NOT NULL,
  base_url      TEXT NOT NULL,
  protocol      TEXT NOT NULL,               -- deepseek_messages | openai_chat | anthropic_messages
  models_json   TEXT NOT NULL,               -- List<ModelInfo>（能力位 + 上下文 + 输出上限）
  builtin       INTEGER NOT NULL DEFAULT 0,  -- 内置（deepseek）不可删
  sort          INTEGER NOT NULL,
  check_status  TEXT NOT NULL DEFAULT 'unknown', -- ok | failed | unknown
  check_message TEXT,
  checked_at    INTEGER,
  meta_json     TEXT                         -- kind 专属：OpenCode Zen 地址、UA、覆盖协议等
)
```

迁移时播种一行 `deepseek`：`kind=deepseek`、`base_url=https://api.deepseek.com`、
`protocol=deepseek_messages`、`models_json` 取 `KnownProviders` 的 flash + v4-pro、`builtin=1`。
现有 `sessions.provider_id` 全是 `deepseek`，天然对齐，**不动会话数据**。

### 5.2 密钥：每家一把

- 存储：仍是一份 `credentials` DataStore + 一把 Keystore 主密钥（`tinychat.api_key.v1`）；
- 键：`cipher.<providerId>`；旧的 `api_key_cipher` 首次读取时迁移成 `cipher.deepseek`，迁移后删旧键；
- 读出为空/解密失败 → 视为未配置（沿用现有行为）；
- 清除某家只删 `cipher.<id>`，不碰其他家。

### 5.3 设置项

| 键 | 含义 |
|---|---|
| `active_provider_id` | 新会话默认服务商（默认 `deepseek`），由聊天页模型选择器写入 |
| `model.<providerId>` | 该家上次用的模型 id；新建会话时取 `active_provider_id` + 它 |

### 5.4 会话快照与删除后的会话

- `sessions.provider_id` + `sessions.model` 建会话时写入（沿用现有列）；
- 服务商被删后：会话仍可读；打开时输入区禁用，上方一行 `此会话的服务商已被移除，无法继续对话`，
  右侧 `新建会话`；顶栏胶囊显示 `已移除`；
- 不做「换一家继续」（会把不同家的 thinking 签名/缓存搅在一起，且用户未必知道自己换了模型）。

### 5.5 能力矩阵

`Capabilities` 增加两项，其余沿用：

- `reasoning: Boolean` — 该模型会下发 `reasoning_content`（用于显示思考块，不影响请求）；
- `imageInput` 已有 — 第三方走 base64 `image_url`（见 6.4），`fileUpload` 第三方恒 false。

内置能力真值仍在 `KnownProviders`（DeepSeek 两档）；目录服务商的模型清单也写在内置目录里；
自定义模型的能力由用户在表单里勾（默认：`thinking=false`、`effort=false`、`imageInput=视觉勾选`、
`webSearch/readUrl/fileUpload/serverSideTitle/safetyCheck=false`）。

---

## 6. 协议层（M8-1）

### 6.1 抽象

```
core/net/protocol/
  ChatProtocol.kt        interface { fun build(...): PreparedRequest; fun assembler(clock): StreamAssembler }
  DeepSeekMessagesProtocol.kt   现有构建器改造（扩展字段按能力位开关，行为不变）
  OpenAiChatProtocol.kt         新增：/chat/completions
  AnthropicMessagesProtocol.kt  新增：/v1/messages（通用 Anthropic 兼容）
  StreamAssembler.kt    interface（fragments/usage/stopReason/title/sawStop/error/finishUnpairedTools）
  OpenAiStreamAssembler.kt      新增
```

`ChatStreamRunner` 不再自己 `MessageAssembler(clock)`，改吃协议给的 assembler；`PreparedRequest`
增加 `protocol` 字段，仅用于选 assembler 与重试策略。

### 6.2 OpenAI Chat Completions 请求

```http
POST {base}/chat/completions
Authorization: Bearer {key}
Content-Type: application/json
```

```json
{
  "model": "…",
  "messages": [{ "role": "system", "content": "…" }, { "role": "user", "content": "…" }],
  "stream": true,
  "stream_options": { "include_usage": true }
}
```

- `reasoning_effort`：仅当模型能力 `effort=true` 且思考开着时发送（自定义模型默认没有）；
- 不发 `temperature/top_p`（决策 11 不暴露）；
- system：`REPLACE` 用快照；`APPEND` = 快照（**不拼 DeepSeek 官方句**——那句只属于官方服务商）；
  `CLEAR` 不发；
- reminder：官方走 `latest_reminder` 角色；第三方把它拼在 system 末尾（单独一段），不伪造角色；
- 端点路径：默认 `/chat/completions`，地址已以 `/v1` 结尾时不再补 `/v1`。

### 6.3 流解码映射

| 上游 | 内部 |
|---|---|
| `choices[0].delta.content` | `RESPONSE` 碎片，连续追加 |
| `choices[0].delta.reasoning_content`（或 `reasoning`） | `THINK` 碎片 |
| `choices[0].finish_reason` | `stop`→END、`length`→MAX_TOKENS、`content_filter`→REFUSAL、`tool_calls`→按 INTERRUPTED 处理（v1 不支持第三方工具） |
| 尾部 `usage`（含 `prompt_tokens_details.cached_tokens`） | `prompt/completion/cacheHit` |
| `data: [DONE]` | 正常收尾（`sawMessageStop=true`） |
| `{"error":{...}}` | `ChatException(ErrorKind.STREAM)`，message 取 `error.message` |

- **`stream_options` 兼容**：若首个请求在**未收到任何帧**前返回 400 且报文提到 `stream_options`，
  自动降级重发一次（不带 `stream_options`），并记在服务商状态里（不弹窗、不加脚注）；
- 标题：`serverSideTitle=false` → 本地生成：首条用户消息压平换行、取前 24 字，超出加 `…`；
- 计价：第三方没有单价表 → 本轮 `cost=null`，统计条只显示 token，不显示 ￥；
- 继续生成（`prefix` 预填充）只有 Messages 协议支持 → 第三方会话隐藏「继续生成」。

### 6.4 图片（第三方）

- 第三方 `视觉` 勾选后，图片以 base64 `image_url` 发送（长边压到 1568、JPEG 0.85），
  不发 Files API，不支持 PDF/Word（附件按钮置灰并 toast「当前服务商不支持文件」）；
- 如果这条在实施时超预算，就把「视觉」勾选在 v1 里隐藏，不保留死开关。

### 6.5 降级矩阵（UI 只读能力位）

| 能力 | DeepSeek 官方 | OpenAI 兼容 | Anthropic 兼容 |
|---|---|---|---|
| 联网搜索 | ✅ | ❌（置灰 + toast） | ❌ |
| 读链接 | ✅ | ❌ | ❌ |
| 图片 | ✅（Files API） | ✅（base64，若勾视觉） | ✅（base64，若勾视觉） |
| 文件 | ✅ | ❌ | ❌ |
| 服务端标题 | ✅ | ❌（本地 24 字） | ❌ |
| 安全审查 / 思考强度 | ✅ | ❌ | ❌ |
| TTS / ASR | ✅ | ❌（隐藏朗读与麦克风） | ❌ |
| 余额 | ✅ ￥ | ❌ | ❌ |
| 订阅额度 | ❌ | ❌ | 仅 OpenCode Go |

---

## 7. 聊天侧改动

- 顶栏胶囊：`模型显示名 + 强度`（现状），第三方只显示模型名；服务商被删显示 `已移除`；
- 模型选择器改成：按服务商分组的列表 + 当前模型支持强度时底部一行 `思考强度：低/高/最大`；
  未配置密钥的服务商行显示 `未配置密钥`，点击跳服务商页（不静默失败）；
- 「未配置服务商」提示条改成按**当前服务商**判断有没有 Key；
- 费用：统计条与抽屉的 ￥ 只对 DeepSeek 出现；第三方只有 token；
- 朗读/麦克风：当前服务商不是 DeepSeek 时隐藏（语音只有官方端点）。

---

## 8. OpenCode Go

### 8.1 已核实的事实（可照着写）

| 事项 | 值 |
|---|---|
| 推理地址 | `https://opencode.ai/inference/go` + `/openai/v1/chat/completions`、`/anthropic/v1/messages` |
| Zen（OpenAI 兼容） | `https://opencode.ai/zen/go/v1/chat/completions`、`/zen/go/v1/models`、`/zen/go/v1/usage` |
| 会话头 | `x-opencode-session`，缺失 400 `MissingSessionID`；按会话稳定、≤64 字符 |
| 额度接口 | `GET https://opencode.ai/zen/go/v1/usage`，`Authorization: Bearer` |
| 额度返回 | `usage.rolling/weekly/monthly`，各含 `status`、`percent`（**已用** 0–100）、`resetsAt` |
| 计量 | 包月订阅，`cost` 恒 `"0"`；`usage` 里有 `cached_tokens` |
| 模型列表 | `/zen/go/v1/models` 只给 `id` |

来源：`pi-opencodego` 的实测文档、OmniRoute 的配额 PR、dsh-token-plan-usage 的口径文档（见文末链接）。

### 8.2 落地方案

- 作为独立 `ProviderKind.OPENCODE_GO`（决策 2 的"推迟到 v1.1"这次提前，因为服务商页反正要重写）；
- 协议：v1 统一走 `zen/go/v1/chat/completions`（OpenAI 兼容，有真实 Key 实测），
  `protocol=openai_chat`，另存 `zenBase` 与 `inferenceBase` 到 `meta_json`；
- 请求头：`Authorization: Bearer`、`x-opencode-session = sha256(sessionId).hex`、`User-Agent: TinyChat/1.0 (Android)`；
  **同一会话派生的所有请求（主请求、重试）用同一个值**；
- 模型：编辑页「获取可用模型」→ `GET zen/go/v1/models`，写入 `models_json`；
  能力默认 `thinking=true`（有 `reasoning_content` 就显示）、`effort=false`、其余 false；
- 额度：`GET zen/go/v1/usage`，只读账户状态、不消耗额度；30 秒 TTL 缓存，抽屉打开时刷新；
- 费用：不显示 ￥（订阅制），抽屉显示额度；
- 删除后历史会话只读，规则同 5.4。

### 8.3 额度 UI（用量抽屉的「账户」区）

```
账户
  订阅额度
    5 小时   已用 11%   ▓░░░░░░░░░   3 小时后重置
    本周     已用 14%   ▓░░░░░░░░░   2 天后重置
    本月     已用 7%    ▓░░░░░░░░░   11 天后重置
```

- 三行固定顺序：5 小时 / 本周 / 本月；数值取 `percent`（显示**已用**；`2026-11-19` 用户反馈：显示剩余不直觉，改成已用）；
- 进度条按已用比例，颜色 `brand`；`status=rate-limited` 或已用 ≥100% → 该行 `danger`；
- 重置时间：`<1 小时` 显示「N 分钟后重置」，`<2 天` 显示「N 小时后重置」，更远显示「N 天后重置」；
- 失败：整个「订阅额度」块隐藏（不加"上次读数"之类的小字，符合决策 27 的口径）。
- 深色/浅色都只换色板，不加边框。

---

## 9. 影响面

| 模块 | 改动 |
|---|---|
| `core:model` | `ProviderKind` 加 `OPENCODE_GO`；`ChatProtocolKind`；`Capabilities.reasoning`；`KnownProviders` 加目录与 OpenCode 预设 |
| `core:data` | `providers` 表 + DAO + 迁移；`provider/ProviderStore`；`SecretStore` 按 id 读写 + 旧键迁移；`UserSettings` 两个新键；`ChatRepository` 按会话服务商取 Key/协议/能力；`ChatStreamRunner` 吃 assembler |
| `core:net` | `ChatProtocol` / `StreamAssembler` 抽象；OpenAI 与 Anthropic 构建器；`OpenAiStreamAssembler`；`AccountApi` 拆成 `models()` / `usage()`（DeepSeek 余额不动）；传输层支持自定义 UA |
| `feature:settings` | 新 `ProviderScreens.kt`（列表/添加/编辑/模型目录），删掉旧 `ProviderSettingsScreen`；设置副标题规则 |
| `feature:chat` | `ChatViewModel` 不再持有单一 `apiKey`（改由仓库按服务商取）；胶囊/选择器；删除后的只读态；费用口径 |
| `app` | 路由 `PROVIDER` / `PROVIDER_EDIT`；`AppContainer` 组装 ProviderStore、密钥迁移、目录播种 |

**不改**：会话表结构、导出格式（导出仍不写 Key/设置）、提示词体系、语音端点。

---

## 10. 实施批次（每批结束都可单独验收）

### 批次 1 — 协议层（M8-1 核心）
`ChatProtocol` / `StreamAssembler` 抽象 + OpenAI 构建器 + 流解码 + 标题本地化 + 单测
（golden JSON、SSE 固件、finish_reason/usage/错误帧）。
验收：用假 transport 跑通一条 OpenAI 流；DeepSeek 路径行为零变化（现有测试全绿）。

### 批次 2 — 服务商数据与页面
`providers` 表与迁移、每服务商密钥、`ProviderStore`、列表/添加/编辑/模型目录；
旧 Key 自动迁移到 `cipher.deepseek`。
验收：模拟器上添加一个假地址的 OpenAI 兼容服务商（Key 假）→ 保存成功、状态红「Key 无效」、重启后仍在；
DeepSeek 老 Key 不用重填。

### 批次 3 — 聊天接线
模型选择器分组、按服务商取 Key、本地标题、费用/朗读/搜索的降级、删除后只读、无 Key 提示条。
验收：新建会话选第三方 → 真 Key 回一句话；切回 DeepSeek 仍正常；删掉第三方后老会话只读。

### 批次 4 — OpenCode Go + 额度
kind、session 头、模型拉取、`/usage` 三窗口、抽屉额度块。
验收：填入 Go Key → 列表绿点；抽屉显示 5 小时/本周/本月的已用与重置；发一条消息有 `reasoning_content` 时显示思考块；
把额度接口改错地址 → 块隐藏且不崩。

### 批次 5 — 收尾
模拟器全流程回归、`docs/03` 决策 33–36 与 §7.6 坑、`docs/02` §12.6 重写、提交。

---

## 11. 不做 / 延后

- OpenAI **Responses** API（v1.1；模型若只支持 Responses，先按不支持处理）；
- 第三方工具调用（`tool_calls` / function calling）；
- 服务商 OAuth（OpenCode 只需 Bearer）；
- 第三方的逐模型价格表（费用只显示 token）；
- 多 Key 轮换 / 代理 / 自定义 Header（OpenCode 的 session 头是唯一例外）；
- 服务商级别的「设为默认」开关（新会话看当前模型）；
- 删除服务商后的「换一家继续」（只给新建会话）。

---

## 12. 风险与待实测

| 风险 | 应对 |
|---|---|
| `x-opencode-session` 在 `zen/go` 路径是否也强制 | 实施时先用真 Key 打一次，401/400 即调整；两条路径都带头是安全的 |
| 部分网关不认 `stream_options` | 未出帧的 400 自动降级重发一次 |
| `reasoning_content` 各家命名不同 | 同时认 `reasoning_content` 与 `reasoning`，未知字段忽略 |
| `/models` 在网关上是 404 或空 | 允许手动添加模型，不把 `/models` 当唯一入口 |
| 页面重写回归 DeepSeek 主链路 | 批次 1 保持旧测试全绿；批次 2/3 各跑一次真实对话 |
| OpenAI 图片 base64 让请求体过大 | 长边 1568 / JPEG 0.85；超预算就把「视觉」开关隐藏 |
| 老 Key 迁移失败 | 解密失败按未配置处理，不阻启动；提示条引导重填 |

---

## 附：一手来源

- OpenCode Go 实测（端点、额度返回形状、`cost="0"`、模型只给 id）：
  <https://cdn.jsdelivr.net/npm/pi-opencodego@0.1.5/docs/OPENCODE-API.md>
- 额度接口为官方 `/zen/go/v1/usage` 的 PR 记录（rolling/weekly/monthly、`percent` 语义）：
  <https://github.com/cheaper-inference/OmniRoute/pull/12124>
- 额度口径（三窗口 = 月额度 20/50/100%、重置规则、`percent` 是**已用**）：
  <https://raw.githubusercontent.com/kongyuna/dsh-token-plan-usage/f408dd87da1fbe98d54c11fa7e6daf2556f92292/docs/quotas.md>

---

## 实施记录（2026-11-19）

与设计稿的偏差与落地口径：

- **保存即校验、失败不回滚**：点保存先落库再跑 `GET /models`；校验结果只写 `providers.check_status`，
  网络类失败写 `UNKNOWN`（灰点），只有 Key 被拒/402 才写 `FAILED`（红点）。
- **「清除」的位置**：编辑页密钥下方的红字文字按钮，仅 `hasKey` 时渲染；文案 `清除已保存的密钥`。
- **删除服务商**：硬删除（表 + `cipher.<id>`）；历史会话可读，发送被拦并显示「此会话的服务商已被移除」。
- **视觉**：按用户决定本轮不做，第三方模型没有「视觉」勾选；图片/文件入口在第三方链路隐藏。
- **OpenCode Go**：对话走 `zen/go/v1/chat/completions`（与「推理走 inference 域」的调研结论不同，
  以真实可用的 OpenAI 兼容端点为准），目录 `zen/go/v1/models`，额度 `zen/go/v1/usage`；
  `x-opencode-session = sha256(sessionId)`、UA = `TinyChat/1.0 (Android)`。
- **额度三窗口**：`rolling/weekly/monthly` → `5 小时/本周/本月`；显示已用 = percent；
  `rate-limited` 视为 100%；解析失败整块隐藏。

未验证项（等真 Key）：
- OpenCode Go 的模型拉取与额度接口是否对 Bearer Key 生效（用假 Key 打 `/models` 返回 401，说明端点存在、鉴权拦住了）；
- `x-opencode-session` 在 `zen/go` 路径是否强制（两条路径都带，安全）；
- 三窗口的 `resetsAt` 时区表现。

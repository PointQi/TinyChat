# 交接与续作指引（上下文压缩后先读本文）

> 用途：**上下文一旦被压缩/新开 session，先读这一篇**，再按需跳转 01 / 02。
> 本文只写"不可丢失的事实 + 一手报文 + 下一步动作"，不重复论证。
> 最后更新：2026-11-19（M5 收尾 + M7 方案定稿）

---

## 1. 当前状态

> 这张表按 **2026-11-19 的代码扫描** 重写（不是按计划勾选）。判断依据是代码里有没有实体：能力位/枚举/注释不算「已做」。
> 上一版表格长期只记了 M1/M2，导致后续会话误判过两次（把已做完的引用角标、联网搜索当成待办）。

| 里程碑 | 状态 | 证据 / 缺口 |
|---|---|---|
| 调研 / 决策 / 规划 / Spike | ✅ | 01、02、04 号文档；25 项决策全部拍板（14 起见 §3） |
| **M0 骨架** | ✅ | 模块划分、设计 token、Gradle 版本目录；图标按决策 15 从 chat-demo 机械移植 41 个，只剩分享/上箭头/返回三个手绘（`docs/design/icons-contact-sheet.png`） |
| **M1 聊天内核** | ✅ | M1-1 ~ M1-13；真机验过发送/停止/编辑重发/重新生成/版本切换/继续生成/抽屉 |
| **M2 渲染** | ✅ 2026-11-19 | M2-1/2、M2-3（抖动三坑）、M2-4、M2-5/M2-6、M2-7、M2-8、M2-9 全部落地并真机对照；宽公式「自然宽度 + 左贴边 + 横向滚动」已对照（`6248c97`） |
| **M3 联网** | ✅ 已真机在用 | `server_tool_use` / `web_search_tool_result` 解析、搜索阶段碎片与卡片、来源胶囊 + 来源页、「N 个网页」聚合、`deepseek_web_search` / `deepseek_read_url` 声明；工具报文见 §6 |
| **M4 附件** | ✅ **全部验完**（2026-11-19） | 相册多选、SAF 选文件、拍照入口（按 `resolveActivity` 判空）、草稿预览与删除、落盘（`attachments/{messageId}/` + `thumbs/`）、Files API 流式上传、`purpose` 自动判定、请求块（文件/图片在前、文本在后）、消息内 120dp/64dp 缩略图与文件卡、v4-pro 隐藏入口、断网失败→联网重试可续、删会话清附件——模拟器实拍 + 真实接口用例。仅剩「拍照」未在真机验（模拟器无相机 App）与确认文案分支（并入 M6） |
| **M5 语音** | ✅ **真机全通**（2026-11-19） | 点麦克风 → 键盘图标 + 居中「按住说话」→ 按住录音（频谱 + 秒数）→ 松手识别并**直接发送**；上滑取消；顶栏自动朗读（DataStore 持久化）+ 操作栏单条朗读；设置里两个官方音色。端点实测见 §7「M5 语音」 |
| **M6 数据管理** | ✅ **导入导出 + 清空全通，3340 会话往返 PASS**（2026-11-19） | 流式 ZIP 读（`ZipFile` 中央目录）、v3 解析/校验（未知条目整包失败、索引 message_count 交叉校验）、单事务整包替换 + 文件副作用回滚、导出（官方条目顺序，不新增条目/字段）、数据管理页接 SAF（导入与清空都要二次确认）、**清空所有对话**（先删文件再删行）。**真样本 3340 会话 / 23580 消息 / 39665 碎片 / 79 附件往返，字段级 diff = 0**；导出只有文本进度、没有进度条 |
| **M7 用量与费用** | ✅ **全部落地，模拟器验完**（2026-11-19） | M7-1 计价（`core/model/billing`：价格表 + 峰谷 + 微元整数 + 单测）；M7-2 圆环（只读当前分支真实 usage，输入区回形针左侧）+ 右侧会话汇总抽屉（标签可点展开说明）；M7-3 每轮统计条（设置开关，默认开）；M7-4 余额（`GET /user/balance`，左抽屉 `￥8.43` 实测）；M7-6 凭据（Keystore AES-GCM + 服务商页：保存/测试/清除，假 Key 报「Key 无效」）；M7-5 reminder 开关（关掉后请求不带该消息）。**真机/模拟器验证**：抽屉数值与 `usage` 一致、关闭统计条后整行消失、开关持久化、储蓄的 Key 在「校验失败」后仍可用 || **M7.5 提示词** | ✅ **全部落地，模拟器验完**（2026-11-19） | M7-5-1 种子：`PromptSeeds` 5 条内置（默认=官方原句、无提示词 CLEAR、角色扮演/翻译润色/学习辅导骨架），启动时 `ensureSeeded()` 对齐；M7-5-2 两个页面（列表 + 编辑器：名称/组合方式/内容/字数与 tok 估算/6 个变量按钮/未知变量红字禁用保存/内置只读+复制后编辑/删除自定义/恢复默认二次确认）；M7-5-3 建会话时 `snapshotForNewSession()` 写快照，**发送时**求值 `{{date}} {{weekday}} {{time}} {{location}} {{language}} {{model}}`（未知变量原样保留）；M7-5-4 恢复默认写回官方原文并选中。**端到端实测**：新建「meow」预设（正文 `Always start your reply with MEOW.`）→ 新会话回复以 `MEOW!` 开头；切回默认后**老会话仍以 MEOW 开头**（快照隔离）。单测：`PromptVariablesTest`(7) + `PromptPresetStoreTest`(10) + `ChatRepositoryPromptTest`(3) |
| **M8 打磨** | ✅ **全部落地，模拟器验完**（2026-11-19） | **分享为图片**（操作栏「分享」→ 抽屉「生成图片/复制文本/分享文本」→ 进度 → 预览 → 系统分享；1080px 宽、>12000px 自动切段、页脚「内容由 AI 生成，请仔细甄别」+ TinyChat；公式先预热再拍，代码块横向会被裁——见决策 32）；**外观**（跟随系统/浅色/深色）；**字体大小**（只放大正文，1.3× 不破版）；**语言**（只驱动 `{{language}}` 与 reminder，界面仍中文）；**个性化** = 所在地；**启动** `core-splashscreen`；**M8-1 OpenAI 兼容**（`ChatProtocols` 抽象 + OpenAI Chat / Anthropic Messages 构建器 + `OpenAiStreamAssembler`，本地标题 24 字，费用只显示 token）；**M8-6 多服务商**（`providers` 表 + 每家一把 Keystore Key + 旧 Key 自动迁移 + 列表/添加/编辑页重写：保存即校验、清除只对有密钥的行、删除服务商后会话只读、OpenCode Go 独立 kind + `/usage` 三窗口额度） |
| **M9 发布** | ⬜ 未开始 | 签名仍是 debug；根目录无 README/LICENSE/NOTICE；无 `UncaughtExceptionHandler`；1 万条消息滚动未测 |
| 阻塞项 | 无 | Key 暂走 `.local/dev-key.txt`（debug 注入）→ **M7-6 换成 Keystore + 设置页**（决策 23） |
| 下一步 | — | **M9 发布**：性能专测（1 万条滚动 ≥55fps）、`UncaughtExceptionHandler`、README/LICENSE/NOTICE、release 签名、冒烟清单。M8 的坑见 §7.6；OpenCode Go 的真 Key 验收（模型拉取、额度三窗口）等有 Key 时补 |


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

## 3. 28 项决策（速查，勿重新讨论）

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
| 23 | **M7 只做 DeepSeek 官方一家**（2026-11-19）：Key 的存储/校验/替换/清除与无 Key 引导**从 M8-6 提前到 M7-6**；多服务商 + OpenAI 兼容仍留 M8-1/M8-6。理由：余额与费用都是"某个 Key 的账户"的事，Key 还停在编译期常量时 M7 只在自己机器上成立 |
| 24 | **上下文圆环落输入区操作行、回形针左侧**（视觉 18dp/2dp 描边、触达 40dp、不显示百分比）：`<40%` 品牌色、`≥40%` `warning`、`≥80%` `danger`；点击从**右侧**滑出「用量与费用」抽屉。**有意突破** 02 §10.1「新增功能不改输入区」——这是 Agent 应用的通行位置，例外仅此一处，别改回去 |
| 25 | **语音费用（ASR/TTS）不计入对话费用**：按小时/按字符计价与 token 口径不同；**也不在抽屉里加任何说明小字**（见 27），完全不出现在费用数字里 |
| 26 | **圆环只按真实 `usage` 更新**（2026-11-19）：每轮回答落库后跳一次，流式中保持上一轮值，停止/失败不归零；**不做预估**、不做虚线态、不用输入框增量（`TokenEstimator` 只留给 M7.5 提示词字数）。理由：1M 上下文下当场输入占比可忽略，估出来的环只是噪声 |
| 27 | **用量抽屉只做会话级汇总、且不含脚注**（2026-11-19）：内容仅「上下文占比 + 进度条 / Token 用量（费用、缓存命中、未缓存输入、缓存读取、输出）/ 账户余额」。本轮明细只在消息下方的统计条；**不写说明小字、不放刷新按钮、不放模型名**。费率口径说明以后放设置里 |
| 28 | **用量数字的解释走「点标签 → 行内展开」**（2026-11-19）：可点的是**概念标签**（`上下文已用`/`Token 用量`/`费用`/`缓存命中`/`账户`），不是数字；不加问号、不加下划线、不弹对话框（弹窗要遮挡 + 关闭动作）。文案集中在 `UsageGlossary`，以后 i18n 只改这一处 |
| 29 | **M8 顺序与范围（2026-11-19，用户拍板）**：先做低耦合高收益的（分享出图 → 外观/字号/个性化 → 轻量 i18n → 启动），**M8-1 OpenAI 兼容与 M8-6 多服务商合到最后**（服务商页接近重写，改动面最大）。三处收口：**i18n 只做轻量**（语言行可用、`{{language}}` 联动、`strings.xml` 抽公共文案），不为了「全量英文」重写全部 Kotlin 文案；**个性化 = 用户元信息编辑**（昵称/所在地/常用语言，喂 `{{location}}` `{{language}}`），不是「推荐偏好」；分享长图先出样张再铺开 |
| 30 | **提示词预设的落地口径（2026-11-19）**：5 条内置只读（改要先复制，`恢复默认` 写回官方原句）；全局只有一个「当前预设」，**选中即落库**，点一行 = 选中并打开编辑器；新会话建会话时取快照，变量**发送时**求值，未知变量保留原文且在编辑器里报错禁用保存；`{{model}}` 取模型**显示名**（不是内部 id） |
| 31 | **M8-4 的三处口径（2026-11-19）**：**字体大小只放大正文**（markdown 语义字号），顶栏/按钮/设置列表保持原尺寸 —— 这是「1.3× 不破版」的实现方式，别改成全局 Typography 缩放；**「语言」= 回答语言**，只驱动 `{{language}}` 与 `latest_reminder`，界面文案英文化留 v1.1（用户同意"意思意思"）；**「个性化」= 所在地**（喂 `{{location}}`），不是推荐偏好 |
| 32 | **分享长图的实现与已知限制（2026-11-19）**：1080px 宽、单张 >12000px 切段、PNG 落 `cacheDir/share/` 走 FileProvider；离屏渲染 = 往 Activity decor 临时塞一个**宽度焊死**的宿主 View（不是 Dialog/悬浮窗），并等「高度稳定 + 公式渲染队列清零」再画；**极长的代码行会被横向裁掉**（代码块是横向滚动容器，图里没有滚动），与屏幕上的表现一致，v1.1 再考虑换行渲染 |
| 33 | **服务商页的动作口径（2026-11-19 用户拍板，M8-6）**：**保存是唯一主按钮**，点保存 = 立即写本机 + 后台 `GET /models` 校验；校验结果只是**状态**（绿点 / 灰未校验 / 红 + 一行原因），**不阻塞保存**（离线也能存）。删掉「测试连接」这个动词；要复检就在编辑页点状态行右侧的 `重新校验`。「清除」只在**确实存了密钥**时出现，文案 `清除已保存的密钥`，确认框写明「只删本机密钥，不影响服务商账户」。交互与文档分离见 05 |
| 34 | **每家服务商一把 Key + 会话快照（2026-11-19）**：`credentials` DataStore 里键名 `cipher.<providerId>`，仍是同一把 Keystore AES-GCM 主密钥；M7-6 的 `api_key_cipher` 首次启动自动迁到 `cipher.deepseek`。新表 `providers`（id/kind/display_name/base_url/protocol/models_json/builtin/sort/check_*），启动播种 DeepSeek；内置服务商**不可删**（TTS/ASR/余额都靠它）。会话表仍只存 `provider_id` + `model`：**删服务商后会话可读但发送被拦**（顶部条「此会话的服务商已被移除」，胶囊显示 `已移除`）；不提供「换一家继续」 |
| 35 | **协议抽象与第三方降级（2026-11-19，M8-1）**：`ChatProtocols` 按 `ProviderProtocol` 分发（`DEEPSEEK_MESSAGES` / `ANTHROPIC_MESSAGES` / `OPENAI_CHAT`），`ChatStreamRunner` 只吃 `StreamAssembler`。OpenAI 链路：Bearer、`stream_options.include_usage`（400 时降级一次）、`reasoning_content` 显示思考、`finish_reason` 映射状态、**标题本地取首条 24 字**、`latest_reminder` 拼进 system、`APPEND` **不拼** DeepSeek 官方句。第三方不显示 ￥（只 token）、不提供搜索/文件/TTS/语音；**图片（base64）按用户决定本轮不做**，「视觉」不出现 |
| 37 | **第三方思考参数按模型声明（2026-11-19，M8-1 收尾）**：模型级 `thinking` 三态 `NONE`（默认）/`EFFORT`/`BUDGET`，服务商页每条模型可选；只有声明后才出现思考强度。三档映射：OpenAI `low/medium/high`，Anthropic `budget_tokens` 1024/4096/8192（`max_tokens` 始终大于预算）。服务商若在首帧前 400 且报文点名参数，自动去掉参数重发一次并记 `meta.no_effort=1`，之后不再发。**费用与余额按服务商类型收口**：价格表只对 DeepSeek 官方生效（同一个模型 id 在第三方不代表同样单价），通用第三方不显示 ￥、不去查 DeepSeek 余额 |
| 36 | **OpenCode Go 独立 kind（2026-11-19）**：对话走 `zen/go/v1/chat/completions`（OpenAI 兼容），目录 `zen/go/v1/models`，额度 `zen/go/v1/usage`；`x-opencode-session = sha256(sessionId).hex`（≤64、同会话所有派生请求同值），UA = `TinyChat/1.0 (Android)`（不伪装 CLI）。额度只取 `rolling/weekly/monthly` 的 `percent`（已用）与 `resetsAt`，UI 显示**已用**（用户拍板：进度条走满才符合直觉）；`rate-limited` 视为 100%；接口失败就整块隐藏，不编数字 |
| 38 | **图标与关于页收尾（2026-11-20，用户拍板）**：启动图标换成自绘角色（原图 `C:\Users\lenovo\Desktop\logo.png`，1254×1254）：**角色按原始比例缩进 72dp 安全区（108dp 画布的 2/3），四周透明**，背景色与贴图底色同为 `#020101`，所以没有接缝；五档密度从 1254 原图直接缩放，不放大低清图。**不要**把角色铺满 108dp——启动器展示的就是安全区那一块，铺满会被当成 1.5× 放大（实测过一次，脸和星星都被裁掉）。**不提供 monochrome 层**——角色剪影没有识别度，Android 13 主题图标回退成彩色图标即可。「关于」由三行合并为**一行一个入口**：版本（读安装包 `versionName`，不写死）、开源项目（`https://github.com/Takeoff0518/TinyChat`）、开源许可（按 Apache-2.0 / MIT / 仅测试 分组列组件）；服务协议与帮助入口本轮不做，删掉两个死行 |
| 39 | **文案口径（2026-11-20，用户两轮拍板）**：界面文字按「**少说一遍**」写——不把同一句话反着说一遍、不做防御性兜底；主语是用户或应用本身，不用「此操作」「该操作」；能用白话就不用缩写与黑话（`上下文前缀`→`上一轮的对话内容`，`未缓存输入`→`这一轮实际发给模型的全部内容`）；用户已经知道的事不解释（填 Key 的人不需要再被告知 Key 干什么）。已落地：用量抽屉释义各压到 1–2 句并去掉 DeepSeek 专属细节（缓存 2% 计费、高峰翻倍、语音不计入）；`费用`→`预估费用`；额度行去掉「已用」，重置时间改成 `A 天 B 小时 / A 小时 B 分钟 / A 分钟` 并省略为零的单位；`个性化`→`所在地`（未填 `未设置`）；数据管理副标题改成「兼容 DeepSeek 客户端格式」+ 导入/导出各一句 `.zip` 说明；`生成图片`→`生成长图`（连带进度、预览、分享面板、失败 Toast 一套）；服务商目录副标题只留域名（`platform.deepseek.com` / `opencode.ai/go`）；三种组合方式说明 = 仅发送当前自定义内容 / 先发送官方提示词，再拼接当前内容 / 不发送任何系统提示词。**关于页那句「与深度求索公司无隶属关系」整段删除**——理由（用户）：本客户端已是独立开源项目，同类客户端设计本就趋同，真正的合规点在 chat-demo 的 MIT 素材，开源许可页已写明「图标与界面参考」；再写免责声明等于把话说两遍。三条内置提示词按用户版本替换（角色扮演改成方括号填空 + 第一人称规则，翻译润色加意图判断与格式保护，学习辅导加精准切入与鼓励式反馈）。**尚未改的一批**：`A 通用词` 24 条、`D 聊天页` 94 条、`L 错误与提示` 78 条（`docs/copy-review.md` 里还是空的），按同一口径继续扫 |
| 40 | **上下文上限的处理（2026-11-20，用户拍板）**：**不裁剪历史**——v1.0 明确不做上下文压缩/摘要，请求永远发当前分支的完整前缀。理由：聊天一轮约 300–600 token，1M 要约 2000 轮，真实聊天到不了；能撞上限的只有大附件、反复生成长文、导入的历史。而任何自动裁剪都会改写前缀、把服务端的上下文缓存打死，是「为一个几乎不会发生的事件，做一件每轮都可能付代价的事」。**到 95% 时只拦「追加新提问」**：判据 `contextUsed / contextWindow ≥ 95%`（分子来自上一轮真实 usage，已含收发），重新生成、编辑重发、继续生成都不拦（不追加历史，输入只会变短或不变）。**拦截形态照官方 App**：不改按钮、不改占位符、不置灰语音和附件，只在**按发送那一刻**把一句人话放进现成的错误槽（与「网络异常，请检查网络后重试」同一个位置）——`ContextFull` 的两个预检和「服务商被删」「没 Key」共用同一套：在清空输入框**之前**返回，文字与附件原样留在输入区。语音模式下顺手退回键盘模式（`ChatViewModel.finishVoiceRecording` → `quickSend` → `send`，识别结果先写进 `input` 再发，拦下来时字还在，但语音模式下输入框是藏着的）。新增 `ErrorKind.CONTEXT_LIMIT`（wire `contextLimit`，不可重试），文案「当前对话已达上限，请开启新对话继续。」；`ChatTransport.kindFor` 对 **400** 再看一眼响应体（`prompt is too long` / `context_length_exceeded` / `exceed context limit` 等特征串），命中就映射成它，覆盖「不到 95% 但附件太大」这类客户端算不准的情况。顺带记录：`latest_reminder` 必须用**那一轮自己的快照**（不能拿今天的日期重刷历史，否则跨天后每条历史前都插一条新 reminder，整段前缀缓存全废）——参考实现 `chat-demo-ref/src/services/chat/reply.ts` 的 `appendReminder` 就是这个口径，**本轮未改，待办** |


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
| ASR | `POST /v1/tools/asr`（multipart `file`） | 16-bit PCM WAV / Ogg Opus；≤8MiB、≤120s；WAV 采样率只接受 8k/16k/22.05k/24k/32k/44.1k/48k；返回 `{request_id,text,duration_ms}`；不支持流式。**静音/纯音轨返回 200 + `text:""`**（不是错误）；空文件才 400；>8MiB 是 413、上传超 10 分钟是 408 |
| TTS | `POST /v1/tools/tts` | `{text,voice_id:"stella"\|"tide",format:"wav"\|"pcm",filter_markdown}`；`filter_markdown` **默认 true**；wav 或 24k/16bit/mono PCM；`text` ≤10000 **计费字符**（**汉字算 2 个**，≈5000 汉字）、请求体 ≤64KiB、单次最多 30 分钟音频。400 = 无文本/纯符号/内容审核；还有 429/503/504。**中途断流要当失败** |
| TTS 流式 | `wss://api.deepseek.com/v1/tools/tts` | 语音边生成边播；会话 ≤30 分钟。**v1 不用**：HTTP 整段接口本身就边合成边返回（实测首字节 0.95s） |

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

### Step 2 — M1（13 人日）— ✅ 已完成（2026-11-18）

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

---

**M4 附件（2026-11-19 落地，主链路已通）**

一条龙：Photo Picker / SAF → 草稿（只存 Uri + 元信息 + 缩略图）→ 发送时落盘到 `attachments/{messageId}/` → 上传 Files API 拿 `file_id` → 请求里用**块数组**引用 → 消息里画缩略图/文件卡。

| 事项 | 结论（踩坑换来的，别改回去） |
|---|---|
| 目录结构 | 按 02 §3.4：`filesDir/attachments/{messageId}/{id}_{safeName}` 原件、`filesDir/thumbs/{id}.webp` 缩略图（最长边 512px）。**草稿阶段不拷原件**：`messageId` 要等发送时才有，先只读 Uri 元信息 + 生成缩略图 |
| 单文件上限 | 100MiB（`AttachmentStore.MAX_BYTES`）。有 `SIZE` 就提前拒；拿不到 `SIZE`（部分 provider）就边拷边数，超了删半截文件 |
| 状态机 | `LOCAL`（已落盘未传）→ `UPLOADING` → `UPLOADED`（有 `remote_id`）/ `FAILED`（可重试）。**状态先落下一行再上传**，界面直接读库画「上传中 / 失败」，不另做内存态 |
| `purpose` 判定（M4-5） | 图片 → `user_data`；PDF/Word/PPT/ODF → `text_recognition`；txt/md/csv/json/代码/XLSX/EPUB → `user_data`（不计费）。`rich_layout_recognition` 需要用户显式选，不自动猜（猜错要多花钱）。见 `AttachmentPurpose` |
| 请求块顺序 | **附件块在前、文本块在后**（03 §6.5 示例顺序）。只有图片/文件没写字的那一轮：**不写 REQUEST 碎片**（真实归档样本就是 0 碎片）+ 请求里也不发 `text` 块。`ContextRebuilder.userMessage()` 是唯一入口 |
| beta 头 | `anthropic-beta: files-api-2025-04-14` 现在**自动判定**（`WireMessage.hasAttachmentBlocks` 扫 `document`/`image` 块），不用调用方记得传 `hasFileBlocks` |
| 空轮丢弃 | 附件没拿到 `file_id` 且正文为空 → 这一轮不进上下文（与「孤立 USER」同处理），避免发出只有空 content 的消息 |
| 消息内版式 | **按 chat-demo 官方实现**（`UserMessage.module.css` / `MessageFile.module.css`）：一列 `flex-wrap`、右贴边、间距 10dp；图片是**正方形缩略图 120dp，与文档混排时缩到 64dp**；圆角 16dp + 1dp 描边 + `object-fit: cover`；上传中压 30% 黑蒙层 + 转圈，失败描红边 + 重试图标。**不是** 1:1 / 2 列 / 3 列九宫格（那是照截图猜的，已按参考实现更正） |
| 文件卡 | 40dp 图标 + 文件名 + 第二行「扩展名 大小」（`PDF 1.2 MB`），失败/缺失红字。卡片点击在原件可读时才有动作 |
| 能力位（M4-6） | `state.canAttach` = 当前模型 `imageInput || fileUpload`；v4-pro 整个附件入口**不出现**（不是置灰）。编辑态也不给加附件（编辑只改文字，进入编辑时草稿已清空） |
| 图片解码 | **不加 Coil**：缩略图是我们自己生成的 512px webp，用 `BitmapFactory` + `inSampleSize` 按目标像素解码即可（`rememberAttachmentBitmap`）。要预览时读的还是缩略图，不解析原图 |
| 导入的历史附件 | `attachments` 表为空时退回 `FILE` 碎片（`FilePayload`），标 `MISSING` 显示「原件未导入」——M6 导入要把碎片写成附件行，那时这条兜底才会退休 |

**两个必须记住的 Compose 陷阱**

1. `BoxWithConstraints(Modifier.fillMaxWidth())` 的 content 默认 `TopStart` 对齐：里面的 `Column(horizontalAlignment = End)` 是 wrap-content，**靠不满整行**（图片会浮在左边）。必须给 `BoxWithConstraints` 指定 `contentAlignment = Alignment.BottomEnd`。
2. `uiautomator dump` 的 `bounds` **不能当布局尺寸用**（本项目第二次踩）：那两枚图片节点报成 `330×48`，实际 `onGloballyPositioned` 是 `330×330`、根坐标 `(348,231)`、`(706,231)`——右侧对齐到 1036，与气泡右缘一致。判尺寸一律用 `onGloballyPositioned` 打印，或截图像素探针。

**验证记录（2026-11-19，模拟器 Pixel_3a_API_34，1080×2220 / dpr 2.75）**

| 用例 | 结果 |
|---|---|
| 相册多选 2 张 → 草稿行两枚 56dp 缩略图 + 删除角标 | ✅ `files/thumbs/*.webp` 两个文件（1350/1280 B） |
| 输入文字 + 发送 → 上传 → 视觉问答 | ✅ 附件 `state=UPLOADED`、`remote_id=file-api-…`；回答正确描述两张图的横/竖构图与白框 |
| 消息内版式 | ✅ 两枚 330×330（120dp）右贴边、间距 28px（10dp）；把其中一条改成文档后，图片自动缩到 168px（64dp）+ 文件卡「PNG 3 KB」 |
| 只有图片没有文字 | ✅ 用户消息 0 碎片、请求里只有 image 块，正常回答 |
| v4-pro 下附件入口 | ✅ 切到 `V4 Pro 最大` 后输入区**不再有回形针** |
| 文档块端到端（真实接口） | ✅ `LiveSmokeTest."文档块端到端"`：上传 md（`user_data`）→ `document` 块 → 模型答出文档里的杜撰代号 `BLUEHARBOR`；同时断言有 beta 头 |
| SAF 选文件（M4-2）→ 文档问答 | ✅ `选择文件` → DocumentsUI 的 Recent 里（**要先往下滚**，推到 /sdcard/Download 的文件在列表下方）选 `tc_note.md` → 草稿显示 `MD` 方块 → 发送后附件 `state=UPLOADED`、`remote_id=file-api-…`，消息里是文件卡「tc_note.md / MD 93 B」，回答 `The codename is BLUEHARBOR.` |
| 断网 → 发送 → 联网 → 重试（M4-3 验收） | ✅ 断网时附件落 `FAILED`（无 `remote_id`）、消息下方出现「附件上传失败，点击重试」；联网后点它：`FAILED→UPLOADED` 拿到新 `file_id`，**复用同一条提问**（用户消息 17 未重复），生成回答 18 |
| 删会话清附件 | ✅ 删掉带附件的会话后：缩略图 `thumbs/{id}.webp` 与原件 `attachments/{messageId}/*` 都删了，`attachments/{messageId}/` 空目录也一并收敛（`AttachmentStore.delete` 里 prune） |

**还没做的（都记在账上，别当成已完成）**

- 上传只有不确定进度（转圈），没有百分比——**官方也是转圈**（`FileCard` 的 spinner + 蒙层），所以不算缺口。
- 删会话确认文案还没按「含附件」分支（02 §12.3 要求两句不同的话）；抽屉不知道附件数，等 M6 清理由一处收口时一起做。
- 拍过的照片缩略图/原件不会自动清理（只有删会话时清）；M6「清空所有对话」一起收口。
- 更早期留下的孤儿文件（先在旧包上删过两个会话）：`attachments/13|15|17` 与两个 thumb 是**修好之前**删会话留下的，新代码不会再产生。
- 模拟器上没有相机 App（`ACTION_IMAGE_CAPTURE` 解析不到），所以「拍照」入口在模拟器里**不出现**（代码按 `resolveActivity` 判空隐藏）；真机要再验一次 FileProvider 那条路。


---

**M6 导入导出（2026-11-19 落地，真样本往返 PASS）**

| 模块 | 职责 |
|---|---|
| `data/archive/V3Format.kt` | 条目名与顺序、`parseFileEntry`、时间换算、报告类型 |
| `data/archive/V3Archive.kt` | DTO、**`ZipFile` 读**、`ZipOutputStream` 写、`V3SessionJson` 拼会话 JSON |
| `data/archive/V3Importer.kt` | 校验 + 单事务整包替换 + 附件落地 + 索引交叉校验 |
| `data/archive/V3Exporter.kt` | 按官方顺序导出；本地附件补 `FILE` 碎片 |
| `data/archive/ChatArchive.kt` | 门面：SAF `Uri` 或 `File` → 上面两者；导入后清孤儿文件 |
| `app/src/debug/…/ArchiveLabActivity` | 验收台（release 不打包），按路径跑导入/导出 |

**四个必须记住的坑（都真机验过）**

| # | 坑 | 现象 / 根因 | 修法 |
|---|---|---|---|
| 1 | **`ZipInputStream` 读不了真样本** | 包里有 STORE 条目带 data descriptor：JDK 直接抛 `only DEFLATED entries can have EXT descriptor`；**Android 上更阴**——静默停在那一条，79 个附件只进来 1 个，还不报错 | 先把流落成临时文件，再用 **`ZipFile`（中央目录）**逐条读。代价是多一次 87MB 拷贝（~3s），换来尺寸/CRC 可信 |
| 2 | **`attachments` 主键必须 `(id, message_id)`** | 真样本 124 个附件里 **20 个**被同一会话的两条提问重复引用；单列主键下后写的把前一条顶掉 → 导出丢 `export_path`、界面少一个缩略图 | 改复合主键 + `MIGRATION_2_3`（重建表）；`byId` 返回 List，`setLocal` 同时更新同一 id 的所有行 |
| 3 | **导出消息按 `id` 排，不能按 `inserted_at`** | 真样本里 ASSISTANT 的 `inserted_at` 常比它对应的提问早几毫秒（发言时间 vs 建行时间），按时间排会把一问一答顺序翻过来（`current_message_id` 也随之错位） | `withFragments(...).sortedBy { it.message.id }` |
| 4 | **时间格式不能固定三位毫秒** | 官方整秒写作 `2025-06-17T07:55:45Z`（无小数），有毫秒才写 `.059Z` | 直接用 `Instant.toString()`，往返字节级一致 |

**丢弃规则**（03 §6.8）：`TIP`（UI 提示条）与 `CONTENT_RISK`（同一句英文免责声明）导入时丢弃，报告里计数。真样本丢了 **4411** 条，与文档记录的条数一致。

**验收（2026-11-19，模拟器 Pixel_3a_API_34）**

```bash
# 导入（87MB 真样本，包已推到 app 自己的外部目录，SAF 之外的可复现路径）
adb shell am start -n io.tinychat/io.tinychat.debug.ArchiveLabActivity \
  --es import /sdcard/Android/media/io.tinychat/deepseek_data.zip
adb shell run-as io.tinychat cat cache/archive.txt
# → sessions=3340 messages=23580 fragments=39665 files=79 dropped=4411 ms≈67000

# 导出
adb shell am start -n io.tinychat/io.tinychat.debug.ArchiveLabActivity \
  --es export /sdcard/Android/media/io.tinychat/tinychat_export.zip
# → sessions=3340 messages=23580 fragments=39665 files=79 bytes≈82MB ms≈34000
```

导出包与原包逐字段比对（Python，归一化：丢掉 `TIP`/`CONTENT_RISK`、消息 id 换成会话内序号、`export_path` 只比"文件在不在包里"）：

```
sessions 3340 3340 ids equal True
files     79   79  names equal True
total diff sessions 0 => PASS
```

导出体积 82.4MB < 原包 87.8MB，差额就是丢掉的 4411 条 `CONTENT_RISK`。

**导入语义**：整包替换（先二次确认）。DB 在一个事务里换库；附件原件写在事务外，所以**失败时按"这次写过哪些目录"逐个删**（不是删整个 attachments 根，否则会误伤旧数据）。导入成功后 `ChatArchive` 再清一次孤儿目录与孤儿缩略图。

**还没做**：导出只有"第 N/3340 个会话"的文本进度，没有进度条；`chat_session_index.json` 只用于校验，不用于建列表（会话顺序取 `updated_at`）；**数据管理页的 SAF 点选在模拟器上没点通**——`DocumentsUI` 的 Download 列表里不显示我们 push 进去的 zip（同目录的 pdf/md/txt 都显示），`ChatArchive.import(Uri)` 的「流 → 临时文件 → 解析」这段改由 Robolectric 用例覆盖（`ChatArchiveUriImportTest`），点选本身留真机验。

**清空所有对话（M6-6，已验）**：设置 → 数据管理 → 清空所有对话 → 二次确认。实现顺序是**先删附件文件、再删行**（行没了就找不到 `local_path`，文件会变成永远清不掉的垃圾），一次 `attachmentDao().all()` 查出全库附件，不逐会话查库。模拟器实测：3340 会话清空后 `sessions`/`messages` 归零、`files/attachments` 与 `files/thumbs` 清空。

**真机（FIN-AL60 / Android 12）首轮测试发现并修掉的 7 个问题（2026-11-19 上午）**

| # | 现象 | 根因 | 修法 |
|---|---|---|---|
| 1 | 附件面板里**没有「拍照」**（模拟器上"正常"，因为模拟器真没相机） | Android 11+ 包可见性：manifest 没声明 `<queries>`，`resolveActivity(ACTION_IMAGE_CAPTURE)` **恒为 null** | manifest 加 `<intent><action android:name="android.media.action.IMAGE_CAPTURE"/></intent>`；判断改用 `queryIntentActivities(..., 0)`（华为相机的 capture activity 不一定带 DEFAULT） |
| 2 | 导入**秒失败**，报 `The coroutine scope left the composition` | 导入写成「存 `pendingUri` + `LaunchedEffect(pendingUri)`」，却在 effect 里把 key 置空 → Compose 立刻取消协程；取消异常又被 `catch (Exception)` 当成业务失败显示 | 改成**从选择器回调直接 `scope.launch`**；`CancellationException` 单独 rethrow |
| 3 | 导入的历史**图片一直转圈** | ① `LOCAL` 被当成"上传中"（导入的附件永远停在 LOCAL）；② 导入只拷原件、**没生成缩略图** | ① `isUploading` 只认 `UPLOADING`；② `ChatArchive` 导入后补缩略图（`writeThumbFromFile`），UI 再退一步"没有缩略图就用原件解码"（`imagePath = thumbPath ?: localPath`） |
| 4 | 导入的历史**联网对话没有正文角标** | **导出包本身没有 `citations` 字段**（真样本 11643 个 RESPONSE 碎片里 0 个带），角标数据在流式 chunk 里、没进归档 | 数据没有就是没有（官方 App 同样显示不出）；但 `TOOL_SEARCH` 结果还在 → 正文末尾的「N 个网页」胶囊改由搜索结果兜底 |
| 5 | 点开图片时**先闪一下「图片已不在本机」**再出图 | 解码是异步的，`produceState` 第一帧必然为 null，而空 bitmap 被当成"文件没了"直接出了文案 | 抽出 `BitmapLoad(bitmap, loading)` 三态：加载中画转圈、解码失败才说那句话；缩略图加载中留空（别闪占位图标） |
| 6 | 点开图片时**先出现一条又宽又矮的黑条，出图后位置再往上跳一下** | 预览弹窗的盒子尺寸由内容撑出来：加载态只有转圈（高 124dp），出图后变成图片高度 → 弹窗按新高度重新居中，看着像"往上蹿" | 盒子**第一帧就按附件宽高比定尺寸**（`Modifier.aspectRatio(w/h)`），加载态与出图态同尺寸；没有宽高的老数据退化成 `heightIn(min = 160.dp)` |
| 7 | 「从相册选择」**进的是文件浏览器，不是手机相册** | 用的是 `PickVisualMedia`（照片选择器）；这台机器是 Android 12、没有照片选择器，于是 `ActivityResultContracts` 回落到 `ACTION_OPEN_DOCUMENT`。实测 `ACTION_GET_CONTENT image/*` 在这台机器上只被 `com.android.documentsui` 与华为文件管理接住，**`com.huawei.photos` 根本不接** | 新增 `ImagePick` 三级落点：① 系统照片选择器（13+）② **`ACTION_PICK` + `MediaStore.Images.Media.EXTERNAL_CONTENT_URI`**（这台机器由 `com.huawei.photos/.gallery.app.materialselect.ThirdSelectSlotAlbumActivity` 接住，就是"直接进相册"）③ DocumentsUI 兜底。新契约 `PickImagesFromGallery` 同时兼容单选（`data`）与多选（`clipData`） |

> 顺带确认：文件卡显示**「原件未导入」是正常的**——官方导出只带 79 个图片原件，PDF/Word 之类的原件根本不进包（只有元数据）。它们的附件行是 `MISSING`，卡片给"原件未导入"、不可点。

**图片快捷提问（官方同款，2026-11-19 加）**：只要草稿里**有图片**、不是生成中、也不在编辑态，输入区就在「缩略图行」与「输入框」之间插一排横向滚动的**描线**圆角按钮（`borderL2` 1dp 描边、不填充，文案后带 `→`）：`这是什么 →` `分析一下 →` `怎么办 →` `解释一下 →`。点一下 = 把这句当作提问 + 带上当前全部附件**直接发送**（`ChatViewModel.quickSend`，会覆盖输入框里没发的文字，官方也是这个行为）。

---

**M5 语音（2026-11-19 起步）**

先纠正一处**文档错**：`01 §4.8` 原来写「ASR 返回后填入输入框，不自动发送（与官方一致）」——**这是错的**。
chat-demo 是 Web 实现（点一下开始、再点结束、结果回填输入框），**官方 App 不是这样**。用户核对后的真实交互：

> 点麦克风 → 按钮变**键盘图标**、输入框位置整块变成居中的「按住说话」→ **按住录音** → **松手直接转写并发送**；
> 没识别出内容弹提示、不回填输入框；再点键盘图标回文字输入。

**端点实测（2026-11-19，脚本 `.local/probe_speech.py` + `probe_speech2.py`，不入库；总花费约 3 分钱）**

| 项 | 结果 |
|---|---|
| TTS wav / pcm | 均 200；pcm `Content-Type: audio/pcm; rate=24000; encoding=signed-integer; bits=16; channels=1` |
| TTS 是否真流式 | ✅ 570 字请求 **首字节 0.95s**，17.8s 出完 108s 音频（≈6 倍速）→ 边收边播有意义 |
| TTS `wav` 头 | **`data` 长度是流式占位 `0x7FFFFFFF`**（解出来 89478 秒）。落文件喂 `MediaPlayer` 会算疯，所以一律走 `pcm` + `AudioTrack` |
| TTS 400 的三种含义 | `text must not be empty` / `No readable text to synthesize`（纯符号）/ 内容审核 `Content Exists Risk` |
| ASR 静音 / 纯正弦 1s | 均 **200 + `text:""`**（不是错误）→「未识别到语音内容」是客户端判空 |
| ASR 空文件 | 400 `Unsupported audio format: expected 16-bit PCM WAV or Ogg Opus` |
| 计费 | TTS 汉字算 **2 个**计费字符，10000 上限 ≈ 5000 汉字；ASR 0.78 元/小时 |

**架构**（新增 `:core:audio`，别把录音/播放塞进 `core:data`）：

| 位置 | 职责 |
|---|---|
| `core:audio/VoiceRecorder` | `AudioRecord` 16k/mono/16bit；预写 44 字节 WAV 头、`stop()` 回填长度；120s 自动停；边录边吐 PCM 帧 |
| `core:audio/Fft` + `SpectrumAnalyser` | 512 点 FFT → 100–4000Hz 对数分段 → 0..1 柱高；纯 Kotlin 可单测 |
| `core:audio/WavHeader` | 写 + 解析（容忍 `0x7FFFFFFF` 占位），纯 Kotlin 可单测 |
| `core:audio/TtsPlayer` | `AudioTrack` MODE_STREAM（24k/mono/s16le）+ `AudioFocusRequest`（26+）+ `become noisy` |
| `core:net/SpeechApi` | `transcribe()`（multipart `file`）、`synthesize()`（流式 chunk 回调）；`call.cancel()` 真取消 |
| `feature:chat` | ViewModel 语音状态机（`IDLE/RECORDING/TRANSCRIBING` + `playingMessageId`）；输入区麦克风/键盘切换、频谱行、操作栏朗读、顶栏自动朗读 |

**四个别再踩的坑**

1. 自定义 WAV 头别忘了录音时长上限后**回填** RIFF/data 长度，否则 ASR 可能按 0 长度拒（我们探针里空文件就是 400）。
2. TTS 断流要当失败（官方文档明写），不能当成"播完了"。
3. 朗读长文必须按句切分：5000 汉字正好是 10000 计费字符上限。
4. 顶栏 🔊 是**自动朗读开关**（开关状态要能看出来，现在两个状态同一个图标）；消息操作栏的「朗读」是**单条**朗读，两者不是一件事。

**落地记录（2026-11-19 真机 PASS）**

| 模块 | 文件 |
|---|---|
| 录音 / WAV / 频谱 / 播放 | `core/audio/`：`VoiceRecorder`、`WavHeader`、`Fft`、`SpectrumAnalyser`、`TtsPlayer` |
| ASR / TTS 请求 | `core/net/speech/`：`SpeechRequests`（可离线断言）、`SpeechClient`（流式 chunk） |
| 长文切分 | `feature/chat/voice/TtsTextChunker`（按句 + 硬切，汉字算 2 计费字符） |
| 编排 | `feature/chat/voice/VoiceController`（录音/识别/朗读三件事 + 事件流） |
| 状态机 | `ChatViewModel`：`voiceMode` / `voiceStatus` / `voiceSeconds` / `speakingMessageId` / `autoSpeak` |
| 持久化 | `core/data/settings/UserSettings`（DataStore：`auto_speak`、`tts_voice`） |

**两个真机踩出来的坑（都已修）**

1. **点「停止朗读」会弹「朗读失败，请稍后再试」**：停止走的是 `TtsPlayer.stop()` → 正在 `write` 的协程抛 `PlaybackStopped`；它不是 `CancellationException`，被 `catch (Exception)` 当成真失败。修法：`PlaybackStopped` 在 `VoiceController.play` 与 `ChatViewModel.speak` 里都按**正常结束**吞掉。
2. **按住就立刻结束**：`pointerInput` 的 key 里带了录音状态，状态一变手势协程被重启，正在按住的这次手势直接取消。修法：key 固定 `Unit`，状态用 `rememberUpdatedState` 取；手势用 `awaitEachGesture` + `try/finally` 保证任何情况下都收尾（不然录音线程会一直录）。

**真机验收（FIN-AL60 / Android 12）**

| 用例 | 结果 |
|---|---|
| 点麦克风 → 弹权限 → 允许 | ✅ 授予「仅使用期间允许」后进入语音模式：键盘图标 + 居中「按住说话」 |
| 按住 3s 不说话 → 松手 | ✅ 录音中有频谱与秒数（dump 到 `3s` / `松开发送`），松手后弹「未识别到语音内容」 |
| 按住说「你好。」松手 | ✅ 识别正确并**直接发送**（用户实测） |
| 上滑取消 | ✅ 横竖位移判定，松手丢弃（用户实测） |
| 操作栏朗读 | ✅ 出声、再点停止；停止后**不再弹失败提示** |
| 顶栏自动朗读 | ✅ 打开后 `files/datastore/user_settings.preferences_pb` 写入 `auto_speak=true`，重启后图标仍为品牌色；用户实测「生成完毕后自动触发朗读」 |
| 录满 120s 自动停 | ⬜ 未实测到点（逻辑与单测在，真机没等满 2 分钟） |

**还没做的（别当已完成）**

- 长文按句切分的**真机**效果没跑过（5000 字以上回答才会触发第二段），只有单测；
- 音色切到「暗潮」后的实际听感没验（只验了默认海星）；
- 朗读中切会话/删会话会停，但**没有**做「朗读进度」或「边生成边读」（服务端支持 WebSocket 流式，v1 明确不做）；
- 语音输入没有做「说话时间太短」的独立文案，<600ms 一律并入「未识别到语音内容」。



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

## 7.5 M7 定稿方案（2026-11-19，决策 23/24/25）

> 规格已写进 02：§4.7（圆环算法与位置、语音不计费）、§12.1（输入区版式）、§12.10（右侧用量抽屉 + 每轮统计条）、§6 WBS（M7-6 与 M8 缩减）。这里只记**执行顺序与坑**。

| 顺序 | 任务 | 关键约束 |
|---|---|---|
| 1 | **M7-6 凭据** | `SecretStore` 接口 + `KeystoreSecretBox`（AES-GCM，AndroidKeyStore 里的 key，密文+IV 进 DataStore）。**不用 androidx.security-crypto（已废弃）**。Keystore 在 Robolectric 里没有实现 → 单测只用假实现，真机验「加密/重启可用/卸载重装失效」。debug 构建 DataStore 为空时用 `BuildConfig.DEV_API_KEY` **播种一次**，否则每装一次都得手输。页面只做 DeepSeek 官方：粘贴/替换/清除/测试（`GET /models`，不计费） |
| 2 | **M7-1 计价** | 价格表进 `assets/pricing.json`（带 `effectiveFrom` 与 `limited` 标记），`costMicros` 用**微元整数**累加，别用 Double；峰谷判定 `Asia/Shanghai` + 工作日 9–12/14–18；单测锁 01 §2.4 三个算例 |
| 3 | **M7-2 圆环 + 右抽屉** | 视觉 18dp/2dp，触达 40dp；`<40` 品牌、`≥40` warning、`≥80` danger；**不做预估/虚线**，只读当前分支最后一条助手消息的 `usage.contextUsed`（决策 26），流式中不动。抽屉里是**进度条 + 会话级汇总**（决策 27，版式见 02 §12.10），不放本轮明细、不放脚注、不放刷新按钮 |
| 4 | **M7-3 统计条** | 设置开关**默认开**；一行 12sp：`4,096 命中  9,078 未命中  1,686 输出  ￥0.0259`；关掉整行不占位 |
| 5 | **M7-4 余额** | `GET /user/balance`；左抽屉底部那行从死行改成真实数据（60s 节流 + ↻）；失败显示 `--` 不弹错 |
| 6 | **M7-5 reminder** | 开关进 DataStore；星期用 `DayOfWeek.getDisplayName(TextStyle.FULL, locale)`，不手写 70 套 |

**开工前先打两枪（都不花钱）**

1. `GET /user/balance` 目前只是调研文档里抄的，**没实测**。先 curl 一次，确认 `balance_infos[].total_balance` 与 402 语义，再写客户端。
2. 确认价格表当前值（TTS 3→1.5、ASR 0.78→0.39 是限时价），把「限时」写进 JSON 的标记位，UI 不承诺长期有效。

**别踩的坑**

- 金额别用 `Float`/`Double` 展示（`￥0.0123` 会被算成 `0.0122999`）；一律微元 `Long`，格式化时才除。
- 圆环占位变化只该重绘不该重组：沿用 M5 频谱那套（`mutableIntStateOf` + 只在 `Canvas` draw lambda 里读）。
- 抽屉用 overlay + `slideInHorizontally`，**不要嵌套第二个 `ModalNavigationDrawer`**（会和左抽屉抢手势）。
- 会话合计用一条 `SUM(...)` 查询（含被丢弃分支，那是真花掉的钱），别把当前分支的消息加起来当合计。
- 设置里新增两行（`显示每轮用量`、`用户元信息`）会挤动现有顺序，按 02 §12.5 的分组放，别插到「语音」组。
- **`newChat()` 会从零重建 `ChatUiState`**：任何新增的全局字段（`hasApiKey`、`showUsage`、`usageSummary`、`balanceText`…）都要在那一处显式带过去，否则「开新对话」会把它悄悄清掉（M7 真机 bug：顶部误报「尚未配置服务商」）。同理，**不要**把「有没有 Key」这类跨页状态只存在 VM 的 state 里，直接读 `container.apiKey` 这条流。
- 服务商页按钮的可用性别绑「输入框非空」：用户手里已经有 Key 时，「测试连接」必须能测**已存**的 Key（`onTestStored`），否则永远灰着，看着像坏了。

### M7.5 提示词的坑（2026-11-19 验完）

- **`:app` 不引 Room 类型**：在 app 里写 `database.promptPresetDao()` 会报 `Cannot access androidx.room.RoomDatabase`。取 DAO 这步留在 core:data（`PromptPresetStore.create(database, …)`），别为了它给 app 加 room 依赖。
- **点一行 = 选中并打开**：不做「先选中、再点编辑」两步；选中即落库。
- **内置不可写要在存储层拦**：`save(id=内置)` 直接 `require(!builtin)` 抛错，别只靠 UI 禁用（UI 总会漏一条路径）。
- **快照存原文、发送时求值**：`sessions.system_prompt_snapshot` 里必须保留 `{{date}}` 原文，否则同一会话第二天还是旧日期。`ChatRepositoryPromptTest` 已断言「求值不污染库」。
- **删除当前选中的自定义预设要回退**到 `default`，否则下一个新会话拿到空快照。
- **`{{model}}` 用显示名**（`DeepSeek V4.1 Flash`），不是内部 id。
- **别用 shell 管道改 UTF-8 源码**：`Get-Content -Raw | … | Set-Content` 在中文字符上会把文件写坏（本次毁过一次 `PromptScreens.kt`，还吞了引号）。改代码一律走补丁工具。
- **`adb shell input text`**：只有 `%s` 表示空格（写 `%start` 会丢字符）；`{`/`}` 可输入，但要一个字符发一次命令。
- **UI 断言脚本**：`.local/ui.ps1 <dump.xml>` 打印「bounds | text | content-desc」。手写正则时 `$matches` 会被第二次 `-match` 覆盖，别在同一个表达式里连用两个 `-match` 再取组。

### 分享长图（M8-2）的坑

- **不要用 Dialog 装 ComposeView**：新窗口要自己造 window recomposer，Dialog 的 decor 上没有 `ViewTreeLifecycleOwner`，attach 时直接崩（`ViewTreeLifecycleOwner not found`）。挂到**当前 Activity 的 decorView** 里最省事。
- **别手动 measure 一个普通子 View**：decorView 每帧按 LayoutParams 重新量，你量成 1080 宽、下一帧又被量回去；Compose 里 `BoxWithConstraints` 宽度来回跳，`TcMath` 的 `LaunchedEffect(key=request)` 每帧重启 —— 公式永远停在「公式渲染中」。解法：外面套一层自己定尺寸的容器（`FixedWidthHost`），把宽度焊死。`ComposeView` 与 `onMeasure` 都是 final，不能用继承。
- **等高度等不出公式**：占位块高度固定，公式出图前后高度可能不变。要同时看 `TcRenderHost` 的渲染队列（`MathRenderState.inFlight()`）并先做 `MathPrewarm`。
- **必须切段立刻落盘**：1080×12000 的 ARGB_8888 是 51MB，攒三张就 OOM；预览只留首张的 1/2 缩略图。
- **`adb exec-out > file` 在 PowerShell 里会写坏 PNG**（走文本管道），要用 `cmd /c "adb ... > file"`。

### M8-1 / M8-6 服务商重写的坑（2026-11-19 验完）

- **`providerStore` 必须先播种再发请求**：仓库按 `session.provider_id` 查表，表里没有就先抛 `ProviderMissingException`。单测里忘了 `ensureSeeded()` 会以「服务商已移除」失败；`onSession` 打开老会话也依赖这一行。
- **`hasApiKey` 改成「当前服务商在 `keyedProviders` 里」**：只看 DeepSeek 那把会误报。`keyedProviders` 由容器在目录/凭据变化时重算（`refreshKeyedProviders()`），写 Key / 删服务商后要显式调一次。
- **`newChat()` 会重建 `ChatUiState`**：新字段（`providerId`/`providers`/`keyedProviders`/`quota`）漏带就会被清空；本次又踩了一次。凡新增跨页状态，先去 `newChat()` 补一行。
- **模型选择器按服务商分组后，已有消息的会话禁止换服务商**：`selectModel(providerId, modelId, effort)` 里有 `messages.isNotEmpty() && providerId != state.providerId` 的拦截；thinking 签名与缓存都跟服务商绑定，别放开。
- **`ModelInfo.displayName` 可能为空**（自定义模型只填了 ID）：保存时把空显示名归一成 ID，UI 再兜一层 `ifBlank { id }`，否则选择器里是一行空白。
- **DeepSeek 的显示名是 `DeepSeek V4.1 Flash`**：pill 直接显示会挤，`shortName()` 要去掉 `DeepSeek V4.1 ` / `DeepSeek ` 前缀，第三方再去掉 `org/` 与冒号前缀。
- **目录 tab 选中后必须继续渲染密钥与模型**：第一版把整个表单包在 `if (!catalogMode)` 里，结果选完 OpenCode Go 只能看见「已选择」，没地方填 Key。
- **`/models` 的 401 要翻成人话**：直接抛 `HTTP 401` 用户看不懂；`AccountClient.modelIds` 里把 401/403 映射成 `Key 无效`、404 映射成「该地址没有模型列表，请手动添加」。
- **校验失败别把点染红**：网络不通 / 5xx 写 `UNKNOWN`（灰点），只有 Key 被拒 / 402 才写 `FAILED`（红点）；否则用户一进地铁就看到一片红。
- **`SecretStore` 改成按 id** 后，所有旧调用点都要改；`read()` 无参版本删掉，迁移只发生在 `migrateLegacy()`（读旧键、写 `cipher.deepseek`、删旧键，已有新键不覆盖）。
- **`ChatRepository` 不再收 `apiKey` 参数**：发送/编辑/重生成/继续/重试全部内部按会话服务商取 Key；ViewModel 里只剩语音/余额还用 `container.apiKey`（DeepSeek 那把）。测试要传 `providerStore` + `apiKeyFor`。
- **DB 升到 v4**：`providers` 表迁移只建表不塞数据，播种在 `ProviderStore.ensureSeeded()`；迁移里写死模型 JSON 的话，以后改能力位还得再发一次迁移。
- **又把源码交给 PowerShell 改坏一次**（`ChatStreamRunner.kt` 的中文注释被写坏、还丢了一个换行）：结论不变——**改代码走补丁工具**，`Set-Content` 只用于纯 ASCII 的小替换。
- **目录 tab 拉取模型会“静默成功但界面不更新”**（2026-11-19 用户实测 OpenCode Go）：第一版 `currentModels` 读 `pickedCatalog.models`（常量），拉取成功分支又只处理「自定义 / 已保存的 Go」，于是模型被丢掉、也不报错。修法：模型统一以可变的 `models` 状态为准（选目录服务商时灌入），目录服务商拉取即替换、自定义追加；服务端返回空列表时显式提示 `服务端没有返回模型`，不要装作成功。
- **模拟器坐标会随内容高度漂移**：服务商表单在填完字段后会滚动/变矮，上一轮 dump 的坐标下一轮就点空。每次点击前重新 dump，别复用坐标。
- **费用/余额会“串家”**：费用表按模型 id 命中，第三方用同名模型（如 `deepseek-flash`）会算出 DeepSeek 的价格、显示 ￥；余额还会拿本机的 DeepSeek Key 去查账户。现在两处都按**服务商类型**收口（只有 `DEEPSEEK_OFFICIAL` 才算 ￥、才查余额），动这两块时别看模型 id。
- **「自定义」标签别用 `!builtin` 判断**：OpenCode Go 也是 `builtin=false`，会被误标成自定义服务商；标签只给 `kind == OPENAI_COMPATIBLE`。
- **思考参数发出去被静默忽略是常态**：DeepSeek 的 OpenAI 兼容端点对 `reasoning_effort` 不报错也不一定生效；我们只对**明确 400 且点名参数**的做降级重试，别把“没报错”当成“生效了”。

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

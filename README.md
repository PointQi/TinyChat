<div align="center">

<img src="docs/assets/logo.png" width="112" alt="TinyChat">

# TinyChat

轻巧纯净的 Android AI 聊天客户端，支持 DeepSeek 官方与 OpenAI / Anthropic 兼容接口。

<p>
  <a href="https://github.com/Takeoff0518/TinyChat/blob/master/LICENSE"><img src="https://img.shields.io/github/license/Takeoff0518/TinyChat" alt="License"></a>
  <img src="https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white" alt="Android 8.0 及以上">
  <a href="https://github.com/Takeoff0518/TinyChat/actions/workflows/ci.yml"><img src="https://github.com/Takeoff0518/TinyChat/actions/workflows/ci.yml/badge.svg" alt="CI"></a>
  <a href="https://github.com/Takeoff0518/TinyChat/releases"><img src="https://img.shields.io/github/v/release/Takeoff0518/TinyChat" alt="Release"></a>
</p>

</div>

TinyChat 是一款开源的 Android 聊天客户端，用来连接 DeepSeek 官方接口，以及其他兼容 OpenAI 或 Anthropic 接口的大模型。它的界面贴近官方客户端的操作习惯，轻巧纯净，且**所有数据都只保存在你的手机本地**。

> **关于这个仓库**：这里是 [Takeoff0518/TinyChat](https://github.com/Takeoff0518/TinyChat) 的 fork，应用名「TinyChat 工作版」，包名 `io.github.pointqi.tinychat`，可以和上游原版同时安装、互不影响。在上游基础上补了附件发送与专家模式；多服务商联网搜索（Tavily / AnySearch）仍在打通中，目前联网搜索只支持 DeepSeek 原生。签名与校验方式见[文末](#签名与校验)。

> **免责声明**：本应用为独立开发的第三方客户端，与深度求索（DeepSeek）官方没有任何关联，也未获得其授权或背书。「DeepSeek」等相关名称与商标归其各自权利人所有。

---

## 核心功能

- **流畅的实时对话**：支持多轮连续对话与流式输出，回答边生成边显示，无需干等整段写完。
- **查看思考过程**：支持一键展开深度思考模型的推理过程。
- **专家模式**：在模型面板里打开后，本轮改用 DeepSeek Harness 的 Agent 系统提示词，让它像编码助手那样带着工具思维回话；关掉即恢复会话原本的提示词。
- **联网搜索**：目前仅 DeepSeek 官方接口的原生联网搜索可用，在「设置 → 联网搜索」选择 DeepSeek，可直接复用已有的官方 API Key。Tavily、AnySearch 尚在打通中，暂时不可用。
- **发送图片与文件**：支持上传图片或文档让模型识别分析（具体支持哪些格式取决于你所选的模型）。
- **语音交互**：不想打字时可以直接语音输入，也可以让模型把回答朗读出来（仅支持 DeepSeek 官方接口）。
- **会话管理**：支持历史对话搜索、置顶、重命名以及批量删除。
- **用量与费用统计**：清晰展示每轮对话消耗的 Token/字数、预估花费以及当前账户余额。
- **生成长图分享**：一键将精彩的回答导出为图片，方便保存或分享给朋友。
- **无缝备份与恢复**：导出的数据文件与 DeepSeek 官方客户端格式完全一致，支持双向迁移。

---

## 使用前须知

1. **需要自备 API Key**：应用本身不包含任何 Key，也不提供代充或共享账号服务。你可以前往 [DeepSeek 开放平台](https://platform.deepseek.com/) 申请，或使用手头任意兼容 OpenAI 或 Anthropic 格式的服务商接口。
2. **按量计费由服务商收取**：TinyChat 软件完全免费，但每次对话消耗的 Token 额度会由你填入的服务商按其定价标准扣除。
3. **系统要求**：支持 **Android 8.0 及以上**系统，安装包体积仅约 **4 MB**。

---

## 快速上手

1. 下载并安装 TinyChat，打开应用。
2. 点击进入 **「设置 → 服务商与 API Key」**，选择或新增一家服务商。
3. 填入对应的接口地址（Base URL）和 **API Key** 并保存（日后随时可以修改）。

   > 💡 *小贴士：如果你使用的是 DeepSeek 官方服务，系统已经预置好了接口地址，通常只需填入 API Key 即可。*
   >
   > 💡 *如果对方走的是 Anthropic 协议，添加服务商时把协议选成 Anthropic Messages。地址填到域名就行，带不带 `/v1` 都可以，应用会自动补上路径。*

4. 回到聊天主界面，在底部输入框上方选择你想使用的模型。
5. 输入问题并发送，系统会自动为你创建第一个对话。

---

## 功能支持一览

不同服务商的接口能力有所差异，具体支持情况如下：

| 功能特性 | DeepSeek 官方接口 | 第三方（OpenAI 兼容接口） | 第三方（Anthropic 兼容接口） |
| --- | --- | --- | --- |
| **基础对话、流式输出、思考过程** | ✅ 支持 | ✅ 支持（具体效果取决于对方接口） | ✅ 支持（具体效果取决于对方接口） |
| **联网搜索** | ✅ 支持（DeepSeek 原生） | ❌ 暂不可用 | ❌ 暂不可用 |
| **发送图片** | ✅ 支持（视具体模型而定） | ✅ 启用模型的视觉能力后使用 | ✅ 启用模型的视觉能力后使用 |
| **发送文件** | ✅ 支持 | ✅ 纯文本。PDF 需启用模型文件输入且接口支持 | ✅ 纯文本。PDF 需启用模型文件输入且接口支持 |
| **语音输入、语音朗读** | ✅ 支持 | ❌ 不支持 | ❌ 不支持 |
| **余额与花费统计** | ✅ 显示实时余额与每轮花费 | 仅统计字数/用量，不估算金额 | 仅统计字数/用量，不估算金额 |

> 界面会根据当前所选模型的实际能力自动调整。如果切换模型后发现某个功能按钮不见了，说明该模型或服务商暂不支持此项能力。
>
> OpenCode Go 这类订阅制接口是个例外：它没有余额概念，界面改成正上方三个时间窗口的剩余额度。

---

## 隐私与数据安全

* **纯本地存储**：聊天记录、附件和应用设置保存在当前手机中。发送消息时，聊天服务商会收到对话请求；开启 DeepSeek 原生联网搜索时，用于检索的本轮问题会由 DeepSeek 处理。
* **系统级密钥加密**：你的 API Key 会通过 Android 系统原生的加密库（Android Keystore）安全存储，绝不会写入运行日志，也不会包含在导出的备份文件中。
* **本地崩溃日志**：万一应用发生崩溃，系统会在本地生成一份诊断报告。你可以在 **「设置 → 关于 → 诊断信息」** 中随时查看、复制（用于向开发者反馈）或彻底删除，它绝不会在后台自动上传。
* **彻底清除**：卸载应用或清除应用数据后，本地保存的所有对话记录与 API Key 都会被一并删除。

---

## 备份与恢复

你可以在 **「设置 → 数据管理」** 中随时导出或导入聊天记录。导出的文件格式与 DeepSeek 官方客户端完全通用，两边的数据可以互相导入。

> ⚠️ **注意**：导入备份采用的是**整包覆盖**模式——导入成功后，手机里现有的所有对话都会被备份包里的内容替换掉。在执行导入前，强烈建议先将当前数据导出一份作为备份！

---

## 当前已知限制

受限于客户端定位与接口机制，以下情况目前暂不支持，请知悉：

* **仅支持前台生成**：为保证轻量化，本版本未做后台保活。回答生成期间如果切到后台或锁屏，生成过程可能会被系统中断。
* **无云端多端同步**：应用没有自建服务器，换新手机时请通过「导出 → 导入」手动迁移数据。
* **语音功能限制**：语音输入和朗读依赖 DeepSeek 官方接口，使用第三方服务商时会自动隐藏这两个按钮。
* **Anthropic 兼容接口只走文字**：这条协议目前只支持纯文本对话，图片与文件不会随消息发送；需要发图请改用 OpenAI 兼容接口且模型本身支持读图，或者使用 DeepSeek 官方接口。
* **仅支持中文界面**：目前 UI 仅提供简体中文。设置里的「语言」选项是用来控制发给大模型的提示词语言，并不会改变应用本身的界面文字。
* **联网搜索只支持 DeepSeek 原生**：目前只有 DeepSeek 官方接口的原生联网搜索可用；第三方搜索（Tavily / AnySearch）仍在打通中，选它们不会生效。
* **接口能力边界**：识图、文件输入取决于所选模型是否声明支持，客户端据此隐藏对应入口；搜索入口始终显示，没有可用的搜索服务时点击只会提示去「设置 → 联网搜索」配置。

---

## 更新记录

### 1.0.4（versionCode 5）

* 联网失败不再一律显示「请检查网络后重试」：超时、域名解析失败、连不上分别给出提示，超时注明 120 秒。
* 搜索失败时，搜索卡片的标题会带上真实原因，便于判断是网络问题还是配置问题。
* 「字体大小」改为全局生效，此前只缩放消息正文，设置页与列表不动。
* AnySearch 不再强制要求密钥（其公开接口本身不需要鉴权）。

### 1.0.2（versionCode 3）

* 安装包身份独立为 `io.github.pointqi.tinychat`，可与上游原版同时安装。
* 支持向 OpenAI / Anthropic 兼容接口发送图片与文件。
* 新增专家模式：本轮改用 DeepSeek Harness 的 Agent 系统提示词。
* 联网搜索接入 Tavily / AnySearch 的代码路径（仍在打通中，暂不可用）。

---

## 自行编译构建

如果你想修改源码或自己打包 APK，可以按照以下步骤操作：

1. **准备环境**：安装 Android Studio（自带 JDK 17），或自行配置好 JDK 17 与 Android SDK 环境。
2. **加载项目**：用 Android Studio 打开项目根目录，等待 Gradle 自动同步并下载依赖。
3. **打包调试版**：在终端运行 `./gradlew assembleDebug`（Windows 系统请运行 `gradlew.bat assembleDebug`）。
4. **安装体验**：编译完成后，安装包位于 `app/build/outputs/apk/debug/app-debug.apk`，发送到手机即可安装。

**进阶配置（可选）：**

* **发布正式版**：如需打包签名的 Release 版本，请将你的签名密钥文件放在本地，并在项目根目录创建 `.local/keystore.properties` 填好路径、密码和别名，随后执行 `./gradlew assembleRelease`。（若不配置此文件也可编译，但生成的未签名包无法直接安装）。

---

## 签名与校验

本仓库（TinyChat 工作版）的包名是 `io.github.pointqi.tinychat`，与上游不同，所以两者可以装在同一台手机上、互不覆盖；签名也是独立的一把。当前安装包的信息如下：

| 项目 | 值 |
| --- | --- |
| 包名（applicationId） | `io.github.pointqi.tinychat` |
| 版本 | `1.0.4`（versionCode 5） |
| 签名方案 | APK Signature Scheme v2（RSA 3072） |
| 证书主体 | `CN=TinyChat Work Fork, O=PointQi` |
| 证书 SHA-256 | `8E:7F:17:9C:41:59:6E:86:81:A7:4E:06:F7:56:5B:09:77:8C:10:AE:B3:5D:53:E5:BC:16:50:E3:84:6C:60:DB` |
| 证书 SHA-1 | `40:FC:80:A5:58:41:1D:DE:BA:BD:9E:B1:40:A3:D0:EE:FC:B4:22:0C` |
| 证书 MD5 | `77:F2:92:AF:B0:C0:64:5C:74:10:74:1A:C5:48:FA:20` |

装之前想确认手里这个包是不是本仓库发的，用下面任意一条核对即可：

```bash
# 方式一：Android SDK 的 apksigner，看输出的 certificate SHA-256 digest
apksigner verify --print-certs TinyChat-Work-v1.0.4.apk

# 方式二：只用 JDK，不用装 Android SDK
keytool -printcert -jarfile TinyChat-Work-v1.0.4.apk
```

指纹和上表对不上，就说明这个包不是从本仓库构建的（可能被二次打包），请不要安装。

> 这里公开的是**证书指纹**，不是私钥。签名密钥和口令只存在本地 `.local/`（已被 `.gitignore` 排除）和仓库的 Actions Secrets 里，从不进入版本库——把私钥公开，等于把「以本应用名义发布任意更新」的能力交给所有人。

---

## 常见问题（FAQ）

**Q：API Key 要去哪里获取？**
A：如果你用 DeepSeek 官方服务，前往 [DeepSeek 开放平台](https://platform.deepseek.com/) 注册账号，在控制台的「API keys」页面创建即可；如果用其他服务商，请前往对应平台的控制台获取。

**Q：为什么界面上看不到账户余额？**
A：查询余额需要调用特定的账单接口，目前只有 DeepSeek 官方接口提供此数据。连接第三方服务商时，界面只会统计每轮对话的字数。

**Q：为什么「联网搜索」只能用 DeepSeek 官方？**
A：目前只有 DeepSeek 官方接口的原生搜索可用。「设置 → 联网搜索」里的 Tavily、AnySearch 仍在打通中，选了不会生效，先按 DeepSeek 用（可复用已填的官方 Key）。

**Q：搜索失败了怎么看原因？**
A：搜索失败时，消息里那张搜索卡片的标题会带上真实原因（例如 `联网搜索暂不可用：请求超时：超过 120 秒没有收到数据`），可以据此判断是网络问题还是配置问题。

**Q：导入备份后，我原来的聊天记录去哪了？**
A：导入操作会用备份文件完全替换掉当前手机上的对话列表。为防数据丢失，导入前请务必先将现有记录导出备份。

**Q：应用闪退或崩溃了怎么办？**
A：直接重新打开即可继续使用（崩溃瞬间正在生成的那条回答可能未能保存）。如果遇到频繁崩溃，欢迎前往 **「设置 → 关于 → 诊断信息」** 复制本地错误日志，提交 Issue 反馈给我。

**Q：应用会向 DeepSeek 官方上传我的使用统计吗？**
A：完全不会。除了你主动发送给大模型的对话内容外，TinyChat 不包含任何数据统计或追踪代码，不会向任何地方上报你的使用情况。

---

## 开源许可与致谢

* 本项目基于 **MIT 许可证** 开源，版权归 `Copyright (c) 2026 Takeoff0518` 所有。
* 界面排版、配色方案与交互逻辑参考了官方开源项目 [deepseek-ai/chat-demo](https://github.com/deepseek-ai/chat-demo)（MIT 许可证），在此表示诚挚感谢。
* 「专家模式」的系统提示词移植自 [deepseek-ai/deepseek-harness](https://github.com/deepseek-ai/deepseek-harness)（MIT 许可证），许可证原文随安装包一起分发。
* 完整的第三方开源组件及许可证清单，请参阅仓库根目录下的 `NOTICE` 文件，或在应用内前往 **「设置 → 关于 → 开源许可」** 查看。

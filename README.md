<div align="center">

<img src="docs/apexmark-logo.png" width="120" height="120" alt="ApexMark Logo" />

# ApexMark

**Markdown → 富文本，一键转换**

[![Download APK](https://img.shields.io/github/v/release/raymondx-byte/ApexMark?label=Download%20APK&logo=github&color=2DA44E)](https://github.com/raymondx-byte/ApexMark/releases/latest)
[![License: AGPL-3.0](https://img.shields.io/badge/License-AGPL_v3-blue.svg)](https://www.gnu.org/licenses/agpl-3.0)
[![Commercial License Available](https://img.shields.io/badge/Commercial-Available-green.svg)](#开源协议--license)
[![Platform](https://img.shields.io/badge/Platform-Android%208.0%2B-brightgreen.svg)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-7F52FF.svg?logo=kotlin)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4.svg)](https://developer.android.com/jetpack/compose)

### 📥 [**Download the latest APK →**](https://github.com/raymondx-byte/ApexMark/releases/latest)

[简体中文](#简体中文) · [English](#english) · [Releases](https://github.com/raymondx-byte/ApexMark/releases) · [Issues](https://github.com/raymondx-byte/ApexMark/issues)

</div>

---

## 简体中文

### 这是什么？

从 AI 聊天工具（ChatGPT、Claude、Gemini……）复制的回答都是 **Markdown 纯文本**，直接粘贴到微信、飞书、钉钉、WPS 时排版全部丢失——代码块变纯文字、表格没有框线、加粗斜体全部消失。

**ApexMark** 解决的就是这个痛点：**复制 → 点一下 → 粘贴出富文本**。

它是一个**极轻量的"转换流"工具**，嵌入系统各个层级，随时待命。安装包小、内存占用低、零后台开销 —— 它不抢资源，只在你点击时工作。

| 入口        | 操作                       | 场景      |
| --------- | ------------------------ | ------- |
| **悬浮球**   | 短按 MD → 富文本，长按 富文本 → MD | 全局任何界面  |
| **通知栏按钮** | 两个按钮直接显示，下拉即点         | 通知栏常驻 |
| **分享菜单**  | 从其他 App 分享文本进来           | 系统分享入口  |
| **主界面**   | 打开 App 直接双向转换             | 最直接的方式  |

### 设计理念

| 维度       | 目标                                                |
| -------- | ------------------------------------------------- |
| **核心痛点** | AI 时代 Markdown 与移动办公套件（微信/WPS/飞书）之间的格式鸿沟           |
| **核心能力** | 双向无损转换：MD ↔ 富文本（HTML，剪贴板 MIMETYPE 双载荷）            |
| **设计原则** | 极轻量 · 低资源 · 零网络 · 零追踪 · 零后台监听 · 100% 本地 · 触发即用 |

### 核心特性

#### 🔄 转换引擎 (Apex-Link)

- 基于 [Flexmark-java](https://github.com/vsch/flexmark-java) 的高性能 Markdown ↔ HTML 双向解析
- **全内联 CSS** — 不依赖 `<style>` 标签，确保第三方 App 正确渲染
- **表格强制框线** — `<table border="1" cellpadding="5">` + 每个 `<td>` 独立 border
- **微信/WPS 深度兼容** — `<td>` 强制白底、`<tr>` 内联边框、表头蓝底白字
- **双格式剪贴板** — 同时写入 HTML + Plain Text，富文本和纯文本场景都能粘贴
- **多空行压缩** — 自动将连续空行合并为一行，粘贴后排版干净
- **反向转换** — 富文本/HTML → Markdown，把 Word/网页内容快速结构化

#### 🫧 悬浮球 + 通知栏 (Floating Portal)

- 品牌 Logo 圆形悬浮球，自由拖动 + 自动贴靠 + 半隐藏
- **短按转换**（MD → 富文本）· **长按反向**（富文本 → MD），带触觉反馈
- **通知栏常驻**：MD → 富文本 / 富文本 → MD 两个按钮直接显示，无需展开
- **前台直转**：App 在前台时直接调用引擎，杜绝任务切换动画
- **省电优化** — 灭屏自动隐藏并暂停所有动画，常驻状态零定时器、零轮询

#### 🎨 软件美学

- **明 / 暗双主题** — 跟随系统或手动切换
- **品牌色谱** — 源自图标深蓝渐变 (`#0050B0` → `#3380E0`)
- 暗色模式采用 **深蓝灰**（非纯黑），保留品牌色温
- Material 3 设计语言 + 统一圆角体系

#### 🌐 多语言

支持 12 种语言：英文、简/繁中文、日、韩、法、德、意、俄、葡、西、阿。

#### 🪶 轻量 & 低资源（设备实测）

实测设备：Android `dumpsys` 真机采样，App 切后台 30s 后稳态：

| 指标 | 实测值 |
|------|--------|
| **APK 体积** | **1.74 MB**（R8 minify + 资源压缩 + 死代码全部剥离后） |
| **稳态 CPU**（后台 idle） | **0.0%**（连续 3 次采样 / `top -d 1`） |
| **稳态 PSS** | **~55-60 MB**（其中约 40 MB 为 Android 框架共享内存） |
| **熄屏 PSS** | **~55 MB**，27 MB 被 ZRAM 压缩换出 |
| **深度 Doze CPU** | **0.0%**（`dumpsys deviceidle force-idle deep` 验证） |
| **AlarmManager / JobScheduler / WakeLock** | **全部为空**，零隐藏唤醒 |
| **累计电池消耗（背景）** | `cpu:bg` 在 `batterystats` 中不出现 |

代码层保证：

- **常驻态零定时器** — 整个 Service 没有任何 `postAtTime`/`Timer`/`WorkManager`/`Worker`/协程轮询
- **事件驱动唤醒** — 仅注册 `ACTION_SCREEN_ON/OFF` 两个系统广播；其余唤醒全部来自用户触摸或通知栏点击
- **灭屏自动节流** — 悬浮球在屏幕关闭瞬间 `visibility=GONE`、所有 `Animator` 立即 `cancel()`
- **共享转换引擎** — Service / 透明 Activity / MainActivity 三处共用同一份 `MarkdownConverter` 实例（Flexmark parser 只初始化一次）
- **前台直转优化** — App 在前台时直接调用引擎，跳过透明 Activity 桥接，零任务切换开销
- **单一前台 Service** — 只为通知栏常驻保留一个轻量 Service，无 IPC、无额外进程、无 worker
- **冷启即用** — 进程一启动就在 main looper 首位排队启动通知，毫秒级可用

#### 🛡️ 安全 & 隐私

- 所有转换 **100% 本地执行**，零网络请求，零数据上传
- 仅在用户主动点击时读取剪贴板，不监听后台
- 超过 1MB 的内容自动拒绝同步处理，防止卡死
- 大文本异步转换，UI 始终响应

### 架构

```
com.apexmark/
├── engine/
│   ├── MarkdownConverter.kt        # 核心引擎 (MD ↔ HTML，剪贴板双格式)
│   └── StyleStyler.kt              # 内联 CSS 注入器 (微信/WPS 兼容)
├── service/
│   ├── FloatingPortalService.kt    # 悬浮球 + 通知栏前台 Service
│   └── ClipboardConvertActivity.kt # 桥接 Activity (Android 10+ 剪贴板访问)
├── receiver/
│   └── QuickActionReceiver.kt      # 快捷指令广播
├── ui/                              # Compose UI + Material 3 主题
└── MainActivity.kt                  # 主入口 + 权限引导 + About / 主题
```

### 技术栈

| 类别          | 技术                                                 |
| ----------- | -------------------------------------------------- |
| 语言          | Kotlin 2.0                                         |
| UI          | Jetpack Compose + Material 3                       |
| Markdown ↔ HTML | Flexmark-java (GFM Tables / Strikethrough / Html2Md) |
| 包体大小（release） | **1.74 MB**                                    |
| 最低系统        | Android 8.0 (API 26)                               |
| 目标系统        | Android 14 (API 34)                                |

### 构建

```bash
git clone https://github.com/raymondx-byte/ApexMark.git
cd ApexMark
./gradlew assembleDebug         # debug APK
./gradlew test                  # 单元测试
./gradlew assembleRelease       # 已签名 release APK（需要 keystore.properties）
```

### 安装

ApexMark 通过 **GitHub Releases** 分发，**不上架** Google Play / 国内应用商店。

📥 **[点此下载最新版 APK](https://github.com/raymondx-byte/ApexMark/releases/latest)**（约 1.74 MB）

1. 打开 [Releases 页](https://github.com/raymondx-byte/ApexMark/releases)
2. 下载最新版 `app-release.apk`（约 1.74 MB）
3. 在手机上打开 APK 文件
4. 首次安装时，系统会提示「来自此来源的应用」未授权 → 允许即可
5. 安装完成后按 App 内引导授予「悬浮窗」权限

> 因为没走应用商店审核，可以从签名指纹核验来源是否被篡改：
> SHA-1: `89:29:AA:01:2F:AD:A9:0E:6B:57:7E:56:A6:1D:AA:F0:3E:C2:18:FF`
> 在手机上安装后查看「设置 → 应用 → ApexMark → 应用详情 → 应用签名」对比即可。

### 使用方法

1. 打开 ApexMark，按提示授予 **悬浮窗权限**
2. 点击「启动悬浮球」— 悬浮球 + 通知栏转换按钮同时就绪
3. 从 ChatGPT / Claude / Gemini 复制回答
4. 点击悬浮球（或通知栏按钮）→ 剪贴板自动变为富文本
5. 粘贴到微信 / WPS / 飞书 / 钉钉

**反向使用**：复制富文本（网页、Word 内容），**长按悬浮球**或点通知栏「富文本 → MD」即可得到 Markdown。

### 兼容性测试

| 目标 App | 表格框线 | 代码底色 | 加粗/斜体 | 链接  |
| ------ | ---- | ---- | ----- | --- |
| 微信聊天   | ✅    | ✅    | ✅     | ✅   |
| WPS 文档 | ✅    | ✅    | ✅     | ✅   |
| 飞书文档   | ✅    | ✅    | ✅     | ✅   |
| 钉钉聊天   | ✅    | ⚠️   | ✅     | ✅   |
| 邮件客户端  | ✅    | ✅    | ✅     | ✅   |

### 权限说明

| 权限                              | 用途                   | 必须？ |
| ------------------------------- | -------------------- | --- |
| `SYSTEM_ALERT_WINDOW`           | 显示悬浮球                | 可选  |
| `FOREGROUND_SERVICE`            | 保持悬浮球/通知服务存活         | 必须  |
| `FOREGROUND_SERVICE_SPECIAL_USE`| Android 14+ 前台 service | 必须  |
| `POST_NOTIFICATIONS`            | 通知栏转换按钮 (Android 13+) | 必须  |

**隐私承诺**：ApexMark 不联网、不上传、不后台读取剪贴板。所有操作仅在用户主动触发时执行。

---

## English

### What is ApexMark?

When you copy answers from AI chat tools (ChatGPT, Claude, Gemini…) and paste them into WeChat, Feishu, DingTalk, WPS, or any rich-text editor, all formatting is lost — code blocks become plain text, tables lose their borders, bold/italic vanish.

**ApexMark** solves this in one tap: **copy → tap → paste as rich text**.

It is a **featherweight "conversion pipe"** woven into every system layer. Small footprint, low memory, zero background work — it never steals resources and only runs when you tap.

| Entry point      | Action                                | Scenario          |
| ---------------- | ------------------------------------- | ----------------- |
| **Floating bubble** | Tap = MD → Rich, long-press = Rich → MD | Any screen, anywhere |
| **Notification**    | Two buttons, no expand required        | Always-on in shade |
| **Share menu**      | Receive text from any app              | System share intent |
| **Main screen**     | Bidirectional one-tap                  | Most direct       |

### Design philosophy

| Dimension      | Goal                                                                          |
| -------------- | ----------------------------------------------------------------------------- |
| **Pain point** | The format gap between AI-era Markdown and mobile office suites               |
| **Core**       | Lossless bidirectional conversion: MD ↔ HTML via dual clipboard MIME          |
| **Principles** | Lightweight · low-resource · zero network · zero tracking · zero background polling · 100% local |

### Key features

#### 🔄 Conversion engine (Apex-Link)

- High-performance bidirectional Markdown ↔ HTML powered by [Flexmark-java](https://github.com/vsch/flexmark-java)
- **Fully inlined CSS** — no `<style>` blocks, every third-party renderer cooperates
- **Hardened tables** — `<table border="1" cellpadding="5">` plus inline `<td>` borders
- **WeChat / WPS friendly** — forced white-on-color rows, header background, padded cells
- **Dual clipboard payload** — HTML + Plain Text written together for universal paste
- **Blank-line collapsing** — 3+ blank lines reduce to one for clean paste
- **Reverse conversion** — Rich Text / HTML back to clean Markdown

#### 🫧 Floating bubble + persistent notification

- Branded circular bubble, draggable, auto-snap, half-hidden idle state
- **Tap** = MD → Rich, **long-press** = Rich → MD (with haptic feedback)
- **Notification with two action buttons** (MD → Rich / Rich → MD), no need to expand
- **Foreground shortcut** — when the app is in front, conversion runs in-process to avoid task-switch animations
- **Battery friendly** — bubble hides on screen-off, zero timers, zero polling

#### 🎨 Design

- Light / dark / system themes
- Brand palette derived from the logo (`#0050B0` → `#3380E0`)
- Dark mode uses a deep blue-gray (not pure black) to preserve brand warmth
- Material 3 components everywhere

#### 🌐 Localization

12 languages out of the box: English, Simplified & Traditional Chinese, Japanese, Korean, French, German, Italian, Russian, Portuguese, Spanish, Arabic.

#### 🪶 Lightweight & low-resource (measured on device)

Measured on a real device via Android `dumpsys`, sampled at the 30 s post-backgrounding steady state:

| Metric | Measured |
|--------|----------|
| **APK size** | **1.74 MB** (R8 minify + resource shrink + full dead-code removal) |
| **Steady-state CPU** (background idle) | **0.0%** (3 consecutive `top -d 1` samples) |
| **Steady-state PSS** | **~55–60 MB** (~40 MB of which is shared Android-framework memory) |
| **Screen-off PSS** | **~55 MB**, 27 MB compressed into ZRAM |
| **Deep-Doze CPU** | **0.0%** (verified with `dumpsys deviceidle force-idle deep`) |
| **AlarmManager / JobScheduler / WakeLocks** | **all empty** — zero hidden wake-ups |
| **Cumulative background battery** | `cpu:bg` does not appear in `batterystats` |

Code-level guarantees:

- **Zero timers in the resident state** — no `postAtTime`, no `Timer`, no `WorkManager`, no coroutine polling
- **Event-driven wake-ups only** — registers `ACTION_SCREEN_ON/OFF` broadcasts; every other wake comes from user touch or notification tap
- **Screen-off throttling** — the bubble's `visibility=GONE` and all `Animator`s `cancel()` instantly on screen-off
- **Shared conversion engine** — service / bridging Activity / MainActivity share a single `MarkdownConverter` instance (Flexmark parser is initialized once)
- **In-process fast path** — when the app is in the foreground, conversion runs directly without the bridging Activity, eliminating task-switch overhead
- **One lean foreground service** — kept alive only to host the persistent notification; no IPC, no extra processes, no workers
- **Cold-start ready** — the notification is enqueued at the head of the main looper the moment the process boots

#### 🛡️ Privacy

- All conversion runs **100% on-device** — no network calls, no telemetry, no uploads
- Clipboard is only read on explicit user action; never monitored in the background
- Payloads over 1 MB are rejected from the synchronous path to keep the UI snappy
- Large texts convert off the main thread

### Architecture

```
com.apexmark/
├── engine/        Markdown ↔ HTML core, inline-CSS styler
├── service/       Foreground service for bubble + notification, bridging activity
├── receiver/      Broadcast receivers for quick actions / shortcuts
├── ui/            Jetpack Compose screens, Material 3 theme
└── MainActivity.kt  Entry, permissions, About & theme sheets
```

### Tech stack

| Layer            | Technology                                            |
| ---------------- | ----------------------------------------------------- |
| Language         | Kotlin 2.0                                            |
| UI               | Jetpack Compose · Material 3                          |
| Markdown ↔ HTML  | Flexmark-java (GFM Tables, Strikethrough, Html2Md)    |
| Release APK size | **1.74 MB**                                           |
| minSdk           | Android 8.0 (API 26)                                  |
| targetSdk        | Android 14 (API 34)                                   |

### Build

```bash
git clone https://github.com/raymondx-byte/ApexMark.git
cd ApexMark
./gradlew assembleDebug      # debug APK
./gradlew test               # unit tests
./gradlew assembleRelease    # signed release APK (needs keystore.properties)
```

### Install

ApexMark is distributed exclusively through **GitHub Releases** — it is **not** published to Google Play or any other store.

📥 **[Download the latest APK](https://github.com/raymondx-byte/ApexMark/releases/latest)** (~1.74 MB)

1. Open the [Releases page](https://github.com/raymondx-byte/ApexMark/releases)
2. Download the latest `app-release.apk` (~1.74 MB)
3. Open the APK on your phone
4. On first install your phone will warn that "apps from this source aren't verified" — allow it for ApexMark
5. After installing, follow the in-app prompt to grant the **Display over other apps** permission

> Since this APK doesn't go through a store, you can verify the build hasn't been tampered with using the SHA-1 signing fingerprint:
> `89:29:AA:01:2F:AD:A9:0E:6B:57:7E:56:A6:1D:AA:F0:3E:C2:18:FF`
> On Android, go to **Settings → Apps → ApexMark → App info → App signature** and compare.

### Usage

1. Open ApexMark and grant the **Display over other apps** permission
2. Tap **Start Floating Bubble** — the bubble and the notification become ready
3. Copy Markdown from any AI chat tool
4. Tap the bubble (or notification button) — clipboard is now rich text
5. Paste into WeChat / WPS / Feishu / DingTalk / Email

**Reverse flow**: Copy rich text (web page, Word document), **long-press the bubble** or tap the notification's **Rich → MD** button to get clean Markdown back.

### Compatibility matrix

| Target app   | Table borders | Code background | Bold / italic | Links |
| ------------ | :-: | :-: | :-: | :-: |
| WeChat       | ✅ | ✅ | ✅ | ✅ |
| WPS Office   | ✅ | ✅ | ✅ | ✅ |
| Feishu       | ✅ | ✅ | ✅ | ✅ |
| DingTalk     | ✅ | ⚠️ | ✅ | ✅ |
| Gmail / mail | ✅ | ✅ | ✅ | ✅ |

> ⚠️ = visual degradation only, text content is fully preserved.

### Permissions

| Permission                          | Purpose                                       | Required? |
| ----------------------------------- | --------------------------------------------- | --------- |
| `SYSTEM_ALERT_WINDOW`               | Draw the floating bubble                      | Optional  |
| `FOREGROUND_SERVICE`                | Keep the converter alive                      | Required  |
| `FOREGROUND_SERVICE_SPECIAL_USE`    | Android 14+ foreground service compliance     | Required  |
| `POST_NOTIFICATIONS`                | Show the persistent convert notification      | Required  |

**Privacy promise**: ApexMark never connects to the internet, never reads the clipboard in the background, and never uploads anything. All conversion happens locally on your device.

---

## 贡献 · Contributing

Issues and pull requests are welcome. To submit a PR:

1. Fork the repo
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit (`git commit -m 'Add amazing feature'`)
4. Push (`git push origin feature/amazing-feature`)
5. Open a Pull Request

Please make sure to:

- Match the existing code style
- Add unit tests for new features
- Describe the *why* of your change clearly in the PR

---

## 开源协议 · License

ApexMark is released under a **dual license**:

### AGPL-3.0

Source is published under the [GNU Affero General Public License v3.0](https://www.gnu.org/licenses/agpl-3.0.html). The full text is in [LICENSE](LICENSE).

You are free to:

- ✅ **Use** the program for any purpose
- ✅ **Study** how it works and modify it
- ✅ **Share** verbatim copies
- ✅ **Improve** it and share your improvements

Key obligations:

- 📛 **Copyleft** — derivatives must be released under AGPL-3.0 with full source
- 🌐 **Network use is distribution** — if you modify and serve ApexMark to users over a network, those users must receive the modified source
- 🔗 **Notice preservation** — keep all copyright, license, and disclaimer notices

### Commercial License

If you want to use ApexMark **without the AGPL-3.0 obligations** — e.g.:

- Embedding ApexMark into a closed-source commercial product
- Deploying a modified version inside a company without publishing source
- Building a SaaS on top of ApexMark with proprietary additions

Please contact the author for a commercial license:

📧 **Email**: [raymondxiang.zm@gmail.com](mailto:raymondxiang.zm@gmail.com)

> **Short version**: personal use, study, and open-source projects are free; closed-source commercial use requires a commercial license.

---

<div align="center">

Made with ❤️ for mobile productivity · by **Raymond X**

</div>

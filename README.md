<div align="center">

# 码上取 · MashangQu

**一个 App，取遍所有驿站。**

拼多多 · 淘宝菜鸟 · 京东 · 顺丰 · 如意邻里 —— 身份码与快递信息一站式聚合

[![Platform](https://img.shields.io/badge/platform-Android%207.0%2B-3DDC84?logo=android&logoColor=white)](#)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.x-7F52FF?logo=kotlin&logoColor=white)](#)
[![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?logo=jetpackcompose&logoColor=white)](#)
[![Min SDK](https://img.shields.io/badge/minSdk-26-34A853)](#)
[![Release](https://img.shields.io/github/v/release/Haven-Lv/MaShangQu-APP?color=F57C00)](https://github.com/Haven-Lv/MaShangQu-APP/releases)
[![License](https://img.shields.io/badge/license-可自用修改·禁商用-lightgrey)](#-许可与免责声明)

</div>

---

<div align="center">

<p>
  <img src="docs/images/page-identity.png" width="240" />
  <img src="docs/images/page-express.png" width="240" />
  <img src="docs/images/page-mine.png" width="240" />
</p>

*身份码大屏出示 ｜ 快递列表聚合 ｜ 登录态与日志管理*

</div>

## 🔧 本 Fork 改动（风控加固版 v1.0.8-rh3）

基于上游 [njpemo/MaShangQu-APP](https://github.com/njpemo/MaShangQu-APP) v1.0.8，围绕「降低账号风控风险」的防御性加固，不改变既有功能：

| 类别 | 改动 |
|---|---|
| 修复请求风暴 | 身份码过期重取失败后，旧逻辑保留过期码导致 tick 每秒重发请求；现失败即移除过期码 + 指数退避 + 重取在途去重 |
| 失败退避 | 全平台连续失败按 1min→2h 指数退避（成功清零），静默自动刷新跳过退避中的平台，手动刷新不受限 |
| 刷新去脚本化 | ON_RESUME 静默全量刷新节流 30s→3min；平台间加 150-600ms 随机间隔（原先 5 平台 2 秒内背靠背连发） |
| 真实设备 UA | 启动采集本机 WebView UA，清洗 `; wv` / `Version/4.0` 为标准 Chrome 移动端形态（`DeviceUa`），替代写死的 Nexus 5 / Windows 桌面等矛盾 UA；未显式设 UA 的请求由拦截器统一补齐（原先裸发 `okhttp/x.x`） |
| TLS 指纹 | `ChromeTls`：OkHttp ConnectionSpec 密码套件对齐 Chrome 移动端。真机 adb reverse + PC 抓 ClientHello 验证：15 个套件集合完全一致；Conscrypt 套件重排 / 无 GREASE 的天花板已在代码注释如实说明 |
| 安全面收紧 | 移除 `usesCleartextTraffic` 与 `REQUEST_INSTALL_PACKAGES`；停用应用内更新（fork 签名与上游蒲公英渠道不同，OTA 永远无法覆盖安装） |

**明确不做**：umidToken/设备指纹伪造、TLS 字节级伪装（需换 Cronet 网络栈）、anti-content 强化——前两者属主动对抗平台检测且当前无必要，后者实测正在通过、强化零现收益。

**构建说明**：国内网络下 Maven Central CDN 会按 TLS 指纹拦截 Java 客户端（403），`settings.gradle.kts` 已配置阿里云镜像优先；本机无 Android SDK 时可运行仓库根目录 `install-sdk.ps1` 一键安装工具链。详见提交历史 `b72d1dc`（rh1）/ `2121f77`（rh2）/ `98773cf`（rh3）。

## ✨ 核心特性

| | 特性 | 说明 |
|:---:|---|---|
| 🎫 | **五平台身份码聚合** | 拼多多 / 淘宝菜鸟 / 京东 / 顺丰 / 如意邻里，大幅二维码条码一屏出示，下拉刷新 + 可配置自动刷新；顺丰身份码**本地公式生成**，零网络调用 |
| 📦 | **快递列表聚合** | 多平台站点包裹统一视图，按平台 / 站点筛选，跨平台按运单号去重合并、互补查码，本地缓存离线可查 |
| 🔢 | **取件码直达** | 单件快递取件码大字大图展示，驿站柜台一步到位 |
| 🔐 | **登录态守护** | 启动自动巡检各平台凭据有效性，失效标红、一键重登 |
| ⬆️ | **应用内更新** | 启动静默检查新版本，应用内下载 APK 直接拉起安装 |
| 📋 | **调试日志导出** | 内置实时日志面板，支持一键导出 txt 排查问题 |

## 🛡️ 隐私与架构

**无后端依赖、数据不出设备。** 登录凭据存放于本机 Keystore + EncryptedSharedPreferences 加密存储，所有请求由 App 直连平台接口，不经过任何第三方服务器。

```
┌────────────────────────── app（宿主壳）──────────────────────────┐
│   身份码页 ｜ 快递页 ｜ 我的页 ｜ 导航 ｜ 应用内更新             │
├──────────────────────────── core ────────────────────────────────┤
│   model（统一模型）  network（OkHttp）  storage（加密存储/Room） │
│   webview（通用登录）  yyb（远程签发服务）                       │
├────────────────────────── platform ──────────────────────────────┤
│   api（PlatformAdapter 契约）                                    │
│   pdd ｜ taobao ｜ jd ｜ sf ｜ ryll —— 各平台适配器实现          │
└──────────────────────────────────────────────────────────────────┘
```

### 登录方式

| 平台 | 方式 |
|---|---|
| 淘宝 | 手机号 + 短信验证码（App 内协议登录，含风控二次验证） |
| 京东 / 顺丰 / 拼多多 | 微信 code 换链（YYB-GO 远程签发，App 直连平台换链） |
| 如意邻里（圆通） | 手机号 + 图形验证码 + 短信验证码（全程 App 内完成） |

## 📲 下载安装

> **本 Fork**：从 [**Releases**](https://github.com/Haven-Lv/MaShangQu-APP/releases) 下载加固版，当前版本：
>
> [mashangqu-v1.0.8-rh3.apk](https://github.com/Haven-Lv/MaShangQu-APP/releases/download/v1.0.8-rh3/mashangqu-v1.0.8-rh3.apk)（SHA256 见 Release 说明）
>
> 也可从源码构建（见下方折叠面板）。上游官方版仍从 [njpemo/mashangqu Releases](https://github.com/njpemo/mashangqu/releases) 获取。

上游官方版支持 App 内更新；**本 Fork 已停用应用内更新**（签名与上游不同无法覆盖安装），升级请下载新 APK 或重新构建。本 Fork 包与上游包互不兼容，需卸载另一方后全新安装。日常使用建议 release 包；debug 包请求日志含 Cookie 等敏感信息，仅排查问题时使用。

<details>
<summary><b>从源码构建</b></summary>

- Android Studio ｜ Kotlin 2.x + Jetpack Compose ｜ minSdk 26 / targetSdk 34

```bash
cd app
.\gradlew.bat assembleDebug
```

产物位于 `app/app/build/outputs/apk/`。

</details>

<details>
<summary><b>发版机制</b></summary>

更新源为自建接口 `https://msq.l2.ink/api/check.php`（虚拟主机 PHP 代理，服务端持有蒲公英 Key，见 `server/check.php`）：

1. 修改 `update/latest.json` 版本号，`.\gradlew.bat assembleRelease` 构建；
2. APK 上传蒲公英即完成发版——check.php 自动返回最新包信息（versionCode 取包上传时间，零手动维护）；
3. 客户端语义化比对版本号，仅在有新版时弹窗提示。

</details>

## 🗂️ 项目结构

```
app/
├── app/                宿主壳：页面、导航、应用内更新
├── core/
│   ├── model/          统一数据模型与错误模型
│   ├── network/        OkHttp/Retrofit 基础配置
│   ├── storage/        Keystore 加密存储、Room 缓存、DataStore 设置
│   ├── webview/        通用登录 WebView 组件
│   └── yyb/            YYB-GO 远程服务 HTTP 客户端
└── platform/
    ├── api/            PlatformAdapter 接口契约
    └── pdd/ taobao/ jd/ sf/ ryll/   平台适配器实现
```

## 📜 许可与免责声明

**许可**：欢迎个人学习、下载、修改与自用；**禁止商用**（包括但不限于出售、打包收费分发、引流变现）。

**免责**：本项目按「现状」提供，作者不对功能可用性作任何保证。需要特别说明的是：

- 项目部分功能基于对各平台公开客户端接口的研究实现，可能涉及平台用户协议中关于未授权调用的限制，请自行知悉并评估相关风险；
- 登录凭据仅存储于本机（Keystore + EncryptedSharedPreferences 加密），永不上传、不采集任何用户数据；
- 任何人使用或修改本项目产生的一切后果（包括但不限于账号风控、功能限制、封禁及其他损失），均由使用者自行承担，**与作者无关**。

---

<div align="center">

**码上取** — 少装几个 App，快一点取到快递

</div>

<h1 align="center">JavBoss Android</h1>

<p align="center">JavBoss 的 Android 客户端：一套代码同时适配 <b>手机 / 平板 / Android TV</b>，连接你自己部署的 JavBoss 服务端，在移动端与电视上浏览媒体库并播放。</p>

<p align="center">
  <a href="../../releases"><img alt="Release" src="https://img.shields.io/github/v/release/guiyyo/javboss-android?display_name=tag"></a>
  <a href="../../stargazers"><img alt="Stars" src="https://img.shields.io/github/stars/guiyyo/javboss-android?style=social"></a>
  <img alt="Platform" src="https://img.shields.io/badge/Platform-Android%20%7C%20Android%20TV-3DDC84?logo=android&logoColor=white">
  <img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-2.1-7F52FF?logo=kotlin&logoColor=white">
  <img alt="Android" src="https://img.shields.io/badge/Android-5.1%20~%2016-1E88E5">
</p>

## 这是什么

[JavBoss](https://github.com/Solr159/JavBoss) 是一个本地媒体库管理服务端。本项目是它的 **Android 客户端**：
不需要把文件下载到手机，直接连你家里或 NAS 上的服务端，在手机、平板、电视上浏览媒体库并播放。

> **本仓库只有客户端**，服务端请前往 [Solr159/JavBoss](https://github.com/Solr159/JavBoss)。
> 客户端不含任何内容源，所有数据都来自你自己部署的服务端。

## 系统要求

| 端 | 支持范围 | 说明 |
| --- | --- | --- |
| 手机 / 平板 | **Android 5.1（API 22）～ Android 16（API 36）** | 实测 Android 7.1、9；Android 16 为编译目标版本 |
| Android TV / 电视盒子 | **Android 5.1 及以上**，需 Android TV 系统或允许侧载 | 实测 Android 9 电视 |
| CPU 架构 | `arm64-v8a`、`armeabi-v7a` | x86 模拟器请用调试包（额外含 `x86_64`） |
| 屏幕 | 手机 / 平板 / 电视横屏自动适配 | 布局与字号按屏幕宽度自适应 |

## 为什么用它？

- **一套代码三种形态**：手机单列、平板多列双栏、电视遥控器焦点导航，各自按屏幕自适应，不是简单放大的手机界面。

- **为电视而生**：完整的方向键焦点系统、焦点记忆（返回后回到上一次停留的条目）、焦点移动不误切页、播放器支持连按快进快退与倍速切换。

- **播放能力完整**：直连 / HLS 双源自动切换、播放进度记忆、继续播放 / 从头播放（带倒计时自动继续）、倍速、画中画（手机端）。

- **检索与筛选对齐网页端**：支持多维度组合筛选（类型、标签、人物、厂牌、系列、编号前缀、观看状态、评分、收藏夹）与多种排序。

- **隐私可控**：手机端指纹锁 + 隐私模式（可关闭截屏保护）；电视端数字密码锁 + 超时锁屏，播放中不会被锁。

- **细节跟随服务端**：海报横版 / 竖版、每行卡片数、标题行数、字段显隐等，都会读取服务端配置，也可在本机覆盖。

## 快速开始

### 1. 部署服务端

先在 NAS / 电脑 / Docker 里跑起 JavBoss 服务端，记下访问地址（形如 `http://192.168.1.1:8655`）与访问密钥。详见[服务端文档](https://github.com/Solr159/JavBoss#快速开始)。

### 2. 安装客户端

前往 [Releases](../../releases) 下载最新 `javboss-android-<版本>-arm64-armv7.apk`：

- **手机 / 平板**：直接安装，允许「安装未知来源应用」；
- **Android TV**：用 U 盘、`adb install`，或电视上的文件管理器安装；
- **自动更新**：安装 [Obtainium](https://github.com/ImranR98/Obtainium)，添加本仓库地址，之后新版本会自动提示更新。

### 3. 登录

首次启动填写服务端地址与访问密钥，会话以 Cookie 保存，之后自动登录。

## 功能概览

**通用**
- 海报墙瀑布流、无限滚动、全库检索、多种排序
- 多维度筛选：类型、标签、人物、厂牌、系列、编号前缀、观看状态、评分、收藏夹
- 详情：海报、编号、日期、时长、评分、收藏夹、标签、剧集列表
- 播放：直连 / HLS 双源、进度记忆、继续播放 / 从头播放
- 收藏夹管理与筛选、观看记录、海报样式与列数可调

**手机 / 平板**
- 底部导航多入口，手机单列 / 平板多列
- 平板详情双栏：左栏海报与基础信息，右栏剧集与分类信息
- 手势：双击快进快退、长按倍速、左右滑动调亮度与音量
- 支持将海报保存到相册、画中画
- 指纹锁、隐私模式

**Android TV**
- 全部操作可用遥控器完成，焦点记忆与分层返回逻辑
- 电视专属设置：每行卡片数、默认排序、默认倍速、密码锁（数字键盘输入）、超时锁屏
- 播放器：连按快进快退、倍速切换、信息条与播放队列
- 诊断日志：手机扫码即可查看（本机临时网页）

## 常见问题

**Q: 提示连接失败？**
A: 确认手机/电视与服务端在同一局域网；浏览器能打开 `http://你的服务器地址:8655`；服务端防火墙放行端口。

**Q: 海报全是空白？**
A: 海报请求带登录凭据，确认已成功登录；服务端尚未生成海报时也会空白，可在服务端重新生成。

**Q: 电视装不上？**
A: 电视需要 `armeabi-v7a` 或 `arm64-v8a`；x86 模拟器请用调试包。更多见 [docs/FAQ.md](docs/FAQ.md)。

**Q: 会上传我的数据吗？**
A: 不会。客户端只与你填写的服务端地址通信，没有第三方服务器与统计 SDK，代码全部开源可查。

## 反馈问题（Bug / 建议）

遇到问题或想提建议，请到 [Issues](../../issues/new/choose) 提交，仓库里已配好模板，按提示填即可。

为了能快速定位，请尽量附上：

| 需要提供 | 从哪看 |
| --- | --- |
| 设备与系统版本 | 例如「Android 9 电视盒子」/「Android 15 手机」 |
| 哪一端 | 手机 / 平板 / Android TV |
| App 版本号 | 设置页最底部显示 |
| 复现步骤 | 1. 打开… 2. 点击… 3. 出现… |
| 实际现象 / 期望结果 | 看到的是什么、本来应该是什么 |
| 日志 | 设置 → 诊断日志：电视端可用手机扫码查看，手机端可导出后粘贴 |

⚠️ 提交前请**隐去服务端的局域网地址、访问密钥、个人目录路径**等信息；日志里若包含这些，请先删掉再贴。

> 提交前建议先翻一下 [docs/FAQ.md](docs/FAQ.md)——连接失败、海报空白、电视安装失败等常见问题那里已有答案。
## 开发者

| 文档 | 内容 |
| --- | --- |
| [docs/USAGE.md](docs/USAGE.md) | 使用说明：登录、三端操作、按键与手势 |
| [docs/BUILD.md](docs/BUILD.md) | 构建、签名与 CI 发布 |
| [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md) | 技术栈、项目结构、常用命令 |
| [docs/FAQ.md](docs/FAQ.md) | 常见问题 |

```bash
git clone https://github.com/guiyyo/javboss-android.git
cd javboss-android/android
./gradlew assembleDebug     # 调试包（额外含 x86_64，可跑模拟器）
./gradlew assembleRelease   # 正式包（arm64-v8a + armeabi-v7a）
```

## 致谢

- [Solr159/JavBoss](https://github.com/Solr159/JavBoss)：服务端与数据接口来源
- [Jetpack Compose](https://developer.android.com/jetpack/compose) / [Media3](https://developer.android.com/media/media3) / [Coil](https://coil-kt.github.io/coil/)：UI 与播放基础

## 风险提示与免责声明

- 本项目是第三方客户端，仅供学习与交流，与 [JavBoss](https://github.com/Solr159/JavBoss) 服务端作者无隶属关系。
- 本项目**只提供客户端程序**，不提供、不存储、不分发任何视频、图片或元数据内容。
- 使用本项目时，请遵守所在国家或地区的法律法规，请勿用于任何商业用途。
- 用户应自行承担使用本项目产生的一切后果，开发者不对用户的任何使用行为承担法律责任。
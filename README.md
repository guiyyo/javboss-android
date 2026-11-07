<h1 align="center">JavBoss Android</h1>

<p align="center">JavBoss 的 Android 客户端：一套代码适配 <b>手机 / 平板 / Android TV</b>，连接你自己部署的 JavBoss 服务端，在移动端与电视上浏览媒体库、检索内容、播放并管理字幕。</p>

<p align="center">
  📖 <b><a href="docs/USER-GUIDE.md">客户端使用手册</a></b>　服务器地址填写 · 连接排查 · 手机 / 平板 / 电视操作 · 播放器与字幕 · 更新与隐私
  <sub>遇到具体问题可先查 <a href="docs/FAQ.md">常见问题速查</a></sub>
</p>

<p align="center">
  <a href="../../releases"><img alt="Release" src="https://img.shields.io/github/v/release/guiyyo/javboss-android?display_name=tag"></a>
  <a href="../../releases"><img alt="Downloads" src="https://img.shields.io/github/downloads/guiyyo/javboss-android/total?color=2ea44f"></a>
  <a href="../../stargazers"><img alt="Stars" src="https://img.shields.io/github/stars/guiyyo/javboss-android?style=social"></a>
  <a href="../../issues"><img alt="Issues" src="https://img.shields.io/github/issues/guiyyo/javboss-android"></a>
  <a href="../../commits/main"><img alt="Last commit" src="https://img.shields.io/github/last-commit/guiyyo/javboss-android"></a>
  <a href="LICENSE"><img alt="License" src="https://img.shields.io/github/license/guiyyo/javboss-android"></a>
</p>

<p align="center">
  <img alt="Platform" src="https://img.shields.io/badge/Platform-Android%20%7C%20Android%20TV-3DDC84?logo=android&logoColor=white">
  <img alt="Android" src="https://img.shields.io/badge/Android-5.1%20~%2016-1E88E5">
  <img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-2.1-7F52FF?logo=kotlin&logoColor=white">
  <img alt="Compose" src="https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?logo=jetpackcompose&logoColor=white">
  <img alt="Player" src="https://img.shields.io/badge/Player-Media3%20%7C%20ExoPlayer-FF6F00">
</p>

<p align="center">
  <b>三端自适应</b> · 手机 / 平板 / Android TV　|　<b>遥控器焦点导航</b> · 焦点记忆与分层返回　|　<b>字幕</b> · 多源搜索 / 偏移微调 / 代理加速　|　<b>应用内更新</b> · 检查 · 下载 · 安装
</p>

## 这是什么

[JavBoss](https://github.com/Solr159/JavBoss) 是一个本地媒体库管理服务端（自动扫描、生成海报、抓取元数据、提供检索与播放）。

本项目是它的 **Android 客户端**：不需要把文件下载到手机，直接连你家里或 NAS 上的服务端，就能在手机、平板、电视上浏览媒体库、检索内容、播放视频并管理字幕。

> **本仓库只有客户端**，服务端请前往 [Solr159/JavBoss](https://github.com/Solr159/JavBoss)。
> 客户端不含任何内容源，所有数据都来自你自己部署的服务端。

## 系统要求

| 端 | 支持范围 | 说明 |
| --- | --- | --- |
| 手机 / 平板 | **Android 5.1（API 22）～ Android 16（API 36）** | 已在 Android 7.1、9 实测；Android 16 为编译目标 |
| Android TV / 电视盒子 | **Android 5.1 及以上**，需 Android TV 系统或允许侧载 | 已在 Android 9 电视实测 |
| CPU 架构 | `arm64-v8a`、`armeabi-v7a` | x86 模拟器请用调试包（额外含 `x86_64`） |
| 分辨率 | 手机 / 平板 / 电视横屏自动适配 | 布局与字号按屏幕宽度自适应 |

## 快速开始

**1. 部署服务端**：在 NAS / 电脑 / Docker 里跑起 JavBoss，记下访问地址（形如 `http://192.168.1.1:8655`）与访问密钥。详见[服务端文档](https://github.com/Solr159/JavBoss#快速开始)。

**2. 安装客户端**：前往 [Releases](../../releases) 下载 `javboss-android-<版本>-arm64-armv7.apk`（发布页文件名由维护者发布时重命名后上传；仓库与 CI 只产出 `app-release.apk`）。

- 手机 / 平板：直接安装，首次需允许「安装未知来源应用」
- Android TV：U 盘、`adb install`，或电视上的文件管理器安装

**3. 登录**：填写服务端地址与访问密钥，会话以 Cookie 保存，之后自动登录。

**4. 后续升级**：App 内置更新检查——发现新版本可直接在应用内下载并安装（带进度条，支持多个加速镜像与一键测速，自动选用最快镜像）。

## 功能详解

### 浏览与检索

| 功能 | 说明 |
| --- | --- |
| 海报墙 | 自适应瀑布流，按屏幕宽度自动排列，滚动到底自动加载下一页 |
| 全库检索 | 支持按编号、标题、人物等关键词搜索，输入即查 |
| **搜索容错** | 番号输入自动归一化：`snos079`、全角 `ＳＮＯＳ－０７９`、`snos 079` 与 `SNOS-079` 等价 |
| **搜索历史** | 搜索框为空时显示最近 12 条历史，可一键清空 |
| **随便看看** | 顶栏「随机」拉取一批随机作品替换当前列表，由你自己挑；顶部提示条可一键返回原列表。电视端为列表页头部的骰子按钮 |
| 多种排序 | 最近更新、时长、评分、观看次数等，同一项再次点击切换升降序 |
| 多维度筛选 | 类型、标签、人物、厂牌、系列、编号前缀、观看状态、评分、收藏夹，可任意组合 |
| 分类浏览 | 人物 / 厂牌 / 系列 / 编号前缀 各有列表页，点任意条目直接进入该条件的筛选结果 |
| 观看记录 | 记录看过的内容与进度，可继续上次位置 |
| 收藏夹 | 按收藏夹分组管理，列表页可直接按收藏夹筛选 |

### 详情与整理

| 功能 | 说明 |
| --- | --- |
| 详情信息 | 海报、编号、日期、时长、简介、剧集（文件）列表 |
| 评分 | 五档星级，点同一颗可取消 |
| 收藏夹 | 一键加入/移出收藏夹，支持多分组 |
| 标签 | 查看与编辑标签，标签管理页支持新建、重命名、删除、批量删除、调整分类 |
| 信息编辑 | 可修改编号、标题、人物、厂牌、系列等元数据 |
| 刮削 | 支持手动触发刮削、回填结果 |
| 截图 | 播放时截帧，支持截图列表与预览 |
| 平板布局 | 左右双栏：左侧海报与基础信息，右侧剧集与分类信息，一屏看完 |
| **备份与恢复** | 设置、观看进度、字幕状态与选择、搜索历史打包为单个 JSON；导入分「仅恢复数据」「全部恢复」，确认框会列出将被改动的服务端地址 / 代理 / 镜像 |
| **分享** | 备份、已匹配字幕（ZIP）、字幕索引（JSON）、诊断日志均可直接调用系统分享 |

### 播放与字幕

| 功能 | 说明 |
| --- | --- |
| 播放源 | 直连 / HLS 双源，可手动切换，失败自动换源 |
| 进度 | 记忆播放位置；进入已看过的内容会提示「继续播放 / 从头播放」，10 秒后自动继续 |
| 播放控制 | 倍速（0.5x–2x）、快进快退、播放队列、画中画（手机端） |
| 手势（手机/平板） | 单击显示控制条、双击左右快进快退、长按倍速、左右滑动调亮度与音量 |
| **字幕来源** | 在线搜索（多源轮询）、已下载、本地文件夹（SAF 授权）、直链四种 |
| **字幕处理** | 自动识别编码（UTF-8 / UTF-16 / GBK / BIG5），ASS·SSA·VTT 自动转 SRT |
| **字幕偏移** | ±1s / ±0.5s / ±0.1s 微调，即时生效，不会重新加载视频 |
| **内嵌字幕** | 视频自带字幕轨时可在播放器字幕面板直接切换；硬字幕片源可标记「本片无需字幕」，之后不再自动挂外挂字幕 |
| 字幕记忆 | 按编号记住所选字幕与偏移，下次播放自动套用 |
| **字幕加速** | 可配置「网络代理」（HTTP / SOCKS5，主机 + 端口）或「字幕源反向代理」（主机 + 端口 + 协议）。仅作用于字幕、更新等外网请求，访问自家服务端不受影响；不配置则直连 |
| **字幕排序与过滤** | 搜索结果中文优先（简中 > 繁中 > 其它中文 > 其他语言）；「仅中文字幕」开关（默认开） |
| **字幕容错** | 下载失败自动切换下一条候选（最多 3 条）；搜索成功后后台预热首条候选；已解析地址缓存，重试不重复抓取；支持 SRT / ASS / SSA / VTT（`.sub` 按 SRT 解析） |
| **字幕状态管理** | 每部影片记录「已匹配 / 无字幕 / 无需字幕」状态，以角标显示在卡片上；筛选面板中可按「字幕状态」过滤 |
| **批量补齐** | 一键为缺失字幕的作品自动搜索下载：严格编号匹配、人工翻译优先、请求限速、后台运行、进度可续；无匹配项进入待复核列表 |
| **字幕备份与同步** | 导出已匹配字幕（ZIP，`番号.语言.srt`）、导出 / 导入字幕索引，多台设备共享结果 |

### 服务端管理与工具

| 功能 | 说明 |
| --- | --- |
| 目录管理 | 查看服务端目录、触发扫描（显示「扫描中 N 秒」与发现文件数）、删除目录 |
| 数据源 | 查看与切换服务端数据源 |
| 下载设置 | 配置下载相关参数 |
| 服务器状态 | 查看服务端运行状态与任务进度 |
| 诊断日志 | 记录崩溃与关键日志；可在设置中导出或分享日志文件（电视端导出为 `javboss-log.txt`） |
| 账号 | 查看账号信息、修改凭据 |

### 隐私与安全

| 功能 | 说明 |
| --- | --- |
| 指纹锁（手机/平板） | 冷启动与设备锁屏后需指纹/密码解锁 |
| 隐私模式 | 开启后禁止截屏与任务预览（手机 / 平板）；电视端不启用该保护 |
| 密码锁（电视） | 4–6 位数字密码，遥控器数字键盘输入，输满自动解锁；连续错 5 次锁定 30 秒 |
| 超时锁屏（电视） | 无操作达到设定时间自动锁屏；播放视频期间不触发 |
| 本地数据 | 设置、观看进度、字幕索引等仅保存在本机，不上传第三方 |

### 三端体验差异

| | 手机 | 平板 | Android TV |
| --- | --- | --- | --- |
| 导航 | 底部多入口 | 同手机（多列布局） | 左侧栏 + 遥控器焦点 |
| 卡片列数 | 单列（可调） | 多列（默认 3 列） | 可设 3/4/5/6，竖版海报默认 4 列 |
| 详情页 | 单列纵向 | 左右双栏 | 左右双栏 |
| 操作方式 | 触摸 + 手势 | 触摸 + 手势 | 方向键 / OK / 返回 / 菜单键 |
| 设备识别 | 自动（手机界面） | 自动（平板界面） | 自动进入电视界面（也可在设置里强制切换） |
| 输入方式 | 系统键盘 | 系统键盘 | 按 OK 才进入输入，支持遥控器数字键 |
| 字幕入口 | 播放器右侧「字幕」按钮 | 同手机 | 字幕键 / 菜单键 |
| 字幕设置 | 设置 → 字幕（来源、代理、语言偏好、批量补齐、索引导入导出） | 同手机 | 设置 → 字幕分组（代理、批量补齐、字幕与索引导入导出） |
| 更新 | 应用内检查与安装 | 同手机 | 设置内检查、切换镜像、测速 |

### TV 遥控器按键

| 按键 | 功能 |
| --- | --- |
| 方向键 | 移动焦点；侧栏焦点移动不切页，按 OK 才切页 |
| OK | 确认 / 播放暂停 |
| 返回 | 关闭弹窗 → 回到侧栏 → 返回上一页 |
| ← / → | 播放中快退 / 快进（可连按累加） |
| ↑ / ↓ | 播放中信息条与队列 / 切换播放倍速 |
| 字幕键、菜单键 | 播放中打开字幕面板 |
| 设置键 | 打开应用设置（部分电视系统会占用该键，此时用侧栏「设置」） |

## 常见问题

**Q: 提示连接失败？**
A: 确认设备与服务端在同一局域网；浏览器能打开服务端地址；服务端所在电脑的防火墙放行端口（Windows 需手动加入站规则）。详见 [docs/FAQ.md](docs/FAQ.md)。

**Q: 海报全是空白？**
A: 海报请求带登录凭据，确认已成功登录；服务端尚未生成海报时也会空白，可在服务端重新刮削。

**Q: 电视装不上？**
A: 电视需 `armeabi-v7a` 或 `arm64-v8a`；x86 模拟器请用调试包。

**Q: 会上传我的数据吗？**
A: 除更新检查（GitHub API）与字幕检索（字幕站）外，客户端只与你填写的服务端地址通信，不向第三方上传数据，也不含统计 SDK，代码全部开源可查。

**Q: 字幕加载很慢或者一直失败？**
A: 字幕源站本身响应慢（常见 16–45 秒），并非客户端卡住。可在「设置 → 字幕」里配置**网络代理**（填你已有的代理，如 `192.168.1.1:7890`）或**字幕源反向代理**（自建反代，主机:端口），配置后通常 1 秒内出结果。

更多问题见 [docs/FAQ.md](docs/FAQ.md)。

## 反馈问题（Bug / 建议）

遇到问题或想提建议，请到 [Issues](../../issues/new/choose) 提交（仓库已配置模板）。

| 需要提供 | 从哪看 |
| --- | --- |
| 设备与系统版本 | 例如「Android 9 电视盒子」/「Android 15 手机」 |
| 哪一端 | 手机 / 平板 / Android TV |
| App 版本号 | 设置页最底部 |
| 复现步骤 | 1. 打开… 2. 点击… 3. 出现… |
| 实际现象 / 期望结果 | 看到的是什么、本来应该是什么 |
| 日志 | 设置 → 诊断日志（可导出 / 分享日志文件；电视端导出为 `javboss-log.txt`） |

⚠️ 提交前请**隐去服务端的局域网地址、访问密钥、个人目录路径**；日志里若包含请先删掉。

## 开发者

| 文档 | 内容 |
| --- | --- |
| [docs/USER-GUIDE.md](docs/USER-GUIDE.md) | **客户端使用手册（完整）**：服务器地址填写、连接排查、三端操作、播放器与字幕、更新与隐私设置 |
| [docs/BUILD.md](docs/BUILD.md) | 构建、签名与 CI 发布 |
| [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md) | 技术栈、项目结构、常用命令 |
| [docs/FAQ.md](docs/FAQ.md) | **常见问题速查**：现象 → 一句话处理 → 跳转手册对应章节 |

```bash
git clone https://github.com/guiyyo/javboss-android.git
cd javboss-android/android
./gradlew assembleDebug     # 调试包（额外含 x86_64，可跑模拟器）
./gradlew assembleRelease   # 正式包（arm64-v8a + armeabi-v7a）
```

## 贡献者

<a href="https://github.com/guiyyo"><img src="https://github.com/guiyyo.png?size=80" width="48" alt="guiyyo"></a>
<a href="https://github.com/Solr159"><img src="https://github.com/Solr159.png?size=80" width="48" alt="Solr159"></a>

- **guiyyo** — 客户端作者 / 维护者
- **Solr159** — 上游 [JavBoss](https://github.com/Solr159/JavBoss) 服务端作者（本项目的接口来源）

完整名单与第三方组件致谢见 [CONTRIBUTORS.md](CONTRIBUTORS.md)。欢迎提交 Issue 或 PR。

## 致谢

- [Solr159/JavBoss](https://github.com/Solr159/JavBoss)：服务端与数据接口来源
- [Jetpack Compose](https://developer.android.com/jetpack/compose)：界面
- [Media3 / ExoPlayer](https://developer.android.com/media/media3)：播放
- [Coil](https://coil-kt.github.io/coil/)：图片加载
- [Retrofit](https://square.github.io/retrofit/) · [OkHttp](https://square.github.io/okhttp/) · [kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization)：网络与序列化
- [DataStore](https://developer.android.com/topic/libraries/architecture/datastore)：本地设置存储
- [androidx.biometric](https://developer.android.com/jetpack/androidx/releases/biometric)：指纹解锁
- 完整清单与许可证见 [CONTRIBUTORS.md](CONTRIBUTORS.md)

## 风险提示与免责声明

- 本项目是第三方客户端，仅供学习与交流，与 [JavBoss](https://github.com/Solr159/JavBoss) 服务端作者无隶属关系。
- 本项目**只提供客户端程序**，不提供、不存储、不分发任何视频、图片或元数据内容。
- 使用本项目时，请遵守所在国家或地区的法律法规，请勿用于任何商业用途。
- 用户应自行承担使用本项目产生的一切后果，开发者不对用户的任何使用行为承担法律责任。
# 开发者文档

## 技术栈

| 层 | 选型 |
| --- | --- |
| 语言 | Kotlin 2.1.x |
| UI | Jetpack Compose + Material 3（自绘 TV 焦点系统） |
| 播放 | AndroidX Media3（ExoPlayer） |
| 网络 | Retrofit + OkHttp + kotlinx.serialization |
| 图片 | Coil 2.x |
| 存储 | DataStore Preferences |
| 安全 | androidx.biometric（手机指纹）、SHA-256 PIN（TV） |

## 项目结构

```
android/
├─ app/src/main/java/com/javboss/android/
│  ├─ MainActivity.kt          # 手机 / 平板入口
│  ├─ TvMainActivity.kt        # Android TV 入口（LEANBACK_LAUNCHER）
│  ├─ core/                    # 网络、日志、锁、媒体工具
│  ├─ data/
│  │  ├─ api/                  # Retrofit 接口定义（对齐服务端 186 个路由）
│  │  ├─ model/                # DTO
│  │  ├─ repo/                 # 仓库层
│  │  ├─ settings/             # DataStore 偏好（UiPrefs / SettingsStore）
│  │  └─ history/              # 本机观看进度
│  ├─ di/AppGraph.kt           # 手写依赖容器
│  └─ ui/
│     ├─ nav/AppRoot.kt        # 手机底部导航 + 路由
│     ├─ jav/                  # 列表、筛选、详情
│     ├─ library/               # 人物 / 厂牌 / 系列
│     ├─ player/                # 手机播放器
│     ├─ favorites/ settings/   # 收藏夹、设置
│     └─ tv/                    # TV 全套：TvApp、组件、筛选、详情、播放器、设置
├─ app/src/test/               # 单元测试
└─ gradle/libs.versions.toml   # 版本目录
```

## 常用命令

```bash
./gradlew assembleDebug        # 调试包
./gradlew assembleRelease      # 正式包
./gradlew testDebugUnitTest    # 单元测试
./gradlew lint                 # Lint
./gradlew installDebug         # 装到已连接设备
```

## 调试技巧

- **三端 UI 一起调**：手机/平板/电视共用同一份 `ui/` 组件库，改布局时注意 `LayoutSpec`（按屏幕宽度给出 Compact/Medium/Expanded）。
- **TV 焦点**：卡片用 `TvFocusable`，页面用 `TvScreenKey` + `rememberTvFocusRestorer()` 做焦点记忆。
- **封面样式**：服务端 `jav_card_portrait` 决定竖版/横版；客户端 `coverStyle` 可覆盖。
- **日志**：TV 端「设置 → 诊断日志」起一个本机临时 HTTP 服务，手机扫码即可看全文；手机端走 `CrashLogger` + 系统分享。

## 提交规范

`feat: 新功能`、`fix: 修复 xxx`、`docs: 文档`、`chore: 杂项`。
# 构建与发布

## 环境要求
| 项 | 版本 |
| --- | --- |
| JDK | 17 或 21（推荐 21） |
| Android SDK | Platform 36 + Build-Tools 36.0.0 |
| Gradle | 8.13（仓库自带 wrapper） |
| Kotlin | 2.1.x（由构建脚本管理） |

> 以上为本地构建要求；CI 使用 `ubuntu-latest`，依赖 runner 预装的 SDK 与 Build-Tools。

## 本地构建
```bash
cd android

# 调试包（含 x86_64，可跑模拟器）：app/build/outputs/apk/debug/
./gradlew assembleDebug

# 正式包（arm64-v8a + armeabi-v7a）：app/build/outputs/apk/release/
./gradlew assembleRelease
```

没有 SDK 时，先在 `local.properties`（不提交）里写：
```properties
sdk.dir=/path/to/Android/Sdk
```

## 签名（正式包必需）

在 `android/keystore.properties`（**不提交**）里写：
```properties
storeFile=release.jks
storePassword=******
keyAlias=******
keyPassword=******
```
`release.jks` 放在 `android/` 目录下。仓库的 `app/build.gradle.kts` 会自动读取；
若该文件不存在，则 `assembleRelease` 产出的是**未签名包**。
CI 侧由 workflow 依据 Secrets 自动生成同内容的 `android/keystore.properties`（见下节），因此 CI 产出的正式包已签名。

## CI（GitHub Actions）

仓库自带两个 workflow：

| 文件 | 触发 | 作用 |
| --- | --- | --- |
| `.github/workflows/build.yml` | push / PR | 编译调试包 + 运行单元测试（失败即构建失败）+ 上传调试产物 |
| `.github/workflows/release.yml` | **手动触发**（Actions → Release → Run workflow） | 编译正式包并上传为 Actions 产物；**不会自动创建 Release** |

> Release 页面与 APK 附件由维护者在网页手动创建与上传，见下方「发版流程」。

### 配置签名 Secrets
仓库 → **Settings → Secrets and variables → Actions → New repository secret**：

| Secret | 值 |
| --- | --- |
| `KEYSTORE_BASE64` | keystore 文件的 base64：`base64 -w0 release.jks`（Windows：`[Convert]::ToBase64String([IO.File]::ReadAllBytes("release.jks"))`） |
| `KEYSTORE_PASSWORD` | keystore 密码 |
| `KEY_ALIAS` | 别名 |
| `KEY_PASSWORD` | 别名密码 |

workflow 会用这四个 Secrets 生成 `android/keystore.properties` 供 Gradle 读取，所以 CI 产出的正式包是已签名的；
未配置 `KEYSTORE_BASE64` 时 workflow 会打印提示并产出**未签名包**（仅供验证，不可发布）。

> CI 运行在 `ubuntu-latest`，依赖 runner 预装的 Android SDK / Build-Tools；
> 若换用未预装 SDK 的 runner，需要在 workflow 中自行添加 Android SDK 安装步骤。

### 发版流程

1. 修改 `android/app/build.gradle.kts` 的 `versionCode` / `versionName`
2. 本地构建并核对版本：
   ```powershell
   powershell -NoProfile -File .\scripts\build-android.ps1 -Tasks assembleRelease
   powershell -NoProfile -File .\scripts\verify-apk-version.ps1
   ```
3. 同步源码与文档到仓库目录：`scripts\sync-repo.ps1`（同时把 APK 复制到 `dist/` 并执行脱敏）
4. 提交并推送源码（**APK 不进仓库**）：
   ```bash
   git add -A
   git commit -m "release: vX.Y.Z（简要说明）"
   git push
   ```
5. 在网页创建 Release：**Releases → Draft a new release**
   - Tag：`vX.Y.Z`（Create new tag）
   - 标题与说明：填写本次 Release 说明
   - 附件：上传 `dist/javboss-android-X.Y.Z-arm64-armv7.apk`
   - 点击 **Publish release**
6. （可选）在 Actions 页面手动运行 `release` 工作流，用于验证 CI 打包链路；
   其产物只作为 Actions artifact 下载，不会发布到 Releases
## ABI 说明
- 正式包：`arm64-v8a` + `armeabi-v7a`（覆盖手机与多数电视盒子）
- 调试包：`arm64-v8a` + `armeabi-v7a` + `x86_64`（可在模拟器运行）
- 若想单 ABI 包，用 `-PcompatMode=true` 或自行拆分
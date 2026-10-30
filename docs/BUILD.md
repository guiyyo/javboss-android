# 构建与发布

## 环境要求
| 项 | 版本 |
| --- | --- |
| JDK | 17 或 21（推荐 21） |
| Android SDK | Platform 36 + Build-Tools 36.0.0 |
| Gradle | 8.13（仓库自带 wrapper） |
| Kotlin | 2.1.x（由构建脚本管理） |

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

## CI（GitHub Actions）

仓库自带两个 workflow：

| 文件 | 触发 | 作用 |
| --- | --- | --- |
| `.github/workflows/build.yml` | push / PR | 编译调试包 + 单元测试 + 上传产物 |
| `.github/workflows/release.yml` | 推送 `v*` 标签 | 编译正式包并创建 Release，附 APK |

### 配置签名 Secrets
仓库 → **Settings → Secrets and variables → Actions → New repository secret**：

| Secret | 值 |
| --- | --- |
| `KEYSTORE_BASE64` | keystore 文件的 base64：`base64 -w0 release.jks`（Windows：`[Convert]::ToBase64String([IO.File]::ReadAllBytes("release.jks"))`） |
| `KEYSTORE_PASSWORD` | keystore 密码 |
| `KEY_ALIAS` | 别名 |
| `KEY_PASSWORD` | 别名密码 |

### 发版流程
1. 修改 `android/app/build.gradle.kts` 里的 `versionCode` / `versionName`
2. 提交并推送：`git commit -am "chore: release v0.24.2" && git push`
3. 打标签：`git tag v0.24.2 && git push origin v0.24.2`
4. Actions 自动构建 → Releases 出现新版本 + APK

## ABI 说明
- 正式包：`arm64-v8a` + `armeabi-v7a`（覆盖手机与多数电视盒子）
- 调试包：`arm64-v8a` + `armeabi-v7a` + `x86_64`（可在模拟器运行）
- 若想单 ABI 包，用 `-PcompatMode=true` 或自行拆分
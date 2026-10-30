# 贡献指南

## 提 Issue
- 请说明：**设备型号 / Android 版本 / App 版本 / 哪一端（手机、平板、TV）**
- 附上复现步骤与截图（TV 端可在「设置 → 诊断日志」扫码导出日志）
- 一个 issue 只报一个问题

## 提 PR
1. Fork 本仓库并从 `main` 切出分支：`feat/xxx`、`fix/xxx`
2. 保持代码风格一致（Kotlin 官方风格，Compose 命名规范）
3. 提交信息建议使用 Conventional Commits：`feat: 支持 xx`、`fix: 修复 xx`
4. PR 描述里写清：改了什么、怎么验证、影响哪一端
5. 确保 `./gradlew assembleDebug` 能通过

## 不要提交
- `keystore.properties`、`*.jks`、`local.properties`
- `build/`、`.gradle/`、`*.hprof`
- 任何真实的服务器地址、访问密钥、个人设备信息
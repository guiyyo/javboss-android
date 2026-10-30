# 发布到 GitHub（不用装 Git 也能做）

> 说明：Git 只是「命令行版管理工具」，GitHub 网页本身就能完成全部操作。下面先给**纯网页方案**，再给两种装工具的省事方案。

---

## 方案 A：只用网页（推荐先这样，10 分钟搞定）

### A1. 注册与建仓库
1. 打开 <https://github.com/signup> 注册（邮箱验证即可）。
2. 右上角 `+` → **New repository**。
3. **Repository name**：`javboss-android`
4. **Description**：`JavBoss 的 Android 客户端，支持手机 / 平板 / Android TV`
5. 选 **Public**（公开）或 **Private**（私有，后续可在 Settings 里改）。
6. **不要**勾选 `Add a README file`、`.gitignore`、`license`（我们已经有这些文件）。
7. 点 **Create repository**。

### A2. 分批上传文件（网页一次最多 100 个文件）

仓库页面点 **uploading an existing file**，按下面的批次拖拽上传，每批提交一次：

| 批次 | 上传内容 | 文件数 |
| --- | --- | --- |
| 1 | `android\app\src` 整个目录 | ~80 |
| 2 | `android\app` 下剩余文件（`build.gradle.kts`、`proguard-rules.pro` 等） | ~5 |
| 3 | `android` 下其余（`build.gradle.kts`、`settings.gradle.kts`、`gradle.properties`、`gradlew`、`gradlew.bat`、`gradle\`） | ~8 |
| 4 | 根目录文件：`README.md`、`LICENSE`、`CHANGELOG.md`、`CONTRIBUTING.md`、`.gitignore`、`.gitattributes` | 6 |
| 5 | `docs\` 整个目录 | ~7 |
| 6 | `.github\` 整个目录（含 workflows / ISSUE_TEMPLATE） | 5 |
| 7 | `releases\javboss-android-0.24.1-arm64-armv7.apk` | 1 |

> **拖拽技巧**：资源管理器里选中文件/文件夹后拖到浏览器上传区域即可，目录结构会自动保留。
> 隐藏文件（`.gitignore`、`.gitattributes`、`.github`）在资源管理器里要先开启「显示隐藏项目」。
> 如果某一批仍然超过 100 个文件，把该目录再拆成两个子目录上传，GitHub 会自己合并路径。

每批的提交信息可以写：
- 第 1 批：`init: 源码`
- 第 4 批：`init: 项目说明`
- 第 7 批：`release: v0.24.1 APK`

### A3. 替换 README 里的占位符
网页打开 `README.md` → 右上铅笔图标编辑 → 把 `USER/REPO` 两处替换成 `你的用户名/javboss-android` → **Commit changes**。

（`USER/REPO` 出现在顶部的 release 徽章和 star 徽章里。）

### A4. 发布 Release（= 用户下载页）
不用 Git 也能发：
1. 仓库右侧 → **Releases** → **Create a new release**
2. **Choose a tag** → 输入 `v0.24.1` → 点 **Create new tag**
3. **Release title**：`v0.24.1`
4. **Describe this release**：把 `CHANGELOG.md` 里对应版本几行粘进来
5. 附件区 **Attach binaries** → 上传 `releases\javboss-android-0.24.1-arm64-armv7.apk`
6. 点 **Publish release**

完成后用户就能在 Releases 页面直接下载 APK。

### A5. 以后改内容
任何文件都能在网页上直接编辑（铅笔图标）或重新 upload 覆盖，改完点 **Commit changes** 即完成一次提交。

---

## 方案 B：装 GitHub Desktop（图形界面，最省心）

1. 下载安装：<https://desktop.github.com/>
2. 打开后用 GitHub 账号登录。
3. 菜单 **File → Add local repository** → 选择本 `github` 文件夹。
   若提示「not a repository」，点 **create a repository**，名字填 `javboss-android`，点 Create。
4. 左下角填写提交说明 → 点 **Commit to main**。
5. 右上 **Publish repository** → 取消勾选 "Keep this code private"（要公开的话）→ Publish。

之后每次改完代码：打开 Desktop → 看变更 → Commit → **Push origin**。
发版：菜单 **Repository → Create tag**（填 `v0.24.2`）→ Push；或在网页上按 A4 手动发 Release。

---

## 方案 C：装 Git for Windows（命令行，最灵活）

1. 下载：<https://git-scm.com/download/win> → 一路默认安装。
2. 打开「Git Bash」或 PowerShell，执行：

```powershell
cd "路径\github"
git init
git add .
git commit -m "init: JavBoss Android 客户端 v0.24.1"
git branch -M main
git remote add origin https://github.com/你的用户名/javboss-android.git
git push -u origin main
```

第一次 push 会弹出浏览器让你登录 GitHub，授权即可（现在不再支持密码推送，用浏览器授权或 Personal Access Token）。

发版：
```powershell
git tag v0.24.1
git push origin v0.24.1
```
推送 tag 后，仓库自带的 `.github/workflows/release.yml` 会自动编译并创建 Release（需要先配好 Secrets，见下）。

---

## 可选：让 Actions 自动打包（配了就更省事）

仓库 → **Settings** → **Secrets and variables** → **Actions** → **New repository secret**：

| Name | 值 |
| --- | --- |
| `KEYSTORE_BASE64` | keystore 转 base64（见下方命令） |
| `KEYSTORE_PASSWORD` | keystore 密码 |
| `KEY_ALIAS` | 别名 |
| `KEY_PASSWORD` | 别名密码 |

生成 base64（Windows PowerShell）：
```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("release.jks"))
```

同时把 **Settings → Actions → General → Workflow permissions** 设为 **Read and write permissions**，否则创建 Release 会失败。

> 不配 Secrets 也能用方案 A4 手动发 Release，只是每次要自己上传 APK。

---

## 常见问题

| 现象 | 解决 |
| --- | --- |
| 网页上传提示文件过多 | 按 A2 拆批；单批 ≤100 个文件 |
| 隐藏文件（.gitignore / .github）传不上去 | 资源管理器 → 查看 → 勾选「隐藏的项目」，或先打包成 zip 上传后解压不了，改用方案 B/C |
| Release 里点开 APK 变成乱码 | 点 **Download** 按钮，不要点文件名预览 |
| Actions 报 permission denied | 把 Workflow permissions 改成 Read and write |
| 想改仓库名 / 公开性 | Settings → General → Repository name / Danger Zone → Change visibility |
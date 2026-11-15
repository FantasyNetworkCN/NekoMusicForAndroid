# AGENTS.md

本文件是 NekoMusic Android 仓库的编码代理规范。修改前先阅读本文件、`README.md`
和相关模块代码；更深层目录若存在 `AGENTS.md`，以更深层规则为准。

## 基本要求

- 先检查 `git status`、现有实现与相邻代码风格，再做最小范围修改，不重构无关代码。
- 注释、日志和提交信息使用中文；提交信息遵循 Conventional Commits，例如
  `fix(lyrics): 柔化歌词上下边缘渐隐`。
- 不提交密钥、`local.properties`、`keystore.properties`、签名文件、构建产物或与任务无关的文件。
- 不擅自删除用户数据、回滚他人改动或提交工作区中与本次任务无关的变更。
- 对用户可见的改动，必须在同一次改动中更新 `TEMP-LOG.md`。

## 构建与验证

- 修改 Kotlin/Compose 代码后至少运行：

  ```bash
  ./gradlew :app:compileDebugKotlin --offline
  ```

- 涉及逻辑、解析、数据模型或工具类时运行单元测试：

  ```bash
  ./gradlew :app:testDebugUnitTest --offline
  ```

- 构建 APK 时运行：

  ```bash
  ./gradlew :app:assembleDebug --offline
  ```

- 网络不可用或依赖未缓存时，可去掉 `--offline` 后重试；仍无法验证时必须在交付说明中写明。
- 任务结束前运行 `git diff --check`，确认没有空白字符错误。

## 代码约定

- 使用 Kotlin 与 Jetpack Compose 现有架构，优先复用已有组件、主题色、液态玻璃效果和工具类。
- 新界面必须同时适配浅色/深色主题，并兼顾手势区域、状态栏、导航栏、横竖屏和不同屏幕宽度。
- 新增用户可见文案必须同步维护 `app/src/main/res/values-zh/strings.xml` 与
  `app/src/main/res/values-en/strings.xml`，不要在 Compose 中硬编码可翻译文案。
- 新增 Ktor 客户端必须调用 `installNekoClientHeader()`；`verifyNekoClientHeader` 会阻止遗漏。
- 网络请求、缓存、下载、播放和权限逻辑应保持失败可降级，并沿用现有错误提示与日志方式。
- 修改播放器、歌词、通知、桌面歌词或后台播放时，必须检查相关状态同步和生命周期清理。
- 避免无必要的全屏重新布局或高频 Span 重建；滚动和逐字歌词动画应保持稳定帧率。
- 新增交互控件时补充内容描述，保持可访问性和足够的触控区域。

## 未发布日志规范

`TEMP-LOG.md` 用于记录已合入但尚未发布的用户可见变更，规则如下：

- 按 `新增`、`优化`、`修复`、`移除` 分类，只保留真实存在的分类。
- 每条说明用户可感知的结果，不写内部类名、函数名或实现细节。
- 纯内部重构、测试补充、代码格式化等无用户可见影响的改动通常不记录。
- 发布正式版本时，把条目迁移到 `CHANGELOG.md` 对应版本章节，然后重置 `TEMP-LOG.md`。
- 版本号、`versionCode`、`versionName` 和正式版本小节只在发版时修改，不提前写入未发布日志。

## 提交与发布

- 一个提交只包含同一主题的改动，避免把功能、重构和文档混在一起。
- 仅在用户要求或任务流程明确要求时提交、推送或创建发布。
- 发版前同步更新 `CHANGELOG.md`、`app/build.gradle.kts` 中的版本信息，并完成 release 构建与签名校验。

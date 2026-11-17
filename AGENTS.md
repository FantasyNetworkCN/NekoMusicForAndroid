# AGENTS.md

This document defines the coding agent guidelines for the NekoMusic Android repository. Before making any modifications, read this file, `README.md`, and the relevant module code. If a deeper directory contains its own `AGENTS.md`, the rules in that file take precedence.

## General Requirements

- Check `git status`, review the existing implementation, and follow the surrounding code style before making changes. Keep modifications minimal and avoid refactoring unrelated code.
- Comments, logs, and commit messages must be written in Chinese. Commit messages must follow the Conventional Commits specification, for example: `fix(lyrics): 柔化歌词上下边缘渐隐`.
- Do not commit secrets, `local.properties`, `keystore.properties`, signing files, build artifacts, or files unrelated to the current task.
- Do not delete user data, revert other contributors' changes, or commit unrelated working directory changes without explicit authorization.
- Any user-visible changes must be documented in `TEMP-LOG.md` as part of the same modification.

## Build and Verification

- After modifying Kotlin or Jetpack Compose code, run at least:

  ```bash
  ./gradlew :app:compileDebugKotlin --offline
  ```

- When modifying logic, parsers, data models, or utility classes, run unit tests:

  ```bash
  ./gradlew :app:testDebugUnitTest --offline
  ```

- When building an APK, run:

  ```bash
  ./gradlew :app:assembleDebug --offline
  ```

- If network access is unavailable or dependencies are not cached, retry without `--offline` when appropriate. If verification still cannot be completed, explicitly document the limitation in the final delivery report.
- **Do not use Xiaomi devices for testing via ADB (Android Debug Bridge).** Use a non-Xiaomi Android device or an emulator instead.
- Before completing any task, run `git diff --check` to ensure there are no whitespace errors.

## Code Conventions

- Follow the existing Kotlin and Jetpack Compose architecture. Prefer reusing existing components, theme colors, liquid glass effects, and utility classes.
- All new interfaces must support both light and dark themes and properly handle gesture navigation areas, status bars, navigation bars, portrait and landscape orientations, and different screen widths.
- All new user-visible strings must be added to both `app/src/main/res/values-zh/strings.xml` and `app/src/main/res/values-en/strings.xml`. Do not hardcode translatable strings in Compose.
- Every new Ktor client must call `installNekoClientHeader()`. The `verifyNekoClientHeader` check will reject implementations that omit it.
- Network requests, caching, downloads, playback, and permission handling must support graceful degradation on failure and follow the existing error reporting and logging conventions.
- When modifying the player, lyrics, notifications, desktop lyrics, or background playback, verify related state synchronization and lifecycle cleanup.
- Avoid unnecessary full-screen relayouts or frequent Span reconstruction. Scrolling and word-by-word lyrics animations must maintain stable frame rates.
- New interactive controls must include appropriate content descriptions, accessibility support, and sufficiently large touch targets.

## Unreleased Changelog Guidelines

`TEMP-LOG.md` records user-visible changes that have been merged but not yet released. Follow these rules:

- Organize entries into the following categories: `新增` (Added), `优化` (Improved), `修复` (Fixed), and `移除` (Removed). Only include categories that contain actual changes.
- Each entry must describe the user-visible result rather than internal class names, function names, or implementation details.
- Purely internal refactoring, additional tests, code formatting, and other changes without user-visible impact should generally not be recorded.
- When publishing an official release, migrate the entries to the corresponding version section in `CHANGELOG.md`, then reset `TEMP-LOG.md`.
- Version numbers, `versionCode`, `versionName`, and official release sections must only be updated during the release process. Do not add them prematurely to the unreleased changelog.

## Commits and Releases

- Each commit should contain changes related to a single topic. Avoid mixing features, refactoring, and documentation changes in the same commit.
- Only commit, push, or create releases when explicitly requested by the user or required by the established task workflow.
- Before releasing, update `CHANGELOG.md` and the version information in `app/build.gradle.kts`, then complete the release build and verify the APK signature.
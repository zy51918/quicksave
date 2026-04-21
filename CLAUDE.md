# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

```bash
./gradlew build                    # Full build
./gradlew assembleDebug            # Debug APK
./gradlew test                     # Unit tests
./gradlew connectedAndroidTest     # Instrumented tests (requires device/emulator)
./gradlew lint                     # Lint checks

# Run a single unit test class
./gradlew test --tests com.ylib.quicksave.ExampleUnitTest

# Run a single instrumented test class
./gradlew connectedAndroidTest --tests com.ylib.quicksave.ExampleInstrumentedTest
```

## Architecture

Single-module Android app (`com.ylib.quicksave`) using Jetpack Compose with Navigation Compose. Single-Activity architecture — `MainActivity` enables edge-to-edge and hosts the `AppNavigation` composable which owns the `NavController`.

```
MainActivity
  └── AppNavigation (NavHost)
        ├── HomeScreen  ←→  HomeViewModel
        └── SettingsScreen  ←→  SettingsViewModel
```

Material 3 theming with dynamic color support (Android 12+) defined in `ui/theme/`. State managed via `StateFlow` in ViewModels.

Key layers:
- **Service**: `ClipboardMonitorService` (foreground service, persistent LOW-priority notification)
- **Data**: `ClipRepositoryImpl` → `SafFileDataSource` (SAF file I/O) + `AppDataStoreImpl` (DataStore config)

## Agent Team

工作流程：用户想法 → `/产品`（需求分析 → `docs/MRD-*.md`）→ `/HIE`（UI 设计 → `docs/UI_DESIGN-*.md` + 原型设计 → `docs/mockups/*/`）→ 开发和测试

| 角色 | 指令 | 职责 |
|------|------|------|
| **产品经理** | `/产品` | 需求分析，输出 `docs/MRD-*.md` |
| **HIE 设计师** | `/HIE` | UI/UX 设计，输出 `docs/UI_DESIGN-*.md` 和 SVG 设计图，输出到目录 `docs/mockups/` |
| **开发工程师** | `/开发` | 读取 `docs/MRD-*.md` 和 `docs/UI_DESIGN-*.md`，实现功能 |
| **测试工程师** | `/测试` | 读取 `docs/MRD-*.md` 和 `docs/UI_DESIGN-*.md`，单元测试和 UI 测试 |

## Key Libraries

Managed via version catalog at [gradle/libs.versions.toml](gradle/libs.versions.toml):

- **Compose BOM** 2024.09.00 — all Compose UI artifacts
- **Navigation Compose** 2.7.7 — screen routing
- **AGP** 8.13.2, **Kotlin** 2.0.21
- Min SDK 29, Compile/Target SDK 36, JVM target 11

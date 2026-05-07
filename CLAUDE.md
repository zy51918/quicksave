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

## Document folder structure

```
docs/
├── features/<feature_id>/                # 每个feature文档目录
│            ├──prd-<feature_name>.md     # feature的PRD文档
│            ├──ui-<feature_name>.md      # feature的UI交互设计文档
│            ├──design-<feature_name>.md  # feature的设计文档
│            └──mockups/                  # feature的UI原型
├── DESIGN.md             # 完整的架构设计文档
├── DESIGN_changelist.md  # 架构设计文档的change list
├── PRD.md                # 完整的PRD文档
├── PRD_changelist.md     # PRD文档的change list
├── UI.md                 # 完整的UI交互设计文档
└── UI_changelist.md      # UI交互设计文档的change list
```

### change list format

- Change lists are append-only.
- Entries must be kept in reverse chronological order, with the newest entry at the top.
- Use this format for each entry:

```md
## [YYYY-MM-DD] [req_id][Modules] One-line description.
```


## Agent Team

| 角色 | 指令 | 职责 |
|------|------|------|
| **产品经理** | `/产品` | 需求分析，更新 `docs/PRD.md` 和 `docs/PRD_changelist.md` |
| **HIE 设计师** | `/HIE` | 读取 `docs/PRD.md` 和 `docs/UI.md`，UI/UX 设计，输出 `docs/UI.md` 和 UI原型 |
| **开发工程师** | `/开发` | 读取 `docs/PRD.md`、`docs/UI.md` 和 `docs/DESIGN.md`，架构设计、详细设计、功能实现和测试 |
| **测试工程师** | `/测试` | 读取 `docs/PRD.md` 和 `docs/UI.md`，集成测试和系统测试 |

工作流程：用户想法 → `/产品`（需求分析 → 更新 `docs/PRD.md`）→ `/HIE`（UI 设计 → 更新 `docs/UI.md` + 生成原型）→ 人工approve → `/开发`（架构设计 + 实现 → 更新 `docs/DESIGN.md`）→ 人工approve → `/测试`（验收测试）→ 人工验收 → 提交代码




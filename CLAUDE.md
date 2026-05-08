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
├── features/<feature_id>/                # 每个feature文档目录（feature_id 格式：QS-<4位数字>，如 QS-0001，按现有最大编号+1 递增）
│            ├──prd-<feature_name>.md     # feature的PRD文档
│            ├──ui-<feature_name>.md      # feature的UI交互设计文档（当有UI交互设计更改时）
│            ├──arch-<feature_name>.md    # feature的架构设计文档（当有架构更改时）
│            ├──design-<feature_name>.md  # feature的详细设计文档
│            └──mockups/                  # feature的UI原型
├── ARCH.md               # 项目级架构设计文档
├── ARCH_changelist.md    # 架构设计文档的change list
├── PRD.md                # 项目级PRD文档
├── PRD_changelist.md     # PRD文档的change list
├── UI.md                 # 项目级UI交互设计文档
└── UI_changelist.md      # UI交互设计文档的change list
```



## Agent Team

| 角色 | 指令 | 职责 |
|------|------|------|
| **产品经理** | `/prd` | 需求分析，输出 feature的PRD文档，更新 项目级PRD文档` |
| **HIE 设计师** | `/hie` | 读取 `docs/PRD.md` 和 `docs/UI.md`，UI/UX 设计，输出 feature的UI交互设计文档 和 feature的UI原型，更新 项目级UI交互设计文档 |
| **开发工程师** | `/dev` | 读取 `docs/PRD.md`、`docs/UI.md` 和 `docs/ARCH.md`，读取feature的PRD文档和feature的UI交互设计文档，架构设计（输出feature的架构设计文档）、详细设计（输出feature的设计文档）、功能实现和测试 |
| **测试工程师** | `/test` | 读取 `docs/PRD.md`、`docs/UI.md` 和 `docs/ARCH.md`，集成测试和系统测试 |

工作流程：用户想法 → `/prd`（需求分析 → 输出 feature的PRD文档）→ `/hie`（UI 设计 → 输出 feature的UI交互设计文档 和 feature的UI原型）→ 人工approve → `/dev`（架构设计 → 输出 feature的架构设计文档）→ 人工approve → `/dev`（详细设计、功能实现和测试）→ 提交代码 → `/test`（集成测试和系统测试）→ 部署 → 验收测试




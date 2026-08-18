# QS-0004 跨应用文本分享自动保存设计

> 归档说明：本文为迭代设计文档。当前实现状态以 [`docs/superpowers/specs/2026-08-18-qs-0004-share-text.md`](../../superpowers/specs/2026-08-18-qs-0004-share-text.md) 为准。
> 归档日期：2026-08-18

## 1. 背景

QuickSave 当前支持从剪贴板和手动输入保存文本。用户希望在其他 Android App 中选择“分享”后，直接将文本保存到 QuickSave 配置的目标文件中，避免复制、切换应用和再次点击保存。

## 2. 目标

- 让 QuickSave 出现在 Android 系统分享面板的文本分享目标中。
- 接收其他 App 分享的单条 `text/plain` 文本并自动保存。
- 保存时复用 QuickSave 当前选中的分类；未选择分类时按无分类格式保存。
- 保存成功或失败后均不打开 QuickSave 主页面，提示结果并返回原 App。
- 复用现有目标文件、SAF 权限、时间戳和保存格式。

## 3. 非目标

首版不支持：

- `ACTION_SEND_MULTIPLE` 多条分享；
- 图片、视频、附件或其他文件 Uri；
- 仅提供 HTML 而没有纯文本载荷的分享；
- 分享后编辑文本或在分享流程中重新选择分类；
- 新增数据库、网络同步、后台服务或新的运行时权限。

## 4. 用户流程

### 4.1 保存成功

1. 用户在其他 App 选择一段文本。
2. 用户打开 Android 分享面板并选择 QuickSave。
3. QuickSave 接收 `ACTION_SEND` 的 `text/plain` 内容。
4. QuickSave 读取当前选中的分类。
5. QuickSave 调用现有保存链路写入目标文件。
6. 显示“已保存”提示。
7. 关闭分享接收页，返回原 App。

### 4.2 保存失败

1. 按上述步骤接收和校验分享内容。
2. 保存链路返回失败结果。
3. 显示对应的失败原因。
4. 关闭分享接收页，返回原 App。
5. 不打开 QuickSave 主页面。

## 5. 交互与文案

分享接收页使用现有透明主题，不展示可编辑界面，不出现在最近任务列表中。

结果提示：

| 场景 | 文案 |
|---|---|
| 保存成功 | 已保存 |
| 未配置目标文件 | 尚未设置保存文件 |
| 目标文件不可写 | 目标文件无写入权限，请重新选择 |
| 文本为空或仅空白 | 分享内容为空 |
| Action、MIME 或载荷不支持 | 不支持的分享内容 |
| 其他未识别错误 | 使用异常信息；无可用信息时显示“保存失败” |

解析时不修改原始文本，保留换行、空格、网址和代码格式。`EXTRA_TITLE` 等辅助字段不写入目标文件。

## 6. 架构设计

### 6.1 分享接收 Activity

新增 `ShareReceiverActivity`，职责：

- 作为 Android 分享入口接收 Intent；
- 调用分享内容解析器；
- 读取当前分类；
- 调用 `ClipRepository.saveEntry(text, category)`；
- 显示 Toast 结果并结束 Activity。

Activity 通过 `QuickSaveApplication.clipRepository` 获取现有仓库，不新增独立保存实现。

Manifest 注册要求：

- `android:exported="true"`；
- `android:excludeFromRecents="true"`；
- 使用透明主题；
- Intent filter 包含：
  - `android.intent.action.SEND`；
  - `android.intent.category.DEFAULT`；
  - `text/plain`。

不使用 `noHistory`，避免 Activity 因暂时失去焦点而在异步保存完成前被系统销毁。

### 6.2 分享内容解析器

新增 `ShareContentParser`，将 Android Intent 相关字段转换为可测试的解析结果。

输入：

- Action；
- MIME 类型；
- `EXTRA_TEXT` 的 `CharSequence?`；
- 可选的多条分享标识或文件 Uri 信息。

输出：

- 有效的原始文本；或
- 明确的解析失败类型。

校验规则：

1. Action 必须为 `ACTION_SEND`。
2. MIME 类型必须为 `text/plain`。
3. 必须存在 `EXTRA_TEXT`。
4. 文本经过 `isBlank()` 判断不可为空。
5. 通过校验后返回原始文本，不做 `trim()` 或其他内容变换。

### 6.3 保存数据流

```text
其他 App
  → Android Sharesheet
  → ShareReceiverActivity
  → ShareContentParser
  → AppDataStore.getSelectedCategory().first()
  → ClipRepository.saveEntry(text, category)
  → Toast
  → finish()
```

`ClipRepository.saveEntry()` 继续负责：

- 读取目标文件 Uri；
- 检查 Uri 是否可写；
- 添加分类前缀；
- 添加时间戳；
- 通过现有 `FileDataSource` 追加写入。

## 7. 生命周期与异常处理

- 一次 Activity 实例只处理一次分享请求。
- 开始处理后禁止重复触发保存。
- 解析失败、仓库失败或未预期异常都必须进入统一的提示并结束流程。
- 即使保存失败，也不能导航到 `MainActivity` 或主页。
- 如果系统传入不符合范围的内容，提示“不支持的分享内容”后结束。
- 保存协程使用 Activity 的 `lifecycleScope`；在保存完成后显示 Toast 并调用 `finish()`。

## 8. 测试设计

### 8.1 JVM 单元测试

覆盖 `ShareContentParser`：

- 合法 `ACTION_SEND` + `text/plain` + 非空文本；
- 错误 Action；
- 错误 MIME；
- 缺少 `EXTRA_TEXT`；
- 空文本和仅空白文本；
- 原始换行、空格、网址和代码内容保持不变。

覆盖保存调用逻辑：

- 当前分类被传递给 `saveEntry()`；
- 当前无分类时传递 `null`；
- 仓库失败不会触发主页导航。

### 8.2 Android 集成测试

- 分享接收 Activity 能被 `ACTION_SEND` / `text/plain` Intent 启动；
- 合法文本完成保存后 Activity 结束；
- 保存失败后 Activity 仍结束；
- 分享入口不出现在最近任务中；
- Manifest 不引入新的权限要求。

## 9. 文件变更清单

预计新增或修改：

- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/com/ylib/quicksave/share/ShareReceiverActivity.kt`
- `app/src/main/java/com/ylib/quicksave/share/ShareContentParser.kt`
- `app/src/main/res/values/strings.xml`
- `app/src/test/java/com/ylib/quicksave/share/ShareContentParserTest.kt`
- 视测试可行性补充分享 Activity 的 Android 测试

## 10. 验收标准

- 在浏览器、资讯、聊天等支持纯文本分享的 App 中，QuickSave 出现在分享面板。
- 选择 QuickSave 后，无需额外点击即可将文本追加到目标文件。
- 保存记录继续使用现有格式：`[分类][yyyy-MM-dd HH:mm:ss] 文本内容`；无分类时不包含分类前缀。
- 成功时提示“已保存”，随后回到原 App。
- 未配置目标文件、目标文件不可写或内容不支持时，只提示错误并回到原 App，不打开 QuickSave 主页。
- 不影响现有手动输入、剪贴板监听、悬浮窗和录音功能。

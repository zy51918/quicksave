# QS-0003 全局悬浮窗 — 设计文档

> 版本：1.0
> 日期：2026-06-11
> 状态：设计待评审
> 关联：QS-0001（MVP 文字保存）、QS-0002（手动输入保存）
> 说明：本文件与 `docs/features/QS-0003/design-floating-window.md` 内容一致，作为 brainstorming 流程的规范副本。

---

## 一、概述

为 QuickSave 增加一个**全局悬浮窗**：总开关开启后常驻于所有应用之上，平时贴边显示一个半透明小把手，点击展开成一个固定尺寸、横排 2 按钮的小面板，提供两个预定义快捷操作：

- **【文字输入】** —— 拉起一个轻量透明窗口，复用 QS-0002 的输入框 UI 与保存逻辑，功能与主页手动输入一致。
- **【录音】** —— 点击立即开始录音（QuickSave 自录），再次点击停止；录音期间切换 App 或锁屏均不中断。

操作完成后悬浮窗自动收回贴边。

**核心价值：** 在任何 App 内零摩擦地"记一笔文字 / 录一段音"，无需切回 QuickSave 主界面。

---

## 二、范围

### 2.1 本期（Phase 1）包含
- 悬浮窗总开关（设置页）+ 悬浮窗权限申请流程
- 常驻悬浮窗：贴边半透明把手、点击展开/收起、拖拽沿边定位、操作后自动收回
- 横排固定尺寸 2 按钮面板
- 【文字输入】拉起透明 Activity 保存（复用现有链路）
- 【录音】toggle 录音，前台服务防中断，落盘到 `Music/QuickSave/*.m4a`
- 录音中状态可视化（收起态把手变红+红点；展开态录音按钮变红显示计时）

### 2.2 明确不做（YAGNI）
- 自定义按钮（启动其他 App / 系统开关 toggle）
- 窗口大小自定义、按钮分页滑动、一行 3 个布局
- 开机自启
- 录音波形、暂停/继续、录音列表管理

> 备注：横排面板 + 双服务的结构为后续加"自定义按钮"预留了演进空间，但本期不实现相关代码。

---

## 三、架构与组件

```
[Settings 总开关] --on--> 检查/请求 SYSTEM_ALERT_WINDOW --> 启动 OverlayService
                                                                  │
                                                      ┌───────────┴───────────┐
                                                      │      OverlayService     │  前台(specialUse / 子类型 floatingWindow)
                                                      │  WindowManager 叠加层    │  常驻通知
                                                      │  把手↔面板状态机 + 拖拽   │
                                                      └───────────┬───────────┘
                                        【文字输入】│                      │【录音】toggle
                                                    ▼                      ▼
                                      透明 InputActivity         启动/停止 RecorderService
                                      复用 ClipRepository→SAF     microphone 前台 + MediaRecorder
```

### 3.1 新增组件

| 组件 | 职责 | 依赖 |
|------|------|------|
| `OverlayService` | 前台服务，持有 WindowManager 叠加层；管理把手↔面板展开/收起、贴边拖拽定位、转发按钮点击 | `WindowManager`、`AppDataStore` |
| 悬浮窗视图层（`OverlayView` 等） | 渲染把手与横排 2 按钮面板；处理触摸（点击展开、拖拽移动）；反映录音中红色态 | `OverlayService` |
| `InputActivity`（透明主题） | 复用 QS-0002 输入框 UI 与保存逻辑；保存后 finish 并通知悬浮窗收起 | `HomeViewModel` / `ClipRepository` |
| `PermissionActivity`（透明主题） | 悬浮窗内无法直接弹系统权限框时，用于申请 `RECORD_AUDIO` | `PermissionHelper` |
| `RecorderService` | `microphone` 前台服务；`MediaRecorder` 录音、计时、防中断；停止时落盘 | `MediaRecorder`、MediaStore、`AppDataStore` |
| `OverlayController` / 共享状态 | 在服务间传递"录音中"状态，使把手/面板实时反映 | StateFlow / 绑定 / 广播 |

### 3.2 复用现有
- `ClipRepository` / `SafFileDataSource`：文字保存
- `AppDataStore`：持久化总开关、把手位置
- `PermissionHelper`：扩展悬浮窗与录音权限判断
- 设置页：新增"全局悬浮窗"总开关区块

### 3.3 前台服务与通知
- `OverlayService` 常驻一条通知（开关开启期间）。
- 录音时 `RecorderService` 另起一条通知（含计时 + 停止动作），停止后撤销。
- 取舍：两条通知略多，但职责清晰、契合 Android 14+ 前台服务类型规范（叠加层用 specialUse、录音用 microphone）。本期接受。

---

## 四、交互与状态机

### 4.1 悬浮窗状态
```
        点击把手 / 点按钮触发后返回
 [贴边收起] ───────────────► [展开面板]
     ▲                            │
     └──── 点空白处 / 操作完成 ─────┘
```
- **贴边收起**：半透明竖条把手，吸附在屏幕左/右边；可上下拖动改位置；越界自动吸附最近边。
- **展开面板**：从所在边向内伸出横排 2 按钮；点击面板外区域或完成一次操作后收回。
- **位置持久化**：贴左/右 + 纵向 y 存入 DataStore，重启 App 恢复。

### 4.2 文字输入
点【文字输入】→ 经后台启动豁免（持有 SYSTEM_ALERT_WINDOW）拉起透明 `InputActivity` → 复用 QS-0002 输入与保存 → 保存成功反馈 → finish → 悬浮窗收回。

### 4.3 录音（toggle）
1. 点【录音】→ 检查 `RECORD_AUDIO`；未授权 → 拉起透明 `PermissionActivity` 申请。
2. 已授权 → 启动 `RecorderService`（`startForeground`，microphone 类型）→ `MediaRecorder` 开始 → 进入录音态（红）+ 通知计时。
3. 再次点【录音】或通知"停止" → 停止、落盘、`stopForeground`、退出录音态。

---

## 五、录音文件与数据

- **位置**：经 MediaStore 写入公共目录 `Music/QuickSave/`，其他 App / 文件管理器 / 播放器均可见，无需额外存储权限。
- **文件名**：`QS_yyyyMMdd_HHmmss.m4a`
- **格式**：AAC / MPEG-4 容器（`.m4a`），默认采样率 44.1kHz。
- **DataStore 新增键**：
  - `overlay_enabled: Boolean` —— 悬浮窗总开关
  - `overlay_edge: Enum(LEFT/RIGHT)` —— 贴边方向
  - `overlay_y: Int/Float` —— 把手纵向位置

---

## 六、权限

| 权限 | 用途 | 申请时机 |
|------|------|---------|
| `SYSTEM_ALERT_WINDOW` | 绘制悬浮窗 | 开总开关时，未授权则跳系统授权页 |
| `RECORD_AUDIO` | 录音 | 首次点【录音】时（透明 Activity 申请） |
| `FOREGROUND_SERVICE_MICROPHONE` | 录音前台服务类型 | Manifest 声明 |
| `FOREGROUND_SERVICE`（已有） | 前台服务 | Manifest 声明 |
| `POST_NOTIFICATIONS`（已有） | 通知 | 已有流程 |

**后台启动豁免**：持有 `SYSTEM_ALERT_WINDOW` 时，可从悬浮窗后台拉起 `InputActivity` / `PermissionActivity`，不受后台 Activity 启动限制。

---

## 七、错误处理与边界

| 场景 | 处理 |
|------|------|
| 未授予悬浮窗权限就开总开关 | 跳转系统授权页；返回仍未授权 → 总开关回弹 + Toast |
| 录音权限被拒 | Toast「需麦克风权限」，不进入录音态 |
| 麦克风被占用 / `MediaRecorder` 启动失败 | 捕获异常，Toast 提示，不进入录音态 |
| 录音中被系统抢占（来电等） | 停止录制并**保留已录片段**，通知告知已停止 |
| 录音中进程被杀 | `finally` 中安全 `stop+release`，避免文件损坏 |
| 文字输入时 SAF 文件失效 | 复用现有 ClipRepository 错误反馈 |
| 设备重启 | 前台服务不自启；重新打开 App / 开关后恢复（与现有 ClipboardMonitorService 一致） |
| 把手拖出屏幕 | 限制在可视范围，松手吸附最近左/右边 |

---

## 八、测试策略

- **单元测试**：录音状态机（idle↔recording）、把手位置吸附计算、文件名生成、DataStore 读写。
- **仪器测试（需设备）**：真录一段验证 `Music/QuickSave/` 生成可播放 `.m4a`；悬浮窗权限流程。
- **手动 / 系统测试（`/test` 阶段）**：跨 App 切换 + 锁屏不中断录音；贴边/展开/收起交互；文字输入保存链路。

---

## 九、原型

布局原型见本次 brainstorming 会话：贴边收起 / 横排展开面板 / 录音中三态。
（如需固化，可在 `/hie` 阶段补充 `mockups/` SVG。）

# ARCH Changelist

## [2026-06-11] [QS-0003] 全局悬浮窗合入 main，**有架构变更**，项目级 ARCH 升 v1.3：新增 `overlay/`、`recorder/` 包；新增 OverlayService（非前台）/RecorderService（mic 前台）/RecordingController（StateFlow 桥）/OverlayRepository/InputActivity/RecordPermissionActivity；§一 技术栈补 WindowManager/MediaRecorder/MediaStore；§四 增 OverlayRepository、RecordingController、录音输出协议与 3 个 DataStore 键；§七 增 overlayRepository 与启停流程；§八 增 SYSTEM_ALERT_WINDOW/RECORD_AUDIO/FOREGROUND_SERVICE_MICROPHONE 并注明 OverlayService 非前台；§十 索引追加 QS-0003（架构变更=有，以 design-floating-window.md 承载）。

## [2026-06-11] [QS-0003][打磨] OverlayService 由前台服务降级为普通 started 服务（靠 ClipboardMonitorService 保活），消除第二条常驻通知；无新增模块，仅服务前台属性与启动方式调整。

## [2026-05-08] [QS-0002] feature 实现合入 main（PR #1），无架构变更（仅 UI 层增量）；§十 Feature 索引追加 QS-0002 注记，并扩展索引列说明"无架构变更"约定。

## [2026-05-07] [QS-0001] 首次创建项目级架构文档与 MVP feature 级架构文档（回填，描述当前已交付的分层、DI、SAF、前台 Service 设计）。

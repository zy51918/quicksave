package com.ylib.quicksave.recorder

/** 录音 UI 状态：由 RecorderService 更新、OverlayService 订阅。 */
data class RecordingUiState(
    val isRecording: Boolean = false,
    val elapsedSeconds: Int = 0
)

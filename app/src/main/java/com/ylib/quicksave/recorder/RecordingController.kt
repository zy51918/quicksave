package com.ylib.quicksave.recorder

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 进程内单例：桥接 RecorderService（写入）与 OverlayService（订阅）的录音状态。
 * 两个服务同进程，直接共享一个 StateFlow。
 */
object RecordingController {
    private val _state = MutableStateFlow(RecordingUiState())
    val state: StateFlow<RecordingUiState> = _state.asStateFlow()

    fun update(isRecording: Boolean, elapsedSeconds: Int) {
        _state.value = RecordingUiState(isRecording, elapsedSeconds)
    }

    fun reset() {
        _state.value = RecordingUiState()
    }
}

package com.ylib.quicksave.share

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ShareReceiverState {
    data object Idle : ShareReceiverState
    data object Processing : ShareReceiverState
    data class Completed(val result: Result<Unit>) : ShareReceiverState
}

class ShareReceiverViewModel(
    private val saveAction: suspend (String) -> Result<Unit>
) : ViewModel() {

    private val _state = MutableStateFlow<ShareReceiverState>(ShareReceiverState.Idle)
    val state: StateFlow<ShareReceiverState> = _state.asStateFlow()

    private var saveStarted = false

    fun save(text: String) {
        if (saveStarted) return
        saveStarted = true
        _state.value = ShareReceiverState.Processing

        viewModelScope.launch {
            val result = try {
                saveAction(text)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                Result.failure(error)
            }
            _state.value = ShareReceiverState.Completed(result)
        }
    }

    companion object {
        fun factory(saveAction: suspend (String) -> Result<Unit>): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (!modelClass.isAssignableFrom(ShareReceiverViewModel::class.java)) {
                        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
                    }
                    @Suppress("UNCHECKED_CAST")
                    return ShareReceiverViewModel(saveAction) as T
                }
            }
    }
}

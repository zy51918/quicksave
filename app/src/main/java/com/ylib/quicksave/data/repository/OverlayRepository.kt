package com.ylib.quicksave.data.repository

import com.ylib.quicksave.overlay.OverlayPosition
import kotlinx.coroutines.flow.Flow

interface OverlayRepository {
    fun isEnabled(): Flow<Boolean>
    suspend fun setEnabled(enabled: Boolean)
    fun getPosition(): Flow<OverlayPosition>
    suspend fun setPosition(position: OverlayPosition)
}

package com.ylib.quicksave.data.repository

import com.ylib.quicksave.data.source.AppDataStore
import com.ylib.quicksave.overlay.OverlayEdge
import com.ylib.quicksave.overlay.OverlayPosition
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class OverlayRepositoryImpl(
    private val dataStore: AppDataStore
) : OverlayRepository {

    override fun isEnabled(): Flow<Boolean> = dataStore.getOverlayEnabled()

    override suspend fun setEnabled(enabled: Boolean) = dataStore.saveOverlayEnabled(enabled)

    override fun getPosition(): Flow<OverlayPosition> =
        dataStore.getOverlayPosition().map { (edge, ratio) ->
            OverlayPosition(OverlayEdge.fromStorage(edge), ratio)
        }

    override suspend fun setPosition(position: OverlayPosition) =
        dataStore.saveOverlayPosition(position.edge.name, position.yRatio)
}

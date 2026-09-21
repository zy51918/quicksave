package com.ylib.quicksave.share

import com.ylib.quicksave.data.repository.ClipRepository
import kotlinx.coroutines.flow.first

class ShareSaveCoordinator(
    private val repository: ClipRepository
) {
    suspend fun save(text: String): Result<Unit> {
        val category = repository.getSelectedCategory().first()
        return repository.saveEntry(text, category)
    }
}

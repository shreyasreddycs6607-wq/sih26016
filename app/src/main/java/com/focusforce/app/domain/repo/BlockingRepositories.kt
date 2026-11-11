package com.focusforce.app.domain.repo

import com.focusforce.app.domain.blocking.BlockSettings
import com.focusforce.app.domain.blocking.PassState
import kotlinx.coroutines.flow.Flow

interface BlockSettingsRepository {
    val settings: Flow<BlockSettings>
    val passState: Flow<PassState>

    /** Atomic read-modify-write. */
    suspend fun update(transform: (BlockSettings) -> BlockSettings)
    suspend fun updatePass(transform: (PassState) -> PassState)
}

interface BlockedAppsRepository {
    fun observe(): Flow<Set<String>>
    suspend fun add(packageNames: Collection<String>)
    suspend fun remove(packageNames: Collection<String>)
}

package com.focusforce.app.data.blocking

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.focusforce.app.data.db.BlockedAppDao
import com.focusforce.app.data.db.BlockedAppEntity
import com.focusforce.app.domain.blocking.BlockSettings
import com.focusforce.app.domain.blocking.PassState
import com.focusforce.app.domain.repo.BlockSettingsRepository
import com.focusforce.app.domain.repo.BlockedAppsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private val Context.blockingDataStore: DataStore<Preferences> by preferencesDataStore("focusforce_blocking")

private object K {
    val ENABLED = booleanPreferencesKey("enabled")
    val PASSES = booleanPreferencesKey("passes_enabled")
    val GUARD = booleanPreferencesKey("guard_settings")
    val STRICT_UNTIL = longPreferencesKey("strict_until_ms")
    val PASS_DAY = longPreferencesKey("pass_epoch_day")
    val PASS_USED = intPreferencesKey("pass_used_today")
    val PASS_GRANTS = stringSetPreferencesKey("pass_grants") // "package|untilMs"
}

internal fun Preferences.toBlockSettings() = BlockSettings(
    enabled = this[K.ENABLED] ?: false,
    passesEnabled = this[K.PASSES] ?: true,
    guardSettings = this[K.GUARD] ?: true,
    strictUntilMs = this[K.STRICT_UNTIL] ?: 0L,
)

internal fun Preferences.toPassState() = PassState(
    epochDay = this[K.PASS_DAY] ?: 0L,
    usedToday = this[K.PASS_USED] ?: 0,
    grants = (this[K.PASS_GRANTS] ?: emptySet()).mapNotNull { entry ->
        val i = entry.lastIndexOf('|')
        val until = if (i > 0) entry.substring(i + 1).toLongOrNull() else null
        if (until == null) null else entry.substring(0, i) to until
    }.toMap(),
)

/** Settings survive process death and reboot because they live in DataStore, not in memory. */
@Singleton
class BlockSettingsRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : BlockSettingsRepository {
    private val store get() = context.blockingDataStore

    override val settings: Flow<BlockSettings> = store.data.map { it.toBlockSettings() }.distinctUntilChanged()
    override val passState: Flow<PassState> = store.data.map { it.toPassState() }.distinctUntilChanged()

    override suspend fun update(transform: (BlockSettings) -> BlockSettings) {
        store.edit { p ->
            val n = transform(p.toBlockSettings())
            p[K.ENABLED] = n.enabled
            p[K.PASSES] = n.passesEnabled
            p[K.GUARD] = n.guardSettings
            p[K.STRICT_UNTIL] = n.strictUntilMs
        }
    }

    override suspend fun reset() {
        store.edit { it.clear() }
    }

    override suspend fun updatePass(transform: (PassState) -> PassState) {
        store.edit { p ->
            val n = transform(p.toPassState())
            p[K.PASS_DAY] = n.epochDay
            p[K.PASS_USED] = n.usedToday
            p[K.PASS_GRANTS] = n.grants.map { (pkg, until) -> "$pkg|$until" }.toSet()
        }
    }
}

@Singleton
class BlockedAppsRepositoryImpl @Inject constructor(
    private val dao: BlockedAppDao,
) : BlockedAppsRepository {
    override fun observe(): Flow<Set<String>> = dao.observePackages().map { it.toSet() }.distinctUntilChanged()

    override suspend fun add(packageNames: Collection<String>) {
        val now = System.currentTimeMillis()
        dao.insert(packageNames.map { BlockedAppEntity(it, now) })
    }

    override suspend fun remove(packageNames: Collection<String>) {
        // chunk: SQLite limits the number of bound variables
        packageNames.chunked(500).forEach { dao.delete(it) }
    }
}

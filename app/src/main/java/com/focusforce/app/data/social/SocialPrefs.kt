package com.focusforce.app.data.social

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.socialStore: DataStore<Preferences> by preferencesDataStore("focusforce_social")

data class SocialSettings(
    /** What others see as your name; null = use the sanitized name from your Google account. */
    val displayName: String? = null,
    /** Let squad mates see when you are in a focus block. */
    val sharePresence: Boolean = true,
    /** Stay off every leaderboard (your weekly score is not published and any published one is removed). */
    val hideFromLeaderboards: Boolean = false,
    val friendCode: String = "",
)

private object S {
    val NAME = stringPreferencesKey("display_name")
    val PRESENCE = booleanPreferencesKey("share_presence")
    val HIDE = booleanPreferencesKey("hide_leaderboards")
    val CODE = stringPreferencesKey("friend_code")
    val LAST_STREAK = intPreferencesKey("last_shown_streak")
}

@Singleton
class SocialPrefs @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val store get() = context.socialStore

    val settings: Flow<SocialSettings> = store.data.map {
        SocialSettings(it[S.NAME], it[S.PRESENCE] ?: true, it[S.HIDE] ?: false, it[S.CODE].orEmpty())
    }.distinctUntilChanged()

    suspend fun current(): SocialSettings = settings.first()

    suspend fun setDisplayName(name: String?) { store.edit { if (name == null) it.remove(S.NAME) else it[S.NAME] = name } }
    suspend fun setSharePresence(on: Boolean) { store.edit { it[S.PRESENCE] = on } }
    suspend fun setHideFromLeaderboards(on: Boolean) { store.edit { it[S.HIDE] = on } }
    suspend fun setFriendCode(code: String) { store.edit { it[S.CODE] = code } }
    /** The streak the power meter showed last time, so a broken streak can drain gently from there. */
    suspend fun lastShownStreak(): Int = store.data.first()[S.LAST_STREAK] ?: 0
    suspend fun setLastShownStreak(days: Int) { store.edit { it[S.LAST_STREAK] = days } }
    suspend fun clear() { store.edit { it.clear() } }
}

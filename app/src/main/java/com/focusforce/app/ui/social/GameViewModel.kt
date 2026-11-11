package com.focusforce.app.ui.social

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusforce.app.data.social.GameState
import com.focusforce.app.data.social.GameStateRepository
import com.focusforce.app.data.social.SocialPrefs
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Rank, XP and streak for the Profile screen. */
@HiltViewModel
class GameViewModel @Inject constructor(
    repository: GameStateRepository,
    private val prefs: SocialPrefs,
) : ViewModel() {

    val state: StateFlow<GameState?> = repository.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Null until loaded, so the meter does not animate from a wrong starting point. */
    private val _previousStreak = MutableStateFlow<Int?>(null)
    val previousStreak: StateFlow<Int?> = _previousStreak

    init {
        viewModelScope.launch { _previousStreak.value = prefs.lastShownStreak() }
    }

    fun markShown(streak: Int) {
        viewModelScope.launch { prefs.setLastShownStreak(streak) }
    }
}

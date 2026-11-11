package com.focusforce.app.ui.social

import androidx.annotation.StringRes
import com.focusforce.app.R
import com.focusforce.app.domain.game.Rank

val Rank.nameRes: Int @StringRes get() = when (this) {
    Rank.RECRUIT -> R.string.rank_recruit
    Rank.CADET -> R.string.rank_cadet
    Rank.GUARDIAN -> R.string.rank_guardian
    Rank.SENTINEL -> R.string.rank_sentinel
    Rank.CHAMPION -> R.string.rank_champion
    Rank.LEGEND -> R.string.rank_legend
}

package com.focusforce.app.ui.theme

import androidx.annotation.StringRes
import com.focusforce.app.R
import com.focusforce.app.domain.model.HeroTheme

/** User-facing identity copy for each hero (string resources so Phase 7 can localize). */
val HeroTheme.nameRes: Int @StringRes get() = when (this) {
    HeroTheme.VECTOR -> R.string.hero_vector_name
    HeroTheme.NOCTIS -> R.string.hero_noctis_name
    HeroTheme.DASH -> R.string.hero_dash_name
}

val HeroTheme.titleRes: Int @StringRes get() = when (this) {
    HeroTheme.VECTOR -> R.string.hero_vector_title
    HeroTheme.NOCTIS -> R.string.hero_noctis_title
    HeroTheme.DASH -> R.string.hero_dash_title
}

val HeroTheme.taglineRes: Int @StringRes get() = when (this) {
    HeroTheme.VECTOR -> R.string.hero_vector_tagline
    HeroTheme.NOCTIS -> R.string.hero_noctis_tagline
    HeroTheme.DASH -> R.string.hero_dash_tagline
}

val HeroTheme.traitsRes: Int @StringRes get() = when (this) {
    HeroTheme.VECTOR -> R.string.hero_vector_traits
    HeroTheme.NOCTIS -> R.string.hero_noctis_traits
    HeroTheme.DASH -> R.string.hero_dash_traits
}

package com.focusforce.app.domain.model

/** The three switchable hero identities. Each one is a complete theme. */
enum class HeroTheme { VECTOR, NOCTIS, DASH }

/** What the recruit wants FocusForce to help with. Chosen in onboarding. */
enum class FocusGoal { STUDY, WORK, SLEEP, LESS_SOCIAL }

/** Bounds for the daily screen-time goal, shared by onboarding, profile and (later) limits. */
object DailyGoal {
    const val MIN_MINUTES = 30
    const val MAX_MINUTES = 720
    const val STEP_MINUTES = 15
    const val DEFAULT_MINUTES = 180

    fun clamp(minutes: Int): Int =
        minutes.coerceIn(MIN_MINUTES, MAX_MINUTES) / STEP_MINUTES * STEP_MINUTES
}

data class UserSettings(
    val hero: HeroTheme = HeroTheme.VECTOR,
    val goals: Set<FocusGoal> = emptySet(),
    val dailyGoalMinutes: Int = DailyGoal.DEFAULT_MINUTES,
    val onboardingComplete: Boolean = false,
)

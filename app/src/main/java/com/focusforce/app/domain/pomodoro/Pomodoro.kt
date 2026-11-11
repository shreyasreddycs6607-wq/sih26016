package com.focusforce.app.domain.pomodoro

enum class PomodoroPhase { FOCUS, SHORT_BREAK, LONG_BREAK }

/** Custom lengths in minutes. [coerced] clamps to sane ranges so storage can never hold nonsense. */
data class PomodoroConfig(
    val focusMin: Int = 25,
    val shortBreakMin: Int = 5,
    val longBreakMin: Int = 15,
    val cyclesBeforeLong: Int = 4,
) {
    fun coerced() = PomodoroConfig(
        focusMin.coerceIn(5, 120), shortBreakMin.coerceIn(1, 30),
        longBreakMin.coerceIn(5, 60), cyclesBeforeLong.coerceIn(2, 8),
    )
}

/** A running set: focus, break, focus, ... ending with a long break after [PomodoroConfig.cyclesBeforeLong] focus blocks. */
data class PomodoroRun(
    val config: PomodoroConfig,
    val phase: PomodoroPhase,
    val completedFocus: Int,
    val phaseStartMs: Long,
    val phaseEndMs: Long,
)

sealed interface PomodoroEvent {
    data class FocusCompleted(val startMs: Long, val endMs: Long, val minutes: Int) : PomodoroEvent
    data class FocusStopped(val startMs: Long, val endMs: Long) : PomodoroEvent
    data class BreakStarted(val long: Boolean) : PomodoroEvent
    data object SetCompleted : PomodoroEvent
}

object Pomodoro {
    private const val MIN = 60_000L

    fun start(config: PomodoroConfig, nowMs: Long): PomodoroRun {
        val c = config.coerced()
        return PomodoroRun(c, PomodoroPhase.FOCUS, 0, nowMs, nowMs + c.focusMin * MIN)
    }

    /** Apps are blocked only during FOCUS. */
    fun isBlocking(run: PomodoroRun?): Boolean = run?.phase == PomodoroPhase.FOCUS

    fun remainingMs(run: PomodoroRun, nowMs: Long): Long = (run.phaseEndMs - nowMs).coerceAtLeast(0L)

    /**
     * Moves through every phase whose end time has passed (the phone may have slept through several).
     * Returns the new run (null when the whole set is finished) and what happened on the way.
     */
    fun advance(run: PomodoroRun, nowMs: Long): Pair<PomodoroRun?, List<PomodoroEvent>> {
        var current: PomodoroRun? = run
        val events = mutableListOf<PomodoroEvent>()
        while (current != null && nowMs >= current.phaseEndMs) {
            val c = current
            when (c.phase) {
                PomodoroPhase.FOCUS -> {
                    val done = c.completedFocus + 1
                    events += PomodoroEvent.FocusCompleted(c.phaseStartMs, c.phaseEndMs, c.config.focusMin)
                    val long = done % c.config.cyclesBeforeLong == 0
                    events += PomodoroEvent.BreakStarted(long)
                    val len = (if (long) c.config.longBreakMin else c.config.shortBreakMin) * MIN
                    current = c.copy(
                        phase = if (long) PomodoroPhase.LONG_BREAK else PomodoroPhase.SHORT_BREAK,
                        completedFocus = done, phaseStartMs = c.phaseEndMs, phaseEndMs = c.phaseEndMs + len,
                    )
                }
                PomodoroPhase.SHORT_BREAK -> current = c.copy(
                    phase = PomodoroPhase.FOCUS, phaseStartMs = c.phaseEndMs, phaseEndMs = c.phaseEndMs + c.config.focusMin * MIN,
                )
                PomodoroPhase.LONG_BREAK -> {
                    events += PomodoroEvent.SetCompleted
                    current = null
                }
            }
        }
        return current to events
    }

    /** Stopping during a focus block records the partial block (only if at least a minute passed). */
    fun stop(run: PomodoroRun, nowMs: Long): PomodoroEvent? =
        if (run.phase == PomodoroPhase.FOCUS && nowMs - run.phaseStartMs >= MIN) PomodoroEvent.FocusStopped(run.phaseStartMs, nowMs) else null
}

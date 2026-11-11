package com.focusforce.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

enum class Haptic { Tick, Confirm }

/** Returns a function that fires the right haptic for a key action. */
@Composable
fun rememberHaptics(): (Haptic) -> Unit {
    val feedback = LocalHapticFeedback.current
    return remember(feedback) {
        { kind: Haptic ->
            feedback.performHapticFeedback(
                when (kind) {
                    Haptic.Tick -> HapticFeedbackType.TextHandleMove
                    Haptic.Confirm -> HapticFeedbackType.LongPress
                },
            )
        }
    }
}

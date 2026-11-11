package com.focusforce.app.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.focusforce.app.R

/** "2h 05m", "45m" or "<1m". */
@Composable
fun durationText(ms: Long): String {
    val totalMin = ms / 60_000L
    return when {
        totalMin >= 60 -> stringResource(R.string.duration_hm, (totalMin / 60).toInt(), (totalMin % 60).toInt())
        totalMin >= 1 -> stringResource(R.string.duration_m, totalMin.toInt())
        ms > 0 -> stringResource(R.string.duration_lt_1m)
        else -> stringResource(R.string.duration_m, 0)
    }
}

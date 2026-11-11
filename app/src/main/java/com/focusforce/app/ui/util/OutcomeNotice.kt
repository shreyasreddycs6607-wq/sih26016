package com.focusforce.app.ui.util

import com.focusforce.app.R
import com.focusforce.app.data.blocking.Outcome

/** The message to show for a refused action, or null when there is nothing to say. One mapping for every screen. */
fun Outcome.noticeRes(): Int? = when (this) {
    Outcome.OK, Outcome.NO_PASS -> null
    Outcome.DENIED_STRICT -> R.string.notice_strict_locked
    Outcome.INVALID -> R.string.web_notice_invalid_input
    Outcome.UPGRADE -> R.string.notice_pro_required
}

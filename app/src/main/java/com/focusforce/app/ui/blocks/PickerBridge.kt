package com.focusforce.app.ui.blocks

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Hand-over point between an editor (schedule, group, limit) and the app picker: the editor seeds the current
 * selection, opens the picker, and reads the result back when the picker closes. In-memory on purpose: it only
 * lives while one editing flow is on screen.
 */
@Singleton
class PickerBridge @Inject constructor() {
    val selection = MutableStateFlow<Set<String>>(emptySet())

    fun begin(initial: Set<String>) {
        selection.value = initial
    }

    companion object {
        const val MODE_BLOCKED = "blocked"
        const val MODE_MULTI = "multi"
        const val MODE_SINGLE = "single"
    }
}

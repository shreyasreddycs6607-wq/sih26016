package com.focusforce.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/** Calls [onRefresh] when the screen becomes visible and then every [intervalMs] while it stays visible. */
@Composable
fun RefreshWhileVisible(intervalMs: Long = 30_000L, onRefresh: () -> Unit) {
    val owner = LocalLifecycleOwner.current
    val latest = rememberUpdatedState(onRefresh)
    LaunchedEffect(owner) {
        owner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) {
                latest.value()
                delay(intervalMs)
            }
        }
    }
}

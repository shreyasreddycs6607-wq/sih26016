package com.focusforce.app.ui.insights

import java.time.LocalDate
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow

/** Emits today's local date, again whenever midnight passes (checked every 30 seconds while collected). */
fun todayFlow(): Flow<LocalDate> = flow {
    while (true) {
        emit(LocalDate.now())
        delay(30_000)
    }
}.distinctUntilChanged()

package com.focusforce.app.ui.permissions

import androidx.lifecycle.ViewModel
import com.focusforce.app.permissions.PermissionChecker
import com.focusforce.app.permissions.PermissionKind
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Live grant state. Call [refresh] whenever the user returns from Settings (ON_RESUME). */
@HiltViewModel
class PermissionsViewModel @Inject constructor(
    private val checker: PermissionChecker,
) : ViewModel() {
    private val _state = MutableStateFlow(checker.snapshot())
    val state: StateFlow<Map<PermissionKind, Boolean>> = _state.asStateFlow()

    fun refresh() {
        _state.value = checker.snapshot()
    }
}

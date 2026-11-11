package com.focusforce.app.ui.settings

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusforce.app.R
import com.focusforce.app.data.billing.EntitlementRepository
import com.focusforce.app.data.billing.ProProduct
import com.focusforce.app.data.billing.PurchaseLaunch
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class UpgradeUi(val pro: Boolean, val product: ProProduct?, val fakeStore: Boolean, val notice: Int?)

@HiltViewModel
class UpgradeViewModel @Inject constructor(
    private val entitlement: EntitlementRepository,
) : ViewModel() {

    private val notice = MutableStateFlow<Int?>(null)
    private val billing get() = entitlement.billing

    val state: StateFlow<UpgradeUi> = combine(entitlement.pro, billing.product, notice) { pro, product, n ->
        UpgradeUi(pro, product, product?.productId?.startsWith("debug_") == true, n)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UpgradeUi(false, null, false, null))

    fun clearNotice() { notice.value = null }

    fun buy(activity: Activity) {
        viewModelScope.launch {
            notice.value = when (billing.purchase(activity)) {
                PurchaseLaunch.LAUNCHED -> null
                PurchaseLaunch.UNAVAILABLE -> R.string.upgrade_unavailable
                PurchaseLaunch.ERROR -> R.string.upgrade_error
            }
        }
    }

    fun restore() {
        viewModelScope.launch {
            billing.restore()
            notice.value = R.string.upgrade_restore_done
        }
    }
}

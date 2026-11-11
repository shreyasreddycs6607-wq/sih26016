package com.focusforce.app.ui.web

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusforce.app.R
import com.focusforce.app.data.apps.AppInfoProvider
import com.focusforce.app.data.blocking.Outcome
import com.focusforce.app.data.web.RulesRepository
import com.focusforce.app.data.web.RulesSource
import com.focusforce.app.data.web.RulesUpdate
import com.focusforce.app.data.web.WebBlockManager
import com.focusforce.app.data.web.WebPrefs
import com.focusforce.app.domain.repo.BlockSettingsRepository
import com.focusforce.app.domain.rules.ParseResult
import com.focusforce.app.domain.rules.RulesParser
import com.focusforce.app.permissions.PermissionChecker
import com.focusforce.app.permissions.PermissionKind
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class FeedRow(val packageName: String, val label: String, val verified: Boolean, val enabled: Boolean)
data class CategoryRow(val id: String, val domainCount: Int, val enabled: Boolean)
data class Notice(val res: Int, val arg: Int = 0)

data class WebUi(
    val blockingOn: Boolean,
    val accessibilityOn: Boolean,
    val feeds: List<FeedRow>,
    val websitesEnabled: Boolean,
    val categories: List<CategoryRow>,
    val domains: List<String>,
    val keywords: List<String>,
    val browsers: List<String>,
    val rulesVersion: Int,
    val rulesSource: RulesSource,
    val remoteConfigured: Boolean,
    val autoUpdate: Boolean,
    val lastCheckedMs: Long,
)

@HiltViewModel
class WebBlockingViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    prefs: WebPrefs,
    private val rules: RulesRepository,
    private val manager: WebBlockManager,
    blockSettings: BlockSettingsRepository,
    private val permissions: PermissionChecker,
    private val appInfo: AppInfoProvider,
) : ViewModel() {

    private val tick = MutableStateFlow(0)
    private val _notice = MutableStateFlow<Notice?>(null)
    val notice: StateFlow<Notice?> = _notice

    val state: StateFlow<WebUi?> = combine(prefs.settings, rules.rules, prefs.rulesCheckedAtMs, blockSettings.settings, tick) { web, loaded, checked, block, _ ->
        val cfg = loaded.config
        WebUi(
            blockingOn = block.enabled,
            accessibilityOn = permissions.isGranted(PermissionKind.ACCESSIBILITY),
            feeds = cfg.feeds.filter { appInfo.meta(it.packageName).installed }
                .map { FeedRow(it.packageName, it.label, it.verified, it.packageName in web.feedPackages) },
            websitesEnabled = web.websitesEnabled,
            categories = cfg.categories.map { CategoryRow(it.id, it.domains.size, it.id in web.categories) },
            domains = web.customDomains.sorted(),
            keywords = web.keywords.sorted(),
            browsers = cfg.browsers.filter { appInfo.meta(it.packageName).installed }.map { it.label },
            rulesVersion = cfg.version,
            rulesSource = loaded.source,
            remoteConfigured = rules.remoteConfigured,
            autoUpdate = web.autoUpdateRules,
            lastCheckedMs = checked,
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun refresh() { tick.value++ }
    fun clearNotice() { _notice.value = null }

    private fun act(action: suspend () -> Outcome) {
        viewModelScope.launch {
            when (action()) {
                Outcome.DENIED_STRICT -> _notice.value = Notice(R.string.notice_strict_locked)
                Outcome.INVALID -> _notice.value = Notice(R.string.web_notice_invalid_input)
                else -> Unit
            }
        }
    }

    fun setFeed(pkg: String, on: Boolean) = act { manager.setFeed(pkg, on) }
    fun setWebsites(on: Boolean) = act { manager.setWebsitesEnabled(on) }
    fun setCategory(id: String, on: Boolean) = act { manager.setCategory(id, on) }
    fun addDomain(text: String) = act { manager.addDomain(text) }
    fun removeDomain(domain: String) = act { manager.removeDomain(domain) }
    fun addKeyword(text: String) = act { manager.addKeyword(text) }
    fun removeKeyword(word: String) = act { manager.removeKeyword(word) }
    fun setAutoUpdate(on: Boolean) { viewModelScope.launch { manager.setAutoUpdate(on) } }

    fun importFrom(uri: Uri) {
        viewModelScope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        val out = java.io.ByteArrayOutputStream()
                        val buffer = ByteArray(8 * 1024)
                        while (out.size() <= RulesParser.MAX_BYTES) {
                            val n = stream.read(buffer)
                            if (n < 0) break
                            out.write(buffer, 0, n)
                        }
                        out.toString(Charsets.UTF_8.name())
                    }
                }.getOrNull()
            }
            _notice.value = if (text == null) Notice(R.string.web_notice_unreadable) else describe(rules.importFromText(text))
        }
    }

    fun checkForUpdates() {
        viewModelScope.launch { _notice.value = describe(rules.fetchRemote()) }
    }

    private fun describe(result: RulesUpdate): Notice = when (result) {
        is RulesUpdate.Applied -> Notice(R.string.web_notice_applied, result.version)
        is RulesUpdate.NotNewer -> Notice(R.string.web_notice_not_newer, result.currentVersion)
        is RulesUpdate.Invalid -> Notice(if (result.reason == ParseResult.Reason.TOO_LARGE) R.string.web_notice_too_large else R.string.web_notice_invalid_file)
        RulesUpdate.NotConfigured -> Notice(R.string.web_notice_not_configured)
        RulesUpdate.NetworkError -> Notice(R.string.web_notice_network)
    }
}

package com.focusforce.app.data.web

import com.focusforce.app.data.billing.EntitlementRepository
import com.focusforce.app.data.blocking.Outcome
import com.focusforce.app.domain.pro.ProFeature
import com.focusforce.app.domain.pro.ProPolicy
import com.focusforce.app.domain.blocking.BlockingRules
import com.focusforce.app.domain.repo.BlockSettingsRepository
import com.focusforce.app.domain.rules.SiteMatcher
import com.focusforce.app.domain.rules.UrlParser
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/** Writer for short-video and website choices. Turning anything OFF or removing it is refused during a strict lock. */
@Singleton
class WebBlockManager @Inject constructor(
    private val prefs: WebPrefs,
    private val blockSettings: BlockSettingsRepository,
    private val entitlement: EntitlementRepository,
) {
    private suspend fun locked() = !BlockingRules.canLoosen(blockSettings.settings.first(), System.currentTimeMillis())

    suspend fun setFeed(packageName: String, on: Boolean): Outcome {
        if (on && !ProPolicy.hasFeature(ProFeature.SHORT_VIDEO_BLOCK, entitlement.isPro())) return Outcome.UPGRADE
        if (!on && locked()) return Outcome.DENIED_STRICT
        prefs.update { it.copy(feedPackages = if (on) it.feedPackages + packageName else it.feedPackages - packageName) }
        return Outcome.OK
    }

    suspend fun setWebsitesEnabled(on: Boolean): Outcome {
        if (on && !ProPolicy.hasFeature(ProFeature.WEBSITE_BLOCK, entitlement.isPro())) return Outcome.UPGRADE
        if (!on && locked()) return Outcome.DENIED_STRICT
        prefs.update { it.copy(websitesEnabled = on) }
        return Outcome.OK
    }

    suspend fun setCategory(id: String, on: Boolean): Outcome {
        if (!on && locked()) return Outcome.DENIED_STRICT
        prefs.update { it.copy(categories = if (on) it.categories + id else it.categories - id) }
        return Outcome.OK
    }

    suspend fun addDomain(raw: String): Outcome {
        val host = UrlParser.host(raw) ?: return Outcome.INVALID
        prefs.update { it.copy(customDomains = it.customDomains + host) }
        return Outcome.OK
    }

    suspend fun removeDomain(domain: String): Outcome {
        if (locked()) return Outcome.DENIED_STRICT
        prefs.update { it.copy(customDomains = it.customDomains - domain) }
        return Outcome.OK
    }

    suspend fun addKeyword(raw: String): Outcome {
        val word = raw.trim().lowercase()
        if (word.length < SiteMatcher.MIN_KEYWORD_LENGTH || word.length > 40) return Outcome.INVALID
        prefs.update { it.copy(keywords = it.keywords + word) }
        return Outcome.OK
    }

    suspend fun removeKeyword(word: String): Outcome {
        if (locked()) return Outcome.DENIED_STRICT
        prefs.update { it.copy(keywords = it.keywords - word) }
        return Outcome.OK
    }

    suspend fun setAutoUpdate(on: Boolean) { prefs.update { it.copy(autoUpdateRules = on) } }
}

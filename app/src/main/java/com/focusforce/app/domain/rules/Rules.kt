package com.focusforce.app.domain.rules

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Detection rules, shipped in assets/rules_default.json and replaceable by a newer file (imported or fetched).
 * Rules only ever name STRUCTURAL identifiers: view-id resource names of the app UI and the id of a browser's address bar.
 * They never describe or match on message text, names or other screen content.
 */
@Serializable
data class RulesConfig(
    val version: Int,
    val feeds: List<FeedRule> = emptyList(),
    val browsers: List<BrowserRule> = emptyList(),
    val categories: List<SiteCategoryList> = emptyList(),
)

/**
 * A short-video section inside an app. The feed counts as "on screen" when ANY id of [anyViewIds] is present
 * (if given), ALL of [allViewIds] are present, and NONE of [noneViewIds] is present.
 * [verified] is false for rules the maintainers have not re-checked against the current app version.
 */
@Serializable
data class FeedRule(
    val packageName: String,
    val label: String,
    val verified: Boolean = false,
    val anyViewIds: List<String> = emptyList(),
    val allViewIds: List<String> = emptyList(),
    val noneViewIds: List<String> = emptyList(),
)

/** A browser and the view-id(s) of its address bar. Only that one field is ever read, and only to extract the site name. */
@Serializable
data class BrowserRule(
    val packageName: String,
    val label: String,
    val verified: Boolean = false,
    val urlBarIds: List<String>,
)

/** A built-in website category (e.g. "adult", "social", "gambling"): a starter list of domains, not an exhaustive one. */
@Serializable
data class SiteCategoryList(val id: String, val domains: List<String>)

sealed interface ParseResult {
    data class Ok(val config: RulesConfig) : ParseResult
    data class Error(val reason: Reason) : ParseResult
    enum class Reason { TOO_LARGE, NOT_JSON, BAD_VERSION, TOO_MANY_ENTRIES, INVALID_ENTRY }
}

object RulesParser {
    const val MAX_BYTES = 512 * 1024
    private const val MAX_FEEDS = 200
    private const val MAX_BROWSERS = 60
    private const val MAX_CATEGORIES = 30
    private const val MAX_DOMAINS_PER_CATEGORY = 20_000

    private val json = Json { ignoreUnknownKeys = true; isLenient = false }
    private val packageRegex = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+$")
    private val viewIdRegex = Regex("^[A-Za-z0-9_.:/$\\-]{1,200}$")
    private val domainRegex = Regex("^[a-z0-9]([a-z0-9\\-]*[a-z0-9])?(\\.[a-z0-9]([a-z0-9\\-]*[a-z0-9])?)+$")
    private val categoryIdRegex = Regex("^[a-z][a-z0-9_]{1,30}$")

    /** All-or-nothing: one invalid entry rejects the whole file, so a half-garbage file can never half-apply. */
    fun parse(text: String): ParseResult {
        if (text.toByteArray().size > MAX_BYTES) return ParseResult.Error(ParseResult.Reason.TOO_LARGE)
        val config = try {
            json.decodeFromString(RulesConfig.serializer(), text)
        } catch (_: Exception) {
            return ParseResult.Error(ParseResult.Reason.NOT_JSON)
        }
        if (config.version < 1) return ParseResult.Error(ParseResult.Reason.BAD_VERSION)
        if (config.feeds.size > MAX_FEEDS || config.browsers.size > MAX_BROWSERS || config.categories.size > MAX_CATEGORIES ||
            config.categories.any { it.domains.size > MAX_DOMAINS_PER_CATEGORY }
        ) return ParseResult.Error(ParseResult.Reason.TOO_MANY_ENTRIES)

        val feedsOk = config.feeds.all { f ->
            packageRegex.matches(f.packageName) && f.label.length in 1..60 &&
                (f.anyViewIds + f.allViewIds + f.noneViewIds).all { viewIdRegex.matches(it) } &&
                (f.anyViewIds.isNotEmpty() || f.allViewIds.isNotEmpty())
        }
        val browsersOk = config.browsers.all { b ->
            packageRegex.matches(b.packageName) && b.label.length in 1..60 && b.urlBarIds.isNotEmpty() && b.urlBarIds.all { viewIdRegex.matches(it) }
        }
        val categoriesOk = config.categories.all { c -> categoryIdRegex.matches(c.id) && c.domains.all { domainRegex.matches(it) } }
        return if (feedsOk && browsersOk && categoriesOk) ParseResult.Ok(config) else ParseResult.Error(ParseResult.Reason.INVALID_ENTRY)
    }

    /** A fetched file replaces the current one only if strictly newer; a file the user picked may be equal. */
    fun shouldReplace(currentVersion: Int, incomingVersion: Int, userPicked: Boolean): Boolean =
        if (userPicked) incomingVersion >= currentVersion else incomingVersion > currentVersion
}

object FeedMatcher {
    /** [ids] are the view-id names currently on screen in the app's window. */
    fun isFeed(rule: FeedRule, ids: Set<String>): Boolean =
        (rule.anyViewIds.isEmpty() || rule.anyViewIds.any { it in ids }) &&
            rule.allViewIds.all { it in ids } &&
            rule.noneViewIds.none { it in ids }
}

/** What to block on the web. Built from the user's choices, the rules file and any running focus schedule. */
data class SiteRules(val domains: Set<String>, val keywords: Set<String>)

sealed interface SiteHit {
    data class Domain(val domain: String) : SiteHit
    data class Keyword(val keyword: String) : SiteHit
}

object UrlParser {
    /** The site name out of what an address bar shows ("https://www.Reddit.com/r/x?y", "reddit.com/r/x"), or null if it is not a site. */
    fun host(raw: String): String? {
        var s = raw.trim().lowercase()
        if (s.isEmpty() || s.any { it.isWhitespace() }) return null
        s = s.substringAfter("://", s)
        s = s.substringAfter('@', s)
        s = s.substringBefore('/').substringBefore('?').substringBefore('#').substringBefore(':')
        s = s.removePrefix("www.").removePrefix("m.").removePrefix("mobile.")
        if (!s.contains('.') || s.startsWith('.') || s.endsWith('.')) return null
        return if (s.all { it.isLetterOrDigit() || it == '.' || it == '-' }) s else null
    }
}

object SiteMatcher {
    /** `reddit.com` matches reddit.com and old.reddit.com, never notreddit.com. */
    fun matchesDomain(host: String, domain: String): Boolean = host == domain || host.endsWith(".$domain")

    const val MIN_KEYWORD_LENGTH = 3

    /**
     * Checks what the address bar shows. Domains are matched on the site name only. Keywords are matched
     * (case-insensitively) against the whole text, so they also catch search words typed or shown in the bar.
     */
    fun check(barText: String, rules: SiteRules): SiteHit? {
        val text = barText.trim().lowercase()
        if (text.isEmpty()) return null
        UrlParser.host(text)?.let { host ->
            rules.domains.firstOrNull { matchesDomain(host, it) }?.let { return SiteHit.Domain(it) }
        }
        return rules.keywords.firstOrNull { it.length >= MIN_KEYWORD_LENGTH && text.contains(it) }?.let { SiteHit.Keyword(it) }
    }
}

/** The user's website choices. Categories refer to ids in the rules file. */
data class WebSettings(
    val feedPackages: Set<String> = emptySet(),
    val websitesEnabled: Boolean = false,
    val categories: Set<String> = emptySet(),
    val customDomains: Set<String> = emptySet(),
    val keywords: Set<String> = emptySet(),
    val autoUpdateRules: Boolean = true,
)

object SiteRulesBuilder {
    /** Everything blocked right now: chosen categories + own domains + domains of running schedules; keywords as typed. */
    fun build(settings: WebSettings, config: RulesConfig, scheduleSites: Set<String>): SiteRules {
        val fromCategories = config.categories.filter { it.id in settings.categories }.flatMapTo(mutableSetOf()) { it.domains }
        val own = if (settings.websitesEnabled) fromCategories + settings.customDomains else emptySet()
        val domains = own + scheduleSites
        val keywords = if (settings.websitesEnabled) settings.keywords.map { it.trim().lowercase() }.filter { it.length >= SiteMatcher.MIN_KEYWORD_LENGTH }.toSet() else emptySet()
        return SiteRules(domains, keywords)
    }
}

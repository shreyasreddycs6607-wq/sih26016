package com.focusforce.app.data.web

import android.content.Context
import com.focusforce.app.BuildConfig
import com.focusforce.app.domain.rules.ParseResult
import com.focusforce.app.domain.rules.RulesConfig
import com.focusforce.app.domain.rules.RulesParser
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

enum class RulesSource { BUNDLED, IMPORTED, UPDATED }

data class LoadedRules(val config: RulesConfig, val source: RulesSource)

sealed interface RulesUpdate {
    data class Applied(val version: Int) : RulesUpdate
    data class NotNewer(val currentVersion: Int) : RulesUpdate
    data class Invalid(val reason: ParseResult.Reason) : RulesUpdate
    data object NotConfigured : RulesUpdate
    data object NetworkError : RulesUpdate
}

/**
 * Holds the active detection rules: the bundled file, or a newer one the user imported or the optional updater
 * fetched. Every candidate goes through [RulesParser] (strict, all-or-nothing) before it is used or saved, and a
 * saved file that no longer parses is ignored. The only network use in this class is [fetchRemote], and it only
 * runs when the app was built with a rules URL.
 */
@Singleton
class RulesRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: WebPrefs,
) {
    private val _rules = MutableStateFlow(loadInitial())
    val rules: StateFlow<LoadedRules> = _rules.asStateFlow()

    /** True when the build includes a rules URL, i.e. remote updates are possible at all. */
    val remoteConfigured: Boolean get() = BuildConfig.RULES_URL.startsWith("https://")

    private fun fileFor(source: RulesSource) = File(context.filesDir, "rules-${source.name.lowercase()}.json")

    private fun loadInitial(): LoadedRules {
        val bundled = parseText(readBundled())?.let { LoadedRules(it, RulesSource.BUNDLED) }
            ?: LoadedRules(RulesConfig(version = 1), RulesSource.BUNDLED) // cannot happen: the asset is checked by a unit test
        val stored = listOf(RulesSource.IMPORTED, RulesSource.UPDATED).mapNotNull { src ->
            fileFor(src).takeIf { it.exists() }?.let { f -> parseText(runCatching { f.readText() }.getOrDefault(""))?.let { LoadedRules(it, src) } }
        }
        // Newest wins; on a tie a file the user imported or downloaded beats the bundled one.
        var best = bundled
        for (candidate in stored) if (candidate.config.version >= best.config.version) best = candidate
        return best
    }

    private fun readBundled(): String = context.assets.open(BUNDLED_ASSET).bufferedReader().use { it.readText() }

    private fun parseText(text: String): RulesConfig? = (RulesParser.parse(text) as? ParseResult.Ok)?.config

    /** A rules file picked by the user. Same version is accepted (re-importing your own file is fine); older is not. */
    suspend fun importFromText(text: String): RulesUpdate = apply(text, RulesSource.IMPORTED, userPicked = true)

    private suspend fun apply(text: String, source: RulesSource, userPicked: Boolean): RulesUpdate = withContext(Dispatchers.IO) {
        when (val result = RulesParser.parse(text)) {
            is ParseResult.Error -> RulesUpdate.Invalid(result.reason)
            is ParseResult.Ok -> {
                val current = _rules.value.config.version
                if (!RulesParser.shouldReplace(current, result.config.version, userPicked)) return@withContext RulesUpdate.NotNewer(current)
                val target = fileFor(source)
                val tmp = File(target.parentFile, target.name + ".tmp")
                tmp.writeText(text)
                if (!tmp.renameTo(target)) { target.delete(); tmp.renameTo(target) }
                _rules.value = LoadedRules(result.config, source)
                RulesUpdate.Applied(result.config.version)
            }
        }
    }

    /**
     * Fetches the rules file from the build's rules URL (HTTPS only, no identifiers, generic user agent, size-capped),
     * and applies it only if strictly newer. Returns NotConfigured when the build has no URL.
     */
    suspend fun fetchRemote(): RulesUpdate = withContext(Dispatchers.IO) {
        if (!remoteConfigured) return@withContext RulesUpdate.NotConfigured
        val text = try {
            val conn = URL(BuildConfig.RULES_URL).openConnection() as HttpURLConnection
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            conn.setRequestProperty("User-Agent", "FocusForce-rules")
            conn.setRequestProperty("Accept", "application/json")
            try {
                if (conn.responseCode != HttpURLConnection.HTTP_OK) return@withContext RulesUpdate.NetworkError
                conn.inputStream.use { stream ->
                    // Read at most MAX_BYTES + 1 so an endless or oversized response is detected without loading it all.
                    val out = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(8 * 1024)
                    while (out.size() <= RulesParser.MAX_BYTES) {
                        val n = stream.read(buffer)
                        if (n < 0) break
                        out.write(buffer, 0, n)
                    }
                    if (out.size() > RulesParser.MAX_BYTES) return@withContext RulesUpdate.Invalid(ParseResult.Reason.TOO_LARGE)
                    out.toString(Charsets.UTF_8.name())
                }
            } finally {
                conn.disconnect()
            }
        } catch (_: java.io.IOException) {
            return@withContext RulesUpdate.NetworkError
        }
        prefs.setRulesCheckedAt(System.currentTimeMillis())
        apply(text, RulesSource.UPDATED, userPicked = false)
    }

    private companion object {
        const val BUNDLED_ASSET = "rules_default.json"
    }
}

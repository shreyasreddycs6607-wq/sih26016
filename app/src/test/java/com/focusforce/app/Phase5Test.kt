package com.focusforce.app

import com.focusforce.app.domain.blocking.BlockContext
import com.focusforce.app.domain.blocking.BlockDecision
import com.focusforce.app.domain.blocking.BlockDecisionEngine
import com.focusforce.app.domain.blocking.BlockReason
import com.focusforce.app.domain.blocking.BlockSettings
import com.focusforce.app.domain.blocking.PassState
import com.focusforce.app.domain.rules.FeedMatcher
import com.focusforce.app.domain.rules.FeedRule
import com.focusforce.app.domain.rules.ParseResult
import com.focusforce.app.domain.rules.RulesConfig
import com.focusforce.app.domain.rules.RulesParser
import com.focusforce.app.domain.rules.SiteCategoryList
import com.focusforce.app.domain.rules.SiteHit
import com.focusforce.app.domain.rules.SiteMatcher
import com.focusforce.app.domain.rules.SiteRules
import com.focusforce.app.domain.rules.SiteRulesBuilder
import com.focusforce.app.domain.rules.UrlParser
import com.focusforce.app.domain.rules.WebSettings
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RulesParserTest {
    private val valid = """{"version":2,"feeds":[{"packageName":"com.a.b","label":"A","anyViewIds":["com.a.b:id/x"]}],
        "browsers":[{"packageName":"com.c.d","label":"C","urlBarIds":["com.c.d:id/url_bar"]}],
        "categories":[{"id":"social","domains":["example.com"]}]}"""

    @Test fun parsesAValidFile() {
        val r = RulesParser.parse(valid) as ParseResult.Ok
        assertEquals(2, r.config.version)
        assertEquals("com.a.b", r.config.feeds.single().packageName)
        assertFalse(r.config.feeds.single().verified)
    }

    @Test fun ignoresUnknownKeysButRejectsGarbage() {
        assertTrue(RulesParser.parse("""{"version":1,"somethingNew":true}""") is ParseResult.Ok)
        assertEquals(ParseResult.Reason.NOT_JSON, (RulesParser.parse("not json") as ParseResult.Error).reason)
        assertEquals(ParseResult.Reason.BAD_VERSION, (RulesParser.parse("""{"version":0}""") as ParseResult.Error).reason)
    }

    @Test fun oneBadEntryRejectsTheWholeFile() {
        val badPkg = valid.replace("com.a.b\"", "not a package\"")
        assertEquals(ParseResult.Reason.INVALID_ENTRY, (RulesParser.parse(badPkg) as ParseResult.Error).reason)
        val badDomain = valid.replace("example.com", "exa mple.com")
        assertEquals(ParseResult.Reason.INVALID_ENTRY, (RulesParser.parse(badDomain) as ParseResult.Error).reason)
        val noIds = """{"version":1,"feeds":[{"packageName":"com.a.b","label":"A"}]}"""
        assertEquals(ParseResult.Reason.INVALID_ENTRY, (RulesParser.parse(noIds) as ParseResult.Error).reason)
        val badId = """{"version":1,"browsers":[{"packageName":"com.a.b","label":"A","urlBarIds":["has space"]}]}"""
        assertEquals(ParseResult.Reason.INVALID_ENTRY, (RulesParser.parse(badId) as ParseResult.Error).reason)
    }

    @Test fun rejectsOversizedInput() {
        val big = """{"version":1,"pad":"${"x".repeat(RulesParser.MAX_BYTES)}"}"""
        assertEquals(ParseResult.Reason.TOO_LARGE, (RulesParser.parse(big) as ParseResult.Error).reason)
    }

    @Test fun versionRule() {
        assertTrue(RulesParser.shouldReplace(1, 2, userPicked = false))
        assertFalse(RulesParser.shouldReplace(2, 2, userPicked = false))
        assertTrue(RulesParser.shouldReplace(2, 2, userPicked = true))
        assertFalse(RulesParser.shouldReplace(3, 2, userPicked = true))
    }

    @Test fun theBundledDefaultFileIsValid() {
        val file = listOf("src/main/assets/rules_default.json", "app/src/main/assets/rules_default.json").map(::File).first { it.exists() }
        val config = (RulesParser.parse(file.readText()) as ParseResult.Ok).config
        assertTrue(config.feeds.isNotEmpty())
        assertTrue(config.browsers.any { it.packageName == "com.android.chrome" })
        assertEquals(setOf("adult", "social", "gambling"), config.categories.map { it.id }.toSet())
        assertTrue(config.categories.all { it.domains.isNotEmpty() })
    }
}

class FeedMatcherTest {
    private val rule = FeedRule("p", "Feed", anyViewIds = listOf("p:id/a", "p:id/b"), allViewIds = listOf("p:id/root"), noneViewIds = listOf("p:id/comments"))

    @Test fun needsAnyOfAndAllOfAndNoneOf() {
        assertTrue(FeedMatcher.isFeed(rule, setOf("p:id/root", "p:id/b")))
        assertFalse(FeedMatcher.isFeed(rule, setOf("p:id/root")))               // no "any" id
        assertFalse(FeedMatcher.isFeed(rule, setOf("p:id/b")))                  // missing required root
        assertFalse(FeedMatcher.isFeed(rule, setOf("p:id/root", "p:id/a", "p:id/comments"))) // excluded id present
    }

    @Test fun emptyAnyListMeansOnlyAllMatters() {
        assertTrue(FeedMatcher.isFeed(FeedRule("p", "F", allViewIds = listOf("x")), setOf("x")))
    }
}

class SiteMatchingTest {
    @Test fun hostParsing() {
        assertEquals("reddit.com", UrlParser.host("https://www.Reddit.com/r/android?x=1#top"))
        assertEquals("reddit.com", UrlParser.host("reddit.com/r/android"))
        assertEquals("old.reddit.com", UrlParser.host("http://old.reddit.com:8080/"))
        assertEquals("example.org", UrlParser.host("https://user:pw@example.org/path"))
        assertEquals("example.org", UrlParser.host("m.example.org"))
        assertNull(UrlParser.host("how to cook rice"))     // a search, not a site
        assertNull(UrlParser.host("localhost"))
        assertNull(UrlParser.host(""))
    }

    private val rules = SiteRules(setOf("reddit.com", "bet365.com"), setOf("casino"))

    @Test fun domainsMatchSubdomainsButNotLookalikes() {
        assertEquals(SiteHit.Domain("reddit.com"), SiteMatcher.check("https://old.reddit.com/r/x", rules))
        assertEquals(SiteHit.Domain("reddit.com"), SiteMatcher.check("reddit.com", rules))
        assertNull(SiteMatcher.check("notreddit.com", rules))
        assertNull(SiteMatcher.check("reddit.com.evil.example", rules))
    }

    @Test fun keywordsMatchTypedTextAndUrlsCaseInsensitively() {
        assertEquals(SiteHit.Keyword("casino"), SiteMatcher.check("best Casino bonus", rules))
        assertEquals(SiteHit.Keyword("casino"), SiteMatcher.check("https://example.org/CASINO/play", rules))
        assertNull(SiteMatcher.check("how to cook rice", rules))
        assertNull(SiteMatcher.check("", rules))
    }

    @Test fun shortKeywordsAreIgnored() {
        assertNull(SiteMatcher.check("go to the gym", SiteRules(emptySet(), setOf("go"))))
    }

    @Test fun builderCombinesCategoriesOwnDomainsAndScheduleSites() {
        val config = RulesConfig(1, categories = listOf(SiteCategoryList("social", listOf("facebook.com")), SiteCategoryList("adult", listOf("x.example"))))
        val settings = WebSettings(websitesEnabled = true, categories = setOf("social"), customDomains = setOf("news.example"), keywords = setOf("  Poker ", "ab"))
        val built = SiteRulesBuilder.build(settings, config, scheduleSites = setOf("youtube.com"))
        assertEquals(setOf("facebook.com", "news.example", "youtube.com"), built.domains)
        assertEquals(setOf("poker"), built.keywords)
    }

    @Test fun disabledWebsiteBlockingStillHonoursRunningSchedulesButNotCategories() {
        val config = RulesConfig(1, categories = listOf(SiteCategoryList("social", listOf("facebook.com"))))
        val built = SiteRulesBuilder.build(WebSettings(websitesEnabled = false, categories = setOf("social"), customDomains = setOf("a.example")), config, setOf("b.example"))
        assertEquals(setOf("b.example"), built.domains)
        assertTrue(built.keywords.isEmpty())
    }
}

class FeedAndSiteDecisionTest {
    private val now = 1_000_000_000L
    private val today = 20_000L
    private fun ctx(feed: Set<String> = emptySet(), site: String? = null, pass: PassState = PassState(), strictUntil: Long = 0, enabled: Boolean = true) =
        BlockContext(BlockSettings(enabled = enabled, strictUntilMs = strictUntil), emptySet(), pass, "me", emptySet(), feedOnScreen = feed, blockedSite = site)
    private fun decide(c: BlockContext, pkg: String = "app") = BlockDecisionEngine.decide(pkg, "", c, now, today)

    @Test fun shortVideoFeedIsBlockedOnlyWhileOnScreen() {
        val d = decide(ctx(feed = setOf("app"))) as BlockDecision.Block
        assertEquals(BlockReason.SHORT_VIDEO, d.reason)
        assertEquals("feed:app", d.passKey)
        assertEquals(BlockDecision.Allow, decide(ctx()))
    }

    @Test fun websiteBlockCarriesTheSiteAndAPerSitePassKey() {
        val d = decide(ctx(site = "reddit.com"), "browser") as BlockDecision.Block
        assertEquals(BlockReason.WEBSITE, d.reason)
        assertEquals("reddit.com", d.detail)
        assertEquals("site:reddit.com", d.passKey)
    }

    @Test fun aPassUnlocksOnlyThatSiteOrFeed() {
        val pass = PassState().grant("site:reddit.com", now, today)
        assertEquals(BlockDecision.Allow, decide(ctx(site = "reddit.com", pass = pass), "browser"))
        assertTrue(decide(ctx(site = "other.com", pass = pass), "browser") is BlockDecision.Block)
        val feedPass = PassState().grant("feed:app", now, today)
        assertEquals(BlockDecision.Allow, decide(ctx(feed = setOf("app"), pass = feedPass)))
    }

    @Test fun strictLockRemovesPassesAndMasterSwitchOffAllowsAll() {
        assertFalse((decide(ctx(feed = setOf("app"), strictUntil = now + 1)) as BlockDecision.Block).canPass)
        assertEquals(BlockDecision.Allow, decide(ctx(feed = setOf("app"), enabled = false)))
    }
}

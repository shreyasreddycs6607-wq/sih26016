package com.focusforce.app.domain.usage

/**
 * First-guess category for an app before the user edits it. Combines Android's own app category
 * (ApplicationInfo.category, API 26+) with a list of well-known package names.
 * The user's override always wins (see [resolve]).
 */
object CategoryDefaults {
    // Values of ApplicationInfo.CATEGORY_* (copied so this stays pure Kotlin).
    const val ANDROID_GAME = 0
    const val ANDROID_VIDEO = 2
    const val ANDROID_SOCIAL = 4
    const val ANDROID_PRODUCTIVITY = 7

    private val distractingPackages = setOf(
        "com.instagram.android", "com.facebook.katana", "com.facebook.orca", "com.twitter.android",
        "com.snapchat.android", "com.zhiliaoapp.musically", "com.ss.android.ugc.trill", "com.reddit.frontpage",
        "com.google.android.youtube", "com.netflix.mediaclient", "com.pinterest", "com.whatsapp.w4b.status",
        "in.mohalla.sharechat", "com.next.innovation.takatak", "tv.twitch.android.app",
    )
    private val productivePackages = setOf(
        "com.google.android.apps.docs", "com.google.android.apps.docs.editors.docs", "com.google.android.apps.docs.editors.sheets",
        "com.google.android.gm", "com.google.android.calendar", "com.google.android.keep", "com.microsoft.office.word",
        "com.microsoft.office.excel", "com.microsoft.teams", "com.slack", "us.zoom.videomeetings", "notion.id",
        "com.google.android.apps.classroom", "com.google.android.apps.meetings", "org.mozilla.firefox.focus",
    )

    fun classify(packageName: String, androidCategory: Int): AppCategory = when {
        packageName in distractingPackages -> AppCategory.DISTRACTING
        packageName in productivePackages -> AppCategory.PRODUCTIVE
        androidCategory == ANDROID_SOCIAL || androidCategory == ANDROID_GAME || androidCategory == ANDROID_VIDEO -> AppCategory.DISTRACTING
        androidCategory == ANDROID_PRODUCTIVITY -> AppCategory.PRODUCTIVE
        else -> AppCategory.NEUTRAL
    }

    fun resolve(packageName: String, androidCategory: Int, overrides: Map<String, AppCategory>): AppCategory =
        overrides[packageName] ?: classify(packageName, androidCategory)
}

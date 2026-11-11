package com.focusforce.app

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.focusforce.app.domain.model.HeroTheme
import com.focusforce.app.ui.theme.HeroTokensRegistry
import org.junit.Assert.assertTrue
import org.junit.Test

/** WCAG AA gate: every text/background pairing the components use must reach 4.5:1 in every theme. */
class ContrastTest {

    private fun ratio(a: Color, b: Color): Double {
        val l1 = a.luminance().toDouble()
        val l2 = b.luminance().toDouble()
        return (maxOf(l1, l2) + 0.05) / (minOf(l1, l2) + 0.05)
    }

    @Test fun allThemesMeetAaForTextPairs() {
        for (hero in HeroTheme.entries) {
            val c = HeroTokensRegistry.forHero(hero).colors
            val pairs = mapOf(
                "text/background" to (c.text to c.background),
                "text/surface" to (c.text to c.surface),
                "text/surfaceHigh" to (c.text to c.surfaceHigh),
                "textMuted/background" to (c.textMuted to c.background),
                "textMuted/surface" to (c.textMuted to c.surface),
                "textMuted/surfaceHigh" to (c.textMuted to c.surfaceHigh),
                "primary/background" to (c.primary to c.background),
                "primary/surface" to (c.primary to c.surface),
                "primary/surfaceHigh" to (c.primary to c.surfaceHigh),
                "onPrimary/primary" to (c.onPrimary to c.primary),
                "secondary/surface" to (c.secondary to c.surface),
                "secondary/surfaceHigh" to (c.secondary to c.surfaceHigh),
                "tertiary/surfaceHigh" to (c.tertiary to c.surfaceHigh),
                "danger/surfaceHigh" to (c.danger to c.surfaceHigh),
                "success/surfaceHigh" to (c.success to c.surfaceHigh),
            )
            for ((name, pair) in pairs) {
                val r = ratio(pair.first, pair.second)
                assertTrue("$hero $name = ${"%.2f".format(r)} (< 4.5)", r >= 4.5)
            }
        }
    }
}

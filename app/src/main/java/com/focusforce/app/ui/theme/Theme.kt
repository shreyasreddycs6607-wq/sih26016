package com.focusforce.app.ui.theme

import android.provider.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.focusforce.app.domain.model.HeroTheme

val LocalHeroTokens = staticCompositionLocalOf { HeroTokensRegistry.forHero(HeroTheme.VECTOR) }

/** True when the user turned system animations off (Settings > Accessibility > Remove animations). */
val LocalReduceMotion = staticCompositionLocalOf { false }

/** Short accessors so components read `FocusTheme.colors.primary`. */
object FocusTheme {
    val tokens: HeroTokens @Composable get() = LocalHeroTokens.current
    val colors: FocusColors @Composable get() = LocalHeroTokens.current.colors
    val typography: FocusTypography @Composable get() = LocalHeroTokens.current.typography
    val spacing: FocusSpacing @Composable get() = LocalHeroTokens.current.spacing
    val shapes: FocusShapes @Composable get() = LocalHeroTokens.current.shapes
    val motion: FocusMotion @Composable get() = LocalHeroTokens.current.motion
    val hero: HeroTheme @Composable get() = LocalHeroTokens.current.hero
    val reduceMotion: Boolean @Composable get() = LocalReduceMotion.current
}

@Composable
private fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    fun read() = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    var reduce by remember { mutableStateOf(read()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { reduce = read() }
    return reduce
}

@Composable
fun FocusForceTheme(hero: HeroTheme, content: @Composable () -> Unit) {
    val tokens = remember(hero) { HeroTokensRegistry.forHero(hero) }
    val c = tokens.colors
    val scheme = darkColorScheme(
        primary = c.primary, onPrimary = c.onPrimary,
        secondary = c.secondary, onSecondary = c.onPrimary,
        tertiary = c.tertiary, onTertiary = c.onPrimary,
        background = c.background, onBackground = c.text,
        surface = c.surface, onSurface = c.text,
        surfaceVariant = c.surfaceHigh, onSurfaceVariant = c.textMuted,
        outline = c.outline, error = c.danger,
    )
    val t = tokens.typography
    val typography = Typography(
        displayLarge = t.display, displayMedium = t.display, displaySmall = t.headline,
        headlineLarge = t.headline, headlineMedium = t.headline, headlineSmall = t.title,
        titleLarge = t.title, titleMedium = t.title.copy(fontSize = 18.sp), titleSmall = t.label,
        bodyLarge = t.body, bodyMedium = t.body.copy(fontSize = 14.sp, lineHeight = 20.sp), bodySmall = t.label,
        labelLarge = t.label, labelMedium = t.label, labelSmall = t.label.copy(fontSize = 12.sp),
    )
    CompositionLocalProvider(
        LocalHeroTokens provides tokens,
        LocalReduceMotion provides rememberReduceMotion(),
        LocalContentColor provides c.text,
        LocalTextStyle provides t.body,
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = typography,
            // Material3 only accepts corner-based shapes (DASH uses a custom slanted shape), and every
            // visible container is a FocusForce component that reads FocusTheme.shapes directly.
            shapes = Shapes(),
            content = content,
        )
    }
}

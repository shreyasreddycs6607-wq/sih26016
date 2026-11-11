package com.focusforce.app.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focusforce.app.domain.model.HeroTheme

/*
 * DESIGN TOKENS: the single place where the three hero themes are defined.
 * Components never hard-code a color, size, shape or duration; they read FocusTheme.tokens.
 */

/** Semantic color roles. All text/background pairs used by components meet WCAG AA (4.5:1); see ContrastTest. */
@Immutable
data class FocusColors(
    val background: Color,
    val surface: Color,
    val surfaceHigh: Color,
    val primary: Color,
    val onPrimary: Color,
    val secondary: Color,
    val tertiary: Color,
    val text: Color,
    val textMuted: Color,
    /** Decorative strokes and borders (not used for text). */
    val outline: Color,
    val danger: Color,
    val success: Color,
)

/** 4dp grid. */
@Immutable
data class FocusSpacing(
    val xs: Dp = 4.dp,
    val sm: Dp = 8.dp,
    val md: Dp = 12.dp,
    val lg: Dp = 16.dp,
    val xl: Dp = 24.dp,
    val xxl: Dp = 32.dp,
    val huge: Dp = 48.dp,
)

@Immutable
data class FocusShapes(
    val small: Shape,
    val medium: Shape,
    val large: Shape,
    val button: Shape,
    val card: Shape,
)

@Immutable
data class FocusElevation(val low: Dp, val mid: Dp, val high: Dp)

/** Type scale. Same sizes in every theme (so layouts hold); families, weights and tracking differ. */
@Immutable
data class FocusTypography(
    val display: TextStyle,
    val headline: TextStyle,
    val title: TextStyle,
    val body: TextStyle,
    val label: TextStyle,
    /** Numbers in HUD/ring/counter contexts. */
    val numeral: TextStyle,
    val numeralLarge: TextStyle,
)

@Immutable
data class FocusMotion(
    val fastMs: Int,
    val mediumMs: Int,
    val slowMs: Int,
    /** Period of ambient background loops (fog, scan line, ...). */
    val ambientMs: Int,
    val easing: Easing,
    val springDamping: Float,
    val springStiffness: Float,
) {
    fun <T> spring(): FiniteAnimationSpec<T> = spring(springDamping, springStiffness)
    fun <T> fast(): FiniteAnimationSpec<T> = tween(fastMs, easing = easing)
    fun <T> medium(): FiniteAnimationSpec<T> = tween(mediumMs, easing = easing)
    fun <T> slow(): FiniteAnimationSpec<T> = tween(slowMs, easing = easing)
}

@Immutable
data class HeroTokens(
    val hero: HeroTheme,
    val colors: FocusColors,
    val typography: FocusTypography,
    val spacing: FocusSpacing,
    val shapes: FocusShapes,
    val elevation: FocusElevation,
    val motion: FocusMotion,
    /** Line weight of rings, borders and HUD strokes. */
    val strokeWidth: Dp,
)

/** Parallelogram used for Dash's comic panels. */
@Immutable
class SlantedShape(private val slant: Dp) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val s = with(density) { slant.toPx() }.coerceAtMost(size.width / 4f)
        val path = Path().apply {
            moveTo(s, 0f)
            lineTo(size.width, 0f)
            lineTo(size.width - s, size.height)
            lineTo(0f, size.height)
            close()
        }
        return Outline.Generic(path)
    }
}

private fun typography(
    display: FontFamily,
    body: FontFamily,
    numeral: FontFamily,
    displayWeight: FontWeight,
    titleWeight: FontWeight,
    displayStyle: FontStyle = FontStyle.Normal,
    tracking: Float = 0f,
) = FocusTypography(
    display = TextStyle(fontFamily = display, fontWeight = displayWeight, fontStyle = displayStyle, fontSize = 36.sp, lineHeight = 42.sp, letterSpacing = tracking.sp),
    headline = TextStyle(fontFamily = display, fontWeight = titleWeight, fontStyle = displayStyle, fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = tracking.sp),
    title = TextStyle(fontFamily = display, fontWeight = titleWeight, fontStyle = displayStyle, fontSize = 20.sp, lineHeight = 26.sp, letterSpacing = tracking.sp),
    body = TextStyle(fontFamily = body, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    label = TextStyle(fontFamily = body, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = 0.4.sp),
    numeral = TextStyle(fontFamily = numeral, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp),
    numeralLarge = TextStyle(fontFamily = numeral, fontWeight = FontWeight.Bold, fontSize = 48.sp, lineHeight = 56.sp),
)

object HeroTokensRegistry {

    // VECTOR, "the Engineer": holographic HUD. Chamfered glass, thin cyan lines, monospace numerals, crisp motion.
    private val vector = HeroTokens(
        hero = HeroTheme.VECTOR,
        colors = FocusColors(
            background = Color(0xFF05090F), surface = Color(0xFF0B1620), surfaceHigh = Color(0xFF12263A),
            primary = Color(0xFF4DE8E0), onPrimary = Color(0xFF02151A),
            secondary = Color(0xFF7AB8FF), tertiary = Color(0xFF3DDC97),
            text = Color(0xFFE6F7F8), textMuted = Color(0xFF8FB3B8),
            outline = Color(0xFF2A8F98), danger = Color(0xFFFF6B6B), success = Color(0xFF3DDC97),
        ),
        typography = typography(
            display = FontFamily.SansSerif, body = FontFamily.SansSerif, numeral = FontFamily.Monospace,
            displayWeight = FontWeight.Light, titleWeight = FontWeight.Medium, tracking = 1.2f,
        ),
        spacing = FocusSpacing(),
        shapes = FocusShapes(
            small = CutCornerShape(6.dp), medium = CutCornerShape(12.dp), large = CutCornerShape(20.dp),
            button = CutCornerShape(topStart = 12.dp, bottomEnd = 12.dp), card = CutCornerShape(topStart = 16.dp, bottomEnd = 16.dp),
        ),
        elevation = FocusElevation(0.dp, 0.dp, 0.dp),
        motion = FocusMotion(
            fastMs = 140, mediumMs = 280, slowMs = 520, ambientMs = 6_000,
            easing = CubicBezierEasing(0.2f, 0f, 0f, 1f), springDamping = 0.9f, springStiffness = 700f,
        ),
        strokeWidth = 1.dp,
    )

    // NOCTIS, "the Night Sentinel": moonlit indigo, silver, serif display, slow soft motion.
    private val noctis = HeroTokens(
        hero = HeroTheme.NOCTIS,
        colors = FocusColors(
            background = Color(0xFF0D0F1F), surface = Color(0xFF161A33), surfaceHigh = Color(0xFF222850),
            primary = Color(0xFFD6DCF0), onPrimary = Color(0xFF0D0F1F),
            secondary = Color(0xFF9AA5E8), tertiary = Color(0xFFF4E9B8),
            text = Color(0xFFF2F4FF), textMuted = Color(0xFFA9B0CF),
            outline = Color(0xFF8F97C0), danger = Color(0xFFFF8A9A), success = Color(0xFF8FE3B5),
        ),
        typography = typography(
            display = FontFamily.Serif, body = FontFamily.SansSerif, numeral = FontFamily.Serif,
            displayWeight = FontWeight.Normal, titleWeight = FontWeight.Medium, tracking = 0.3f,
        ),
        spacing = FocusSpacing(),
        shapes = FocusShapes(
            small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(20.dp), large = RoundedCornerShape(28.dp),
            button = RoundedCornerShape(50), card = RoundedCornerShape(24.dp),
        ),
        elevation = FocusElevation(2.dp, 8.dp, 16.dp),
        motion = FocusMotion(
            fastMs = 220, mediumMs = 420, slowMs = 800, ambientMs = 24_000,
            easing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f), springDamping = 1f, springStiffness = 180f,
        ),
        strokeWidth = 1.dp,
    )

    // DASH, "the Courier": comic pop. Slanted panels, thick outlines, punchy bouncy springs.
    private val dash = HeroTokens(
        hero = HeroTheme.DASH,
        colors = FocusColors(
            background = Color(0xFF101014), surface = Color(0xFF1C1C24), surfaceHigh = Color(0xFF2A2A36),
            primary = Color(0xFFFF5A5A), onPrimary = Color(0xFF101014),
            secondary = Color(0xFFFFD92E), tertiary = Color(0xFF4D9BFF),
            text = Color(0xFFFFFFFF), textMuted = Color(0xFFC9C9D6),
            outline = Color(0xFFF5F0E6), danger = Color(0xFFFF6B6B), success = Color(0xFF4CD97B),
        ),
        typography = typography(
            display = FontFamily.SansSerif, body = FontFamily.SansSerif, numeral = FontFamily.SansSerif,
            displayWeight = FontWeight.Black, titleWeight = FontWeight.ExtraBold,
            displayStyle = FontStyle.Italic, tracking = 0f,
        ),
        spacing = FocusSpacing(),
        shapes = FocusShapes(
            small = RoundedCornerShape(4.dp), medium = SlantedShape(10.dp), large = SlantedShape(16.dp),
            button = SlantedShape(10.dp), card = SlantedShape(14.dp),
        ),
        elevation = FocusElevation(3.dp, 5.dp, 8.dp),
        motion = FocusMotion(
            fastMs = 100, mediumMs = 220, slowMs = 400, ambientMs = 3_000,
            easing = CubicBezierEasing(0.3f, 1.4f, 0.5f, 1f), springDamping = 0.45f, springStiffness = 380f,
        ),
        strokeWidth = 3.dp,
    )

    fun forHero(hero: HeroTheme): HeroTokens = when (hero) {
        HeroTheme.VECTOR -> vector
        HeroTheme.NOCTIS -> noctis
        HeroTheme.DASH -> dash
    }
}

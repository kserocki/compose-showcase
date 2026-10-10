package pl.kserocki.gravityjar.ui.designsystem

import androidx.annotation.DrawableRes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import pl.kserocki.gravityjar.R

/**
 * A base color plus the alpha variants used across the jar UI. Semantic tokens that need multiple
 * opacities (e.g. [AppColors.OverMedia.foreground]) are typed as this; read the variant you need
 * (`.a76`, `.a26`, …) or `.base` for the raw opaque color.
 */
@Immutable
class ColorWithAlpha(baseColor: Color) {
    val base = baseColor
    val a100 = baseColor.copy(alpha = 1.0f)
    val a76 = baseColor.copy(alpha = 0.76f)
    val a52 = baseColor.copy(alpha = 0.52f)
    val a26 = baseColor.copy(alpha = 0.26f)

    operator fun invoke(): Color = base
}

/**
 * Semantic color tokens for the jar UI. A trimmed, self-contained subset of a larger dark design
 * system — only the tokens the community-coupon jar actually reads are modelled here.
 */
@Immutable
data class AppColors(
    val fg: Fg,
    val border: Border,
    val container: Container,
    val bgInt: BgInt,
    val fgInt: FgInt,
    val overMedia: OverMedia,
    val decorative: Decorative,
    val background: Color,
) {
    @Immutable data class Fg(val neutral: Neutral, val brand: Brand) {
        @Immutable data class Neutral(val primary: Color, val secondary: Color)
        @Immutable data class Brand(val primary: Color)
    }

    @Immutable data class Border(val neutral: Neutral) {
        @Immutable data class Neutral(val primary: Color)
    }

    @Immutable data class Container(val neutral: Neutral) {
        @Immutable data class Neutral(val primary: Color)
    }

    @Immutable data class BgInt(val light: Light) {
        @Immutable data class Light(val primary: Color)
    }

    @Immutable data class FgInt(val light: Light) {
        @Immutable data class Light(val primary: Color)
    }

    @Immutable data class OverMedia(val foreground: ColorWithAlpha)

    // Fixed decorative hues used by canvas-drawn flourishes (the tip-jar flames). Theme-invariant.
    @Immutable data class Decorative(val gold: Color, val mandarine: Color)
}

private val White = Color(0xFFF7F7F8)
private val Black = Color(0xFF080808)
private val Neutral2 = Color(0xFF141415)
private val Neutral3 = Color(0xFF232324)
private val Violet = Color(0xFF9840FF)
private val Gold = Color(0xFFF7D843)
private val Mandarine = Color(0xFFFF7B38)

val DarkAppColors = AppColors(
    fg = AppColors.Fg(
        neutral = AppColors.Fg.Neutral(primary = White, secondary = White.copy(alpha = 0.76f)),
        brand = AppColors.Fg.Brand(primary = Violet),
    ),
    border = AppColors.Border(neutral = AppColors.Border.Neutral(primary = Neutral3)),
    container = AppColors.Container(neutral = AppColors.Container.Neutral(primary = Neutral3)),
    bgInt = AppColors.BgInt(light = AppColors.BgInt.Light(primary = White)),
    fgInt = AppColors.FgInt(light = AppColors.FgInt.Light(primary = Black)),
    overMedia = AppColors.OverMedia(foreground = ColorWithAlpha(White)),
    decorative = AppColors.Decorative(gold = Gold, mandarine = Mandarine),
    background = Neutral2,
)

val LocalAppColors = staticCompositionLocalOf { DarkAppColors }

/** System sans font in the weights the jar UI needs (500/700, plus synthesized 650 for the mini jar). */
val appFontFamily = FontFamily.Default

@Immutable
data class AppTypography(
    val title3: TextStyle,
    val subheadline: TextStyle,
    val footnote: TextStyle,
    val footnoteBold: TextStyle,
)

fun createTypography(contentColor: Color): AppTypography {
    val default = TextStyle(fontFamily = appFontFamily, color = contentColor)
    return AppTypography(
        title3 = default.copy(fontSize = 20.sp, fontWeight = FontWeight.W700, lineHeight = 24.sp),
        subheadline = default.copy(fontSize = 15.sp, fontWeight = FontWeight.W700, lineHeight = 20.sp),
        footnote = default.copy(fontSize = 13.sp, fontWeight = FontWeight.W500, lineHeight = 20.sp),
        footnoteBold = default.copy(fontSize = 13.sp, fontWeight = FontWeight.W700, lineHeight = 20.sp),
    )
}

val LocalAppTypography = staticCompositionLocalOf { createTypography(White) }

@Immutable
data class AppAssets(@param:DrawableRes val avatarPlaceholder: Int)

val DarkAssets = AppAssets(avatarPlaceholder = R.drawable.avatar_placeholder_dark)

val LocalAppAssets = staticCompositionLocalOf { DarkAssets }

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalAppColors provides DarkAppColors,
        LocalAppTypography provides createTypography(DarkAppColors.fg.neutral.primary),
        LocalAppAssets provides DarkAssets,
    ) {
        MaterialTheme(colorScheme = darkColorScheme(), content = content)
    }
}

object AppTheme {
    val colors: AppColors
        @Composable @ReadOnlyComposable get() = LocalAppColors.current

    val typography: AppTypography
        @Composable @ReadOnlyComposable get() = LocalAppTypography.current

    val assets: AppAssets
        @Composable @ReadOnlyComposable get() = LocalAppAssets.current
}

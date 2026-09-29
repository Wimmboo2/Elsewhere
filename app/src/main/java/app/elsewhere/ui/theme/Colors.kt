package app.elsewhere.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** The prototype's `[data-theme]` token blocks. Names follow the Spec's color table. */
@Immutable
class ElsewhereColors(
    val isDark: Boolean,
    // The 20 Spec tokens
    val bg: Color,
    val surface: Color,
    val surfaceHigh: Color,
    val ink: Color,
    val inkMuted: Color,
    val line: Color,
    val accent: Color,
    val accentStrong: Color,
    val onAccent: Color,
    val accentTint: Color,
    val onAccentTint: Color,
    val sage: Color,
    val sageStrong: Color,
    val sageTint: Color,
    val onSageTint: Color,
    val activeGround: Color,
    val peach: Color,
    val blocked: Color,
    val scrim: Color,
    val skeleton: Color,
    // Prototype-only extras
    val press: Color,
    val onSage: Color,
    val sageBtn: Color,
    val onSageBtn: Color,
    val onPeach: Color,
    val onBlocked: Color,
    val skeleton2: Color,
    val sheet: Color,
    /** 1dp top edge of the sheet (dark only). */
    val edge: Color,
    /** `--e-shadow`: 0 -8px 28px rgba(46,43,37,.16) in light, none in dark. */
    val sheetShadow: Color,
    /** `--e-map-filter`, applied in order. */
    val mapFilter: MapFilter,
)

/** sepia(s) saturate(t) contrast(c) brightness(b), applied in the order listed in CSS. */
@Immutable
data class MapFilter(val steps: List<Pair<Kind, Float>>) {
    enum class Kind { Sepia, Saturate, Contrast, Brightness }
}

val LightColors = ElsewhereColors(
    isDark = false,
    bg = Color(0xFFF5EAD8),
    surface = Color(0xFFEBDDC5),
    surfaceHigh = Color(0xFFF9F4ED),
    ink = Color(0xFF201E1D),
    inkMuted = Color(0xFF645C50),
    line = Color(32, 30, 29).copy(alpha = .12f),
    accent = Color(0xFFC67139),
    accentStrong = Color(0xFFB2622D),
    onAccent = Color(0xFFFFF8F0),
    accentTint = Color(0xFFFFE1D0),
    onAccentTint = Color(0xFF643312),
    sage = Color(0xFF7A8A5E),
    sageStrong = Color(0xFF56633F),
    sageTint = Color(0xFFE1EECC),
    onSageTint = Color(0xFF3D472B),
    activeGround = Color(0xFFE2E8C6),
    peach = Color(0xFFFFC6A5),
    blocked = Color(0xFFC0B6A5),
    scrim = Color(46, 43, 37).copy(alpha = .42f),
    skeleton = Color(0xFFE3D4B9),
    press = Color(32, 30, 29).copy(alpha = .06f),
    onSage = Color(0xFFF9F4ED),
    sageBtn = Color(0xFF56633F),
    onSageBtn = Color(0xFFF9F4ED),
    onPeach = Color(0xFF643312),
    onBlocked = Color(0xFFF9F4ED),
    skeleton2 = Color(0xFFEFE3CF),
    sheet = Color(0xFFF9F4ED),
    edge = Color.Transparent,
    sheetShadow = Color(46, 43, 37).copy(alpha = .16f),
    mapFilter = MapFilter(
        listOf(
            MapFilter.Kind.Sepia to .38f, MapFilter.Kind.Saturate to .95f,
            MapFilter.Kind.Contrast to .94f, MapFilter.Kind.Brightness to 1.02f,
        ),
    ),
)

val DarkColors = ElsewhereColors(
    isDark = true,
    bg = Color(0xFF1B1916),
    surface = Color(0xFF27231F),
    surfaceHigh = Color(0xFF322C26),
    ink = Color(0xFFF3E8D6),
    inkMuted = Color(0xFFB8AC99),
    line = Color(243, 232, 214).copy(alpha = .12f),
    accent = Color(0xFFE08650),
    accentStrong = Color(0xFFE08650),
    onAccent = Color(0xFF1B1916),
    accentTint = Color(0xFF3B2517),
    onAccentTint = Color(0xFFFFC6A5),
    sage = Color(0xFFAEBF92),
    sageStrong = Color(0xFFCCDBB2),
    sageTint = Color(0xFF283020),
    onSageTint = Color(0xFFCCDBB2),
    activeGround = Color(0xFF1C2517),
    peach = Color(0xFF8C491A),
    blocked = Color(0xFF3A342D),
    scrim = Color(8, 7, 6).copy(alpha = .6f),
    skeleton = Color(0xFF2C2722),
    press = Color(243, 232, 214).copy(alpha = .07f),
    onSage = Color(0xFF1B1916),
    sageBtn = Color(0xFFCCDBB2),
    onSageBtn = Color(0xFF1B1916),
    onPeach = Color(0xFFFFE1D0),
    onBlocked = Color(0xFFB8AC99),
    skeleton2 = Color(0xFF37312A),
    sheet = Color(0xFF27231F),
    edge = Color(243, 232, 214).copy(alpha = .1f),
    sheetShadow = Color.Transparent,
    mapFilter = MapFilter(
        listOf(
            MapFilter.Kind.Sepia to .4f, MapFilter.Kind.Saturate to .8f,
            MapFilter.Kind.Brightness to 1.15f, MapFilter.Kind.Contrast to .95f,
        ),
    ),
)

val LocalElsewhereColors = staticCompositionLocalOf { LightColors }

/** Material roles from the Spec's "Compose role" column. Material components are not used for visuals. */
fun ElsewhereColors.toColorScheme(): ColorScheme {
    val base = if (isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        background = bg, onBackground = ink,
        surface = bg, onSurface = ink,
        surfaceContainer = surface, surfaceContainerHigh = surfaceHigh,
        onSurfaceVariant = inkMuted, outlineVariant = line,
        primary = accentStrong, onPrimary = onAccent,
        primaryContainer = accentTint, onPrimaryContainer = onAccentTint,
        tertiary = sageStrong, onTertiary = onSage,
        tertiaryContainer = sageTint, onTertiaryContainer = onSageTint,
        surfaceDim = blocked, scrim = scrim,
    )
}

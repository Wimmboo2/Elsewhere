package app.elsewhere.ui.theme

import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.elsewhere.R

val Caprasimo = FontFamily(Font(R.font.caprasimo_regular, FontWeight.Normal))
val Figtree = FontFamily(
    Font(R.font.figtree_regular, FontWeight.Normal),
    Font(R.font.figtree_semibold, FontWeight.SemiBold),
    Font(R.font.figtree_bold, FontWeight.Bold),
)

/** CSS line boxes: half-leading on both sides, no font padding. */
private val CssLine = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)
private val NoPadding = PlatformTextStyle(includeFontPadding = false)

private fun style(
    family: FontFamily,
    weight: FontWeight,
    size: Float,
    lineHeight: Float?,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    tabular: Boolean = false,
) = TextStyle(
    fontFamily = family,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight?.sp ?: TextUnit.Unspecified,
    letterSpacing = letterSpacing,
    fontFeatureSettings = if (tabular) "tnum" else null,
    lineHeightStyle = CssLine,
    platformStyle = NoPadding,
)

/** Every text style in the prototype markup, as `font: weight size/lineHeight`. */
object Type {
    // Caprasimo
    val Wordmark = style(Caprasimo, FontWeight.Normal, 24f, 24f)                      // 24px/1
    val Display = style(Caprasimo, FontWeight.Normal, 34f, 44f, (-0.01f).em)          // home city title
    val ObTitle = style(Caprasimo, FontWeight.Normal, 34f, 40f)                       // onboarding title
    val Action = style(Caprasimo, FontWeight.Normal, 24f, 30f)                        // Start / Stop
    val Title = style(Caprasimo, FontWeight.Normal, 22f, 28f)                         // app bar titles
    val Heading = style(Caprasimo, FontWeight.Normal, 20f, 26f)                       // empty states
    val Button = style(Caprasimo, FontWeight.Normal, 18f, null)                       // onboarding primary

    // Figtree
    val BodyLarge = style(Figtree, FontWeight.Normal, 16f, 24f)                       // onboarding body
    val Label = style(Figtree, FontWeight.SemiBold, 16f, 22f)                         // row names
    val Input = style(Figtree, FontWeight.Normal, 16f, 16f)                           // search input 16px/1
    val AboutLabel = style(Figtree, FontWeight.SemiBold, 15f, 22f)
    val CardTitle = style(Figtree, FontWeight.Bold, 15f, 20f)
    val Skip = style(Figtree, FontWeight.SemiBold, 15f, 15f)                          // 15px/1
    val Pill = style(Figtree, FontWeight.SemiBold, 14f, 14f)                          // buttons and chips 14px/1
    val Body = style(Figtree, FontWeight.Normal, 14f, 20f)                            // empty-state guidance
    val Sub = style(Figtree, FontWeight.Normal, 14f, 20f, tabular = true)             // Start sub-line
    val Hint = style(Figtree, FontWeight.Normal, 14f, 18.2f)                          // 14px/1.3
    val Kicker = style(Figtree, FontWeight.SemiBold, 13f, 18f)
    val Supporting = style(Figtree, FontWeight.Normal, 13f, 18f)
    val Coords = style(Figtree, FontWeight.Normal, 13f, 18f, tabular = true)
    val AboutBody = style(Figtree, FontWeight.Normal, 13f, 19f)
    val AboutValue = style(Figtree, FontWeight.Normal, 13f, 19f, tabular = true)
    val Count = style(Figtree, FontWeight.Normal, 13f, 13f)                           // 13px/1
    val StripLabel = style(Figtree, FontWeight.SemiBold, 13f, 13f)                    // 13px/1
    val Crumb = style(Figtree, FontWeight.SemiBold, 13f, 13f)                         // onboarding chips 13px/1
    val Tag = style(Figtree, FontWeight.Bold, 12f, 12f)                               // Live, mock tag
    val Chip12 = style(Figtree, FontWeight.SemiBold, 12f, 12f)                        // map offline chip
    val TagSmall = style(Figtree, FontWeight.Bold, 11f, 11f)                          // Current
    val Attribution = style(Figtree, FontWeight.SemiBold, 10f, 10f)
}

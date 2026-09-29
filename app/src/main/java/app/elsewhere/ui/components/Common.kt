package app.elsewhere.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.elsewhere.R
import app.elsewhere.ui.icons.Icon
import app.elsewhere.ui.icons.Icons
import app.elsewhere.ui.shape.BlobKind
import app.elsewhere.ui.shape.Blobs
import app.elsewhere.ui.theme.LocalElsewhereColors
import app.elsewhere.ui.theme.Type

val Pill = RoundedCornerShape(999.dp)

/** 64dp sub-screen app bar: 48dp back, then [content] (title, optional flag). */
@Composable
fun SubAppBar(onBack: () -> Unit, content: @Composable RowScope.() -> Unit) {
    val c = LocalElsewhereColors.current
    Row(
        Modifier.fillMaxWidth().height(64.dp).padding(start = 4.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            Modifier.size(48.dp).pressable(onBack, pressedFill = c.press, label = stringResource(R.string.back)),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.ArrowLeft, 24.dp, c.ink) }
        content()
    }
}

/** 52dp search pill: icon, 16sp input, 44dp clear button. Never autofocused. */
@Composable
fun SearchField(value: String, onValueChange: (String) -> Unit, placeholder: String, label: String) {
    val c = LocalElsewhereColors.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    Row(
        Modifier.fillMaxWidth().height(52.dp).background(c.surface, Pill).padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Search, 20.dp, c.inkMuted)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = Type.Input.copy(color = c.ink),
            cursorBrush = SolidColor(c.accent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { keyboard?.hide(); focus.clearFocus() }),
            modifier = Modifier.weight(1f).height(48.dp).semantics { contentDescription = label },
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) BasicText(placeholder, style = Type.Input.copy(color = c.inkMuted), maxLines = 1, overflow = TextOverflow.Clip)
                    inner()
                }
            },
        )
        if (value.isNotEmpty()) {
            Box(
                Modifier.size(44.dp).pressable({ onValueChange("") }, pressedFill = c.press, label = stringResource(R.string.clear_search)),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.X, 18.dp, c.ink) }
        }
    }
}

/** 96dp soft blob (sun shape) with an icon, Heading, one line of guidance and one action. */
@Composable
fun EmptyState(
    title: String,
    body: String,
    action: String,
    onAction: () -> Unit,
    blobColor: Color,
    icon: @Composable () -> Unit,
    bodyMaxWidth: Dp,
    topPadding: Dp,
    filledAction: Boolean,
    iconOffset: Dp,
    modifier: Modifier = Modifier,
) {
    val c = LocalElsewhereColors.current
    val blob = remember { Path() }
    Column(
        modifier.fillMaxWidth().padding(start = 32.dp, end = 32.dp, top = topPadding, bottom = if (filledAction) 8.dp else 0.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(96.dp).drawBehind {
                val sun = Blobs.points(BlobKind.Sun)
                Blobs.buildPath(blob, sun, sun, 1f, 1.25f, size.width)
                drawPath(blob, blobColor)
            },
        ) { Box(Modifier.offset(iconOffset, iconOffset)) { icon() } }
        Spacer(Modifier.height(12.dp + 6.dp))
        BasicText(title, style = Type.Heading.copy(color = c.ink, textAlign = TextAlign.Center))
        Spacer(Modifier.height(6.dp))
        BasicText(body, style = Type.Body.copy(color = c.inkMuted, textAlign = TextAlign.Center), modifier = Modifier.widthIn(max = bodyMaxWidth))
        Spacer(Modifier.height(6.dp + 10.dp))
        if (filledAction) {
            Box(
                Modifier.height(48.dp).pressable(onAction, fill = c.accentStrong, pressedScale = 0.96f).padding(horizontal = 20.dp),
                contentAlignment = Alignment.Center,
            ) { BasicText(action, style = Type.Pill.copy(color = c.onAccent)) }
        } else {
            Box(
                Modifier.height(48.dp).border(1.5.dp, c.line, Pill).pressable(onAction, pressedFill = c.press).padding(horizontal = 20.dp),
                contentAlignment = Alignment.Center,
            ) { BasicText(action, style = Type.Pill.copy(color = c.ink)) }
        }
    }
}

/** Search-x icon used by the no-results states. */
@Composable
fun NoResultsIcon() {
    val c = LocalElsewhereColors.current
    Icon(Icons.SearchX, 36.dp, c.inkMuted)
}

/** Stroked icon helper for empty states. */
@Composable
fun StrokeIcon(icon: ImageVector, size: Dp, color: Color) = Icon(icon, size, color)

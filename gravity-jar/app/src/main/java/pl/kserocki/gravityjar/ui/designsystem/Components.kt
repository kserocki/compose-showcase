package pl.kserocki.gravityjar.ui.designsystem

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class ButtonSize(val height: Dp) {
    SIZE_40(40.dp),
    SIZE_48(48.dp),
}

/** Filled brand button. Shows a spinner instead of the label while [isProcessing]. */
@Composable
fun ButtonPrimary(
    title: String,
    size: ButtonSize,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isProcessing: Boolean = false,
    onClick: () -> Unit,
) {
    val colors = AppTheme.colors
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(size.height)
            .clip(RoundedCornerShape(100.dp))
            .background(colors.fg.brand.primary)
            .graphicsLayer { alpha = if (enabled) 1f else 0.4f }
            .clickable(enabled = enabled && !isProcessing, onClick = onClick)
            .padding(horizontal = Dimens.padding20),
    ) {
        if (isProcessing) {
            CircularProgressIndicator(
                strokeWidth = 2.dp,
                color = colors.fg.neutral.primary,
                modifier = Modifier.size(20.dp),
            )
        } else {
            Text(title, style = AppTheme.typography.subheadline, color = colors.fg.neutral.primary)
        }
    }
}

/** Neutral filled button with an optional leading icon. */
@Composable
fun ButtonSecondary(
    title: String,
    size: ButtonSize,
    modifier: Modifier = Modifier,
    @DrawableRes leadingIcon: Int? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val colors = AppTheme.colors
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .height(size.height)
            .clip(RoundedCornerShape(100.dp))
            .background(colors.container.neutral.primary)
            .graphicsLayer { alpha = if (enabled) 1f else 0.4f }
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = Dimens.padding16),
    ) {
        if (leadingIcon != null) {
            Icon(
                painter = painterResource(leadingIcon),
                contentDescription = null,
                tint = colors.fg.neutral.primary,
                modifier = Modifier
                    .padding(end = Dimens.padding6)
                    .size(18.dp),
            )
        }
        Text(title, style = AppTheme.typography.subheadline, color = colors.fg.neutral.primary)
    }
}

/** Low-emphasis outlined button. */
@Composable
fun ButtonGhost(
    title: String,
    size: ButtonSize,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val colors = AppTheme.colors
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(size.height)
            .clip(RoundedCornerShape(100.dp))
            .border(Dimens.strokeM, colors.border.neutral.primary, RoundedCornerShape(100.dp))
            .graphicsLayer { alpha = if (enabled) 1f else 0.4f }
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = Dimens.padding20),
    ) {
        Text(title, style = AppTheme.typography.subheadline, color = colors.fg.neutral.secondary)
    }
}

/** Circular neutral icon button. */
@Composable
fun IconButtonSecondary(
    onClick: () -> Unit,
    @DrawableRes iconDrawableId: Int,
    size: ButtonSize,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size.height)
            .clip(CircleShape)
            .background(colors.container.neutral.primary)
            .clickable(onClick = onClick),
    ) {
        Icon(
            painter = painterResource(iconDrawableId),
            contentDescription = null,
            tint = colors.fg.neutral.primary,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
fun AppAlertDialog(
    title: String,
    description: String,
    confirm: String,
    dismiss: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    shape: Shape = RoundedCornerShape(Dimens.corner14),
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = shape,
        title = { Text(title, style = AppTheme.typography.title3) },
        text = { Text(description, style = AppTheme.typography.footnote) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(dismiss) } },
    )
}

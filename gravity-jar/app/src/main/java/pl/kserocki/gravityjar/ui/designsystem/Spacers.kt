package pl.kserocki.gravityjar.ui.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp

@Composable
fun HeightSpacer(height: Dp) {
    Spacer(Modifier.height(height))
}

/** Wraps @Preview content in [AppTheme] on a dark background. */
@Composable
fun PreviewWrapper(content: @Composable () -> Unit) {
    AppTheme {
        Box(Modifier.background(AppTheme.colors.background)) { content() }
    }
}

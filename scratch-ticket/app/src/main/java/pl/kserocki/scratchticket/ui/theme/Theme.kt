package pl.kserocki.scratchticket.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object TicketColors {
    val Paper = Color(0xFFFF8A65)
    val PaperShade = Color(0xFFFFB199)
    val Ink = Color(0xFF1C0E0A)
}

object AppColors {
    val Background = Color(0xFF0B0B0F)
    val Surface = Color(0xFF1A1A20)
    val TextPrimary = Color(0xFFF5F5F7)
    val TextSecondary = Color(0xFF9A9AA4)
    val Success = Color(0xFF7FE3A8)
    val Error = Color(0xFFFF6B6B)
    val ButtonDisabled = Color(0xFF3A3A42)
}

@Composable
fun ScratchTicketTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = AppColors.Background,
            surface = AppColors.Surface,
            primary = TicketColors.Paper,
            onPrimary = TicketColors.Ink,
        ),
        content = content,
    )
}

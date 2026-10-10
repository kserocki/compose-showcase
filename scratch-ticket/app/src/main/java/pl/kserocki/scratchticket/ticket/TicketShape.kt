package pl.kserocki.scratchticket.ticket

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/**
 * A ticket outline: rounded rectangle with scalloped short edges and two half-moon notches where
 * the tear-off stub meets the body.
 *
 * @param stubWidth distance from the start edge to the perforation line.
 * @param scallopRadius radius of each bite taken out of the left and right edges.
 * @param scallopGap spacing between neighbouring scallops.
 */
class TicketShape(
    private val stubWidth: Dp,
    private val cornerRadius: Dp = 14.dp,
    private val scallopRadius: Dp = 5.dp,
    private val scallopGap: Dp = 7.dp,
    private val notchRadius: Dp = 9.dp,
) : Shape {

    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        with(density) {
            val corner = cornerRadius.toPx()
            val scallop = scallopRadius.toPx()
            val gap = scallopGap.toPx()
            val notch = notchRadius.toPx()
            val stubX = stubWidth.toPx()

            val body = Path().apply {
                addRoundRect(RoundRect(Rect(Offset.Zero, size), CornerRadius(corner)))
            }

            val bites = Path().apply {
                val usable = size.height - corner * 2
                val pitch = scallop * 2 + gap
                val count = (usable / pitch).toInt().coerceAtLeast(1)
                val start = corner + (usable - count * pitch + gap) / 2 + scallop
                repeat(count) { i ->
                    val y = start + i * pitch
                    addOval(Rect(Offset(0f, y), scallop))
                    addOval(Rect(Offset(size.width, y), scallop))
                }
                addOval(Rect(Offset(stubX, 0f), notch))
                addOval(Rect(Offset(stubX, size.height), notch))
            }

            Outline.Generic(Path.combine(PathOperation.Difference, body, bites))
        }
}

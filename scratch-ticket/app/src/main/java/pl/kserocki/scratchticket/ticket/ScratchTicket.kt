package pl.kserocki.scratchticket.ticket

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pl.kserocki.scratchticket.motion.DeviceTilt
import pl.kserocki.scratchticket.ui.theme.TicketColors

sealed interface TicketState {
    data object Locked : TicketState
    data class Unlocked(val amount: String) : TicketState
    data class Revealed(val amount: String) : TicketState
}

private val StubWidth = 58.dp

@Composable
fun ScratchTicket(
    state: TicketState,
    tilt: () -> DeviceTilt,
    onRevealed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = TicketShape(stubWidth = StubWidth)
    val amount = when (state) {
        TicketState.Locked -> null
        is TicketState.Unlocked -> state.amount
        is TicketState.Revealed -> state.amount
    }
    val revealed = state is TicketState.Revealed

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(156.dp)
            .graphicsLayer {
                val current = tilt()
                rotationY = current.roll * 9f
                rotationX = -current.pitch * 7f
                cameraDistance = 14f * density
                this.shape = shape
                clip = true
            }
            .background(TicketColors.Paper)
            .drawWithContent {
                drawContent()
                val sweep = size.width * (0.5f + tilt().roll * 0.8f)
                drawRect(
                    brush = Brush.linearGradient(
                        0f to Color.Transparent,
                        0.5f to Color.White.copy(alpha = 0.22f),
                        1f to Color.Transparent,
                        start = Offset(sweep - size.width * 0.35f, 0f),
                        end = Offset(sweep + size.width * 0.35f, size.height),
                    ),
                )
            },
    ) {
        Row(Modifier.fillMaxHeight()) {
            Box(
                modifier = Modifier
                    .width(StubWidth)
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "ORBIT",
                    color = TicketColors.Ink,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp,
                    modifier = Modifier.vertical(),
                    softWrap = false,
                )
            }
            Perforation(Modifier.fillMaxHeight().width(2.dp))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 16.dp, end = 18.dp, top = 14.dp, bottom = 12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Store credit",
                        color = TicketColors.Ink,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.weight(1f))
                    AnimatedVisibility(
                        visible = revealed,
                        enter = fadeIn() + scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy)),
                    ) {
                        AddedBadge()
                    }
                }
                Spacer(Modifier.height(8.dp))
                ScratchPanel(
                    enabled = state is TicketState.Unlocked,
                    revealed = revealed,
                    tilt = tilt,
                    onRevealed = onRevealed,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(TicketColors.PaperShade, RoundedCornerShape(10.dp)),
                ) {
                    if (amount != null) RevealedAmount(amount, revealed)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = if (amount == null) "Enter your code to unlock" else "to spend on your first order",
                    color = TicketColors.Ink,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun RevealedAmount(amount: String, revealed: Boolean) {
    val scale by animateFloatAsState(
        targetValue = if (revealed) 1.12f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "amountScale",
    )
    Text(
        text = amount,
        color = TicketColors.Ink,
        fontSize = 34.sp,
        fontWeight = FontWeight.Black,
        modifier = Modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        },
    )
}

@Composable
private fun AddedBadge() {
    Row(
        modifier = Modifier
            .background(TicketColors.Ink, RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Canvas(Modifier.size(10.dp)) {
            drawPath(
                path = Path().apply {
                    moveTo(size.width * 0.12f, size.height * 0.55f)
                    lineTo(size.width * 0.4f, size.height * 0.8f)
                    lineTo(size.width * 0.9f, size.height * 0.22f)
                },
                color = TicketColors.Paper,
                style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
        Text(text = "Added", color = TicketColors.Paper, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun Perforation(modifier: Modifier) {
    Canvas(modifier) {
        drawLine(
            color = TicketColors.Ink.copy(alpha = 0.8f),
            start = Offset(size.width / 2, 12.dp.toPx()),
            end = Offset(size.width / 2, size.height - 12.dp.toPx()),
            strokeWidth = size.width,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(7.dp.toPx(), 5.dp.toPx())),
        )
    }
}

/** Lays the content out rotated by -90°, reporting the swapped size to the parent. */
private fun Modifier.vertical(): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(
        Constraints(maxWidth = constraints.maxHeight, maxHeight = constraints.maxWidth),
    )
    layout(placeable.height, placeable.width) {
        placeable.placeWithLayer(
            x = -(placeable.width - placeable.height) / 2,
            y = (placeable.width - placeable.height) / 2,
        ) { rotationZ = -90f }
    }
}

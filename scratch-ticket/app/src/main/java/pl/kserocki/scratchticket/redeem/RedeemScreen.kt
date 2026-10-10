package pl.kserocki.scratchticket.redeem

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import pl.kserocki.scratchticket.motion.rememberDeviceTilt
import pl.kserocki.scratchticket.ticket.ScratchTicket
import pl.kserocki.scratchticket.ticket.TicketState
import pl.kserocki.scratchticket.ui.theme.AppColors
import pl.kserocki.scratchticket.ui.theme.TicketColors

@Composable
fun RedeemScreen(viewModel: RedeemViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val tilt = rememberDeviceTilt()
    val focusManager = LocalFocusManager.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.Background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(40.dp))
        Text(
            text = "Got a promo code?",
            color = AppColors.TextPrimary,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Enter a code to unlock up to \$100 in store credit.",
            color = AppColors.TextSecondary,
            fontSize = 15.sp,
        )
        Spacer(Modifier.height(28.dp))
        ScratchTicket(
            state = state.ticket,
            tilt = { tilt.value },
            onRevealed = viewModel::onRevealed,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        AnimatedVisibility(
            visible = state.ticket is TicketState.Unlocked,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Text(
                text = "Drag across the panel to scratch it off",
                color = AppColors.TextSecondary,
                fontSize = 13.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(24.dp))
        CodeField(
            value = state.code,
            enabled = !state.isUnlocked,
            onValueChange = viewModel::onCodeChanged,
            onDone = {
                focusManager.clearFocus()
                viewModel.onRedeem()
            },
        )
        StatusLine(state)
        Spacer(Modifier.weight(1f))
        PrimaryButton(
            state = state,
            onClick = {
                focusManager.clearFocus()
                if (state.isUnlocked) viewModel.onReset() else viewModel.onRedeem()
            },
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = if (state.isUnlocked) " " else "I don't have a code",
            color = AppColors.TextPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(vertical = 8.dp),
        )
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun CodeField(
    value: String,
    enabled: Boolean,
    onValueChange: (String) -> Unit,
    onDone: () -> Unit,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        singleLine = true,
        textStyle = TextStyle(color = AppColors.TextPrimary, fontSize = 16.sp, letterSpacing = 1.sp),
        cursorBrush = SolidColor(TicketColors.Paper),
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Characters,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        decorationBox = { inner ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AppColors.Surface, RoundedCornerShape(16.dp))
                    .padding(horizontal = 16.dp, vertical = 15.dp),
            ) {
                if (value.isEmpty()) {
                    Text("Promo code", color = AppColors.TextSecondary, fontSize = 16.sp)
                }
                inner()
            }
        },
    )
}

@Composable
private fun StatusLine(state: RedeemViewState) {
    val (text, color) = when {
        state.error != null -> state.error to AppColors.Error
        state.ticket is TicketState.Revealed -> "Credit added to your wallet." to AppColors.Success
        state.isUnlocked -> "Code unlocked." to AppColors.Success
        else -> null to Color.Transparent
    }
    AnimatedVisibility(visible = text != null, enter = fadeIn(), exit = fadeOut()) {
        Text(
            text = text.orEmpty(),
            color = color,
            fontSize = 13.sp,
            modifier = Modifier.padding(start = 4.dp, top = 8.dp),
        )
    }
}

@Composable
private fun PrimaryButton(state: RedeemViewState, onClick: () -> Unit) {
    val enabled = state.canRedeem || state.ticket is TicketState.Revealed
    val background by animateColorAsState(
        targetValue = if (enabled) AppColors.TextPrimary else AppColors.ButtonDisabled,
        label = "buttonBackground",
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .background(background, RoundedCornerShape(50))
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = when {
                state.isLoading -> ButtonContent.Loading
                state.isUnlocked -> ButtonContent.Claim
                else -> ButtonContent.Redeem
            },
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "buttonContent",
        ) { content ->
            when (content) {
                ButtonContent.Loading -> CircularProgressIndicator(
                    color = AppColors.TextPrimary,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(22.dp),
                )
                ButtonContent.Redeem -> ButtonLabel("Redeem", enabled)
                ButtonContent.Claim -> ButtonLabel("Claim & continue", enabled)
            }
        }
    }
}

@Composable
private fun ButtonLabel(text: String, enabled: Boolean) {
    Text(
        text = text,
        color = if (enabled) AppColors.Background else AppColors.TextSecondary,
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
    )
}

private enum class ButtonContent { Loading, Redeem, Claim }

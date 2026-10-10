package pl.kserocki.gravityjar.blesschat

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import pl.kserocki.gravityjar.R
import pl.kserocki.gravityjar.data.BlessChatRemoteConfig
import pl.kserocki.gravityjar.data.PriceFormatter
import pl.kserocki.gravityjar.data.LocalAccountData
import pl.kserocki.gravityjar.ui.designsystem.HeightSpacer
import pl.kserocki.gravityjar.data.PaymentMethod
import pl.kserocki.gravityjar.ui.designsystem.ButtonGhost
import pl.kserocki.gravityjar.ui.designsystem.ButtonPrimary
import pl.kserocki.gravityjar.ui.designsystem.ButtonSecondary
import pl.kserocki.gravityjar.ui.designsystem.ButtonSize
import pl.kserocki.gravityjar.ui.designsystem.IconButtonSecondary
import pl.kserocki.gravityjar.ui.designsystem.AppAlertDialog
import pl.kserocki.gravityjar.ui.designsystem.Dimens
import pl.kserocki.gravityjar.ui.designsystem.AppTheme
import pl.kserocki.gravityjar.data.TipJarDonor
import pl.kserocki.gravityjar.data.TipJarDonationStatus
import kotlin.math.floor
import kotlin.math.roundToInt

enum class BlessChatFooter { SLIDE_TO_TIP, SEND_TIP, COUPON_READY }

@Composable
fun BlessChatModal(
    audio: BlessChatAudio,
    roomId: String,
    paymentMethod: PaymentMethod? = null,
    onDismiss: () -> Unit = {},
    onClickPaymentMethod: () -> Unit = {},
    onConfirmedTotalChange: (Int) -> Unit = {},
    onPendingChange: (Int) -> Unit = {},
    viewModel: BlessChatViewModel = blessChatViewModel(roomId),
) {
    val tipJar by viewModel.tipJar.collectAsStateWithLifecycle()
    val blessChatConfig by viewModel.blessChatConfig.collectAsStateWithLifecycle()
    val goalPrice: Int = tipJar?.targetAmount?.coerceAtLeast(1) ?: BlessChatConfig.GOAL_PRICE
    val currency: String = tipJar?.currency ?: "GBP"
    val stepPrice: Int = blessChatConfig.feeForCurrency(currency)?.stepAmount
        ?: BlessChatRemoteConfig.DEFAULT_STEP_AMOUNT
    val confirmedTotalInitialPrice: Int = tipJar?.currentAmount ?: 0
    val youLabel = stringResource(R.string.common_you)

    val accountData = LocalAccountData.current
    val userAvatar = remember(accountData, youLabel) {
        TipJarDonor(
            id = accountData?.userId ?: "user",
            username = accountData?.username ?: youLabel,
            avatarUrl = accountData?.profilePicture,
        )
    }

    LaunchedEffect(Unit) { viewModel.trackModalViewed() }

    val jarState = rememberBlessChatJarState().apply {
        onDrop = { dropIndex -> audio.playBubble(dropIndex) }
    }
    val currentUserId = accountData?.userId ?: "user"

    // Jar dismiss animation: scale 1→0.7, opacity 1→0 when the jar ID changes.
    var isDismissing by remember { mutableStateOf(false) }
    val dismissAnimSpec = tween<Float>(BlessChatConfig.JAR_DISMISS_ANIM_MS, easing = FastOutSlowInEasing)
    val dismissScale by animateFloatAsState(
        targetValue = if (isDismissing) BlessChatConfig.JAR_DISMISS_SCALE else 1f,
        animationSpec = dismissAnimSpec,
        label = "jarDismissScale",
    )
    val dismissAlpha by animateFloatAsState(
        targetValue = if (isDismissing) 0f else 1f,
        animationSpec = dismissAnimSpec,
        label = "jarDismissAlpha",
    )

    // Auto-dismiss when the backend replaces the jar (goal reached → new active jar).
    // Scale-down + fade-out plays first, then dismiss.
    var trackedJarId by rememberSaveable { mutableStateOf(tipJar?.tipJarId) }
    LaunchedEffect(tipJar?.tipJarId) {
        val newId = tipJar?.tipJarId
        if (trackedJarId != null && newId != null && newId != trackedJarId) {
            isDismissing = true
            delay(BlessChatConfig.JAR_DISMISS_ANIM_MS.toLong())
            onDismiss()
            return@LaunchedEffect
        }
        if (newId != null) trackedJarId = newId
    }

    // rememberSaveable: persists across payment-methods sheet navigation.
    var confirmedTotalPrice by rememberSaveable { mutableIntStateOf(confirmedTotalInitialPrice) }
    // sliderValue / committedSlider are Float in pence for smooth drag physics.
    var sliderValue by rememberSaveable { mutableFloatStateOf(0f) }
    var dragMax by rememberSaveable {
        mutableFloatStateOf(
            (goalPrice - confirmedTotalInitialPrice)
                .coerceAtMost(goalPrice)
                .toFloat()
        )
    }
    var isDragging by remember { mutableStateOf(false) }
    var dragAvatarCount by rememberSaveable { mutableIntStateOf(0) }
    var committedSlider by rememberSaveable { mutableFloatStateOf(0f) }
    var prevNotchIndex by rememberSaveable { mutableIntStateOf(0) }
    var isGoalReached by rememberSaveable { mutableStateOf(false) }
    var isSendingTip by rememberSaveable { mutableStateOf(false) }
    var showConfirmDialog by remember { mutableStateOf(false) }

    val confirmedAvatars by viewModel.expandedAvatars.collectAsStateWithLifecycle()

    var seededAvatarIds by remember { mutableStateOf(emptySet<String>()) }

    /** How many of the current user's subscription avatars to skip (already in jar). */
    var myConfirmedBuffer by remember { mutableIntStateOf(0) }

    // Sync arc with backend when another user donates (never overwrites downward).
    // If the new dragMax is below the current slider position, clamp everything down
    // and remove excess pending avatars so the UI stays consistent.
    LaunchedEffect(tipJar?.currentAmount) {
        val backendAmount = tipJar?.currentAmount ?: return@LaunchedEffect
        if (backendAmount > confirmedTotalPrice) {
            confirmedTotalPrice = backendAmount
            val newMax = (goalPrice - confirmedTotalPrice)
                .coerceAtMost(goalPrice)
                .toFloat()
            dragMax = newMax
            onConfirmedTotalChange(confirmedTotalPrice)

            // Clamp slider & pending avatars when another user's donation shrinks the cap.
            if (sliderValue > newMax) {
                sliderValue = newMax
                committedSlider = committedSlider.coerceAtMost(newMax)
                val newAvatarCount = floor(newMax / stepPrice).toInt()
                val excess = dragAvatarCount - newAvatarCount
                if (excess > 0) jarState.removePending(excess)
                dragAvatarCount = newAvatarCount
                prevNotchIndex = (newMax / stepPrice).roundToInt()
                onPendingChange(newMax.roundToInt())
            }

            if (confirmedTotalPrice >= goalPrice) {
                isGoalReached = true
                jarState.setMaxed(true)
            }
        }
    }

    /** Seed confirmed avatars using avatarId-based diffing (order-independent). */
    LaunchedEffect(confirmedAvatars) {
        val unseeded = confirmedAvatars.filter { it.avatarId !in seededAvatarIds }
        val isInitialSeed = seededAvatarIds.isEmpty()
        for (avatar in unseeded) {
            when (avatar.donor.id) {
                currentUserId if myConfirmedBuffer > 0 -> {
                    myConfirmedBuffer--
                }

                currentUserId if jarState.hasPending() -> {
                    jarState.confirmOnePending()
                }

                else -> {
                    // Use queueDrop so the frame loop staggers them one-by-one.
                    jarState.queueDrop(avatar.donor, pending = false, scattered = isInitialSeed)
                }
            }
        }
        seededAvatarIds = confirmedAvatars.map { it.avatarId }.toSet()
    }

    // Re-drop pending avatars after recomposition (e.g. returning from payment sheet).
    LaunchedEffect(Unit) {
        if (dragAvatarCount > 0) {
            repeat(dragAvatarCount) { jarState.queueDrop(userAvatar, pending = true) }
        }
    }

    /** success → confirm pending + update totals; failure → fade-out + revert. */
    LaunchedEffect(Unit) {
        viewModel.tipConfirmResult.collect { success ->
            if (success) {
                jarState.confirmPending()
                myConfirmedBuffer += dragAvatarCount
                confirmedTotalPrice = (confirmedTotalPrice + committedSlider.roundToInt())
                    .coerceAtMost(goalPrice)
                onConfirmedTotalChange(confirmedTotalPrice)
                if (confirmedTotalPrice >= goalPrice) {
                    isGoalReached = true
                    jarState.setMaxed(true)
                } else {
                    dragMax = (goalPrice - confirmedTotalPrice)
                        .coerceAtMost(goalPrice)
                        .toFloat()
                }
            } else {
                jarState.removePendingAll()
            }
            sliderValue = 0f
            dragAvatarCount = 0
            committedSlider = 0f
            prevNotchIndex = 0
            onPendingChange(0)
            isSendingTip = false
        }
    }

    val confirmedFraction = (confirmedTotalPrice.toFloat() / goalPrice.toFloat()).coerceIn(0f, 1f)
    val liveTotalPrice = (confirmedTotalPrice + sliderValue.roundToInt()).coerceAtMost(goalPrice)
    val totalFraction = (liveTotalPrice.toFloat() / goalPrice.toFloat()).coerceIn(0f, 1f)
    val amountText = PriceFormatter.formatPrice(liveTotalPrice, currency, minimumFractionDigits = 2)
    val goalText = PriceFormatter.formatPrice(goalPrice, currency)
    val topTipper: String = run {
        val totals = mutableMapOf<String, Int>()
        val names = mutableMapOf<String, String>()
        tipJar?.donations
            ?.filter { it.status == TipJarDonationStatus.SUCCEEDED }
            ?.forEach { donation ->
            val uid = donation.user.id
            totals[uid] = (totals[uid] ?: 0) + donation.amount
            names.getOrPut(uid) { donation.user.username ?: uid }
        }
        if (sliderValue > 0f) {
            totals[currentUserId] = (totals[currentUserId] ?: 0) + sliderValue.roundToInt()
            names.getOrPut(currentUserId) { accountData?.username ?: youLabel }
        }
        if (totals.isEmpty()) return@run "-"
        val (topId, topAmount) = totals.maxByOrNull { it.value } ?: return@run "-"
        if (topAmount <= 0) "-" else if (topId == currentUserId) youLabel else names[topId] ?: topId
    }

    val onSliderStart: () -> Unit = { isDragging = true }

    val onSliderDrag: (Float) -> Unit = { value ->
        val targetCount = floor(value / stepPrice).toInt()
        val delta = targetCount - dragAvatarCount
        when {
            delta > 0 -> repeat(delta) {
                jarState.queueDrop(userAvatar, pending = true)
            }

            delta < 0 -> jarState.removePending(-delta)
        }
        dragAvatarCount = targetCount

        // Click sound on every notch crossing
        val newNotch = (value / stepPrice).roundToInt()
        if (newNotch != prevNotchIndex) {
            audio.playClick()
            prevNotchIndex = newNotch
        }

        sliderValue = value
        onPendingChange(value.roundToInt())
    }

    val onSliderRelease: (Float) -> Unit = { value ->
        isDragging = false
        committedSlider = value
        sliderValue = value
        onPendingChange(value.roundToInt())
        if (value > 0f) viewModel.trackSliderAdjusted(value.roundToInt(), currency)
    }

    val onSendTip: () -> Unit = {
        if (!isSendingTip && committedSlider > 0f && paymentMethod != null) {
            showConfirmDialog = true
        }
    }

    if (showConfirmDialog) {
        val tipAmountPence = committedSlider.roundToInt()
        val currencyFee = blessChatConfig.feeForCurrency(currency)
        val feePence = if (currencyFee != null) {
            currencyFee.fixedFee + (tipAmountPence * currencyFee.percentageFee / 100).toInt()
        } else {
            0
        }
        val totalPence = tipAmountPence + feePence

        AppAlertDialog(
            title = stringResource(R.string.live_room_blesschat_confirm_tip_title),
            description = confirmTipDescription(
                tipAmountPence = tipAmountPence,
                feePence = feePence,
                totalPence = totalPence,
                currency = currency,
            ),
            confirm = stringResource(R.string.live_room_blesschat_confirm_tip_send),
            dismiss = stringResource(R.string.common_cancel),
            onConfirm = {
                showConfirmDialog = false
                if (paymentMethod != null) {
                    viewModel.confirmTip(tipAmountPence, currency, userAvatar)
                    isSendingTip = true
                }
            },
            onDismiss = { showConfirmDialog = false },
            shape = RoundedCornerShape(Dimens.corner14)
        )
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = Dimens.padding8),
    ) {
        HeightSpacer(Dimens.padding12)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.padding16)
        ) {
            IconButtonSecondary(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.CenterEnd),
                iconDrawableId = R.drawable.ic_close,
                size = ButtonSize.SIZE_40
            )
        }
        HeightSpacer(Dimens.padding12)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.padding24),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.live_room_blesschat_header_overline),
                color = AppTheme.colors.fg.neutral.secondary,
                style = AppTheme.typography.footnoteBold
            )
            HeightSpacer(Dimens.padding4)
            Text(
                text = stringResource(R.string.live_room_blesschat_header_title),
                color = AppTheme.colors.fg.neutral.primary,
                style = AppTheme.typography.title3
            )
            HeightSpacer(Dimens.padding4)
            Text(
                text = stringResource(R.string.live_room_blesschat_header_subtitle),
                color = AppTheme.colors.fg.neutral.secondary,
                style = AppTheme.typography.footnote,
                textAlign = TextAlign.Center
            )
        }

        HeightSpacer(Dimens.padding16)

        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.padding32),
        ) {
            StatGroup(
                label = stringResource(R.string.live_room_blesschat_stats_top_tipper_label),
                value = topTipper,
            )
            Spacer(Modifier.width(Dimens.padding24))
            Box(
                modifier = Modifier
                    .width(Dimens.strokeM)
                    .height(Dimens.padding32)
                    .background(AppTheme.colors.border.neutral.primary),
            )
            Spacer(Modifier.width(Dimens.padding24))
            StatGroup(
                label = stringResource(
                    if (isGoalReached) {
                        R.string.live_room_blesschat_stats_goal_reached_label
                    } else {
                        R.string.live_room_blesschat_stats_goal_label
                    }
                ),
                value = goalText,
            )
        }

        HeightSpacer(Dimens.padding16)

        BoxWithConstraints(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxWidth(),
        ) {
            val jarSize = maxWidth * 0.6f
            val bowlRadius = jarSize * BlessChatConfig.BOWL_RATIO
            val maxAvatars = (goalPrice / stepPrice).coerceAtLeast(1)
            val avatarRadius = BlessChatConfig.computeAvatarRadius(bowlRadius, maxAvatars)

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(jarSize)
                    .graphicsLayer {
                        scaleX = dismissScale
                        scaleY = dismissScale
                        alpha = dismissAlpha
                    },
            ) {
                BlessChatJarCanvas(
                    state = jarState,
                    canvasSize = jarSize,
                    avatarRadius = avatarRadius,
                )
                BlessChatProgressArc(
                    confirmedFraction = confirmedFraction,
                    totalFraction = totalFraction,
                    isDragging = isDragging,
                    amountText = amountText,
                    avatarUrl = if (sliderValue > 0f) accountData?.profilePicture else null,
                    canvasSize = jarSize,
                )
            }
        }

        HeightSpacer(Dimens.padding16)

        BlessChatSlider(
            value = sliderValue,
            goalPrice = goalPrice,
            stepPrice = stepPrice,
            dragMax = dragMax,
            currency = currency,
            onStart = onSliderStart,
            onDrag = onSliderDrag,
            onRelease = onSliderRelease,
            enabled = !isSendingTip,
            isWaving = isGoalReached,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.padding32),
        )

        HeightSpacer(Dimens.padding20)

        val footerState by remember(isGoalReached, sliderValue, isSendingTip) {
            derivedStateOf {
                when {
                    isGoalReached -> BlessChatFooter.COUPON_READY
                    sliderValue > 0f || isSendingTip -> BlessChatFooter.SEND_TIP
                    else -> BlessChatFooter.SLIDE_TO_TIP
                }
            }
        }

        AnimatedContent(targetState = footerState) { state ->
            when (state) {
                BlessChatFooter.SLIDE_TO_TIP -> {
                    ButtonGhost(
                        title = stringResource(
                            if (paymentMethod == null) {
                                R.string.live_room_blesschat_button_add_card
                            } else {
                                R.string.live_room_blesschat_button_slide_to_tip
                            }
                        ),
                        size = ButtonSize.SIZE_48,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Dimens.padding20),
                        enabled = paymentMethod == null,
                        onClick = if (paymentMethod == null) {
                            {
                                viewModel.trackAddPaymentTapped()
                                onClickPaymentMethod()
                            }
                        } else {
                            {}
                        },
                    )
                }

                BlessChatFooter.SEND_TIP -> {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Dimens.padding8),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Dimens.padding20),
                    ) {
                        ButtonSecondary(
                            title = paymentMethod?.let {
                                stringResource(R.string.live_room_blesschat_payment_card_mask, it.cardLast4)
                            } ?: stringResource(R.string.live_room_blesschat_button_add_card),
                            size = ButtonSize.SIZE_48,
                            leadingIcon = R.drawable.ic_credit_card,
                            enabled = !isSendingTip,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                if (paymentMethod != null) {
                                    viewModel.trackChangePaymentTapped()
                                } else {
                                    viewModel.trackAddPaymentTapped()
                                }
                                onClickPaymentMethod()
                            },
                        )
                        ButtonPrimary(
                            title = stringResource(
                                R.string.live_room_blesschat_button_send_tip,
                                PriceFormatter.formatPrice(
                                    minorUnitsPrice = committedSlider.roundToInt(),
                                    currency = currency,
                                    minimumFractionDigits = 2
                                ),
                            ),
                            size = ButtonSize.SIZE_48,
                            enabled = paymentMethod != null && !isSendingTip,
                            isProcessing = isSendingTip,
                            modifier = Modifier.weight(1f),
                            onClick = onSendTip,
                        )
                    }
                }

                BlessChatFooter.COUPON_READY -> {
                    ButtonGhost(
                        title = stringResource(R.string.live_room_blesschat_button_coupon_ready),
                        size = ButtonSize.SIZE_48,
                        modifier = Modifier,
                        enabled = false,
                        onClick = {},
                    )
                }
            }
        }
    }
}

@Composable
private fun confirmTipDescription(
    tipAmountPence: Int,
    feePence: Int,
    totalPence: Int,
    currency: String,
): String {
    val tipAmountFormatted = PriceFormatter.formatPrice(tipAmountPence, currency, minimumFractionDigits = 2)
    val feeFormatted = PriceFormatter.formatPrice(feePence, currency, minimumFractionDigits = 2)
    val totalFormatted = PriceFormatter.formatPrice(totalPence, currency, minimumFractionDigits = 2)
    return stringResource(R.string.live_room_blesschat_confirm_tip_description) +
        "\n\n" +
        stringResource(R.string.live_room_blesschat_confirm_tip_amount, tipAmountFormatted) +
        "\n" +
        stringResource(R.string.live_room_blesschat_confirm_tip_processing, feeFormatted) +
        "\n" +
        stringResource(R.string.live_room_blesschat_confirm_tip_total, totalFormatted)
}

@Composable
private fun StatGroup(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        AnimatedContent(
            targetState = value,
            transitionSpec = { (slideInVertically() + fadeIn()) togetherWith (slideOutVertically() + fadeOut()) }
        ) { value ->
            Text(
                text = value,
                color = AppTheme.colors.fg.neutral.primary,
                style = AppTheme.typography.footnoteBold
            )
        }
        HeightSpacer(Dimens.padding2)
        AnimatedContent(
            targetState = label,
            transitionSpec = { (slideInVertically() + fadeIn()) togetherWith (slideOutVertically() + fadeOut()) }
        ) { label ->
            Text(
                text = label,
                color = AppTheme.colors.fg.neutral.secondary,
                style = AppTheme.typography.footnote
            )
        }
    }
}
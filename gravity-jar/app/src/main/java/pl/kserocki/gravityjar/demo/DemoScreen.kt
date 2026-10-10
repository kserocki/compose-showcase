package pl.kserocki.gravityjar.demo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import pl.kserocki.gravityjar.blesschat.BlessChatAudio
import pl.kserocki.gravityjar.blesschat.BlessChatMiniJar
import pl.kserocki.gravityjar.blesschat.BlessChatModal
import pl.kserocki.gravityjar.blesschat.blessChatViewModel
import pl.kserocki.gravityjar.data.AccountData
import pl.kserocki.gravityjar.data.LocalAccountData
import pl.kserocki.gravityjar.data.PaymentMethod
import pl.kserocki.gravityjar.ui.designsystem.AppTheme
import pl.kserocki.gravityjar.ui.designsystem.ButtonPrimary
import pl.kserocki.gravityjar.ui.designsystem.ButtonSize
import pl.kserocki.gravityjar.ui.designsystem.Dimens
import pl.kserocki.gravityjar.ui.designsystem.HeightSpacer

private const val ROOM_ID = "demo-room"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DemoScreen() {
    val context = LocalContext.current

    // Low-latency SoundPool audio — loaded once, released when the screen leaves composition.
    val audio = remember { BlessChatAudio(context) }
    DisposableEffect(Unit) {
        audio.load()
        onDispose { audio.release() }
    }

    val account = remember {
        AccountData(userId = "me", username = "You", profilePicture = "https://i.pravatar.cc/150?img=8")
    }

    CompositionLocalProvider(LocalAccountData provides account) {
        val viewModel = blessChatViewModel(ROOM_ID)
        var paymentMethod by remember { mutableStateOf<PaymentMethod?>(PaymentMethod("pm_visa", "4242")) }
        var showSheet by remember { mutableStateOf(false) }
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val snackbarHostState = remember { SnackbarHostState() }

        LaunchedEffect(viewModel) {
            viewModel.errorMessage.collect { messageRes ->
                snackbarHostState.showSnackbar(context.getString(messageRes))
            }
        }

        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            containerColor = AppTheme.colors.background,
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                BlessChatMiniJar(
                    roomId = ROOM_ID,
                    viewModel = viewModel,
                    onClick = { showSheet = true },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(Dimens.padding16),
                )

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = Dimens.padding32),
                ) {
                    Text(
                        text = "GravityJar",
                        style = AppTheme.typography.title3,
                        color = AppTheme.colors.fg.neutral.primary,
                    )
                    HeightSpacer(Dimens.padding8)
                    Text(
                        text = "A physics-based community tip jar. Tap the jar in the corner, or open it below, and slide to drop a tip.",
                        style = AppTheme.typography.footnote,
                        color = AppTheme.colors.fg.neutral.secondary,
                        textAlign = TextAlign.Center,
                    )
                    HeightSpacer(Dimens.padding24)
                    ButtonPrimary(
                        title = "Open the jar",
                        size = ButtonSize.SIZE_48,
                        onClick = { showSheet = true },
                    )
                }
            }
        }

        if (showSheet) {
            ModalBottomSheet(
                onDismissRequest = { showSheet = false },
                sheetState = sheetState,
                containerColor = AppTheme.colors.background,
            ) {
                BlessChatModal(
                    audio = audio,
                    roomId = ROOM_ID,
                    viewModel = viewModel,
                    paymentMethod = paymentMethod,
                    onDismiss = { showSheet = false },
                    onClickPaymentMethod = { paymentMethod = PaymentMethod("pm_visa", "4242") },
                )
            }
        }
    }
}

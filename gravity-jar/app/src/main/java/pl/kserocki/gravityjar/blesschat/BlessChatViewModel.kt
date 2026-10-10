package pl.kserocki.gravityjar.blesschat

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pl.kserocki.gravityjar.data.BlessChatRemoteConfig
import pl.kserocki.gravityjar.data.TipJarDonationStatus
import pl.kserocki.gravityjar.data.TipJarDonor
import pl.kserocki.gravityjar.data.TipJarsRepository
import timber.log.Timber

class BlessChatViewModel(
    private val roomId: String,
    private val tipJarsRepository: TipJarsRepository,
) : ViewModel() {

    val blessChatConfig: StateFlow<BlessChatRemoteConfig> = tipJarsRepository.observeConfig()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BlessChatRemoteConfig.DEFAULT)

    val tipJar = tipJarsRepository.observeTipJarByRoom(roomId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _tipConfirmResult = MutableSharedFlow<Boolean>(extraBufferCapacity = 1)
    val tipConfirmResult = _tipConfirmResult.asSharedFlow()

    /** String resource for an error to surface (e.g. via a snackbar). */
    private val _errorMessage = MutableSharedFlow<Int>(extraBufferCapacity = 1)
    val errorMessage = _errorMessage.asSharedFlow()

    /**
     * Expands donations into individual [ExpandedAvatar] entries.
     *
     * Only [TipJarDonationStatus.SUCCEEDED] donations are included — PENDING and FAILED donations
     * are filtered out so they never appear in the jar.
     *
     * Each donation produces `amount / stepAmount` avatars (min 1), where stepAmount comes from the
     * remote config for the tip jar's currency. Each entry gets a unique
     * `avatarId = "userId_perUserIndex"` for stable ID-based diffing.
     */
    val expandedAvatars: StateFlow<List<ExpandedAvatar>> = combine(tipJar, blessChatConfig) { jar, config ->
        val stepAmount = jar?.currency?.let { config.feeForCurrency(it)?.stepAmount }
            ?: BlessChatRemoteConfig.DEFAULT_STEP_AMOUNT
        val result = mutableListOf<ExpandedAvatar>()
        val userIndexes = mutableMapOf<String, Int>()
        jar?.donations
            ?.filter { it.status == TipJarDonationStatus.SUCCEEDED }
            ?.forEach { donation ->
                val count = (donation.amount / stepAmount).coerceAtLeast(1)
                val userId = donation.user.id
                val startIdx = userIndexes.getOrDefault(userId, 0)
                repeat(count) { i ->
                    result.add(
                        ExpandedAvatar(
                            avatarId = "${userId}_${startIdx + i}",
                            donor = donation.user,
                        ),
                    )
                }
                userIndexes[userId] = startIdx + count
            }
        result.toList()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun confirmTip(amountPence: Int, currency: String, donor: TipJarDonor) {
        viewModelScope.launch {
            val result = runCatching { tipJarsRepository.confirmTip(roomId, amountPence, donor) }
                .getOrElse {
                    Timber.w(it, "confirmTip call failed")
                    _errorMessage.emit(TipErrorCode.GENERIC.messageRes)
                    _tipConfirmResult.emit(false)
                    return@launch
                }
            if (result.success) {
                _tipConfirmResult.emit(true)
            } else {
                Timber.w("Tip failed: ${result.failureReason}")
                _errorMessage.emit(TipErrorCode.fromFailureReason(result.failureReason).messageRes)
                _tipConfirmResult.emit(false)
            }
        }
    }

    // Analytics hooks — no-ops in this standalone build.
    fun trackMiniJarTapped() = Unit
    fun trackModalViewed() = Unit
    fun trackSliderAdjusted(sliderValue: Int, currency: String) = Unit
    fun trackAddPaymentTapped() = Unit
    fun trackChangePaymentTapped() = Unit
}

/**
 * Composition-scoped [BlessChatViewModel] keyed by [roomId] so the mini jar and the modal share one
 * instance (and therefore one live tip-jar subscription) within the same ViewModelStoreOwner.
 */
@Composable
fun blessChatViewModel(roomId: String): BlessChatViewModel = viewModel(
    key = "blesschat-$roomId",
    factory = viewModelFactory {
        initializer { BlessChatViewModel(roomId, pl.kserocki.gravityjar.data.FakeTipJarRepository) }
    },
)

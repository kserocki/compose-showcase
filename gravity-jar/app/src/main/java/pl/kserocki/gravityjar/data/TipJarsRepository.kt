package pl.kserocki.gravityjar.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

interface TipJarsRepository {
    fun observeTipJarByRoom(roomId: String): StateFlow<TipJar?>
    fun observeConfig(): Flow<BlessChatRemoteConfig>
    suspend fun confirmTip(roomId: String, amountPence: Int, donor: TipJarDonor): TipResult
}

/**
 * In-memory repository that stands in for the backend tip-jar service. It seeds a partially-filled
 * jar and then simulates other shoppers dropping tips in live, so the physics jar keeps filling on
 * its own. [confirmTip] fakes a payment round-trip and occasionally fails to exercise the error path.
 */
object FakeTipJarRepository : TipJarsRepository {

    private const val CURRENCY = "GBP"
    private const val GOAL = 1_000
    private const val STEP = 50

    /** Bots fill up to this cap, then stop so the shopper can complete the goal themselves. */
    private const val BOT_FILL_CAP = 700

    private val donationIds = AtomicInteger(0)

    private val botDonors = listOf(
        "Ava" to 5, "Noah" to 12, "Mia" to 24, "Leo" to 33, "Zoe" to 45,
        "Kai" to 51, "Ivy" to 58, "Max" to 63, "Ella" to 68, "Finn" to 15,
    ).map { (name, img) ->
        TipJarDonor(id = "bot_$name", username = name, avatarUrl = "https://i.pravatar.cc/150?img=$img")
    }

    private fun nextDonationId() = "d${donationIds.incrementAndGet()}"

    private val seedDonations = listOf(
        TipJarDonation(nextDonationId(), botDonors[0], amount = 100, status = TipJarDonationStatus.SUCCEEDED),
        TipJarDonation(nextDonationId(), botDonors[1], amount = 50, status = TipJarDonationStatus.SUCCEEDED),
    )

    private val _tipJar = MutableStateFlow(
        TipJar(
            tipJarId = "jar_1",
            currency = CURRENCY,
            targetAmount = GOAL,
            currentAmount = seedDonations.sumOf { it.amount },
            donations = seedDonations,
        ),
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    init {
        scope.launch { simulateLiveDonations() }
    }

    override fun observeTipJarByRoom(roomId: String): StateFlow<TipJar?> = _tipJar.asStateFlow()

    override fun observeConfig(): Flow<BlessChatRemoteConfig> = flowOf(BlessChatRemoteConfig.DEFAULT)

    override suspend fun confirmTip(roomId: String, amountPence: Int, donor: TipJarDonor): TipResult {
        delay(1_200) // fake payment round-trip

        // ~15% of tips fail, to demonstrate the in-UI error handling.
        if (Random.nextFloat() < 0.15f) {
            return TipResult(success = false, failureReason = FAILURE_REASONS.random())
        }

        val id = nextDonationId()
        addDonation(TipJarDonation(id, donor, amountPence, TipJarDonationStatus.SUCCEEDED))
        return TipResult(success = true, donationId = id)
    }

    private suspend fun simulateLiveDonations() {
        while (true) {
            delay(3_000L + Random.nextLong(2_500L))
            val jar = _tipJar.value
            if (jar.currentAmount >= BOT_FILL_CAP) continue
            val steps = 1 + Random.nextInt(3) // 1–3 steps → £0.50–£1.50
            addDonation(
                TipJarDonation(
                    id = nextDonationId(),
                    user = botDonors.random(),
                    amount = steps * STEP,
                    status = TipJarDonationStatus.SUCCEEDED,
                ),
            )
        }
    }

    private fun addDonation(donation: TipJarDonation) {
        _tipJar.update { jar ->
            jar.copy(
                donations = jar.donations + donation,
                currentAmount = (jar.currentAmount + donation.amount).coerceAtMost(jar.targetAmount),
            )
        }
    }

    private val FAILURE_REASONS = listOf(
        "card_declined", "insufficient_funds", "expired_card", "processing_error",
    )
}

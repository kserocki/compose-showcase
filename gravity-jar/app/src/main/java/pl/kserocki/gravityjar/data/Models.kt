package pl.kserocki.gravityjar.data

const val DEFAULT_CURRENCY = "GBP"

/** A single contributor to the jar. [avatarUrl] renders inside the physics avatar. */
data class TipJarDonor(
    val id: String,
    val username: String?,
    val avatarUrl: String?,
)

enum class TipJarDonationStatus { PENDING, SUCCEEDED, FAILED }

data class TipJarDonation(
    val id: String,
    val user: TipJarDonor,
    val amount: Int,
    val status: TipJarDonationStatus,
    val failureReason: String? = null,
)

/**
 * A community coupon jar for one live room. [currentAmount]/[targetAmount] are minor units
 * (pence). When [currentAmount] reaches [targetAmount] the goal is met.
 */
data class TipJar(
    val tipJarId: String,
    val currency: String,
    val targetAmount: Int,
    val currentAmount: Int,
    val donations: List<TipJarDonation>,
)

/** Outcome of a confirm-tip call. */
data class TipResult(
    val success: Boolean,
    val failureReason: String? = null,
    val donationId: String? = null,
)

/** A saved card the shopper can tip with. */
data class PaymentMethod(
    val id: String,
    val cardLast4: String,
)

/** Per-currency step size and processing-fee configuration. */
data class CurrencyFee(
    val stepAmount: Int,
    val fixedFee: Int,
    val percentageFee: Double,
)

/**
 * Remote-configurable jar settings. In production this would come from a feature-flag service;
 * here it is a static default.
 */
data class BlessChatRemoteConfig(
    val fees: Map<String, CurrencyFee>,
) {
    fun feeForCurrency(currency: String): CurrencyFee? = fees[currency]

    companion object {
        const val DEFAULT_STEP_AMOUNT = 50

        val DEFAULT = BlessChatRemoteConfig(
            fees = mapOf(
                "GBP" to CurrencyFee(stepAmount = 50, fixedFee = 20, percentageFee = 2.9),
                "EUR" to CurrencyFee(stepAmount = 50, fixedFee = 25, percentageFee = 2.9),
                "USD" to CurrencyFee(stepAmount = 50, fixedFee = 30, percentageFee = 2.9),
            ),
        )
    }
}

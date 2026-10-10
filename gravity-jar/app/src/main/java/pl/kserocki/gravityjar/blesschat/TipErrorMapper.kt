package pl.kserocki.gravityjar.blesschat

import androidx.annotation.StringRes
import pl.kserocki.gravityjar.R

enum class TipErrorCode(@param:StringRes val messageRes: Int) {
    CARD_DECLINED(R.string.live_room_blesschat_error_card_declined),
    AUTHENTICATION_REQUIRED(R.string.live_room_blesschat_error_authentication_required),
    PAYMENT_FAILED(R.string.live_room_blesschat_error_payment_failed),
    PAYMENT_ATTEMPT_ISSUE(R.string.live_room_blesschat_error_payment_attempt_issue),
    EXPIRED_CARD(R.string.live_room_blesschat_error_expired_card),
    INSUFFICIENT_FUNDS(R.string.live_room_blesschat_error_insufficient_funds),
    GENERIC(R.string.live_room_blesschat_error_generic);

    companion object {
        private val codeMap = mapOf(
            "card_declined" to CARD_DECLINED,
            "payment_method_provider_decline" to CARD_DECLINED,
            "card_decline_rate_limit_exceeded" to CARD_DECLINED,
            "authentication_required" to AUTHENTICATION_REQUIRED,
            "payment_intent_action_required" to AUTHENTICATION_REQUIRED,
            "payment_intent_authentication_failure" to AUTHENTICATION_REQUIRED,
            "payment_intent_payment_attempt_failed" to PAYMENT_FAILED,
            "processing_error" to PAYMENT_FAILED,
            "payment_method_provider_timeout" to PAYMENT_FAILED,
            "payment_method_not_available" to PAYMENT_FAILED,
            "payment_intent_payment_attempt_expired" to PAYMENT_ATTEMPT_ISSUE,
            "rate_limit" to PAYMENT_ATTEMPT_ISSUE,
            "idempotency_key_in_use" to PAYMENT_ATTEMPT_ISSUE,
            "expired_card" to EXPIRED_CARD,
            "insufficient_funds" to INSUFFICIENT_FUNDS,
        )

        fun fromFailureReason(reason: String?): TipErrorCode =
            reason?.let { codeMap[it] } ?: GENERIC
    }
}

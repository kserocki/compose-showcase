package pl.kserocki.scratchticket.redeem

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pl.kserocki.scratchticket.ticket.TicketState

data class RedeemViewState(
    val code: String = "",
    val isLoading: Boolean = false,
    val ticket: TicketState = TicketState.Locked,
    val error: String? = null,
) {
    val isUnlocked: Boolean get() = ticket !is TicketState.Locked
    val canRedeem: Boolean get() = code.isNotBlank() && !isLoading && !isUnlocked
}

/**
 * Validates promo codes against a fake backend: any code of 4+ characters unlocks credit equal to
 * its trailing number (default 10, capped at 100).
 */
class RedeemViewModel : ViewModel() {

    private val _state = MutableStateFlow(RedeemViewState())
    val state: StateFlow<RedeemViewState> = _state.asStateFlow()

    fun onCodeChanged(code: String) {
        if (_state.value.isUnlocked) return
        _state.update { it.copy(code = code.uppercase().take(16), error = null) }
    }

    fun onRedeem() {
        val code = _state.value.code.trim()
        if (code.isEmpty() || _state.value.isLoading) return
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            delay(1_200)
            _state.update {
                if (code.length < 4) {
                    it.copy(isLoading = false, error = "That code doesn't look right.")
                } else {
                    it.copy(isLoading = false, ticket = TicketState.Unlocked(amountFor(code)))
                }
            }
        }
    }

    fun onRevealed() {
        val ticket = _state.value.ticket as? TicketState.Unlocked ?: return
        _state.update { it.copy(ticket = TicketState.Revealed(ticket.amount)) }
    }

    fun onReset() {
        _state.value = RedeemViewState()
    }

    private fun amountFor(code: String): String {
        val value = code.takeLastWhile(Char::isDigit).toIntOrNull()?.coerceIn(1, 100) ?: 10
        return "$$value"
    }
}

package pk.psx.wealth.feature.portfolio

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import pk.psx.wealth.data.repository.PortfolioRepository
import pk.psx.wealth.domain.PortfolioTransaction
import pk.psx.wealth.domain.TransactionType
import pk.psx.wealth.domain.ZERO
import pk.psx.wealth.feature.common.PortfolioSession
import java.time.LocalDate
import javax.inject.Inject

data class TransactionEditorState(
    val existing: PortfolioTransaction? = null,
    val initialType: TransactionType = TransactionType.BUY,
    val initialSymbol: String = "",
    val initialQuantity: String = "",
    val initialPrice: String = "",
    val initialCashAmount: String = "",
    val saving: Boolean = false,
    val saved: Boolean = false,
    val error: String? = null,
)

data class TransactionFormData(
    val type: TransactionType,
    val date: String,
    val symbol: String = "",
    val quantity: String = "",
    val price: String = "",
    val grossAmount: String = "",
    val fees: String = "",
    val tax: String = "",
    val cashAmount: String = "",
    val notes: String = "",
    val allowNegativeCash: Boolean = false,
)

@HiltViewModel
class TransactionEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: PortfolioRepository,
    private val session: PortfolioSession,
) : ViewModel() {
    private val transactionId = savedStateHandle.get<String>("id")?.toLongOrNull() ?: 0L
    private val requestedType = savedStateHandle.get<String>("type")
        ?.let { runCatching { TransactionType.valueOf(it) }.getOrNull() } ?: TransactionType.BUY
    private val _state = MutableStateFlow(TransactionEditorState(
        initialType = requestedType,
        initialSymbol = savedStateHandle.get<String>("symbol").orEmpty().trim().uppercase(),
        initialQuantity = savedStateHandle.get<String>("quantity").orEmpty(),
        initialPrice = savedStateHandle.get<String>("price").orEmpty(),
        initialCashAmount = savedStateHandle.get<String>("cash").orEmpty(),
    ))
    val state: StateFlow<TransactionEditorState> = _state.asStateFlow()

    init {
        if (transactionId > 0) viewModelScope.launch {
            val existing = repository.getTransaction(transactionId)
            _state.value = _state.value.copy(existing = existing, initialType = existing?.type ?: requestedType)
        }
    }

    fun save(form: TransactionFormData) {
        if (_state.value.saving) return
        viewModelScope.launch {
            _state.value = _state.value.copy(saving = true, error = null)
            runCatching {
                val portfolioId = requireNotNull(session.selectedPortfolioId.value) { "Create or select a portfolio first" }
                val transaction = PortfolioTransaction(
                    id = _state.value.existing?.id ?: 0,
                    portfolioId = portfolioId,
                    type = form.type,
                    tradeDate = LocalDate.parse(form.date),
                    symbol = form.symbol.trim().uppercase().takeIf(String::isNotEmpty),
                    quantity = form.quantity.toBigDecimalOrNull() ?: ZERO,
                    price = form.price.toBigDecimalOrNull() ?: ZERO,
                    grossAmount = form.grossAmount.toBigDecimalOrNull(),
                    fees = form.fees.toBigDecimalOrNull() ?: ZERO,
                    tax = form.tax.toBigDecimalOrNull() ?: ZERO,
                    cashAmount = form.cashAmount.toBigDecimalOrNull(),
                    notes = form.notes.trim().takeIf(String::isNotEmpty),
                )
                repository.saveTransaction(transaction, form.allowNegativeCash)
            }.onSuccess {
                _state.value = _state.value.copy(saving = false, saved = true)
            }.onFailure {
                _state.value = _state.value.copy(saving = false, error = it.message ?: "Could not save transaction")
            }
        }
    }
}

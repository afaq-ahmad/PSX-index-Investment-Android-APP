package pk.psx.wealth.feature.rebalance

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pk.psx.wealth.data.repository.PlanWithItems
import pk.psx.wealth.data.repository.StrategyRepository
import pk.psx.wealth.data.repository.TradeExecution
import pk.psx.wealth.domain.TradeAction
import java.math.BigDecimal
import java.time.LocalDate
import javax.inject.Inject

data class ExecutionInput(
    val symbol: String,
    val action: TradeAction,
    val quantity: String,
    val price: String,
    val fees: String = "0",
    val tax: String = "0",
    val date: String = LocalDate.now().toString(),
)

data class ExecutePlanUiState(
    val plan: PlanWithItems? = null,
    val executing: Boolean = false,
    val completed: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class ExecutePlanViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val strategy: StrategyRepository,
) : ViewModel() {
    private val planId = requireNotNull(savedStateHandle.get<String>("planId")?.toLongOrNull())
    private val operation = MutableStateFlow(ExecutePlanUiState())

    val state: StateFlow<ExecutePlanUiState> = combine(strategy.observePlan(planId), operation) { plan, op ->
        op.copy(plan = plan)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExecutePlanUiState())

    fun execute(inputs: List<ExecutionInput>) {
        if (operation.value.executing) return
        viewModelScope.launch {
            operation.value = operation.value.copy(executing = true, error = null)
            runCatching {
                strategy.executePlan(planId, inputs.map { input ->
                    TradeExecution(
                        symbol = input.symbol,
                        action = input.action,
                        quantity = input.quantity.toBigDecimal(),
                        price = input.price.toBigDecimal(),
                        fees = input.fees.toBigDecimalOrNull() ?: BigDecimal.ZERO,
                        tax = input.tax.toBigDecimalOrNull() ?: BigDecimal.ZERO,
                        date = LocalDate.parse(input.date),
                    )
                })
            }.onSuccess { operation.value = operation.value.copy(executing = false, completed = true) }
                .onFailure { operation.value = operation.value.copy(executing = false, error = it.message) }
        }
    }
}

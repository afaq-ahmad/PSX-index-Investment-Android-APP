package pk.psx.wealth.data.repository

import kotlinx.coroutines.flow.Flow
import pk.psx.wealth.data.local.RebalancePlanEntity
import pk.psx.wealth.data.local.RebalancePlanItemEntity
import pk.psx.wealth.domain.RebalanceResult
import pk.psx.wealth.domain.TradeAction
import java.math.BigDecimal
import java.time.LocalDate

data class RebalancePlanDraft(
    val portfolioId: Long,
    val strategyType: String,
    val additionalCash: BigDecimal,
    val cashReserve: BigDecimal,
    val allowSelling: Boolean,
    val minimumTrade: BigDecimal,
    val result: RebalanceResult,
)

data class PlanWithItems(
    val plan: RebalancePlanEntity,
    val items: List<RebalancePlanItemEntity>,
)

data class TradeExecution(
    val symbol: String,
    val action: TradeAction,
    val quantity: BigDecimal,
    val price: BigDecimal,
    val fees: BigDecimal = BigDecimal.ZERO,
    val tax: BigDecimal = BigDecimal.ZERO,
    val date: LocalDate,
)

interface StrategyRepository {
    fun observeTargets(portfolioId: Long): Flow<Map<String, BigDecimal>>
    suspend fun saveTargets(portfolioId: Long, weights: Map<String, BigDecimal>)
    fun observePlans(portfolioId: Long): Flow<List<RebalancePlanEntity>>
    fun observePlan(planId: Long): Flow<PlanWithItems?>
    suspend fun savePlan(draft: RebalancePlanDraft): Long
    suspend fun executePlan(planId: Long, executions: List<TradeExecution>)
    suspend fun cancelPlan(planId: Long)
}

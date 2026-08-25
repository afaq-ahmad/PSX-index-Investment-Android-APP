package pk.psx.wealth.data.repository

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import pk.psx.wealth.data.local.RebalanceDao
import pk.psx.wealth.data.local.RebalancePlanEntity
import pk.psx.wealth.data.local.RebalancePlanItemEntity
import pk.psx.wealth.data.local.SecurityDao
import pk.psx.wealth.data.local.SecurityEntity
import pk.psx.wealth.data.local.TargetAllocationDao
import pk.psx.wealth.data.local.TargetAllocationEntity
import pk.psx.wealth.data.local.TransactionDao
import pk.psx.wealth.data.local.PsxDatabase
import pk.psx.wealth.data.local.toDomain
import pk.psx.wealth.data.local.toEntity
import pk.psx.wealth.domain.PortfolioCalculator
import pk.psx.wealth.domain.PortfolioTransaction
import pk.psx.wealth.domain.TradeAction
import pk.psx.wealth.domain.TransactionType
import java.math.BigDecimal
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomStrategyRepository @Inject constructor(
    private val db: PsxDatabase,
    private val targetDao: TargetAllocationDao,
    private val rebalanceDao: RebalanceDao,
    private val transactionDao: TransactionDao,
    private val securityDao: SecurityDao,
    private val calculator: PortfolioCalculator,
    private val clock: Clock,
) : StrategyRepository {
    override fun observeTargets(portfolioId: Long): Flow<Map<String, BigDecimal>> =
        targetDao.observe(portfolioId).map { rows ->
            rows.associate { it.symbol to it.targetPercent.toBigDecimal().movePointLeft(2) }
        }

    override suspend fun saveTargets(portfolioId: Long, weights: Map<String, BigDecimal>) {
        require(weights.isNotEmpty()) { "At least one target is required" }
        require(weights.values.all { it.signum() >= 0 }) { "Targets cannot be negative" }
        require(weights.values.fold(BigDecimal.ZERO, BigDecimal::add) <= BigDecimal.ONE) { "Targets exceed 100%" }
        targetDao.replace(portfolioId, weights.toSortedMap().map { (symbol, weight) ->
            val clean = symbol.trim().uppercase()
            val securityId = ensureSecurity(clean)
            TargetAllocationEntity(portfolioId, clean, securityId, weight.movePointRight(2).toDouble(), clock.millis())
        })
    }

    override fun observePlans(portfolioId: Long): Flow<List<RebalancePlanEntity>> =
        rebalanceDao.observePlans(portfolioId)

    override fun observePlan(planId: Long): Flow<PlanWithItems?> = combine(
        rebalanceDao.observePlan(planId),
        rebalanceDao.observeItems(planId),
    ) { plan, items -> plan?.let { PlanWithItems(it, items) } }

    override suspend fun savePlan(draft: RebalancePlanDraft): Long = db.withTransaction {
        val plan = RebalancePlanEntity(
            portfolioId = draft.portfolioId,
            createdAt = clock.millis(),
            strategyType = draft.strategyType,
            newCash = draft.additionalCash.toDouble(),
            cashReserve = draft.cashReserve.toDouble(),
            allowSelling = draft.allowSelling,
            minimumTrade = draft.minimumTrade.toDouble(),
            status = "SAVED",
            driftBefore = draft.result.driftBefore.toDouble(),
            driftAfter = draft.result.driftAfter.toDouble(),
            remainingCash = draft.result.cashAfter.toDouble(),
        )
        rebalanceDao.insertPlanWithItems(plan, draft.result.trades.map { trade ->
            RebalancePlanItemEntity(
                planId = 0,
                symbol = trade.symbol,
                action = trade.action.name,
                quantity = trade.quantity,
                estimatedPrice = trade.estimatedPrice.toDouble(),
                estimatedValue = trade.estimatedValue.toDouble(),
                currentWeight = trade.currentWeight.toDouble(),
                targetWeight = trade.targetWeight.toDouble(),
                projectedWeight = trade.projectedWeight.toDouble(),
            )
        })
    }

    override suspend fun executePlan(planId: Long, executions: List<TradeExecution>) {
        val plan = requireNotNull(rebalanceDao.plan(planId)) { "Plan not found" }
        require(plan.status == "SAVED" || plan.status == "DRAFT") { "Only an unexecuted plan can be executed" }
        val expected = rebalanceDao.items(planId).filter { it.action != TradeAction.HOLD.name }
            .map { it.symbol to TradeAction.valueOf(it.action) }.toSet()
        require(executions.map { it.symbol to it.action }.toSet() == expected) {
            "Confirm actual details for every proposed trade"
        }
        val current = transactionDao.list(plan.portfolioId).map { it.toDomain() }
        val actualTransactions = executions.mapIndexed { index, trade ->
            require(trade.quantity.signum() > 0 && trade.price.signum() > 0) { "Actual quantity and price must be positive" }
            PortfolioTransaction(
                id = Long.MAX_VALUE - executions.size + index,
                portfolioId = plan.portfolioId,
                type = if (trade.action == TradeAction.BUY) TransactionType.BUY else TransactionType.SELL,
                tradeDate = trade.date,
                symbol = trade.symbol,
                quantity = trade.quantity,
                price = trade.price,
                grossAmount = trade.quantity.multiply(trade.price),
                fees = trade.fees,
                tax = trade.tax,
                notes = "Executed from rebalance plan #$planId",
            )
        }
        val projected = calculator.calculate(current + actualTransactions, emptyMap())
        require(projected.cashBalance.signum() >= 0) { "Actual executions would make portfolio cash negative" }
        db.withTransaction {
            actualTransactions.forEach { transaction ->
                val securityId = ensureSecurity(requireNotNull(transaction.symbol))
                transactionDao.upsert(transaction.copy(id = 0).toEntity(securityId))
            }
            rebalanceDao.setStatus(planId, "EXECUTED")
        }
    }

    override suspend fun cancelPlan(planId: Long) = rebalanceDao.setStatus(planId, "CANCELLED")

    private suspend fun ensureSecurity(symbol: String): Long {
        val existing = securityDao.bySymbol(symbol)
        if (existing != null) return existing.id
        return securityDao.upsert(SecurityEntity(symbol = symbol, companyName = symbol))
    }
}

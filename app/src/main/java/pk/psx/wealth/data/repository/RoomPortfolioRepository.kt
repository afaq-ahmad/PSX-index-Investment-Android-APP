package pk.psx.wealth.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import pk.psx.wealth.data.local.LatestQuoteEntity
import pk.psx.wealth.data.local.PortfolioDao
import pk.psx.wealth.data.local.PortfolioEntity
import pk.psx.wealth.data.local.QuoteDao
import pk.psx.wealth.data.local.SecurityDao
import pk.psx.wealth.data.local.SecurityEntity
import pk.psx.wealth.data.local.TransactionDao
import pk.psx.wealth.data.local.toDomain
import pk.psx.wealth.data.local.toEntity
import pk.psx.wealth.domain.MarketQuote
import pk.psx.wealth.domain.PortfolioCalculator
import pk.psx.wealth.domain.PortfolioSnapshot
import pk.psx.wealth.domain.PortfolioTransaction
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomPortfolioRepository @Inject constructor(
    private val portfolioDao: PortfolioDao,
    private val securityDao: SecurityDao,
    private val transactionDao: TransactionDao,
    private val quoteDao: QuoteDao,
    private val calculator: PortfolioCalculator,
) : PortfolioRepository {
    override fun observePortfolios(): Flow<List<PortfolioEntity>> = portfolioDao.observeActive()

    override fun observeTransactions(portfolioId: Long): Flow<List<PortfolioTransaction>> =
        transactionDao.observe(portfolioId).map { rows -> rows.map { it.toDomain() } }

    override fun observeSnapshot(portfolioId: Long): Flow<PortfolioSnapshot> = combine(
        transactionDao.observe(portfolioId),
        quoteDao.observeAll(),
    ) { transactions, quotes ->
        calculator.calculate(
            transactions = transactions.map { it.toDomain() },
            prices = quotes.associate { it.symbol to it.price.toBigDecimal() },
        )
    }

    override fun observeQuotes(): Flow<List<MarketQuote>> =
        quoteDao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override suspend fun createPortfolio(name: String, benchmark: String): Long {
        val cleanName = name.trim()
        require(cleanName.isNotEmpty()) { "Portfolio name is required" }
        val cleanBenchmark = benchmark.trim().uppercase()
        require(cleanBenchmark in setOf("KMI30", "KSE100", "KMIALLSHR")) { "Unsupported benchmark" }
        return portfolioDao.upsert(PortfolioEntity(name = cleanName, benchmark = cleanBenchmark))
    }

    override suspend fun updatePortfolio(portfolio: PortfolioEntity) {
        require(portfolio.name.isNotBlank()) { "Portfolio name is required" }
        portfolioDao.upsert(portfolio.copy(updatedAt = System.currentTimeMillis()))
    }

    override suspend fun archivePortfolio(id: Long) {
        val portfolio = requireNotNull(portfolioDao.get(id)) { "Portfolio not found" }
        portfolioDao.upsert(portfolio.copy(isArchived = true, updatedAt = System.currentTimeMillis()))
    }

    override suspend fun saveTransaction(transaction: PortfolioTransaction, allowNegativeCash: Boolean): Long {
        calculator.validate(transaction)
        val current = transactionDao.list(transaction.portfolioId).map { it.toDomain() }.filterNot { it.id == transaction.id }
        val proposed = current + transaction
        val snapshot = calculator.calculate(proposed, emptyMap())
        require(allowNegativeCash || snapshot.cashBalance.signum() >= 0) {
            "Transaction would make cash negative; confirm it as an accounting correction"
        }
        val securityId = transaction.symbol?.let { upsertSecurity(it) }
        return transactionDao.upsert(transaction.toEntity(securityId))
    }

    override suspend fun getTransaction(id: Long): PortfolioTransaction? = transactionDao.get(id)?.toDomain()

    override suspend fun deleteTransaction(id: Long) = transactionDao.deleteById(id)

    override suspend fun upsertSecurity(symbol: String, companyName: String, sector: String?): Long {
        val normalized = normalizeSymbol(symbol)
        val existing = securityDao.bySymbol(normalized)
        return securityDao.upsert(
            (existing ?: SecurityEntity(symbol = normalized, companyName = companyName.trim().ifBlank { normalized }))
                .copy(companyName = companyName.trim().ifBlank { existing?.companyName ?: normalized }, sector = sector ?: existing?.sector),
        )
    }

    override suspend fun saveManualQuote(symbol: String, price: BigDecimal, priceDate: LocalDate): MarketQuote {
        require(price.signum() > 0) { "Price must be greater than zero" }
        val normalized = normalizeSymbol(symbol)
        val securityId = upsertSecurity(normalized)
        val instant = priceDate.atStartOfDay(ZoneId.systemDefault()).toInstant()
        val entity = LatestQuoteEntity(
            symbol = normalized,
            securityId = securityId,
            price = price.toDouble(),
            fetchedAt = instant.toEpochMilli(),
            marketTimestamp = instant.toEpochMilli(),
            source = "Manual",
            isManual = true,
        )
        quoteDao.upsert(entity)
        return entity.toDomain()
    }

    private fun normalizeSymbol(symbol: String): String {
        val value = symbol.trim().uppercase()
        require(value.matches(Regex("[A-Z0-9-]{1,20}"))) { "Invalid PSX symbol" }
        return value
    }
}

package pk.psx.wealth.data.repository

import kotlinx.coroutines.flow.Flow
import pk.psx.wealth.data.local.PortfolioEntity
import pk.psx.wealth.data.local.SecurityEntity
import pk.psx.wealth.domain.MarketQuote
import pk.psx.wealth.domain.PortfolioSnapshot
import pk.psx.wealth.domain.PortfolioTransaction
import java.math.BigDecimal
import java.time.LocalDate

interface PortfolioRepository {
    fun observePortfolios(): Flow<List<PortfolioEntity>>
    fun observeTransactions(portfolioId: Long): Flow<List<PortfolioTransaction>>
    fun observeSnapshot(portfolioId: Long): Flow<PortfolioSnapshot>
    fun observeQuotes(): Flow<List<MarketQuote>>
    suspend fun createPortfolio(name: String, benchmark: String = "KMI30"): Long
    suspend fun updatePortfolio(portfolio: PortfolioEntity)
    suspend fun archivePortfolio(id: Long)
    suspend fun saveTransaction(transaction: PortfolioTransaction, allowNegativeCash: Boolean = false): Long
    suspend fun deleteTransaction(id: Long)
    suspend fun upsertSecurity(symbol: String, companyName: String = symbol, sector: String? = null): Long
    suspend fun saveManualQuote(symbol: String, price: BigDecimal, priceDate: LocalDate): MarketQuote
}

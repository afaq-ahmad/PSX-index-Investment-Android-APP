package pk.psx.wealth.data.local

import pk.psx.wealth.domain.MarketQuote
import pk.psx.wealth.domain.PortfolioTransaction
import pk.psx.wealth.domain.TransactionType
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

fun TransactionEntity.toDomain() = PortfolioTransaction(
    id = id,
    portfolioId = portfolioId,
    type = TransactionType.valueOf(type),
    tradeDate = LocalDate.parse(date),
    symbol = symbol,
    quantity = quantity.toBigDecimal(),
    price = price.toBigDecimal(),
    grossAmount = amount?.toBigDecimal(),
    fees = fees.toBigDecimal(),
    tax = tax.toBigDecimal(),
    cashAmount = cashAmount?.toBigDecimal(),
    notes = notes,
)

fun PortfolioTransaction.toEntity(securityId: Long? = null) = TransactionEntity(
    id = id,
    portfolioId = portfolioId,
    type = type.name,
    date = tradeDate.toString(),
    symbol = symbol?.trim()?.uppercase(),
    quantity = quantity.toDouble(),
    price = price.toDouble(),
    amount = grossAmount?.toDouble(),
    notes = notes?.trim()?.takeIf(String::isNotEmpty),
    securityId = securityId,
    fees = fees.toDouble(),
    tax = tax.toDouble(),
    cashAmount = cashAmount?.toDouble(),
)

fun LatestQuoteEntity.toDomain() = MarketQuote(
    symbol = symbol,
    price = price.toBigDecimal(),
    change = change?.toBigDecimal(),
    changePercent = changePercent?.toBigDecimal(),
    volume = volume,
    marketTime = marketTimestamp?.let(Instant::ofEpochMilli),
    retrievedAt = Instant.ofEpochMilli(fetchedAt),
    source = source,
    isManual = isManual,
)

fun BigDecimal.normalizedPercent(): BigDecimal = if (this > BigDecimal.ONE) movePointLeft(2) else this

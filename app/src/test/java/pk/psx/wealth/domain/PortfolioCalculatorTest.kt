package pk.psx.wealth.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class PortfolioCalculatorTest {
    private val calculator = PortfolioCalculator()
    private val day = LocalDate.of(2026, 1, 1)

    @Test
    fun `two buys and a partial sell use weighted average cost`() {
        val result = calculate(
            deposit("100000"),
            buy(2, "FFC", "100", "100", fees = "100"),
            buy(3, "FFC", "100", "120", tax = "100"),
            sell(4, "FFC", "50", "150", fees = "50", tax = "50"),
        )
        val holding = result.holdings.single()
        assertMoney("150", holding.quantity)
        assertMoney("111", holding.averageCost)
        assertMoney("1850", result.realizedProfit)
        assertMoney("85200", result.cashBalance)
    }

    @Test
    fun `full sell closes holding and preserves realised gain`() {
        val result = calculate(deposit("2000"), buy(2, "SYS", "10", "100"), sell(3, "SYS", "10", "130"))
        assertEquals(emptyList<Holding>(), result.holdings)
        assertMoney("300", result.realizedProfit)
        assertMoney("2300", result.cashBalance)
    }

    @Test
    fun `buy after sell starts from the remaining weighted cost`() {
        val result = calculate(
            deposit("10000"), buy(2, "MARI", "10", "100"), sell(3, "MARI", "5", "120"),
            buy(4, "MARI", "5", "200"),
        )
        assertMoney("150", result.holdings.single().averageCost)
        assertMoney("100", result.realizedProfit)
    }

    @Test
    fun `dividend is cash income and not an external contribution`() {
        val result = calculate(deposit("1000"), buy(2, "FFC", "5", "100"), dividend(3, "FFC", "60", "10", "50"))
        assertMoney("550", result.cashBalance)
        assertMoney("50", result.dividendIncome)
        assertMoney("1000", result.netContributions)
        assertMoney("50", result.holdings.single().dividends)
    }

    @Test
    fun `cash withdrawal and standalone costs follow sign convention`() {
        val result = calculate(
            deposit("5000"),
            tx(2, TransactionType.CASH_WITHDRAWAL, cash = "1000"),
            tx(3, TransactionType.FEE, cash = "25"),
            tx(4, TransactionType.TAX, cash = "75"),
        )
        assertMoney("3900", result.cashBalance)
        assertMoney("4000", result.netContributions)
        assertMoney("100", result.feesAndTaxes)
    }

    @Test
    fun `bonus and split increase quantity without increasing cost`() {
        val result = calculate(
            deposit("1000"), buy(2, "FFC", "10", "100"),
            tx(3, TransactionType.BONUS_SHARES, symbol = "FFC", quantity = "2"),
            tx(4, TransactionType.SPLIT, symbol = "FFC", quantity = "2"),
        )
        val holding = result.holdings.single()
        assertMoney("24", holding.quantity)
        assertMoney("1000", holding.remainingCost)
    }

    @Test
    fun `missing quote remains unknown rather than zero`() {
        val result = calculator.calculate(listOf(deposit("1000"), buy(2, "FFC", "5", "100")), emptyMap())
        assertNull(result.holdings.single().marketPrice)
        assertNull(result.holdings.single().marketValue)
        assertNull(result.totalProfit)
        assertFalse(result.hasCompletePrices)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `oversell is rejected`() {
        calculate(deposit("1000"), buy(2, "FFC", "5", "100"), sell(3, "FFC", "6", "100"))
    }

    private fun calculate(vararg transactions: PortfolioTransaction) =
        calculator.calculate(transactions.toList(), mapOf("FFC" to bd("140"), "SYS" to bd("130"), "MARI" to bd("200")))

    private fun deposit(amount: String) = tx(1, TransactionType.CASH_DEPOSIT, cash = amount)
    private fun buy(id: Long, symbol: String, quantity: String, price: String, fees: String = "0", tax: String = "0") =
        tx(id, TransactionType.BUY, symbol, quantity, price, fees = fees, tax = tax)
    private fun sell(id: Long, symbol: String, quantity: String, price: String, fees: String = "0", tax: String = "0") =
        tx(id, TransactionType.SELL, symbol, quantity, price, fees = fees, tax = tax)
    private fun dividend(id: Long, symbol: String, gross: String, tax: String, net: String) =
        tx(id, TransactionType.DIVIDEND, symbol = symbol, gross = gross, tax = tax, cash = net)
    private fun tx(
        id: Long,
        type: TransactionType,
        symbol: String? = null,
        quantity: String = "0",
        price: String = "0",
        gross: String? = null,
        fees: String = "0",
        tax: String = "0",
        cash: String? = null,
    ) = PortfolioTransaction(id, 1, type, day, symbol, bd(quantity), bd(price), gross?.let(::bd), bd(fees), bd(tax), cash?.let(::bd))

    private fun assertMoney(expected: String, actual: BigDecimal) =
        assertEquals(0, bd(expected).compareTo(actual))
}

private fun bd(value: String) = BigDecimal(value)

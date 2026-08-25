package pk.psx.wealth.data.remote.psx

import org.junit.Test
import pk.psx.wealth.domain.IndexConstituent
import java.math.BigDecimal
import java.time.LocalDate

class IndexSnapshotValidatorTest {
    private val validator = IndexSnapshotValidator()

    @Test
    fun `valid KMI30 snapshot passes`() {
        validator.validate("KMI30", rows(30))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `truncated snapshot is rejected`() {
        validator.validate("KMI30", rows(3))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `duplicate symbols are rejected`() {
        val rows = rows(30).toMutableList()
        rows[29] = rows[29].copy(symbol = rows[0].symbol)
        validator.validate("KMI30", rows)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `implausible weight sum is rejected`() {
        validator.validate("KMI30", rows(30).map { it.copy(weightPercent = BigDecimal.ONE) })
    }

    private fun rows(count: Int) = (1..count).map { index ->
        IndexConstituent(
            indexCode = "KMI30",
            symbol = "S$index",
            companyName = "Security $index",
            weightPercent = if (index == count) BigDecimal("100").subtract(BigDecimal("3.33").multiply((count - 1).toBigDecimal())) else BigDecimal("3.33"),
            price = BigDecimal("100"),
            snapshotDate = LocalDate.of(2026, 8, 25),
            source = "fixture",
        )
    }
}

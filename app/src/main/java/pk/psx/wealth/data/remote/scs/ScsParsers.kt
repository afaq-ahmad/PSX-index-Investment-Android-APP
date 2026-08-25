package pk.psx.wealth.data.remote.scs

import org.jsoup.Jsoup
import pk.psx.wealth.data.remote.psx.SymbolNormalizer
import pk.psx.wealth.domain.MarketQuote
import pk.psx.wealth.domain.StockSnapshot
import java.math.BigDecimal
import java.time.Instant
import javax.inject.Inject

class ScsCompanyParser @Inject constructor(private val symbols: SymbolNormalizer) {
    fun parseQuote(symbol: String, html: String, retrievedAt: Instant, source: String): MarketQuote {
        val document = Jsoup.parse(html)
        val price = number(document.selectFirst("#ContentPlaceHolder1_lbl_price, [id$=lbl_price]")?.text())
            ?: throw IllegalArgumentException("SCS Trade quote price was not found")
        require(price.signum() > 0) { "SCS Trade quote price is not positive" }
        val changeText = document.selectFirst("#ContentPlaceHolder1_lbl_change, [id$=lbl_change]")?.text().orEmpty()
        val changes = CHANGE_PATTERN.find(changeText)
        return MarketQuote(
            symbol = symbols.normalize(symbol),
            price = price,
            change = changes?.groupValues?.getOrNull(1)?.normalizedNumber(),
            changePercent = changes?.groupValues?.getOrNull(2)?.normalizedNumber(),
            volume = wholeNumber(document.selectFirst("#ContentPlaceHolder1_lbl_volume, [id$=lbl_volume]")?.text()),
            marketTime = null,
            retrievedAt = retrievedAt,
            source = source,
        )
    }

    fun parseSnapshot(symbol: String, html: String, retrievedAt: Instant, source: String): StockSnapshot {
        val normalized = symbols.normalize(symbol)
        val document = Jsoup.parse(html)
        val quote = parseQuote(normalized, html, retrievedAt, source)
        val rawName = document.selectFirst("#ContentPlaceHolder1_lbl_companyname, [id$=lbl_companyname]")?.text().orEmpty()
        return StockSnapshot(
            symbol = normalized,
            companyName = rawName.removePrefix("$normalized -").trim().ifBlank { normalized },
            sector = document.selectFirst("#ContentPlaceHolder1_lbl_sector, [id$=lbl_sector]")?.text()?.trim()?.takeIf(String::isNotBlank),
            quote = quote,
            marketCap = scaledNumber(document.selectFirst("#ContentPlaceHolder1_lbl_mktcap, [id$=lbl_mktcap]")?.text()),
        )
    }

    private fun scaledNumber(raw: String?): BigDecimal? {
        val value = number(raw) ?: return null
        val multiplier = when {
            raw?.contains("tn", ignoreCase = true) == true -> BigDecimal("1000000000000")
            raw?.contains("bn", ignoreCase = true) == true -> BigDecimal("1000000000")
            raw?.contains("mn", ignoreCase = true) == true -> BigDecimal("1000000")
            raw?.contains("th", ignoreCase = true) == true -> BigDecimal("1000")
            else -> BigDecimal.ONE
        }
        return value.multiply(multiplier)
    }

    private companion object {
        val CHANGE_PATTERN = Regex("([-+]?\\d[\\d,]*(?:\\.\\d+)?)\\s*\\(\\s*([-+]?\\d[\\d,]*(?:\\.\\d+)?)%\\s*\\)")
    }
}

private fun number(raw: String?): BigDecimal? = raw?.let {
    Regex("[-+]?\\d[\\d,]*(?:\\.\\d+)?").find(it)?.value?.normalizedNumber()
}

private fun String.normalizedNumber(): BigDecimal? = replace(",", "").trim().toBigDecimalOrNull()
private fun wholeNumber(raw: String?): Long? = number(raw)?.toLong()

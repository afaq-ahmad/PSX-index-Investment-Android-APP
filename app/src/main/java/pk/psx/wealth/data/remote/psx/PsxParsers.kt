package pk.psx.wealth.data.remote.psx

import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import pk.psx.wealth.domain.DailyPrice
import pk.psx.wealth.domain.IndexConstituent
import pk.psx.wealth.domain.MarketQuote
import pk.psx.wealth.domain.StockSnapshot
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

class SymbolNormalizer @Inject constructor() {
    private val corporateMarkers = setOf("XD", "XB", "XR", "XW")
    fun normalize(raw: String): String {
        val tokens = raw.trim().uppercase(Locale.ROOT).split(Regex("\\s+")).filter(String::isNotBlank)
        require(tokens.isNotEmpty()) { "Symbol is empty" }
        require(tokens.drop(1).all { it in corporateMarkers }) { "Ambiguous symbol: $raw" }
        return tokens.first().also {
            require(it.matches(Regex("[A-Z0-9-]{1,20}"))) { "Invalid PSX symbol: $raw" }
        }
    }
}

class PsxIndexParser @Inject constructor(private val symbols: SymbolNormalizer) {
    fun parse(
        indexCode: String,
        html: String,
        snapshotDate: LocalDate,
        source: String,
    ): List<IndexConstituent> {
        val document = Jsoup.parse(html)
        val table = document.select("table").firstOrNull { candidate ->
            val headings = candidate.select("thead th").map { normalizeHeading(it.text()) }
            "SYMBOL" in headings && "CURRENT" in headings && headings.any { it.startsWith("IDX WTG") }
        } ?: throw IllegalArgumentException("PSX index constituent table was not found")
        val headings = table.select("thead th").map { normalizeHeading(it.text()) }
        val positions = headings.withIndex().associate { it.value to it.index }
        val symbolIndex = positions.getValue("SYMBOL")
        val nameIndex = positions.getValue("NAME")
        val currentIndex = positions.getValue("CURRENT")
        val weightIndex = headings.indexOfFirst { it.startsWith("IDX WTG") }
        val volumeIndex = positions["VOLUME"]
        val freeFloatIndex = headings.indexOfFirst { it.startsWith("FREEFLOAT") }.takeIf { it >= 0 }
        val marketCapIndex = headings.indexOfFirst { it.startsWith("MARKET CAP") }.takeIf { it >= 0 }

        return table.select("tbody tr").mapNotNull { row ->
            val cells = row.select("td")
            if (cells.size < headings.size) return@mapNotNull null
            val symbolCell = cells[symbolIndex]
            val rawSymbol = symbolCell.selectFirst("strong")?.text()
                ?: symbolCell.attr("data-order").takeIf(String::isNotBlank)
                ?: symbolCell.text()
            IndexConstituent(
                indexCode = indexCode,
                symbol = symbols.normalize(rawSymbol),
                companyName = cells[nameIndex].text().trim(),
                weightPercent = number(cells[weightIndex]),
                price = number(cells[currentIndex]),
                volume = volumeIndex?.let { longNumber(cells[it]) },
                freeFloat = freeFloatIndex?.let { number(cells[it]) },
                marketCap = marketCapIndex?.let { number(cells[it]) },
                snapshotDate = snapshotDate,
                source = source,
            )
        }
    }

    private fun normalizeHeading(value: String) = value.trim().uppercase(Locale.ROOT).replace(Regex("\\s+"), " ")
}

class PsxCompanyParser @Inject constructor(private val symbols: SymbolNormalizer) {
    private val marketFormatter = DateTimeFormatter.ofPattern("EEE, MMM d, yyyy h:mm a", Locale.ENGLISH)
    private val marketZone = ZoneId.of("Asia/Karachi")

    fun parseQuote(symbol: String, html: String, retrievedAt: Instant, source: String): MarketQuote {
        val document = Jsoup.parse(html)
        val price = decimal(document.selectFirst(".quote__close")?.text())
            ?: throw IllegalArgumentException("PSX quote price was not found")
        require(price.signum() > 0) { "PSX quote price is not positive" }
        val marketTime = document.selectFirst(".quote__date")?.text()
            ?.substringAfter("As of", "")?.trim()?.takeIf(String::isNotEmpty)
            ?.let { runCatching { LocalDateTime.parse(it, marketFormatter).atZone(marketZone).toInstant() }.getOrNull() }
        val stats = stats(document.select(".stats_item"))
        return MarketQuote(
            symbol = symbols.normalize(symbol),
            price = price,
            change = decimal(document.selectFirst(".change__value")?.text()),
            changePercent = decimal(document.selectFirst(".change__percent")?.text()),
            volume = stats["VOLUME"]?.let(::longValue),
            marketTime = marketTime,
            retrievedAt = retrievedAt,
            source = source,
        )
    }

    fun parseSnapshot(symbol: String, html: String, retrievedAt: Instant, source: String): StockSnapshot {
        val document = Jsoup.parse(html)
        val quote = parseQuote(symbol, html, retrievedAt, source)
        val stats = stats(document.select(".stats_item"))
        val marketCapThousands = stats.entries.firstOrNull { it.key.startsWith("MARKET CAP") }?.value?.let(::decimal)
        val freeFloat = stats.entries.firstOrNull { it.key == "FREE FLOAT" && !it.value.contains('%') }?.value?.let(::decimal)
        return StockSnapshot(
            symbol = quote.symbol,
            companyName = document.selectFirst(".quote__name")?.text()?.trim().orEmpty().ifBlank { quote.symbol },
            sector = document.selectFirst(".quote__sector")?.text()?.trim()?.takeIf(String::isNotEmpty),
            quote = quote,
            marketCap = marketCapThousands?.multiply(BigDecimal(1_000)),
            freeFloat = freeFloat,
            peRatio = stats["P/E RATIO (TTM) **"]?.let(::decimal),
            dividendYield = stats.entries.firstOrNull { it.key.contains("DIVIDEND YIELD") }?.value?.let(::decimal),
        )
    }

    private fun stats(items: Iterable<Element>): Map<String, String> = items.mapNotNull { item ->
        val label = item.selectFirst(".stats_label")?.text()?.trim()?.uppercase(Locale.ROOT) ?: return@mapNotNull null
        val value = item.selectFirst(".stats_value")?.text()?.trim() ?: return@mapNotNull null
        label to value
    }.toMap()
}

@JsonClass(generateAdapter = false)
data class TimeseriesResponse(val data: List<List<Double?>>? = null)

class PsxHistoryParser @Inject constructor() {
    private val adapter = Moshi.Builder().add(KotlinJsonAdapterFactory()).build().adapter(TimeseriesResponse::class.java)
    private val marketZone = ZoneId.of("Asia/Karachi")

    fun parse(symbol: String, json: String, retrievedAt: Instant, source: String): List<DailyPrice> {
        val rows = adapter.fromJson(json)?.data ?: throw IllegalArgumentException("PSX history data is missing")
        return rows.mapNotNull { row ->
            val epochSeconds = row.getOrNull(0)?.toLong() ?: return@mapNotNull null
            val close = row.getOrNull(1)?.takeIf(Double::isFinite)?.toBigDecimal() ?: return@mapNotNull null
            if (close.signum() <= 0) return@mapNotNull null
            DailyPrice(
                symbol = symbol,
                date = Instant.ofEpochSecond(epochSeconds).atZone(marketZone).toLocalDate(),
                open = row.getOrNull(3)?.takeIf(Double::isFinite)?.toBigDecimal(),
                close = close,
                volume = row.getOrNull(2)?.takeIf(Double::isFinite)?.toLong(),
                adjusted = true,
                source = source,
                retrievedAt = retrievedAt,
            )
        }.distinctBy(DailyPrice::date).sortedBy(DailyPrice::date)
    }
}

private fun number(cell: Element): BigDecimal? = decimal(cell.attr("data-order").takeIf(String::isNotBlank) ?: cell.text())
private fun longNumber(cell: Element): Long? = longValue(cell.attr("data-order").takeIf(String::isNotBlank) ?: cell.text())
private fun longValue(raw: String): Long? = decimal(raw)?.toLong()
private fun decimal(raw: String?): BigDecimal? {
    if (raw.isNullOrBlank() || raw.contains("N/A", ignoreCase = true) || raw.trim() in setOf("—", "-")) return null
    val cleaned = raw.replace(",", "").replace("Rs.", "", ignoreCase = true)
        .replace("%", "").replace("(", "").replace(")", "").trim()
    return cleaned.toBigDecimalOrNull()
}

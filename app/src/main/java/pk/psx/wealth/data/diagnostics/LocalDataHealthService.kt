package pk.psx.wealth.data.diagnostics

import pk.psx.wealth.data.local.BackupDao
import pk.psx.wealth.data.local.toDomain
import pk.psx.wealth.domain.DataHealthIssue
import pk.psx.wealth.domain.DataHealthReport
import pk.psx.wealth.domain.DataHealthSeverity
import pk.psx.wealth.domain.DataQualityInput
import pk.psx.wealth.domain.DataQualityQuote
import pk.psx.wealth.domain.DataQualityTarget
import pk.psx.wealth.domain.IndexCacheQuality
import pk.psx.wealth.domain.LocalDataQualityEngine
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalDataHealthService @Inject constructor(
    private val dao: BackupDao,
    private val engine: LocalDataQualityEngine,
    private val clock: Clock,
) {
    suspend fun inspect(): DataHealthReport {
        val mappingIssues = mutableListOf<DataHealthIssue>()
        val portfolios = dao.portfolios()
        val transactions = dao.transactions().mapNotNull { row ->
            runCatching { row.toDomain() }.getOrElse {
                mappingIssues += corrupt("TRANSACTION_DECODE_FAILED", "A transaction cannot be decoded", "Transaction ID ${row.id}")
                null
            }
        }
        val quotes = dao.quotes().mapNotNull { row ->
            runCatching { DataQualityQuote(row.symbol, BigDecimal.valueOf(row.price), Instant.ofEpochMilli(row.fetchedAt)) }
                .getOrElse {
                    mappingIssues += corrupt("QUOTE_DECODE_FAILED", "A quote cannot be decoded", row.symbol)
                    null
                }
        }
        val targets = dao.targets().mapNotNull { row ->
            runCatching { DataQualityTarget(row.portfolioId, row.symbol, BigDecimal.valueOf(row.targetPercent)) }
                .getOrElse {
                    mappingIssues += corrupt("TARGET_DECODE_FAILED", "A target cannot be decoded", row.symbol)
                    null
                }
        }
        val headers = dao.indexSnapshots()
        val rowsBySnapshot = dao.indexConstituents().groupBy { it.snapshotId }
        val latestIndexes = headers.groupBy { it.indexCode }.mapNotNull { (code, snapshots) ->
            val latest = snapshots.maxWithOrNull(compareBy({ it.snapshotDate }, { it.retrievedAt })) ?: return@mapNotNull null
            runCatching {
                val rows = rowsBySnapshot[latest.id].orEmpty()
                val weights = rows.mapNotNull { it.weightPercent?.let { value -> BigDecimal.valueOf(value) } }
                IndexCacheQuality(
                    indexCode = code,
                    snapshotDate = LocalDate.parse(latest.snapshotDate),
                    memberCount = rows.size,
                    weightPercentTotal = weights.takeIf { it.isNotEmpty() }?.fold(BigDecimal.ZERO, BigDecimal::add),
                )
            }.getOrElse {
                mappingIssues += corrupt("INDEX_DECODE_FAILED", "An index snapshot cannot be decoded", code)
                null
            }
        }
        val checkedAt = Instant.now(clock)
        val report = engine.inspect(
            DataQualityInput(
                portfolioIds = portfolios.map { it.id }.toSet(),
                transactions = transactions,
                quotes = quotes,
                targets = targets,
                watchlistSymbols = dao.watchlistItems().map { it.symbol }.toSet(),
                latestIndexes = latestIndexes,
                checkedAt = checkedAt,
                checkedDate = LocalDate.now(clock),
            ),
        )
        return report.copy(
            issues = (mappingIssues + report.issues)
                .sortedWith(compareBy<DataHealthIssue> { it.severity.ordinal }.thenBy { it.code }),
        )
    }

    private fun corrupt(code: String, title: String, record: String) = DataHealthIssue(
        code = code,
        severity = DataHealthSeverity.ERROR,
        title = title,
        detail = "Unreadable local record: $record.",
        affectedCount = 1,
        action = "Restore a known-good backup or correct the affected record.",
    )
}

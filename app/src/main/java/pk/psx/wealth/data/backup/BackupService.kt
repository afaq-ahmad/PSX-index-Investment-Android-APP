package pk.psx.wealth.data.backup

import androidx.room.withTransaction
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import pk.psx.wealth.data.local.BackupDao
import pk.psx.wealth.data.local.DailyPriceEntity
import pk.psx.wealth.data.local.FundamentalMetricEntity
import pk.psx.wealth.data.local.IndexConstituentEntity
import pk.psx.wealth.data.local.IndexDefinitionEntity
import pk.psx.wealth.data.local.IndexSnapshotEntity
import pk.psx.wealth.data.local.LatestQuoteEntity
import pk.psx.wealth.data.local.PortfolioEntity
import pk.psx.wealth.data.local.PsxDatabase
import pk.psx.wealth.data.local.RebalancePlanEntity
import pk.psx.wealth.data.local.RebalancePlanItemEntity
import pk.psx.wealth.data.local.SecurityEntity
import pk.psx.wealth.data.local.TargetAllocationEntity
import pk.psx.wealth.data.local.TransactionEntity
import pk.psx.wealth.data.local.WatchlistEntity
import pk.psx.wealth.data.local.WatchlistItemEntity
import pk.psx.wealth.domain.TransactionType
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.Clock
import java.time.Instant
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

enum class BackupKind { ESSENTIAL, FULL }

@JsonClass(generateAdapter = false)
data class BackupManifest(
    val format: String = "pk.psx.wealth.backup",
    val formatVersion: Int = 1,
    val databaseSchema: Int = 2,
    val kind: BackupKind,
    val createdAt: String,
    val counts: Map<String, Int>,
)

@JsonClass(generateAdapter = false)
data class BackupBundle(
    val kind: BackupKind,
    val portfolios: List<PortfolioEntity>,
    val securities: List<SecurityEntity>,
    val transactions: List<TransactionEntity>,
    val targets: List<TargetAllocationEntity>,
    val watchlists: List<WatchlistEntity>,
    val watchlistItems: List<WatchlistItemEntity>,
    val fundamentals: List<FundamentalMetricEntity>,
    val rebalancePlans: List<RebalancePlanEntity>,
    val rebalanceItems: List<RebalancePlanItemEntity>,
    val indexDefinitions: List<IndexDefinitionEntity>,
    val quotes: List<LatestQuoteEntity> = emptyList(),
    val prices: List<DailyPriceEntity> = emptyList(),
    val indexSnapshots: List<IndexSnapshotEntity> = emptyList(),
    val indexConstituents: List<IndexConstituentEntity> = emptyList(),
)

data class BackupExport(val fileName: String, val bytes: ByteArray)

data class RestorePreview(
    val manifest: BackupManifest,
    val portfolioCount: Int,
    val transactionCount: Int,
    val targetCount: Int,
    val watchlistCount: Int,
    val fundamentalCount: Int,
    val cachedPriceCount: Int,
    val indexSnapshotCount: Int,
    val warnings: List<String>,
)

private data class ParsedBackup(val manifest: BackupManifest, val bundle: BackupBundle)

@Singleton
class BackupService @Inject constructor(
    private val db: PsxDatabase,
    private val dao: BackupDao,
    private val clock: Clock,
) {
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val manifestAdapter = moshi.adapter(BackupManifest::class.java)
    private val bundleAdapter = moshi.adapter(BackupBundle::class.java)

    suspend fun create(kind: BackupKind): BackupExport {
        val bundle = BackupBundle(
            kind = kind,
            portfolios = dao.portfolios(),
            securities = dao.securities(),
            transactions = dao.transactions(),
            targets = dao.targets(),
            watchlists = dao.watchlists(),
            watchlistItems = dao.watchlistItems(),
            fundamentals = dao.fundamentals(),
            rebalancePlans = dao.rebalancePlans(),
            rebalanceItems = dao.rebalanceItems(),
            indexDefinitions = dao.indexDefinitions(),
            quotes = if (kind == BackupKind.FULL) dao.quotes() else emptyList(),
            prices = if (kind == BackupKind.FULL) dao.prices() else emptyList(),
            indexSnapshots = if (kind == BackupKind.FULL) dao.indexSnapshots() else emptyList(),
            indexConstituents = if (kind == BackupKind.FULL) dao.indexConstituents() else emptyList(),
        )
        validate(bundle)
        val counts = counts(bundle)
        val manifest = BackupManifest(kind = kind, createdAt = Instant.now(clock).toString(), counts = counts)
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            zip.writeEntry("manifest.json", manifestAdapter.toJson(manifest))
            zip.writeEntry("backup.json", bundleAdapter.toJson(bundle))
        }
        val stamp = Instant.now(clock).toString().take(10)
        return BackupExport("psx-wealth-${kind.name.lowercase()}-$stamp.psxbackup", output.toByteArray())
    }

    fun preview(bytes: ByteArray): RestorePreview {
        val parsed = parse(bytes)
        val bundle = parsed.bundle
        val warnings = buildList {
            if (bundle.kind == BackupKind.ESSENTIAL) add("Cached prices and index snapshots are not included; refresh them after restore.")
            if (bundle.portfolios.isEmpty()) add("This backup contains no portfolios.")
        }
        return RestorePreview(parsed.manifest, bundle.portfolios.size, bundle.transactions.size, bundle.targets.size,
            bundle.watchlists.size, bundle.fundamentals.size, bundle.prices.size, bundle.indexSnapshots.size, warnings)
    }

    suspend fun restore(bytes: ByteArray) {
        val bundle = parse(bytes).bundle
        db.withTransaction {
            dao.clearRebalanceItems()
            dao.clearRebalancePlans()
            dao.clearTargets()
            dao.clearTransactions()
            dao.clearWatchlistItems()
            dao.clearWatchlists()
            dao.clearFundamentals()
            dao.clearIndexConstituents()
            dao.clearIndexSnapshots()
            dao.clearLegacyIndexSnapshots()
            dao.clearQuotes()
            dao.clearPrices()
            dao.clearProviderStatus()
            dao.clearIndexDefinitions()
            dao.clearSecurities()
            dao.clearPortfolios()

            dao.insertPortfolios(bundle.portfolios)
            dao.insertSecurities(bundle.securities)
            dao.insertIndexDefinitions(bundle.indexDefinitions)
            dao.insertWatchlists(bundle.watchlists)
            dao.insertRebalancePlans(bundle.rebalancePlans)
            dao.insertIndexSnapshots(bundle.indexSnapshots)
            dao.insertTransactions(bundle.transactions)
            dao.insertTargets(bundle.targets)
            dao.insertWatchlistItems(bundle.watchlistItems)
            dao.insertFundamentals(bundle.fundamentals)
            dao.insertRebalanceItems(bundle.rebalanceItems)
            dao.insertQuotes(bundle.quotes)
            dao.insertPrices(bundle.prices)
            dao.insertIndexConstituents(bundle.indexConstituents)
        }
    }

    private fun parse(bytes: ByteArray): ParsedBackup {
        require(bytes.isNotEmpty() && bytes.size <= MAX_BACKUP_BYTES) { "Backup is empty or exceeds 50 MB" }
        val entries = mutableMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                require(!entry.isDirectory && entry.name in setOf("manifest.json", "backup.json")) { "Unexpected backup entry: ${entry.name}" }
                require(entry.name !in entries) { "Duplicate backup entry: ${entry.name}" }
                entries[entry.name] = zip.readLimited(MAX_JSON_BYTES).decodeToString()
                zip.closeEntry()
            }
        }
        val manifest = requireNotNull(entries["manifest.json"]?.let { manifestAdapter.fromJson(it) }) { "Backup manifest is missing" }
        require(manifest.format == "pk.psx.wealth.backup" && manifest.formatVersion == 1) { "Unsupported backup format" }
        require(manifest.databaseSchema == 2) { "Backup database schema ${manifest.databaseSchema} is not supported" }
        val bundle = requireNotNull(entries["backup.json"]?.let { bundleAdapter.fromJson(it) }) { "Backup data is missing" }
        require(bundle.kind == manifest.kind) { "Backup kind does not match its manifest" }
        validate(bundle)
        require(counts(bundle) == manifest.counts) { "Backup counts do not match its manifest" }
        return ParsedBackup(manifest, bundle)
    }

    private fun validate(bundle: BackupBundle) {
        requireUnique(bundle.portfolios.map { it.id }, "portfolio ids")
        requireUnique(bundle.securities.map { it.id }, "security ids")
        requireUnique(bundle.securities.map { it.symbol }, "security symbols")
        requireUnique(bundle.transactions.map { it.id }, "transaction ids")
        requireUnique(bundle.fundamentals.map { it.id }, "fundamental observation ids")
        requireUnique(bundle.watchlists.map { it.id }, "watchlist ids")
        requireUnique(bundle.rebalancePlans.map { it.id }, "rebalance plan ids")
        requireUnique(bundle.indexSnapshots.map { it.id }, "index snapshot ids")
        requireUnique(bundle.targets.map { it.portfolioId to it.symbol }, "target keys")
        requireUnique(bundle.watchlistItems.map { it.watchlistId to it.symbol }, "watchlist item keys")
        requireUnique(bundle.rebalanceItems.map { Triple(it.planId, it.symbol, it.action) }, "rebalance item keys")
        requireUnique(bundle.indexConstituents.map { it.snapshotId to it.symbol }, "index constituent keys")
        require(bundle.portfolios.all { it.id > 0 } && bundle.securities.all { it.id > 0 } &&
            bundle.transactions.all { it.id > 0 } && bundle.watchlists.all { it.id > 0 } &&
            bundle.fundamentals.all { it.id > 0 } && bundle.rebalancePlans.all { it.id > 0 } &&
            bundle.indexSnapshots.all { it.id > 0 }) { "Backup contains an invalid generated id" }
        val portfolioIds = bundle.portfolios.mapTo(mutableSetOf()) { it.id }
        val watchlistIds = bundle.watchlists.mapTo(mutableSetOf()) { it.id }
        val planIds = bundle.rebalancePlans.mapTo(mutableSetOf()) { it.id }
        val snapshotIds = bundle.indexSnapshots.mapTo(mutableSetOf()) { it.id }
        require(bundle.transactions.all { it.portfolioId in portfolioIds }) { "Transaction refers to a missing portfolio" }
        require(bundle.targets.all { it.portfolioId in portfolioIds }) { "Target refers to a missing portfolio" }
        require(bundle.rebalancePlans.all { it.portfolioId in portfolioIds }) { "Plan refers to a missing portfolio" }
        require(bundle.watchlistItems.all { it.watchlistId in watchlistIds }) { "Watchlist item refers to a missing list" }
        require(bundle.rebalanceItems.all { it.planId in planIds }) { "Plan item refers to a missing plan" }
        require(bundle.indexConstituents.all { it.snapshotId in snapshotIds }) { "Constituent refers to a missing snapshot" }
        require(bundle.transactions.all { runCatching { TransactionType.valueOf(it.type) }.isSuccess }) { "Backup contains an invalid transaction type" }
        require(bundle.portfolios.all { it.name.isNotBlank() }) { "Backup contains an unnamed portfolio" }
        require(bundle.transactions.all { row -> listOf(row.quantity, row.price, row.fees, row.tax).all(Double::isFinite) &&
            row.amount?.isFinite() != false && row.cashAmount?.isFinite() != false && runCatching { java.time.LocalDate.parse(row.date) }.isSuccess }) {
            "Backup contains an invalid transaction value or date"
        }
        require(bundle.targets.all { it.targetPercent.isFinite() && it.targetPercent in 0.0..1.0 }) { "Backup contains an invalid target" }
        require(bundle.fundamentals.all { it.value.isFinite() && runCatching { java.time.LocalDate.parse(it.periodEnd) }.isSuccess }) {
            "Backup contains an invalid fundamental observation"
        }
        require(bundle.indexSnapshots.all { runCatching { java.time.LocalDate.parse(it.snapshotDate) }.isSuccess }) { "Backup contains an invalid index date" }
        val symbols = bundle.securities.map { it.symbol } + bundle.transactions.mapNotNull { it.symbol } +
            bundle.targets.map { it.symbol } + bundle.watchlistItems.map { it.symbol }
        require(symbols.all { it.matches(Regex("[A-Z0-9-]{1,20}")) }) { "Backup contains an invalid PSX symbol" }
        require(bundle.quotes.all { it.price.isFinite() && it.price > 0 }) { "Backup contains an invalid quote" }
        require(bundle.prices.all { it.close.isFinite() && it.close > 0 }) { "Backup contains an invalid daily close" }
        if (bundle.kind == BackupKind.ESSENTIAL) {
            require(bundle.quotes.isEmpty() && bundle.prices.isEmpty() && bundle.indexSnapshots.isEmpty() && bundle.indexConstituents.isEmpty()) {
                "Essential backup unexpectedly contains cache data"
            }
        }
    }

    private fun counts(bundle: BackupBundle): Map<String, Int> = linkedMapOf(
        "portfolios" to bundle.portfolios.size,
        "securities" to bundle.securities.size,
        "transactions" to bundle.transactions.size,
        "targets" to bundle.targets.size,
        "watchlists" to bundle.watchlists.size,
        "watchlistItems" to bundle.watchlistItems.size,
        "fundamentals" to bundle.fundamentals.size,
        "rebalancePlans" to bundle.rebalancePlans.size,
        "rebalanceItems" to bundle.rebalanceItems.size,
        "indexDefinitions" to bundle.indexDefinitions.size,
        "quotes" to bundle.quotes.size,
        "prices" to bundle.prices.size,
        "indexSnapshots" to bundle.indexSnapshots.size,
        "indexConstituents" to bundle.indexConstituents.size,
    )

    private fun <T> requireUnique(values: List<T>, label: String) {
        require(values.distinct().size == values.size) { "Backup contains duplicate $label" }
    }

    private fun ZipOutputStream.writeEntry(name: String, value: String) {
        putNextEntry(ZipEntry(name))
        write(value.encodeToByteArray())
        closeEntry()
    }

    private fun ZipInputStream.readLimited(limit: Int): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8_192)
        var total = 0
        while (true) {
            val count = read(buffer)
            if (count < 0) break
            total += count
            require(total <= limit) { "Backup JSON entry exceeds the allowed size" }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    private companion object {
        const val MAX_BACKUP_BYTES = 50 * 1024 * 1024
        const val MAX_JSON_BYTES = 100 * 1024 * 1024
    }
}

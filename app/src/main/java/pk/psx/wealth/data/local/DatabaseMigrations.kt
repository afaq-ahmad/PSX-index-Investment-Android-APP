package pk.psx.wealth.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) = with(db) {
        execSQL("ALTER TABLE portfolios ADD COLUMN createdAt INTEGER NOT NULL DEFAULT 0")
        execSQL("ALTER TABLE portfolios ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
        execSQL("ALTER TABLE portfolios ADD COLUMN isArchived INTEGER NOT NULL DEFAULT 0")

        execSQL("ALTER TABLE transactions ADD COLUMN securityId INTEGER")
        execSQL("ALTER TABLE transactions ADD COLUMN fees REAL NOT NULL DEFAULT 0")
        execSQL("ALTER TABLE transactions ADD COLUMN tax REAL NOT NULL DEFAULT 0")
        execSQL("ALTER TABLE transactions ADD COLUMN cashAmount REAL")
        execSQL("ALTER TABLE transactions ADD COLUMN createdAt INTEGER NOT NULL DEFAULT 0")
        execSQL("CREATE INDEX IF NOT EXISTS index_transactions_securityId ON transactions(securityId)")

        execSQL("ALTER TABLE quotes ADD COLUMN securityId INTEGER")
        execSQL("ALTER TABLE quotes ADD COLUMN changePercent REAL")
        execSQL("ALTER TABLE quotes ADD COLUMN volume INTEGER")
        execSQL("ALTER TABLE quotes ADD COLUMN marketTimestamp INTEGER")
        execSQL("ALTER TABLE quotes ADD COLUMN isManual INTEGER NOT NULL DEFAULT 0")
        execSQL("CREATE INDEX IF NOT EXISTS index_quotes_securityId ON quotes(securityId)")

        execSQL("""
            CREATE TABLE IF NOT EXISTS securities (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                symbol TEXT NOT NULL,
                companyName TEXT NOT NULL,
                sector TEXT,
                isActive INTEGER NOT NULL,
                isShariahKnown INTEGER NOT NULL,
                isShariahCompliant INTEGER,
                lastMetadataUpdate INTEGER
            )
        """.trimIndent())
        execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_securities_symbol ON securities(symbol)")
        execSQL("CREATE INDEX IF NOT EXISTS index_securities_sector ON securities(sector)")

        execSQL("""
            CREATE TABLE IF NOT EXISTS daily_prices (
                symbol TEXT NOT NULL,
                date TEXT NOT NULL,
                securityId INTEGER,
                open REAL,
                high REAL,
                low REAL,
                close REAL NOT NULL,
                volume INTEGER,
                isAdjusted INTEGER,
                source TEXT NOT NULL,
                retrievedAt INTEGER NOT NULL,
                PRIMARY KEY(symbol, date)
            )
        """.trimIndent())
        execSQL("CREATE INDEX IF NOT EXISTS index_daily_prices_securityId ON daily_prices(securityId)")

        execSQL("""
            CREATE TABLE IF NOT EXISTS index_definitions (
                code TEXT NOT NULL PRIMARY KEY,
                name TEXT NOT NULL,
                description TEXT
            )
        """.trimIndent())
        execSQL("INSERT OR IGNORE INTO index_definitions(code,name,description) VALUES ('KMI30','KMI 30','Thirty liquid Shariah-compliant PSX companies')")
        execSQL("INSERT OR IGNORE INTO index_definitions(code,name,description) VALUES ('KSE100','KSE 100','Pakistan Stock Exchange benchmark index')")
        execSQL("INSERT OR IGNORE INTO index_definitions(code,name,description) VALUES ('KMIALLSHR','PSX-KMI All Share','Shariah-compliant all-share index')")

        execSQL("""
            CREATE TABLE IF NOT EXISTS index_snapshot_headers (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                indexCode TEXT NOT NULL,
                snapshotDate TEXT NOT NULL,
                retrievedAt INTEGER NOT NULL,
                source TEXT NOT NULL
            )
        """.trimIndent())
        execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_index_snapshot_headers_indexCode_snapshotDate ON index_snapshot_headers(indexCode, snapshotDate)")

        execSQL("""
            CREATE TABLE IF NOT EXISTS index_constituents (
                snapshotId INTEGER NOT NULL,
                symbol TEXT NOT NULL,
                companyName TEXT NOT NULL,
                securityId INTEGER,
                weightPercent REAL,
                price REAL,
                volume INTEGER,
                freeFloat REAL,
                marketCap REAL,
                PRIMARY KEY(snapshotId, symbol),
                FOREIGN KEY(snapshotId) REFERENCES index_snapshot_headers(id) ON UPDATE NO ACTION ON DELETE CASCADE
            )
        """.trimIndent())
        execSQL("CREATE INDEX IF NOT EXISTS index_index_constituents_symbol ON index_constituents(symbol)")

        execSQL("""
            CREATE TABLE IF NOT EXISTS target_allocations (
                portfolioId INTEGER NOT NULL,
                symbol TEXT NOT NULL,
                securityId INTEGER,
                targetPercent REAL NOT NULL,
                updatedAt INTEGER NOT NULL,
                PRIMARY KEY(portfolioId, symbol)
            )
        """.trimIndent())
        execSQL("CREATE INDEX IF NOT EXISTS index_target_allocations_securityId ON target_allocations(securityId)")

        execSQL("""
            CREATE TABLE IF NOT EXISTS watchlists (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                name TEXT NOT NULL,
                createdAt INTEGER NOT NULL
            )
        """.trimIndent())
        execSQL("""
            CREATE TABLE IF NOT EXISTS watchlist_items (
                watchlistId INTEGER NOT NULL,
                symbol TEXT NOT NULL,
                securityId INTEGER,
                createdAt INTEGER NOT NULL,
                notes TEXT,
                PRIMARY KEY(watchlistId, symbol),
                FOREIGN KEY(watchlistId) REFERENCES watchlists(id) ON UPDATE NO ACTION ON DELETE CASCADE
            )
        """.trimIndent())
        execSQL("CREATE INDEX IF NOT EXISTS index_watchlist_items_securityId ON watchlist_items(securityId)")

        execSQL("""
            CREATE TABLE IF NOT EXISTS rebalance_plans (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                portfolioId INTEGER NOT NULL,
                createdAt INTEGER NOT NULL,
                strategyType TEXT NOT NULL,
                newCash REAL NOT NULL,
                cashReserve REAL NOT NULL,
                allowSelling INTEGER NOT NULL,
                minimumTrade REAL NOT NULL,
                status TEXT NOT NULL,
                driftBefore REAL,
                driftAfter REAL,
                remainingCash REAL
            )
        """.trimIndent())
        execSQL("CREATE INDEX IF NOT EXISTS index_rebalance_plans_portfolioId ON rebalance_plans(portfolioId)")
        execSQL("""
            CREATE TABLE IF NOT EXISTS rebalance_plan_items (
                planId INTEGER NOT NULL,
                symbol TEXT NOT NULL,
                action TEXT NOT NULL,
                quantity INTEGER NOT NULL,
                estimatedPrice REAL NOT NULL,
                estimatedValue REAL NOT NULL,
                currentWeight REAL NOT NULL,
                targetWeight REAL NOT NULL,
                projectedWeight REAL NOT NULL,
                PRIMARY KEY(planId, symbol, action),
                FOREIGN KEY(planId) REFERENCES rebalance_plans(id) ON UPDATE NO ACTION ON DELETE CASCADE
            )
        """.trimIndent())

        execSQL("""
            CREATE TABLE IF NOT EXISTS fundamental_metrics (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                symbol TEXT NOT NULL,
                securityId INTEGER,
                periodEnd TEXT NOT NULL,
                periodType TEXT NOT NULL,
                metricCode TEXT NOT NULL,
                value REAL NOT NULL,
                unit TEXT NOT NULL,
                source TEXT NOT NULL,
                retrievedAt INTEGER NOT NULL
            )
        """.trimIndent())
        execSQL("CREATE INDEX IF NOT EXISTS index_fundamental_metrics_symbol ON fundamental_metrics(symbol)")
        execSQL("CREATE INDEX IF NOT EXISTS index_fundamental_metrics_symbol_metricCode_periodEnd ON fundamental_metrics(symbol, metricCode, periodEnd)")

        execSQL("""
            CREATE TABLE IF NOT EXISTS provider_status (
                providerId TEXT NOT NULL,
                capability TEXT NOT NULL,
                lastAttemptAt INTEGER NOT NULL,
                lastSuccessAt INTEGER,
                lastError TEXT,
                cachedRecordCount INTEGER NOT NULL,
                PRIMARY KEY(providerId, capability)
            )
        """.trimIndent())
    }
}

fun seedReferenceData(db: SupportSQLiteDatabase) {
    db.execSQL("INSERT OR IGNORE INTO index_definitions(code,name,description) VALUES ('KMI30','KMI 30','Thirty liquid Shariah-compliant PSX companies')")
    db.execSQL("INSERT OR IGNORE INTO index_definitions(code,name,description) VALUES ('KSE100','KSE 100','Pakistan Stock Exchange benchmark index')")
    db.execSQL("INSERT OR IGNORE INTO index_definitions(code,name,description) VALUES ('KMIALLSHR','PSX-KMI All Share','Shariah-compliant all-share index')")
}

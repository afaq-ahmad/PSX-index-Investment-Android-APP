# PSX Wealth

A local-first Android portfolio ledger and cash-first rebalancing companion for
Pakistan Stock Exchange investors. The application is decision support only: it
does not place broker orders and does not require an account or a server.

## Implemented foundation

- Branded green-and-gold Material 3 Compose experience with an adaptive launcher
  icon, accessible charts, Home, Index Plan, Portfolio, Research and More.
- A versioned Room ledger with multiple portfolios, securities, quotes, historical
  prices, dated index snapshots, targets, watchlists, rebalance plans, fundamentals
  and provider diagnostics.
- Transaction-derived cash, holdings, weighted average cost, realized/unrealized
  gains, fees/taxes, corporate actions, contributions and dividends using
  `BigDecimal` domain calculations.
- A capability-aware provider boundary with PSX primary data, optional SCS quote
  fallback, configurable ordering, per-source diagnostics and manual/cache fallback.
- Deterministic cash-only and optional full rebalance engines with cash reserve,
  minimum-trade, exclusion, no-sell and maximum-weight constraints.
- Local DataStore preferences and manual security/price support.
- Isolated PSX parsers/providers for index constituents, company quotes and EOD
  history, with validation, atomic caching, partial-failure summaries and optional
  once-daily WorkManager refresh.
- Complete local portfolio workflow: dynamic transaction forms, derived holdings,
  manual prices, target strategies, cash-only/full rebalance previews, saved plans
  and confirmation of actual executions before ledger mutation.
- A first-class Index Plan flow: choose capital and KMI30/KSE100/KMIALLSHR, load
  published stock weights, compare current versus target whole shares, and record
  explicit green buy/red sell gaps through prefilled ledger entries.
- Research workspace with index/sector comparison and recomposition, current value,
  overall and periodic TWR/MWR, annualized XIRR, contribution-adjusted daily and
  cumulative P/L, stock/sector doughnuts, holding-gain bars, dividend analytics,
  offline watchlists, explainable scoring and a cache-only screener.
- Simultaneous contribution-matched KMI30/KSE100/KMIALLSHR benchmarking with
  explicit local history refresh, fair dated cash-flow simulation, gain/XIRR cards,
  and 1Y/3Y/5Y/MAX multi-series charts.
- Nine on-device CSV reports, versioned essential/full ZIP backups with validated
  transactional restore, provider diagnostics, persistent display/data/portfolio
  settings, and optional Keystore-backed PIN/device authentication and screen privacy.
- Read-only local health audits for ledger replay, negative cash, allocation targets,
  missing/stale prices and index-cache integrity, with actionable on-device findings.
- Unit coverage for accounting and rebalancing edge cases plus Android CI.

The manual ledger and deterministic calculation layer work independently of
market-data fetching, so a website change cannot break the owner's source-of-truth
transactions.

## Build and install

Install Android SDK 35, set `ANDROID_HOME` (or create `local.properties`), then:

```bash
gradle testDebugUnitTest lintDebug assembleDebug
```

The minimum supported Android version is Android 8.0 (API 26).

For Android Studio setup, debug installation, private release signing, signature
verification, safe updates and troubleshooting, follow the
[build, install and release guide](docs/BUILD_INSTALL_AND_RELEASE.md). A shorter
[phone installation guide](docs/USER_INSTALLATION_GUIDE.md) is available for the
person receiving the signed APK.

## Data principles

Transactions are the source of truth. Successful market responses are cached
locally, while failed refreshes must retain the last valid snapshot. Index rows
use `(indexCode, symbol, snapshotDate)` as their key so historical weights are
preserved rather than overwritten.

See [the accounting and storage contract](docs/ACCOUNTING_AND_STORAGE.md) for the
exact cost-basis and migration rules.

See [market data and offline behaviour](docs/MARKET_DATA.md) for provider,
validation, caching and responsible-refresh details.

See [portfolio and rebalancing workflow](docs/PORTFOLIO_AND_REBALANCING.md) for
target strategies and the plan-versus-execution safety contract.

See [the index investing workflow](docs/INDEX_INVESTING_WORKFLOW.md) for the
amount-to-index-to-shares experience and its rounding and ledger rules.

See [analytics and research](docs/ANALYTICS_AND_RESEARCH.md) for return, dividend,
fundamental-confidence and cache-only screening rules.

See [performance analytics methodology](docs/PERFORMANCE_ANALYTICS.md) for the exact
TWR, periodic MWR, P/L, allocation and contribution-matched benchmark formulas.

See [reports, backup and local security](docs/REPORTS_BACKUP_SECURITY.md) for export,
restore validation and device-protection details, and [MVP status](docs/MVP_STATUS.md)
for the plan checklist.

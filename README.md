# PSX Wealth

A local-first Android portfolio ledger and cash-first rebalancing companion for
Pakistan Stock Exchange investors. The application is decision support only: it
does not place broker orders and does not require an account or a server.

## Implemented foundation

- Material 3 Compose shell with Home, Portfolio, Research, Rebalance and More.
- A versioned Room ledger with multiple portfolios, securities, quotes, historical
  prices, dated index snapshots, targets, watchlists, rebalance plans, fundamentals
  and provider diagnostics.
- Transaction-derived cash, holdings, weighted average cost, realized/unrealized
  gains, fees/taxes, corporate actions, contributions and dividends using
  `BigDecimal` domain calculations.
- A provider boundary that keeps PSX website parsing separate from calculations.
- Deterministic cash-only and optional full rebalance engines with cash reserve,
  minimum-trade, exclusion, no-sell and maximum-weight constraints.
- Local DataStore preferences and manual security/price support.
- Isolated PSX parsers/providers for index constituents, company quotes and EOD
  history, with validation, atomic caching, partial-failure summaries and optional
  once-daily WorkManager refresh.
- Complete local portfolio workflow: dynamic transaction forms, derived holdings,
  manual prices, target strategies, cash-only/full rebalance previews, saved plans
  and confirmation of actual executions before ledger mutation.
- Research workspace with index/sector comparison and recomposition, dated wealth
  history, XIRR, dividend analytics, stock price charts, offline watchlists, manual
  fundamentals, explainable sector-aware scoring and a cache-only screener.
- Nine on-device CSV reports, versioned essential/full ZIP backups with validated
  transactional restore, provider diagnostics, persistent display/data/portfolio
  settings, and optional Keystore-backed PIN/device authentication and screen privacy.
- Read-only local health audits for ledger replay, negative cash, allocation targets,
  missing/stale prices and index-cache integrity, with actionable on-device findings.
- Unit coverage for accounting and rebalancing edge cases plus Android CI.

The manual ledger and deterministic calculation layer work independently of
market-data fetching, so a website change cannot break the owner's source-of-truth
transactions.

## Build

Install Android SDK 35, set `ANDROID_HOME` (or create `local.properties`), then:

```bash
gradle testDebugUnitTest lintDebug assembleDebug
```

The minimum supported Android version is Android 8.0 (API 26).

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

See [analytics and research](docs/ANALYTICS_AND_RESEARCH.md) for return, dividend,
fundamental-confidence and cache-only screening rules.

See [reports, backup and local security](docs/REPORTS_BACKUP_SECURITY.md) for export,
restore validation and device-protection details, and [MVP status](docs/MVP_STATUS.md)
for the plan checklist.

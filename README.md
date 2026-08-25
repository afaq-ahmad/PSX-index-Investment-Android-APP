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

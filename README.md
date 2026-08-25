# PSX Wealth

A local-first Android portfolio ledger and cash-first rebalancing companion for
Pakistan Stock Exchange investors. The application is decision support only: it
does not place broker orders and does not require an account or a server.

## Current foundation

- Material 3 Compose shell with Home, Portfolio, Research, Rebalance and More.
- Room entities for portfolios, transactions, cached quotes and dated index
  constituent snapshots.
- Transaction-derived cash, holdings, average cost, realized gains and dividends.
- A provider boundary that keeps PSX website parsing separate from calculations.
- A no-selling rebalance engine that spends new SIP cash on allocation deficits.
- Unit coverage for the portfolio and rebalance calculation engines.

Index and quote fetchers are intentionally a subsequent integration: the manual
ledger and deterministic calculation layer come first, so a website change can
never break the owner's source-of-truth transactions.

## Build

Install Android SDK 35, set `ANDROID_HOME` (or create `local.properties`), then:

```bash
gradle test
gradle assembleDebug
```

The minimum supported Android version is Android 8.0 (API 26).

## Data principles

Transactions are the source of truth. Successful market responses are cached
locally, while failed refreshes must retain the last valid snapshot. Index rows
use `(indexCode, symbol, snapshotDate)` as their key so historical weights are
preserved rather than overwritten.

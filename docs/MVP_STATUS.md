# MVP completion status

The developer-plan MVP checklist is implemented:

- [x] Create and select local portfolios; archive with confirmation.
- [x] Add, edit and delete deposits, withdrawals, buys, sells and dividends.
- [x] Derive cash, quantity, weighted-average cost, value and realized/unrealized P/L.
- [x] Support fees, tax, bonus shares, rights shares and splits without fake contributions.
- [x] Store and manually override labelled local prices.
- [x] Explicitly refresh portfolio prices through an isolated provider boundary.
- [x] Fetch, validate and preserve dated KMI30, KSE100 and KMIALLSHR snapshots.
- [x] Compare portfolio/security/sector allocations with a selected index.
- [x] Configure custom, full-index, selected-index, equal-weight and cash targets.
- [x] Add SIP cash and calculate cash-only or explicit full rebalance plans.
- [x] Preview and save plans without changing the ledger.
- [x] Convert only confirmed actual executions into atomic ledger transactions.
- [x] Show wealth decomposition, XIRR, dividends and long-term local charts.
- [x] Download index-level history on demand and simulate contribution-matched
  portfolio-versus-benchmark value, XIRR and chart periods.
- [x] Operate from Room/DataStore caches when offline and show provenance/freshness.
- [x] Export an essential or full versioned backup through Android storage.
- [x] Validate, preview and transactionally restore a backup.

The plan's explicitly later or optional work—PDF reports, automatic corporate-action
feeds, richer index-level benchmark ingestion and advanced sector datasets—remains
non-blocking. Missing source data is shown as unavailable rather than fabricated.

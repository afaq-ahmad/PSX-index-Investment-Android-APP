# Accounting and local storage contract

The transaction ledger is the only source of truth for owned quantity and cash.
Latest quotes, historical prices, index memberships and research metrics are
replaceable caches; refreshing them never edits a transaction.

## Weighted-average cost convention

- Buy fees and taxes are added to acquisition cost.
- Sell fees and taxes reduce disposal proceeds.
- A partial sale releases `average cost × sold quantity` from remaining cost.
- Bonus shares and splits change quantity but preserve total acquisition cost.
- Dividends increase cash and income; they are not external contributions.
- Deposits and withdrawals are the only external contribution flows.

All calculation-facing values use `BigDecimal`. Room currently persists numeric
input as SQLite `REAL` for compatibility with database version 1, and converts at
the repository boundary. A future lossless decimal migration can change the
storage representation without changing the pure domain engines.

## Database safety

Database version 2 migrates the original four tables in place and adds normalized
security, price, index snapshot, allocation, watchlist, rebalance, fundamental and
diagnostic tables. Production initialization does not use destructive migration.

Unknown prices remain null. They are never changed to zero for valuation.

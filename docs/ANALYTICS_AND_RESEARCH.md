# Analytics and research

## Performance

Absolute return is total profit divided by the magnitude of net contributions.
Money-weighted return uses XIRR with only deposits and withdrawals as external
flows; buys and sells are internal portfolio movements. Current portfolio value
is the terminal positive flow. The solver returns unavailable when the cash-flow
signs or dates cannot support a meaningful annualized result.

Wealth history replays the ledger on local transaction and price dates. It carries
the most recent prior cached close forward but omits a date if any then-held
security has no known price. Missing prices are never converted to zero.

A benchmark result is intentionally unavailable until local index-level history
covers every external-flow date. The domain engine can simulate dated deposits
and withdrawals as index units when such levels exist; the UI does not show a
misleading simple-return comparison in their absence.

## Dividends

Dividend income uses net cash (`cashAmount`, or gross less tax and fees) and is
reported by year and security, for the current calendar year, and for the trailing
twelve months. A dividend raises cash. Reinvestment requires an independent buy
transaction.

## Fundamentals and scores

Fundamentals are dated metric observations, not mutable columns on a security.
Manual observations work offline; explicit stock refreshes can record PSX company
snapshot metrics with source and retrieval time.

The score is explanatory decision support, not a trade signal. Its generic weights
are valuation 25%, quality 20%, balance sheet 20%, growth 15%, dividend 10%, and
stability 10%. Bank, fertilizer, cement, and exploration/production profiles alter
the component weights without inventing unavailable sector metrics. Missing
components are excluded, available weights are renormalized, and confidence equals
the configured weight backed by data. Every scored component lists its evidence.

## Screening and watchlists

The screener filters and sorts securities, quotes, index memberships, daily closes,
and fundamentals already stored in Room. It never starts a network request while
scrolling. Multiple watchlists and notes remain fully local. Network activity on a
stock page is limited to the explicit Refresh action; failure preserves cached data.

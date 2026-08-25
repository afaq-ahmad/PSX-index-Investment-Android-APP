# Portfolio and rebalancing workflow

## Ledger ownership

The transaction ledger is the source of truth. Holding quantity, cash, remaining
cost, average cost, realized profit, unrealized profit, dividends, fees and taxes
are recalculated from dated entries. The UI intentionally has no direct quantity
editor. Editing or deleting a transaction recalculates every dependent value.

Market prices are separate observations. A missing price is displayed as missing;
it is never silently converted to zero. Manual prices are labelled `Manual` and
can keep the offline workflow usable when a provider is unavailable.

## Targets

Targets can be saved as:

- custom percentages;
- all constituents in proportion to the latest cached index weights;
- selected index constituents with their weights renormalized; or
- an equal-weight selection.

An optional cash target reduces the stock allocation. Custom stock percentages
must total exactly `100% - cash target`, within a small decimal tolerance. Index
and equal-weight modes calculate normalized decimal fractions with `DECIMAL128`.

## Plans and executions

Cash-only rebalancing is the safe default and never proposes a sale. Full
rebalancing is an explicit switch. Both modes support new cash, a cash reserve,
whole shares and a minimum trade value. Every result exposes drift before/after,
estimated cash after, suggested trades and warnings for missing prices.

Saving a plan stores a snapshot of suggestions but does not change the portfolio.
To execute it, the owner enters actual quantity, price, fees, tax and date from
their contract notes. A final confirmation validates the complete batch against
owned quantities and available cash, inserts transactions atomically, and marks
the plan executed. Cancelled or already-executed plans cannot be replayed.

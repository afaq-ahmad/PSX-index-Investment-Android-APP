# Index Investing Workflow

The **Index Plan** tab is the primary workflow for a user who wants to follow
KMI-30, KSE-100, or PSX-KMI All Share without placing broker orders from the app.

## Intended flow

1. Create or select a local portfolio.
2. Open **Index Plan** and choose `KMI30`, `KSE100`, or `KMIALLSHR`.
3. Enter the **new funds to invest**. Enter `0` to rebalance only the current
   portfolio.
4. Select **Load / refresh**. A valid dated constituent snapshot is downloaded
   from the configured provider and saved locally.
5. Review every stock's published **Default index percentage**. This value is
   shown even when a usable price is unavailable.
6. Save the selected index percentages as the portfolio targets.
7. Review the calculated whole-share status:
   - **BUY** (green): current shares are below the rounded index target.
   - **SELL** (red): current shares are above the rounded index target, or the
     holding is not part of the selected index.
   - **MATCHED**: current shares already equal the rounded target.
   - **PRICE NEEDED**: the index weight is known but shares cannot be calculated
     without a positive price.
8. Replace the prefilled share gap with the quantity actually executed and open
   the ledger form. Confirm date, price, fees, and tax before saving.

The **Record these funds as a cash deposit** action also opens a prefilled ledger
form. A calculation never changes cash or holdings on its own.

## Allocation mathematics

The target capital is:

```text
current locally valued portfolio + new funds
```

Published weights are retained for display. For calculations they are normalized
to 100% so a source total such as 99.99% does not create a hidden allocation gap:

```text
normalized weight = published stock weight / sum of published index weights
target value       = target capital × normalized weight
target shares      = floor(target value / usable market price)
share gap          = target shares − shares derived from the ledger
```

The difference between target capital and the value of all rounded target shares
is shown as **whole-share rounding cash**. The app never suggests fractional PSX
shares and never labels an unknown price as zero.

## Current shares and cost basis

Current quantities come only from the transaction ledger. The Index Plan does not
offer a destructive "set holding" shortcut because a share count without the
actual acquisition price would corrupt cash, cost basis, and return calculations.
Instead, the recommended share count pre-fills a normal buy or sell entry.

For an existing portfolio being entered for the first time:

1. Record the real cash contributions.
2. Record each actual historical or opening buy with its quantity and price.
3. Add a labelled manual price if an online price is unavailable.
4. Return to Index Plan to compare the reconstructed portfolio with the index.

## Data and failure behaviour

- The most recent valid snapshot remains available offline.
- A failed refresh never deletes the prior snapshot.
- Index-page prices are cached as quotes and may be superseded by a newer quote.
- Provider source and snapshot date are visible above the constituent list.
- Missing or invalid weights prevent target saving instead of producing a false
  allocation.
- Missing prices retain the index percentage but suppress the share calculation.

## Decision-support boundary

Green and red are allocation signals, not investment advice and not broker orders.
Users must consider fees, taxes, liquidity, market lots, price movement, and their
own constraints. The advanced rebalance screen remains available for cash reserve,
minimum-trade, no-sell, and saved-plan workflows.

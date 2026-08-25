# Performance analytics methodology

The analytics are calculated locally from the transaction ledger and cached market
data. They are designed for personal portfolio review, not as audited broker, tax,
or investment-advice statements.

## Current value and profit

For every holding with a usable price:

```text
market value = ledger quantity × latest usable price
portfolio value = cash + sum of holding market values
cumulative P/L = portfolio value − net external contributions
```

Deposits increase net external contributions and withdrawals reduce them. Buys and
sells move value between cash and securities and are not external flows. Dividends,
fees, and taxes remain part of portfolio performance.

If a held security has no usable price, calculations requiring a complete valuation
remain unavailable rather than treating the security as worth zero.

## Daily and cumulative P/L

For consecutive complete valuation observations:

```text
daily P/L = ending value − prior value − change in net contributions
cumulative P/L = value on date − net contributions on date
```

"Daily" means each available complete local valuation date. If the phone has no
cached close for a day, the app does not fabricate a zero-return observation. The
next bar therefore represents the change since the prior complete observation.

## Time-weighted return (TWR)

For each sub-period, an external flow is treated as occurring immediately before
the ending valuation:

```text
sub-period growth = (ending value − external flow) / starting value
TWR = product of all sub-period growth factors − 1
```

Chain-linking isolates the investment result from the size and timing of deposits
and withdrawals. A non-positive starting value or insufficient complete valuation
history makes TWR unavailable.

The performance screen reports an overall TWR and periodic 1M, 3M, YTD, 1Y, and
MAX TWR. The dates shown beside each row are the actual complete valuation dates
used; they may be narrower than the requested window when older prices are missing.

## Money-weighted return (MWR and XIRR)

The headline MWR is annualized XIRR. From the investor's perspective:

- deposits are negative cash flows;
- withdrawals are positive cash flows;
- current portfolio value is the positive terminal cash flow;
- buys and sells are internal and excluded.

Periodic MWR includes the opening portfolio value, external flows within the
period, and the ending value. Its solved annualized XIRR is converted to the exact
non-annualized period length so it can be compared with periodic TWR:

```text
periodic MWR = (1 + annualized XIRR)^(days / 365) − 1
```

MWR reflects the owner's cash-flow timing; TWR reflects manager/security performance
independently of that timing. Neither should be substituted for the other.

## Contribution-matched index comparison

KMI 30, KSE 100, and KMI All Share comparisons do not apply a simple index return
to the final contribution total. Each real deposit or withdrawal buys or removes
notional index units at the most recent available prior close:

```text
units added = external flow / applicable index close
benchmark value = accumulated units × terminal index close
```

The portfolio and every benchmark therefore receive the same dated external flows.
A non-trading-day flow uses the most recent prior close. The app does not interpolate
missing index levels and does not invent zero values.

## Allocation and gain charts

- Portfolio allocation uses positive valued holdings plus positive cash, so its
  percentages reconcile to the displayed current portfolio composition.
- Sector allocation uses invested stock market value and groups securities by the
  latest locally stored sector. Missing metadata is shown as `Unknown`.
- The doughnut chart groups minor slices visually as `Other`, while the exact rows
  below it retain every stock or sector percentage.
- Gain by holding combines the locally calculated unrealized P/L, realized P/L,
  and dividends for each current holding. Green and red communicate positive and
  negative results only.

## Accuracy checklist

Before relying on a comparison, confirm that:

1. all deposits and withdrawals have their real dates and amounts;
2. buys, sells, dividends, fees, taxes, and corporate actions are complete;
3. every held security has a recent, correctly sourced price;
4. enough daily price and index history has been explicitly refreshed;
5. any manual price is labelled and dated correctly.


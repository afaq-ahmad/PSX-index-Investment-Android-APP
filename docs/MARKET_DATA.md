# Market data, refresh and offline behaviour

The app talks directly to public PSX Data Portal pages only when the owner asks
for a refresh, or when the optional once-daily worker is enabled. There is no
server, account, minute-level polling or redistribution feature.

## Provider boundary

Network providers are replaceable and capability-aware. `PsxMarketDataProvider`
is the primary provider. An optional `ScsMarketDataProvider` supplies quote and
company-snapshot fallback using the public SCS company snapshot page (including
the `ContentPlaceHolder1_lbl_price` field commonly used by spreadsheet imports).
Both providers have separate parsers and saved offline fixtures. They normalize
responses into domain models before a repository validates and writes them to Room.

Current endpoints:

- `/indices/{code}` for KMI30, KSE100 and KMIALLSHR constituents;
- `/company/{symbol}` for a latest quote and stock snapshot;
- `/timeseries/eod/{symbol}` for split-adjusted end-of-day history as described by
  the PSX page itself.
- `scstrade.com/stockscreening/SS_CompanySnapShot.aspx?symbol={SYMBOL}` for an
  optional secondary quote/company snapshot.

## User-controlled provider policy

More → Settings allows the owner to:

- disable all online market access while retaining manual/cached operation;
- enable or disable PSX and SCS independently;
- choose PSX-first or SCS-first quote order;
- include/exclude held stocks, watchlists and each supported index from Refresh all.

Index constituents and PSX price history remain PSX-only capabilities. Diagnostics
record each provider attempt separately, so a PSX failure followed by a successful
SCS fallback is visible and accurately attributed.

The owner should review and comply with each source's terms for personal,
non-commercial use. Refresh is intentionally conservative and the product never
resells or publishes market data.

## Cache safety

The write sequence is fetch → parse → validate → normalize → atomic Room write.
Expected member ranges, duplicate symbols, price validity and weight totals are
checked before a dated index snapshot is accepted. An invalid or empty response
updates diagnostics but does not delete the last good quote or snapshot.

Latest quotes are also accumulated as daily closes, so the phone gradually builds
usable local history. Manual quotes remain available through the ledger repository.

## Partial failure

`RefreshCoordinator` returns one result per index and symbol. For example, KMI30
may update while KSE100 and one held quote remain on their previous cached values.
The UI can show this as a concise summary and expose details under diagnostics.

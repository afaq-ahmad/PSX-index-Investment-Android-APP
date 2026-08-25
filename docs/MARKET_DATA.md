# Market data, refresh and offline behaviour

The app talks directly to public PSX Data Portal pages only when the owner asks
for a refresh, or when the optional once-daily worker is enabled. There is no
server, account, minute-level polling or redistribution feature.

## Provider boundary

`PsxMarketDataProvider` is the only network-aware implementation. Its parsers are
separate classes with saved offline fixtures. They normalize responses into domain
models before a repository validates and writes them to Room.

Current endpoints:

- `/indices/{code}` for KMI30, KSE100 and KMIALLSHR constituents;
- `/company/{symbol}` for a latest quote and stock snapshot;
- `/timeseries/eod/{symbol}` for split-adjusted end-of-day history as described by
  the PSX page itself.

The owner should review and comply with PSX terms for personal, non-commercial
use. Refresh is intentionally conservative and the product never resells or
publishes market data.

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

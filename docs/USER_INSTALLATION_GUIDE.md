# Install and start using PSX Wealth

This guide is for the owner receiving a trusted APK from the developer. The app is
private and local-first: portfolio transactions and calculations remain on the
phone. Internet access is used only when the owner explicitly refreshes supported
public market data or enables the optional daily refresh.

## Install the APK

1. Ask the developer for the signed `app-release.apk` and its version number.
2. Copy the APK to the Android phone by USB, a private local share, or another
   trusted transfer method.
3. Open the APK from the Files application.
4. If Android asks, temporarily allow that Files application to install unknown
   apps. Do not enable this permission for unrelated applications.
5. Select **Install**, then open **PSX Wealth** using the green-and-gold chart icon.
6. Disable the temporary "Install unknown apps" permission after installation.

Android 8.0 or newer is required.

## First setup

1. Create the local portfolio and give it a clear name.
2. Choose KMI 30, KSE 100, or KMI All Share as the starting benchmark.
3. Record real cash deposits in the ledger.
4. Record each actual buy with its date, quantity, price, fees, and tax. Holdings
   and cost basis are calculated from these entries; do not invent opening values.
5. Open **Index Plan**, enter new investment funds, choose the index, and select
   **Load / refresh** while online.
6. Review every published index percentage and the calculated whole-share gap:
   green means below target and red means above target. These are allocation gaps,
   not broker orders or investment recommendations.
7. Replace a prefilled quantity with the quantity actually executed before saving
   the buy or sell ledger entry.

## Use the analytics correctly

Open **Research → Performance** to see current value, total P/L, TWR, annualized
MWR/XIRR, periodic TWR and MWR, daily and cumulative P/L, portfolio and sector
allocation, gain by holding, and contribution-matched index comparisons.

Refresh benchmark history for KMI 30, KSE 100, and KMI All Share before expecting
all comparison lines. Missing prices and history remain unavailable instead of
being silently replaced with zero.

## Update without losing data

1. Make a full in-app backup before every update.
2. Install the newer signed APK over the existing application. Do not uninstall
   the old version first.
3. The update must be signed with the same developer key as the installed version.
4. After updating, verify the portfolio totals and analytics before deleting the
   APK or backup copy.

## Move to another phone

Create a full backup in the old installation, install the same signed application
on the new phone, preview the backup, and confirm the restore. Keep the backup until
the portfolio, transactions, targets, watchlists, and cached data have been checked.

## Troubleshooting

- **No index stocks appear:** connect to the internet and use **Load / refresh** in
  Index Plan. The last valid snapshot remains available offline afterward.
- **A stock says Price needed:** refresh quotes or enter a clearly labelled manual
  price; the index percentage remains visible.
- **TWR/MWR or P/L is unavailable:** add the real cash-flow history and cache enough
  complete closing-price observations for every held security.
- **Sector is Unknown:** refresh the relevant company details; the app never guesses
  a sector.
- **A new APK will not update the app:** it was probably signed with another key.
  Back up the old installation before taking any uninstall action.


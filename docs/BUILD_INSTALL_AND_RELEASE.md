# Build, package, install, and update PSX Wealth

PSX Wealth is a local Android application. There is no server, web deployment,
Firebase project, paid API, or cloud database to configure. In this project,
"deployment" means building a verified APK, signing the release APK, and installing
it on the owner's Android phone.

## 1. Required development tools

- A 64-bit Windows, Linux, or macOS computer.
- JDK 17.
- Android Studio with Android SDK Platform 35 and Build Tools 35.0.0, or the same
  SDK packages installed through `sdkmanager`.
- Gradle 8.11.1 available as the `gradle` command. The repository's CI uses this
  exact version.
- An Android 8.0 or newer phone (API 26+).

Set `ANDROID_HOME` to the Android SDK directory, or create an untracked
`local.properties` file in the repository root:

```properties
sdk.dir=C:\\Users\\YOUR_NAME\\AppData\\Local\\Android\\Sdk
```

On Linux or macOS, use the actual SDK path, for example:

```properties
sdk.dir=/home/YOUR_NAME/Android/Sdk
```

## 2. Open and verify the project

Clone the repository and open its root folder in Android Studio. Allow the first
Gradle sync to download the Android and Kotlin dependencies.

Before producing an APK, run the same verification used by GitHub Actions:

```bash
gradle --no-daemon testDebugUnitTest lintDebug assembleDebug
```

The debug APK is created at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Do not distribute a build if tests, lint, or APK assembly fail. The financial
calculation tests are part of the release gate.

## 3. Install a debug build on the owner's phone

### USB installation

1. On the phone, enable Developer options and USB debugging.
2. Connect the phone and approve its RSA authorization prompt.
3. Confirm that Android Debug Bridge can see it:

   ```bash
   adb devices
   ```

4. Install or update the debug build:

   ```bash
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

The `-r` option updates an existing installation without intentionally clearing its
local Room database. A debug build is suitable for private testing, not long-term
distribution.

### Manual APK installation

Transfer `app-debug.apk` to the phone, open it in the Files application, and allow
"Install unknown apps" only for that trusted file manager when Android asks. Turn
that permission off again after installation.

## 4. Create the private release signing key once

Android requires every later update to use the same signing key. Losing this key
means the existing app cannot be upgraded in place.

Create a private folder outside normal source control, then run:

```bash
keytool -genkeypair -v \
  -keystore release/psx-wealth-release.jks \
  -alias psx-wealth \
  -keyalg RSA -keysize 4096 -validity 10000
```

Copy `keystore.properties.example` to `keystore.properties` and replace all sample
values. Paths in that file are relative to the repository root unless an absolute
path is supplied.

Never commit or share any of the following:

- the `.jks` or `.keystore` file;
- `keystore.properties`;
- store or key passwords.

The repository ignores these files by default. Keep at least two encrypted copies
of the signing key in secure, separate locations.

## 5. Build and verify a signed release APK

With a complete `keystore.properties` file, run:

```bash
gradle --no-daemon clean testDebugUnitTest lintDebug assembleRelease
```

The signed APK is created at:

```text
app/build/outputs/apk/release/app-release.apk
```

Verify its signature before installation:

```bash
apksigner verify --verbose --print-certs app/build/outputs/apk/release/app-release.apk
```

Install it with:

```bash
adb install -r app/build/outputs/apk/release/app-release.apk
```

If Android reports an incompatible signature, do not uninstall the existing app
until its data has been exported. The old and new APKs were signed by different
keys and cannot update one another.

## 6. Safe update procedure

1. In the installed app, create a full local backup and copy it somewhere safe.
2. Increase `versionCode` and `versionName` in `app/build.gradle.kts`.
3. Run the full test, lint, and release build command.
4. Verify the APK signature and confirm it uses the permanent release certificate.
5. Install with `adb install -r` or open the new APK on the phone.
6. Open the existing portfolio and verify holdings, cash, latest prices, targets,
   index snapshots, and analytics before deleting the old APK file.

Do not uninstall the app as part of a routine update. `allowBackup` is deliberately
disabled and uninstalling removes the on-device database unless the user has made
an export.

## 7. GitHub Actions release gate

`.github/workflows/android.yml` installs JDK 17, Android SDK 35, and Gradle 8.11.1,
then runs unit tests, Android lint, and debug assembly for pull requests and changes
to `main`. A green workflow verifies the source build; it does not contain or use
the owner's private signing key.

## 8. Common build problems

- **SDK location not found:** correct `ANDROID_HOME` or `local.properties`.
- **Android platform 35 missing:** install `platforms;android-35` and
  `build-tools;35.0.0` in SDK Manager.
- **Unsupported Java/Gradle combination:** select JDK 17 and Gradle 8.11.1.
- **Release APK is unsigned:** create `keystore.properties` from the provided
  example and ensure its key path and aliases are correct.
- **Install blocked:** confirm the phone is Android 8.0+, has enough free storage,
  and trusts the selected USB computer or file manager.
- **Update reports signature mismatch:** locate the original signing key; otherwise
  export the old app's data before uninstalling and reinstalling.


# GATAS Companion

This project is licensed under Apache License 2.0 with the Commons Clause license condition. It is source-available and does not grant commercial resale rights to the software itself.

GATAS Companion is the mobile companion app for the GATAS conspicuity device. It is a Kotlin Multiplatform app that runs on Android and iOS.

The iOS app requires iOS 18 or newer and includes an Apple Watch remote control
for watchOS 10 or newer. The paired iPhone handles the GATAS connection and traffic
forwarding. See [Apple Watch setup, architecture and acceptance checks](docs/WATCH.md).

## Reality check for cross-platform signing

- Android: prebuild on one machine, then sign + deploy from macOS or Windows is supported.
- iOS/iPhone: signing and deployment require macOS + Xcode + Apple certificates. Windows cannot do native iPhone signing/deploy.

## iOS setup (macOS only)

1. Ensure you have Apple Developer signing configured in Xcode.
2. Copy and edit local signing config:

```bash
cp iosApp/Configuration/Config.local.xcconfig.example iosApp/Configuration/Config.local.xcconfig
```

3. Set `TEAM_ID` in `iosApp/Configuration/Config.local.xcconfig`.
4. Install iPhone deploy helper:

```bash
brew install ios-deploy
```

5. Open `iosApp/GatasCompanion.xcodeproj` in Xcode. Select the **GatasCompanion**
   scheme for the iPhone app or **GatasWatchApp** for the paired Watch app.

## Prebuild artifacts (commit to git if you want)

This creates commit-friendly artifacts in `./prebuilt`:

- `prebuilt/android/composeApp-release-unsigned.apk`
- `prebuilt/ios/iosApp-unsigned.xcarchive` (macOS only)

Command:

```bash
./prebuildMobile
```

Optional:

```bash
./prebuildMobile --android-only
./prebuildMobile --ios-only
```

## Sign + deploy Android from prebuilt APK

### macOS

```bash
ANDROID_KEYSTORE=/abs/path/release.jks \
ANDROID_KEY_ALIAS=your_alias \
ANDROID_KEYSTORE_PASSWORD=your_keystore_pass \
ANDROID_KEY_PASSWORD=your_key_pass \
./deployAndroid
```

### Windows (PowerShell)

```powershell
$env:ANDROID_KEYSTORE="C:\path\release.jks"
$env:ANDROID_KEY_ALIAS="your_alias"
$env:ANDROID_KEYSTORE_PASSWORD="your_keystore_pass"
$env:ANDROID_KEY_PASSWORD="your_key_pass"
.\deployAndroid.ps1
```

## Sign + deploy iPhone from prebuilt archive (macOS only)

1. Prebuild unsigned archive (once or in CI):

```bash
./prebuildMobile --ios-only
```

2. Sign + export + install on iPhone:

```bash
./deployIos --archive ./prebuilt/ios/iosApp-unsigned.xcarchive
```

## Direct iPhone build/sign/deploy (no prebuild)

```bash
./deployIos
```

Build/sign only (skip install):

```bash
./deployIos --no-install
```

# GATAS Companion

This project is licensed under Apache License 2.0 with the Commons Clause license condition. It is source-available and does not grant commercial resale rights to the software itself.

GATAS Companion is the mobile companion app for the GATAS conspicuity device. It is a Kotlin Multiplatform app that runs on Android and iOS.

The [phone barometer feature](doc/phone-pressure.md) sends ambient pressure to
GATAS over Bluetooth as an alternative to an onboard BMP280. On iOS, enable
**Use phone barometer** in the connected device's **GATAS Settings**. The choice
is saved separately for each Bluetooth device. The feature and its diagnostic
counters are available in Debug and Release builds on both platforms. Android
hardware validation remains open for testers.

The iOS app requires iOS 18 or newer and includes an Apple Watch remote control
for watchOS 10 or newer. The paired iPhone handles the GATAS connection and traffic
forwarding. See [Apple Watch setup, architecture and acceptance checks](docs/WATCH.md).

## Reality check for cross-platform signing

- Android: prebuild on one machine, then sign + deploy from macOS or Windows is supported.
- iOS/iPhone: signing and deployment require macOS + Xcode + Apple certificates. Windows cannot do native iPhone signing/deploy.

## iOS setup (macOS only)

Use Xcode to build, sign and install the app on your own iPhone:

1. Install Xcode, launch it once to complete its setup, and sign in with your
   Apple Account in **Xcode > Settings** under the accounts pane. A free Apple
   Account provides a **Personal Team** for testing on your own devices; a paid
   Apple Developer Program membership is not required for this local workflow.
2. From the repository root, create your local signing configuration:

   ```bash
   cp iosApp/Configuration/Config.local.xcconfig.example iosApp/Configuration/Config.local.xcconfig
   ```

3. Set **both** `TEAM_ID` and `BUNDLE_ID` in
   `iosApp/Configuration/Config.local.xcconfig`:

   ```text
   TEAM_ID=YOUR_TEAM_ID_HERE
   BUNDLE_ID=com.yourname.gatascompanion
   ```

   Use your signing team's ID from your Xcode account details and replace
   `yourname` with your own unique value. Bundle IDs registered with Apple must
   be unique: an ID already owned by another team cannot be registered for your
   team. For a personal build, replace the repository's default `BUNDLE_ID` with
   your own. The Watch app and Live Activity extension automatically derive
   their IDs from this value.

   The shared configuration in `iosApp/Configuration/Config.xcconfig` includes
   `Config.local.xcconfig`, so Xcode reads your local values automatically.
   Make shared configuration changes in `iosApp/Configuration/Config.xcconfig`;
   keep personal `TEAM_ID` and `BUNDLE_ID` overrides in
   `iosApp/Configuration/Config.local.xcconfig`, which is ignored by Git.
4. Open `iosApp/GatasCompanion.xcodeproj` in Xcode and select the
   **GatasCompanion** scheme. Automatic signing is already configured for the
   iPhone app, Watch app and Live Activity extension. **Do not edit the Team or
   Bundle Identifier fields in Xcode**, including under **Signing & Capabilities**
   or **Build Settings**. Editing these fields writes overrides into the tracked
   `iosApp/GatasCompanion.xcodeproj/project.pbxproj` file and can put your private
   IDs into Git. Change the configuration files described above instead.
5. Connect your iPhone to the Mac with a cable, unlock it and accept **Trust This
   Computer** if prompted. Select the connected iPhone as the run destination
   in Xcode and wait for device preparation to finish.
6. On the iPhone, enable **Settings > Privacy & Security > Developer Mode**.
   Restart when prompted, then confirm that you want to enable Developer Mode
   after the restart. If the option is missing, let Xcode pair with the connected
   iPhone first.
7. Choose **Product > Run** (**Command-R**) in Xcode. Accept any device
   registration prompts. Xcode builds, signs, installs and launches the app on
   the iPhone.

To install the Watch app, first install the iPhone app as described above. Then
use the **Watch** app on the paired iPhone, just as you would for other Watch
apps: open **My Watch > Apps** (or scroll down directly on older iOS versions),
find **GATAS** under **Available Apps** and tap **Install**. If automatic
app installation is enabled, the Watch app may already be installed. See
[Apple's Watch app installation instructions](https://support.apple.com/109023)
and [Apple Watch setup](docs/WATCH.md).

**Free signing expires after 7 days.** Without a paid Apple Developer Program
membership, Personal Team provisioning profiles are valid for seven days from
issuance. After they expire, the installed app can no longer launch. Reconnect
the iPhone and use **Product > Run** again to rebuild, sign and reinstall it.
See [Apple's Personal Team limits](https://developer.apple.com/help/account/basics/about-your-developer-account)
and [Developer Mode instructions](https://developer.apple.com/documentation/xcode/enabling-developer-mode-on-a-device).

## TestFlight builds

Archive and upload through Xcode Organizer. Let Xcode manage the build number
during distribution, and include the source commit and English change summary
in TestFlight's "What to Test" information. See the
[development and TestFlight workflow](docs/DEVELOPMENT.md) for the steps.

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

The command-line workflows below use `ios-deploy` for device installation. They
are optional; the Xcode workflow above does not require this helper. If you
choose to use the scripts, install it separately:

```bash
brew install ios-deploy
```

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

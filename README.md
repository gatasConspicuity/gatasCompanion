# GATAS Companion

This project is licensed under Apache License 2.0 with the Commons Clause license
condition. It is source-available and does not grant commercial resale rights to
the software itself. See [LICENSE](LICENSE) for the full terms.

GATAS Companion is the mobile companion app for the GATAS conspicuity device.
It is a Kotlin Multiplatform app that runs on Android and iOS.

The [phone barometer feature](doc/phone-pressure.md) sends ambient pressure to
GATAS over Bluetooth as an alternative to an onboard BMP280. On iOS, enable
**Phone barometer** in the connected device's **GATAS Settings**. The choice
is saved separately for each Bluetooth device. The feature and its diagnostic
counters are available in Debug and Release builds on both platforms. Android
hardware validation remains open for testers.

The Android app requires Android 7.0 (API 24) or newer. The iOS app requires
iOS 18 or newer and includes an Apple Watch remote control
for watchOS 10 or newer. The paired iPhone handles the GATAS connection and traffic
forwarding. See [Apple Watch setup, architecture and acceptance checks](docs/WATCH.md).

## App navigation

Home shows saved GATAS devices. Use **Find GATAS** to discover another device,
or select a saved device to start the bridge. **Settings** contains the GDL90
bridge preference. **About** includes project and license links and the version
and build number of the installed app.

The connected overview shows the device identity and a compact **Connection
Info** card. Tap that card to open the detailed connection counters and live
measurements; **Back** returns to the overview without stopping the bridge.
Green markers indicate active connections or forwarding, red markers indicate
waiting or errors, and gray markers identify disabled optional features.
The bottom action bar stays available throughout the connected area. Use
**Settings** (gear) for **GATAS Settings**, **Actions** (tools) for supported
device commands, and the red **STOP** sign to end the bridge and return Home.

## Build prerequisites

Clone or download this repository before opening it in your IDE. The shared
Kotlin code is built with the included Gradle wrapper on both platforms; no
separate Gradle installation is needed. Use JDK 17 or 21 for Gradle. Android
Studio can use its bundled JDK if it meets this requirement. For Xcode, install
a system JDK so its Gradle build phase can find Java. See the
[Gradle Java compatibility matrix](https://docs.gradle.org/9.3.0/userguide/compatibility.html#java_runtime).

## Android setup (Windows, Linux and macOS)

For ready-to-install APKs, see [GitHub Releases](https://github.com/gatasConspicuity/gatasCompanion/releases).
The [latest stable Android release](https://github.com/gatasConspicuity/gatasCompanion/releases/latest)
is the recommended download when available. Releases labelled **Pre-release**
are test versions. If no stable release has been published, use a prerelease
only if you want to participate in testing. The current availability table in
the release notes separates Android/iOS and stable/test builds.

Download the `.apk` attachment on your Android device, open it and allow
installation from that browser/file manager when Android asks. Future APKs
signed by the same project key install as updates. A locally signed debug
installation may need to be removed first; uninstalling deletes its app data.
Android prevents installation of a lower build number over a newer test build.

To build the app yourself, follow these steps:

Use Android Studio to build and install the app on an Android device:

1. Install [Android Studio](https://developer.android.com/studio/install) for
   your operating system and complete its setup wizard, including the Android
   SDK components.
2. Choose **Open** on the welcome screen or **File > Open**, and select the
   repository root containing `settings.gradle.kts`. Let Android Studio import
   the Gradle project and download dependencies. Keep the Gradle and plugin
   versions defined in the repository when offered upgrade suggestions.
3. Check the **Gradle JDK** under **Settings > Build, Execution, Deployment >
   Build Tools > Gradle**. Open Settings from the **File** menu on Windows/Linux
   or the **Android Studio** menu on macOS, and select JDK 17 or 21. In
   **Tools > SDK Manager**, install **Android SDK Platform 36**, the required
   SDK Build-Tools and Android SDK Platform-Tools. Accept SDK licenses when
   prompted, then sync the project and wait for the sync to complete.
4. On the Android device, enable **Developer options** and **USB debugging**.
   Connect it with a USB data cable, unlock it and accept the **Allow USB
   debugging** prompt for your computer.
5. Select the **composeApp** Android run configuration and the connected device
   in the toolbar. Use the **debug** build variant. If the run configuration is
   missing, create an **Android App** configuration for the **composeApp** module
   under **Run > Edit Configurations**.
6. Click **Run**. Android Studio builds, signs, installs and launches the debug
   app. Enable Bluetooth and grant the permissions requested by the app when
   connecting to GATAS.

On Windows, your device may require an OEM USB driver. On Linux, check device
access permissions and `udev` rules if it is not detected. Follow
[Google's hardware-device setup instructions](https://developer.android.com/studio/run/device)
for your operating system.

An emulator is useful for UI checks. Use a physical Android device to test the
Bluetooth connection to a real GATAS. See
[Android Studio's build and run instructions](https://developer.android.com/studio/run)
for more details.

## iOS setup (macOS only)

Use Xcode to build, sign and install the app on your own iPhone:

1. Install Xcode, launch it once to complete its setup, and sign in with your
   Apple Account in **Xcode > Settings** under the accounts pane. A free Apple
   Account provides a **Personal Team** for testing on your own devices; a paid
   Apple Developer Program membership is not required for this local workflow.
2. If `iosApp/Configuration/Config.local.xcconfig` does not already exist,
   create it from the repository root:

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

### iOS from Android Studio (not verified for this project)

On macOS, the Kotlin Multiplatform plugin supports building and running iOS
apps from Android Studio, using Xcode in the background. Xcode, signing and
device pairing still need to be configured as described above; keep personal
IDs in `iosApp/Configuration/Config.local.xcconfig`.

This is a theoretical alternative for GATAS Companion and **has not yet been
verified with this project**. It still requires macOS; Android Studio on Windows
or Linux cannot perform local iOS builds or iPhone deployment. See the
[Kotlin Multiplatform build and run documentation](https://kotlinlang.org/docs/multiplatform/build-and-run-kmp.html).

## First connection

After installing the app on your phone or tablet:

1. Power on GATAS, keep it nearby and enable Bluetooth on your phone or tablet.
2. Open GATAS Companion and tap **Find GATAS**. Allow the Bluetooth/nearby-device
   and location permissions requested by your platform.
3. Tap your GATAS in the search results to save it. The app returns to the
   device list; tap the saved device to start the bridge.
4. Wait for **Connected via Bluetooth** on the connection screen. This confirms
   the Bluetooth link; the UDP relay and GDL90 statuses are shown separately.

Use **Stop bridge** on the connection screen to stop the bridge.

## Feature requirements

- **Phone barometer:** Your phone or tablet needs a barometer, and GATAS must
  support phone-pressure input (OpenAce 3.5.1 or compatible COBS type-11 support).
  On iOS, allow **Motion & Fitness** access when enabling the feature. See
  [Phone barometer](doc/phone-pressure.md) for setup and limitations.
- **Device actions:** Save configuration, Restart GATAS and firmware update mode
  require matching firmware support. Unsupported actions remain disabled. See
  [GATAS device actions](docs/DEVICE_ACTIONS.md).

## TestFlight builds

You can request an invitation to the GATAS Companion TestFlight beta via
[Discord](https://discord.com/invite/J6mXTcWndS). This lets you install our test
builds without building or signing the app yourself.

To upload your own builds to TestFlight, you need a team enrolled in the Apple
Developer Program and an app record in App Store Connect that your account can
access. See
[Apple's TestFlight setup](https://developer.apple.com/testflight/).

Before a new distributed candidate, prepare the shared version/build in
`Version.xcconfig` after checking both distribution services, GitHub releases
and numbers reserved by other branches. Android and all Apple targets use
the same counter, which continues across product versions. See the
[release setup and operating instructions](docs/RELEASES.md) for preparation,
project signing-key custody, GitHub workflows and stable promotion.
Commit the intended source and build number, then choose **Product > Archive** with the
**GatasCompanion** scheme and an iOS device destination selected. In Xcode
Organizer, choose **Distribute App** > **App Store Connect** > **Upload**.
For detailed steps and this project's
internal release conventions, see the
[development and TestFlight workflow](docs/DEVELOPMENT.md).

## Optional iOS command-line build (macOS only)

After completing the iOS signing setup above, you can build and sign the
development app without installing it. The helper also requires `ripgrep`
(`rg`) to read the local signing configuration:

```bash
./deployIos --no-install
```

Use Xcode to install the app on your iPhone. The script's installation path uses
[`ios-deploy`](https://github.com/ios-control/ios-deploy), which is designed for
iOS versions before 17; it is not the supported installation method for this
app's iOS 18-or-newer requirement.

## Troubleshooting and questions

- **GATAS is not found:** Check that GATAS is powered on, Bluetooth is enabled
  and the app has the requested permissions. If the search has finished, reopen
  **Find GATAS** to search again.
- **iOS signing fails or has expired:** Check your `TEAM_ID` and
  unique `BUNDLE_ID` in `iosApp/Configuration/Config.local.xcconfig`. For expired
  Personal Team signing, rebuild and reinstall through Xcode as described above.
- **Bluetooth is connected but traffic is missing:** Check the phone's Internet
  connection, the UDP relay status and whether GDL90 forwarding is enabled.
  For Companion's GDL90 forwarding, run the EFB on the same phone or tablet. The
  [debugging guide](docs/DEBUGGING.md) explains the traffic path and diagnostics.

For setup questions, connection problems or feedback, contact us on Discord.
Include your platform and OS version, app version/build, GATAS firmware version
and the error message or connection status you see.

[<img src="docs/images/Discord-Logo-Blurple.png" alt="Join the GATAS Discord community" height="45">](https://discord.com/invite/J6mXTcWndS)

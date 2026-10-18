# Apple Watch remote control

The Watch app requires watchOS 10 or newer and the paired iPhone app requires
iOS 18 or newer. The iPhone owns the GATAS BLE connection, internet relay and
local GDL90 forwarding. The Watch app does not connect directly to GATAS.

## Project entry points

- `CompanionController` is the shared app-scoped control plane. The Compose UI
  uses it directly; `WatchRemoteBridge` exposes JSON and a cancellable, sampled
  state subscription to Swift. All control-plane entry points run on Main.
- `PhoneWatchSession` starts from the application delegate, before any visible
  screen is needed. Kable restoration is configured before its central manager
  can be accessed. WCSession remains owned for the lifetime of the app process.
- `WatchSession` manages the Watch cache, foreground refresh and command replies.
  `WatchViews` contains dashboard, aircraft, device, settings and diagnostic views.
- `DeviceDiscovery` shares one native UUID-filtered scanner between independent
  phone and Watch leases. A Watch lease expires after 30 seconds; leaving the
  phone scan screen does not stop an active Watch lease.

Build/run the **GatasCompanion** scheme for the phone and **GatasWatchApp** for the paired
Watch. The phone embeds the Watch product. Signing and the shared build number
continue to come from `Configuration/Config.xcconfig`; private signing overrides
remain in the ignored local configuration.

## Wire contract and failure handling

WCSession dictionaries use `version: 1`. A request without `command` reads the
current `snapshot`. A control request adds a JSON-encoded command, and its reply
contains `reply` plus `snapshot`. The JSON schema is defined by Kotlin's
`RemoteProtocol.kt` and Swift's `WatchProtocol.swift`.

Commands have unique IDs and explicit desired values. A bounded process-local
cache suppresses duplicate delivery and rejects ID reuse with a different body.
Device-bound commands carry the observed active device ID; the phone validates
it against the service immediately before dispatch. Removing an active device,
selecting unavailable aircraft, and controlling unsupported Wi-Fi modes fail
explicitly. There is no background command queue or automatic remote-command retry. The existing
BLE service retains its bounded aircraft-change retry/reconnect behavior.
Commands expire after ten seconds; the phone rejects delayed delivery even if
the framework regains connectivity after the Watch has stopped waiting.

`accepted` means that an asynchronous operation was requested. Aircraft and
Wi-Fi changes are shown as confirmed only after GATAS reports the requested
value for the same device. A lost response can leave the result unknown; the UI
asks the user to refresh instead of repeating the action. Confirmation expires
after 45 seconds without claiming that the device rejected the operation.

The phone samples state changes at most once per second. Application Context
keeps only the latest snapshot. Foreground Watch refreshes every five seconds;
after 15 seconds without a live response, cached status cannot enable controls.
Transport connectivity and BLE/relay health are separate indicators. No permanent
background execution is implied by WatchConnectivity or the Live Activity.

## Live Activity when starting from the Watch

The Live Activity manager starts with the iPhone application delegate and stays
independent of visible SwiftUI views. Existing activities can be updated or ended
when the Watch controls the bridge in the background.

A new Live Activity cannot be started by an ordinary WatchConnectivity background
message while the iPhone is locked or another app is visible. The Watch shows
an instruction to open GatasCompanion on the iPhone; if the bridge is still
running, the activity starts as soon as the app becomes active. If the bridge
stops before that, no activity is created. Disabling Live Activities in iPhone
Settings is reported separately.

The foreground-required/error notice is a Handoff button. After tapping it,
keep that Watch screen open, unlock the iPhone, open its App Switcher and tap
the GatasCompanion Handoff banner. The Watch displays these steps after the tap.
The iPhone registers the shared activity type in `NSUserActivityTypes` and
handles it through SwiftUI's `onContinueUserActivity`. Continuing only requests
a Live Activity synchronization against current bridge state; it never restarts
a stopped bridge or replays a device command. The offer expires after two minutes;
tap again to offer a new continuation. Disabled Live Activities remain an
informational notice because opening the app cannot override that setting.

Handoff must be enabled on both devices, with the same Apple Account and
Bluetooth/Wi-Fi enabled. On iPhone, check Settings > General > AirPlay &
Continuity (or AirPlay & Handoff) > Handoff. For Watch, check the iPhone Watch
app > My Watch > General > Enable Handoff. Both app targets use the same signing
team. See [Apple's Handoff setup](https://support.apple.com/102426).

Apple permits background starts through a system-executed `LiveActivityIntent`
or an ActivityKit push-to-start notification. Calling an intent's `perform()`
method directly from a WCSession handler does not grant that privilege. Supporting
automatic starts with a locked iPhone would therefore need a separate supported
entry point or an APNs server integration; this remote does not provide one.
See [ActivityKit lifecycle](https://developer.apple.com/documentation/activitykit/activity)
and [Apple's background-start clarification](https://developer.apple.com/forums/thread/818467).

## Automated validation

```sh
./gradlew :composeApp:allTests :composeApp:assembleDebug :composeApp:lintDebug
swift test --package-path iosApp/WatchShared --scratch-path /tmp/gatas-watch-protocol-tests
xcodebuild -project iosApp/GatasCompanion.xcodeproj -scheme GatasCompanion -destination 'generic/platform=iOS Simulator' CODE_SIGNING_ALLOWED=NO build
xcodebuild -project iosApp/GatasCompanion.xcodeproj -scheme GatasWatchApp -destination 'generic/platform=watchOS Simulator' CODE_SIGNING_ALLOWED=NO build
```

The tests cover malformed/future commands, duplicate delivery, changed devices,
unsupported firmware capabilities, aircraft validation, cache freshness and
Swift decoding. Simulator builds and runs do not prove physical BLE/background
behavior or that an EFB consumes GDL90 packets.

## Physical acceptance checklist

Use a paired Watch/iPhone and real GATAS running firmware 3.5.1. Install the phone
app, grant Bluetooth access and complete any system prompts on the phone first.

- Start/stop a saved device from the Watch with the phone locked; check the
  actual connection and traffic forwarding on the phone/GATAS.
- Check Live Activity handling separately: an existing activity updates/ends in
  the background. Without one, the Watch explains why the iPhone app must be
  opened. Opening it while the bridge runs starts the activity; stopping first
  must not create one later. Repeat with Live Activities disabled in Settings.
- Tap that Watch notice and continue through the iPhone's Handoff banner. Check
  that the running bridge gets its Live Activity, including a cold iPhone-app
  launch. Repeat after stopping the bridge on the phone and with Handoff disabled;
  no bridge command may execute just because an older continuation was selected.
- Search, save and remove a device; check persistence after restarting both apps.
- Tap the center and edges of the separate Connect/Remove rows. Connect must
  never open the removal dialog; Remove must only request removal confirmation.
- Change aircraft and Wi-Fi AP/CLIENT; distinguish pending from device-confirmed
  values. Check no-internet aircraft metadata fallback to cached data/ICAO.
- Enable/disable GDL90 and verify the setting in both interfaces and after restart.
- Interrupt internet, BLE and Watch-to-phone connectivity independently. Check
  stale timestamps, explicit failures, reconnect status and no delayed actions.
- Operate both interfaces concurrently and switch devices while a Watch screen
  is open. Confirm no stale command affects a newly selected device.
- Check 40 mm Watch layout, large text and VoiceOver labels. Stop/switch/remove
  must require confirmation.
- Test system termination, explicit force quit and relaunch separately. If iOS
  cannot service a background request, the UI must show that it needs the phone.

The GATAS website remains on the phone. With compatible firmware, Settings also
offers Save configuration and Restart GATAS. USB firmware update mode is available
only in the Companion app. See [Device actions](DEVICE_ACTIONS.md) for the
protocol, confirmations and hardware acceptance checks. A runtime Wi-Fi mode
remains temporary; saving does not change its boot policy.

## Validation notes (2026-10-16)

The initial dashboard and live phone-to-Watch snapshot delivery were inspected
on a paired 46 mm watchOS 27 / iOS 27 simulator. The 40 mm onboarding layout was
also inspected. These are startup/layout checks, not acceptance of all commands
against hardware. Simulator haptics are disabled to prevent audible Mac alerts
from headless test runs; physical Watches retain action feedback.

The declared iOS 18.0 / watchOS 10.0 deployment targets compile with the installed
SDKs. Runtime behavior on those minimum OS releases still needs verification.
The prebuilt Compose ICU data object also emits an iOS 18.5 deployment-metadata
linker warning when linking the app for iOS 18.0.

Android Lint completes with two reported warnings in existing Android files.
Its embedded Kotlin analyzer additionally emits metadata-version diagnostics
(AGP 8.10's analyzer expects Kotlin 2.1 metadata; dependencies use Kotlin 2.3).
Treat Kotlin-specific lint coverage as limited until that toolchain mismatch is
resolved separately. The normal Kotlin compiler and unit tests run independently.

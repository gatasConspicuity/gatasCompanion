# Phone barometer: hardware acceptance record

## Status and purpose

The [phone-pressure feature](phone-pressure.md) reads ambient air pressure and
sends it directly over Bluetooth to the active GATAS device. OpenAce 3.5.1 (or
compatible COBS type-11 support) is required. GATAS can use this pressure to
calculate pressure altitude without a BMP280. A fresh internal BMP280 continues
to take priority. The observations below were collected using the earlier
debug-only prototype before the persistent setting was integrated.

**Full hardware acceptance remains pending.** The iPad mini locked-display and
ForeFlight-foreground sensor/Bluetooth trials have passed. The user also confirms
a GATAS-sourced barometric-altitude display in ForeFlight without an active
internal pressure sensor. No-traffic operation, the remaining comparisons, and
Android hardware checks are still open. The physical offline test is skipped
at the user's explicit request, not counted as passed.
Following the two iPad background runs and the manual ForeFlight observations,
the user requested stable completion on 2026-10-17. The per-device setting is now
integrated for normal builds on both platforms. The user subsequently clarified
that all app builds are distributed to testers and requested removal of the
Android Debug-only restriction. Android hardware validation is handed off as an
open upstream PR test task. This does not mark any unperformed hardware
check as passed. The earlier trial activation was temporary and reset when its
bridge stopped; the integrated setting persists by Bluetooth identifier.
No pressure measurements are saved or sent to an Internet server.

## Run the trial

1. Install the Debug build on a physical phone. The simulator cannot demonstrate
   a working barometer or background delivery. Do not attach a debugger.
2. Start the bridge for the intended GATAS device and open **GATAS Settings**.
3. Read the explanation under **Phone barometer**, then enable
   **Use phone barometer** (called **Test phone barometer** in the initial
   prototype). On iOS, accept the Motion & Fitness permission.
   This is the system permission that covers barometer access. If denied, enable
   it for GatasCompanion in iOS Settings, then switch the trial off and on.
4. Check that the pressure, measurement count, and sent count update. Confirm
   that the resulting pressure altitude is present at GATAS / its GDL90 output.
   Successful Bluetooth writes alone do not demonstrate that GATAS used them.
5. Run for 30 minutes with the phone locked, then inspect the counters without
   stopping the bridge. Record elapsed time, maximum sensor/send gaps, maximum
   delivery age, stale/error state, and the GATAS-side result.
6. Switch the trial off and on to reset counters. Repeat for 30 minutes with a
   navigation app in the foreground. Also test without aircraft traffic.
   The physical no-Internet case is intentionally skipped at the user's request;
   its automated coverage remains in place.
7. Check stop/start, Bluetooth loss/reconnect, permission denial, and missing
   sensor handling. A stopped or disconnected phone must not leave a permanently
   valid pressure value at GATAS. Repeat with and without an internal BMP280.

For a stable connection, there must be no interval that causes GATAS's five-second
pressure validity window to expire. The display shows successful Bluetooth
writes, not a GATAS acknowledgement. Compare pressure with a reference sensor
and record the difference; this trial does not apply automatic calibration.

Android measures in the existing connected-device foreground service. A
non-wake-up barometer holds a partial CPU wake lock only during the active
sensor session; stopping the sensor releases it before Bluetooth cleanup.

## Protocol and clock handling

The decoded payload is exactly six bytes: type `11`, pressure `u16` in 0.1 hPa,
QNH `0xffff`, source `2` (pressure sensor), with big-endian fields. A missing
pressure value uses `0xffff` while retaining source `2` to invalidate only the
accepted measured-pressure method. COBS encoding ends with one zero delimiter.

Only new sensor timestamps are sent, at most once per second. The transport
rechecks freshness and connection identity inside the shared whole-packet write
lock, so it cannot interrupt a fragmented relay response. Sensor callbacks keep
only the latest value. On iOS, Mach sensor timestamps are mapped to a clock that
includes system sleep; queued readings from before a detected sleep are dropped.

## Acceptance record

Local validation on 2026-10-16:

- `:composeApp:allTests`: 55 tests each on Android Debug, Android Release, and
  the iOS arm64 simulator; no failures or skipped tests.
- `:composeApp:lintDebug`: passed with two existing warnings (`ImplicitSamInstance`
  in the service-stop call and `ObsoleteSdkInt` in `drawable-v24`).
- `:composeApp:assembleDebug`: passed.
- Xcode `GatasCompanion` Debug build for physical iOS: passed, including the
  existing Watch app and Live Activity extension. Bundle signature verification
  and purpose-text/privacy-manifest checks passed.

These checks do not establish sensor accuracy or background delivery on a phone.

The Debug app was installed on the iPhone 16. Its first launch was rejected by
iOS; its development profile and signature match, include that device, and remain
valid until September 2027. The device reports Developer Mode as disabled.
The user subsequently selected the iPad mini (6th generation) for the trial.
It is also included in the development profile and reports iPadOS 27.0.1 with
Developer Mode initially disabled. The user enabled Developer Mode, and the
Debug app was installed and launched successfully on the iPad mini without a
debugger. The user confirmed that pressure in hPa and both the measurement and
send counters are updating. The 30-minute locked-device trial was then requested.
That running device build is the initial hardware prototype. A later local
revision also protects a newer unsent sample against an out-of-order callback;
its additional regression test and physical-iOS Xcode build pass, but it has not
replaced the running app. The initial hardware build is retained under
`/private/tmp/gatas-pressure-device`; the reviewed build is under
`/private/tmp/gatas-pressure-reviewed`.

The user's next screenshot reports 1,179 seconds elapsed, 1,245 measurements,
1,021 pressure packets sent, maximum sample/send gaps of 1,021/1,593 ms, and
maximum sensor delivery age of 3,043 ms. It does not establish the requested
30-minute background result. The user also reports that GATAS's web interface
is barely responsive from an iPad connected to its Wi-Fi AP. In the first A/B
comparison, disabling only the pressure trial while retaining Bluetooth, the
bridge, and Wi-Fi initially made the Web UI substantially faster. The slowdown
then recurred without re-enabling the trial. The initial improvement was not
sustained, so this comparison does not establish pressure transmission as the
cause. The user reports a distance of approximately two metres and suspects a
weak AP connection; signal strength and packet loss have not been measured.
The user subsequently reports that the AP connection becomes unusable when
moving farther away. This supports a distance-dependent Wi-Fi problem; it does
not establish whether Bluetooth contributes. Bluetooth was not disabled for a
controlled comparison, and no firmware-side load measurements are available.
The user rules out nearby metal as a cause in this installation.

Code inspection confirms that disabling the trial cancels its sensor/send
session, calls the native sensor stop method, and attempts one bounded pressure-
unavailable frame if a pressure value had previously been sent. It does not
leave a periodic pressure sender running. This is a source-code finding, not a
packet capture from the iPad. Continue the pressure background trial with the
sensor device at a fixed distance and a stable Bluetooth connection. The pressure
sender uses Bluetooth directly and does not require the GATAS Wi-Fi AP or an
Internet response. Inspect the app's counters after 30 minutes locked; use the
Web UI at close range if needed for a separate GATAS-side check. Web UI
responsiveness alone cannot confirm pressure receipt.

On 2026-10-17, the user supplied another screenshot from the iPad mini trial:

| Reported measurement | Value |
| --- | --- |
| Trial elapsed time | 2,134 seconds (35 minutes 34 seconds) |
| Measurements counted | 2,261 |
| Pressure packets sent | 1,777 |
| Maximum sensor timestamp gap | 1,020 ms |
| Maximum successful pressure-send gap | 1,442 ms |
| Maximum sensor delivery age | 2,768 ms |
| Current sample age | 2,805 ms |
| State at capture | Bluetooth connected; no stale/error state shown |

The reported maximum gap between successful pressure writes is below five
seconds. This supports sustained sensor-to-Bluetooth operation over more than
30 minutes, but still does not demonstrate pressure receipt/use by GATAS.
The user confirmed that the iPad was continuously locked for at least 30
minutes during this run. The locked-display sensor/Bluetooth check therefore
passes for this iPad mini, iPadOS version, and installed prototype. The subsequent
navigation-app run and GATAS-side observation are recorded below.
The separate GDL90 traffic maximum interval of 8,888 ms is not the pressure-send
gap.

The user then stated that Companion was running in the background with
ForeFlight in the foreground. No debugger was attached. The subsequent
screenshot, supplied on 2026-10-17, shows:

| Reported measurement | Value |
| --- | --- |
| Trial elapsed time | 2,782 seconds (46 minutes 22 seconds) |
| Measurements counted | 2,933 |
| Pressure packets sent | 2,330 |
| Maximum sensor timestamp gap | 1,019 ms |
| Maximum successful pressure-send gap | 2,375 ms |
| Maximum sensor delivery age | 3,376 ms |
| Current sample age | 3,153 ms |
| State at capture | Bluetooth connected; no stale/error state shown |
| Displayed GATAS name | GaTasJR |

Together with the stated foreground/background setup, this passes the
30-minute navigation-app sensor/Bluetooth check on the tested iPad mini.
The earlier screenshots display GaTasJR2; the name difference does not establish
whether this was a renamed device or another Bluetooth device. No device-ID or
persistence acceptance is inferred from it. The user confirmed that Internet
access over Wi-Fi or cellular was available during this run, so it did not cover
offline operation. The user subsequently chose to skip that physical test.
The GDL90 traffic counter is 42,114, so this screenshot does
not establish the no-aircraft-traffic case or prove that GATAS used the pressure
packets.

The user subsequently confirmed that ForeFlight displays **BARO ALT 243 ft**
with **GATAS** as its source and a **1024 hPa** barometric setting. The user
also explicitly confirmed that the connected GATAS has no internal BMP280 or
other active internal pressure sensor. This is recorded as a manual functional
verification of the GATAS-to-ForeFlight pressure-altitude display without an
internal sensor, with the phone-pressure sender active.

ForeFlight's [indicated-altitude documentation](https://support.foreflight.com/hc/en-us/articles/15649977103511-Can-indicated-altitude-be-viewed-in-ForeFlight-Mobile)
defines BARO ALT as baro-corrected pressure altitude. The reported 1024 hPa is
therefore recorded as the indicated barometric setting, not a raw phone-sensor
measurement or an independent reference-pressure measurement. Companion sends
ambient pressure with unavailable QNH; ForeFlight applies its own barometric
correction. No automatic calibration or altitude-accuracy result is inferred.

The user also reports ForeFlight's separate **Pressure Alt -50 ft**, source
**GATAS**, labelled **Uncorrected**, at the same time as the corrected display.
This agrees with [ForeFlight's instrument definitions](https://support.foreflight.com/hc/en-us/articles/360012094814-What-is-the-difference-between-the-Pressure-Altitude-instrument-and-the-Cabin-Pressure-instrument).
As a plausibility check, the phone pressure visible in the preceding screenshot
corresponds to approximately -53.2 ft using OpenAce's standard-atmosphere formula
referenced to 1013.25 hPa. GDL90 ownship pressure altitude has 25-foot resolution.
The user then clarified the current phone pressure as **1015.1 hPa**, which
corresponds to approximately **-50.5 ft** with the same formula and agrees closely
with the reported **-50 ft** uncorrected ForeFlight display. The earlier screenshot
and the later reports are not a synchronized recording. This is a consistency
check, not calibration or an independent sensor-accuracy test.

Code inspection is consistent with this route: COBS type 11 updates external
pressure; GDL90Service obtains ownship pressure altitude from OwnshipState.
Fresh internal pressure takes priority, and external measured pressure takes
priority over external calculated pressure. The protocol also permits calculated
pressure from an external producer. The ForeFlight source label alone cannot
distinguish those external producers. Physical offline isolation from Internet-
relay responses was not performed and is skipped at the user's request.
The runtime app wording
continues to acknowledge Bluetooth send only, since there is no protocol-level
acknowledgement of which pressure GATAS accepted.

| Check | iOS | Android |
| --- | --- | --- |
| Physical test device | iPad mini (6th generation) selected | No device available |
| Foreground sensor and Bluetooth send | User confirmed on iPad mini | Pending |
| Sensor/Bluetooth run longer than 30 minutes | Two runs: 35m 34s locked; 46m 22s with ForeFlight foreground | Pending |
| Purpose text and system permission | App started and sensor access works; exact prompt still to review | Pending |
| 30 minutes locked | Passed for sensor/Bluetooth sender on iPad mini; user confirmed at least 30 minutes continuously locked | Pending |
| 30 minutes navigation app foreground | Passed for sensor/Bluetooth sender on iPad mini with ForeFlight foreground; maximum pressure-send gap 2,375 ms | Pending |
| Physical offline test | Skipped at user's request; not passed | Skipped at user's request; not passed |
| No aircraft traffic | Pending | Pending |
| Wi-Fi AP / Web UI remains responsive | Failure depends on distance and also occurs with pressure off; Bluetooth contribution untested | Pending |
| GATAS / GDL90 pressure-altitude verification | User confirmed corrected BARO ALT 243 ft / 1024 hPa setting and uncorrected Pressure Alt -50 ft, both source GATAS, with phone sender active and no active internal pressure sensor; physical offline isolation skipped at user's request | Pending |
| Internal BMP280 priority | Pending | Pending |
| Reference-pressure comparison | Pending | Pending |

## Integrated per-device setting

`PhonePressureSettings` replaces the temporary trial selection. It
stores one Boolean per saved Bluetooth identifier using Android SharedPreferences
or iOS NSUserDefaults. Unknown devices default off. Device names and aircraft
selection are not part of the key. Removing a saved device also removes its
choice. Common tests cover two devices with equal names, switching lookup between
their identifiers, renaming, rebuilding the settings instance, and removing and
re-adding a device. An iOS simulator test also exercises independent instances
against an isolated NSUserDefaults suite. These are not physical app-restart or
device-switch acceptance tests.

The **Use phone barometer** switch and sender use the persistent setting. Starting
or reconnecting restores the choice without requesting permission. Only an
explicit enable can request access, and iOS additionally requires the app to be
in the foreground. Every connection has a separate identity token, so even a
rapid reconnection that conflates a disconnected status cancels the old sensor
and waits for a new measurement. Both Debug and Release builds expose the switch,
run the same pressure sender, and show the diagnostic counters. The Android
notice describes the missing hardware validation without preventing use.

Validation of the prepared settings on 2026-10-17:

- `:composeApp:allTests`: 58 tests each on Android Debug and Android Release;
  59 on the iOS arm64 simulator, including the isolated native-preferences test.
  No failures, errors, or skipped tests.
- `:composeApp:lintDebug`: completed with zero reported lint errors and the two
  existing warnings listed above. The lint compiler also emits Kotlin metadata
  compatibility diagnostics for existing dependencies; these are not new
  settings findings and limit dependency analysis.
- `:composeApp:assembleDebug` and `:composeApp:linkDebugFrameworkIosArm64`: passed.
- Xcode Debug build for physical iOS, including Watch and Live Activity targets:
  passed. Bundle signature and bundled Info.plist/privacy-manifest checks passed.
  The package is under `/private/tmp/gatas-pressure-settings`; it has not been
  installed over the hardware-trial app.

The previously proposed physical run with Wi-Fi and cellular data disabled is
skipped at the user's explicit request. This does not waive the separate
no-aircraft-traffic check or change the existing automated no-Internet/no-traffic
tests.

## Final integration validation (2026-10-17)

- **Use phone barometer** is integrated into the current device's settings,
  with the approved purpose text and an informational status card on the
  connection screen. The persisted choice is restored on bridge start and
  reconnect; stopping the bridge does not change it.
- The initial integration restricted Android to Debug builds; that restriction
  was subsequently removed at the user's request, as recorded below.
- `:composeApp:allTests`: **63 tests each** on Android Debug and Android Release,
  **64 tests** on the iOS arm64 simulator; no failures, errors, or skipped tests.
  Added integration tests cover A → B → A with fresh measurements, rejecting
  another device's queued setting command, reconnecting without permission
  prompts, replacing a connection whose disconnected state was conflated, rapid
  off/on, stop without losing the choice, and sending without Internet/traffic
  events. These automated cases do not replace physical background tests.
- `:composeApp:lintDebug`: passed with zero reported lint errors and the same
  two pre-existing warnings; the dependency metadata diagnostics noted above
  remain a tooling limitation.
- Android Debug APK and the iOS arm64 **Release framework** built successfully.
  The complete Xcode Debug iOS app, Watch app, and Live Activity extension also
  built successfully. The bundle signature and bundled property lists verified.
- The integrated iOS package is under `/private/tmp/gatas-phone-pressure-final`.
  It has not replaced the prototype installed on the iPad. Hardware observations
  in this record therefore refer to the earlier prototype; physical per-device
  persistence, restart, switch, no-traffic, internal-sensor priority,
  permission-dialog wording, and reference-accuracy checks remain unperformed.
  The physical offline check is intentionally skipped at the user's request.

This completes the regular code integration following the user's request
to close the tested Apple path. It is not a blanket hardware acceptance for both
platforms or a claim that all hardware cases in the original plan have passed.

## Availability for all testers (2026-10-17)

The user clarified that the whole app is still being tested and only testers
receive builds. Debug/Release are build configurations, not acceptance levels.
The build-dependent feature gate has therefore been removed entirely: settings,
sensor operation, and diagnostic counters are available in both configurations
on both platforms. New Bluetooth devices still default off.

Physical Android validation remains open. The user intends to hand it to the
original project through the pull request; the
[PR description and test checklist](phone-pressure-pr.md) are prepared locally.
The missing physical tests are documented, not used as a technical restriction
on testers and not counted as passed.

The unrestricted build was revalidated: all 63 Android Debug tests, 63 Android
Release tests, and 64 iOS simulator tests pass. Android Debug and unsigned Release
APKs, Android lint, the iOS arm64 Release framework, and the full signed Xcode
Debug app with Watch/Live Activity targets build successfully. Lint retains the
two pre-existing warnings and the metadata-analysis limitation recorded above.

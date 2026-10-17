# GATAS device actions

The phone and Android app offer a **Device actions** button on the connection
page. It opens a selection menu for **Save configuration**, **Restart GATAS** and
**Enter firmware update mode**. Progress and results remain visible on the
connection page after closing the menu. Watch Settings offers Save
and Restart through its paired iPhone. Firmware mode commands are explicitly
rejected by the Watch command policy, including forged requests.

Requires the matching GATAS firmware extension. On each BLE connection the app
first sends read-only status requests. Without a positive response, these actions
stay disabled while the existing bridge continues working. Deployment targets
remain iOS 18 and watchOS 10. UF2 downloading or uploading is outside this feature.

## Local BLE protocol

COBS framing, terminating zero and existing byte order are unchanged. All
multi-byte integers are **big endian**. These messages are local to BLE: neither
UDP ingress nor the Internet relay may execute or forward them.
The app also rejects UDP reply datagrams containing a management frame, including
one concatenated with traffic, before forwarding any bytes to BLE.

Request type **12**, decoded length **16 bytes**:

| Offset | Bytes | Field |
| --- | --- | --- |
| 0 | 1 | Type = 12 |
| 1 | 1 | Action: status=0, save=1, restart=2, usbBoot=3 |
| 2 | 1 | Phase: Prepare=0, Execute=1 |
| 3 | 4 | Nonzero request ID |
| 7 | 8 | Expected boot ID; status uses zero |
| 15 | 1 | USB policy: RequireSaved=0, SaveFirst=1, AllowUnsaved=2 |

Non-USB actions require policy zero. Status only supports Prepare and is read-only.

Response type **13**, decoded length **17 bytes**:

| Offset | Bytes | Field |
| --- | --- | --- |
| 0 | 1 | Type = 13 |
| 1 | 1 | Action |
| 2 | 4 | Echoed request ID |
| 6 | 1 | Result |
| 7 | 1 | Capabilities: Save=1, Restart=2, USB boot=4 |
| 8 | 8 | Current boot ID |
| 16 | 1 | Unsaved configuration: 0 or 1 |

Results: Ok=0, Accepted=1, Unsupported=2, Invalid=3, Busy=4, SaveFailed=5,
StaleBoot=6, UnsavedChanges=7, Expired=8. A fresh nonzero boot ID is generated at
each firmware start. JSON carries it as 16 hexadecimal characters, preserving all
unsigned 64 bits across Kotlin and Swift.

Shared C++/Kotlin fixtures before COBS encoding:

```text
Save Prepare: 0c010012345678010203040506070800
Save result:  0d01123456780007010203040506070801
```

## Preparation and execution

The app sends Prepare and waits for the matching Accepted reply. Only receiving
that reply authorizes it to send Execute with the same action, ID, boot ID and
USB policy. Preparing never writes flash or resets the device. This avoids
resetting before the app actually received acceptance; a queued BLE ACK alone
would not establish that. Save reports success only after the Execute result.

Preparation expires after ten seconds. One action may be prepared per device.
Firmware retains the last 16 results and never executes an identical cached
request twice. Conflicting ID reuse, stale boot IDs and invalid phases are
rejected. BLE session generations accompany internal requests and replies;
responses target only the originating connection. Disconnect invalidates its
preparation, and stream fragments cannot be combined across sessions/transports.

The app does not queue, retry or replay control commands after reconnect. Only
read-only status probes repeat. A lost Save result is **unconfirmed**. Android
uses a live service callback rather than a redelivered Intent for device actions.

## Saving and restart

Web SaveBr and BLE use the same configuration mutex and checked save function.
It checks capacity, complete byte count and flash readback. Only verified data
clears the dirty state and updates save statistics. Identical data avoids another
flash write. On failure the RAM configuration remains available and the UI must
not report success. The existing single flash store is not power-loss-atomic;
this extension does not change its flash layout.

All web/BLE changes and saves are serialized by one recursive RAII mutex. BLE
aircraft selection now uses a typed configuration request, marks changes dirty
and updates reboot-retained RAM. Modules do not retain Configuration references.

Restart requires confirmation and uses the watchdog. It **does not save flash**;
unsaved configuration survives a normal restart through retained RAM. The bridge
stays running and reconnects. Success requires the same device with a different
boot ID, not just a new BLE connection. After 60 seconds it reports **Restart not
confirmed**. Stop bridge remains available during an action.

## Firmware update mode

The confirmation explains that the computer must use the microcontroller's USB
**data** connection. The user then copies the matching UF2 file to its USB drive.
There is no firmware transfer through the app or Watch.

With unsaved configuration the choices are Save and continue, Continue without
saving, and Cancel. SaveFirst must complete a verified save before USB boot;
every save error prevents entering the bootloader. RequireSaved rechecks at
execution, so a web edit after opening a previously clean confirmation requires
a fresh choice. AllowUnsaved never silently saves.

After a submitted USB Execute, the expected disconnect stops the bridge and its
reconnect attempts. The app reports **Firmware mode requested** and points to the
computer's USB drive. A disconnect is not proof that the bootloader is running.

## Watch compatibility

Watch protocol version remains 1. Optional `deviceControl` fields contain
capabilities, boot ID, dirty state and a correlated operation (ID, action, phase,
message). Commands `saveConfiguration` and `restartGatas` carry `expectedDeviceId`
and `expectedBootId`; the iPhone validates both again against the live service.
Old snapshots decode and disable the unavailable actions. Open action menus and
confirmations keep the device/boot that the person saw, so they cannot silently
target another device after a selection change.

The last 16 terminal results remain in the process-local `recentResults` field.
This carries confirmations through 1 Hz snapshot coalescing even when the phone
starts another action immediately. It is display history, never an execution queue.

## Verification and physical acceptance

Automated checks cover shared wire fixtures, preparation/execute correlation,
duplicates, malformed requests, expiration, stale sessions/boot IDs, holdback and
UDP exclusion, save faults/readback/capacity, concurrent web/BLE configuration,
retained RAM, late replies, old snapshots and Watch firmware-mode rejection.
Builds and mocks cannot prove real BLE/background execution or USB enumeration.

Validation on 2026-10-17:

- Firmware builds: RP2040/pico_w and RP2350/pico2_w, Release, using the repository
  Docker toolchain (Pico SDK 2.1.1, FreeRTOS 11.2). All 26 desktop test executables
  passed: 226 test cases and 13,313 assertions.
- `./gradlew :composeApp:allTests :composeApp:assembleDebug :composeApp:lintDebug
  --offline`: 52 tests each on Android JVM Debug/Release and iOS Simulator passed;
  Android build and lint completed.
- `swift test --package-path iosApp/WatchShared`: 17 protocol tests passed.
- Combined `GatasCompanion` Xcode build for iOS Simulator includes the phone,
  native Watch app and Live Activity extension; signing is disabled for this check.

Existing toolchain limitations remain: the Compose ICU object warns about iOS
18.5 metadata when linked for iOS 18.0, and Android lint's Kotlin analyzer reports
metadata-version incompatibilities. Lint completes with the same two warnings in
existing Android code/resources; normal Kotlin compilation/tests are independent.
Declared minimum OS versions compile, but minimum-version runtime behavior and
all hardware checks below still need physical acceptance.

Before release, complete these checks with real hardware:

- [ ] Change an unsaved value and select another aircraft; restart normally and
      verify both survive without a flash save.
- [ ] Save, remove power and verify the configuration survives a cold boot.
- [ ] Restart: keep the bridge running; confirm a new boot ID and reconnection.
- [ ] Test both USB save decisions; force a save failure and verify no USB boot.
- [ ] Edit through the web while a clean USB confirmation is open; require a new
      choice, and verify the temporary Wi-Fi mode remains temporary.
- [ ] Save/Restart from Watch with locked iPhone, without Internet, and across BLE
      interruptions; no late action may execute after reconnect.
- [ ] Operate phone and Watch concurrently, including device selection changes
      and Stop bridge during preparation/execution.
- [ ] Check small Watch displays, large text and separate action tap targets.
- [ ] New app with old firmware: existing bridge works, new actions disabled.
- [ ] Old app with new firmware: existing bridge functions remain intact.

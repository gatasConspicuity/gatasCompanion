# GATAS Companion Debugging Guide

This document describes the diagnostic facilities that are part of the application and the reasoning behind them. Test-session results and conclusions about external systems do not belong in this repository.

## Logging policy

`initializeLogging()` configures Kermit once during application startup. Debug binaries accept `Debug` and higher severities; release binaries accept `Warn` and higher severities. Android determines this from the installed application's `debuggable` flag. Kotlin/Native reads the debug flag embedded in the binary.

Detailed diagnostic messages must:

- remain at `Debug` severity;
- contain message categories, counters, byte counts, sequence numbers, and durations only;
- never contain aircraft identifiers, callsigns, coordinates, altitudes, or complete protocol payloads;
- remain useful when copied from Xcode without requiring a persistent log file.

Release builds also skip relay-response summarization, diagnostic-window
aggregation, and temporal cycle correlation. The diagnostic implementation is
compiled and tested with the application, but its parsing, allocations, state,
and output are inactive unless the binary is a debug build. Operational warning
and error reporting remains active in releases.

The application does not create diagnostic files. On iOS, collect the messages from Xcode's debug console while running the `GatasCompanion` scheme with the `Debug` build configuration.

## End-to-end traffic path

Traffic follows this path:

```text
GATAS position request
  -> Companion BLE receiver
  -> Internet UDP relay
  -> Companion BLE write
  -> GATAS conversion to GDL90
  -> Companion GDL90 receiver
  -> 127.0.0.1:4000
  -> EFB on the same iOS device
```

The diagnostics deliberately separate these stages. A successful UDP send to `127.0.0.1:4000` means that the operating system accepted the datagram; UDP provides no confirmation that the EFB processed it.

## GDL90 diagnostics

`Gdl90Forwarder` recognizes `MessageType.GDL90_V1`, extracts the GDL90 bytes without modification, and delegates transmission to `Gdl90UdpBridgeService`.

`Gdl90UdpBridgeService` uses an unconnected, reusable UDP socket. The destination is the explicit IPv4 loopback address `127.0.0.1:4000`. This is intentionally a same-device path; Wi-Fi unicast, broadcast, multicast, and local-network discovery are not involved.

An unconnected socket is important on Darwin. A connected UDP socket can surface an ICMP port-unreachable response as `ECONNREFUSED` when the EFB temporarily has no listener. That stale error can affect a later send even though UDP is being used as a one-way, best-effort transport. The unconnected socket preserves those intended semantics. A genuine send failure closes the socket so that the next packet creates a fresh one.

`summarizeGdl90Messages()` inspects only GDL90 message identifiers and reports counts for:

- heartbeat messages;
- ownship reports;
- traffic reports;
- other messages.

The periodic `GDL90 diagnostic` line includes the aggregation window, category counts, the interval between traffic-bearing GDL90 datagrams, and local forwarding time. It can distinguish missing inbound traffic from a slow local UDP send without decoding position-bearing fields.

## Internet relay diagnostics

Internet relay calls do not execute inside the BLE notification collector. A bounded channel feeds a separate ordered worker. Periodic position requests use a latest-position-wins policy when the queue is full; configuration and control messages are not silently displaced.

Both legacy V1 position requests (type 2) and versioned V2 requests (type 10)
are eligible for replacement when stale. A new configuration/control message
may also replace a stale position request, but a queued control message is
preserved; if it fills the queue, the incoming request is rejected and counted.

The `UDP relay diagnostic` line records failures, empty responses, queue delay, and server round-trip time. This shows whether a slow Internet request delayed later relay work. It is independent of the local GDL90-to-EFB socket.

## Relay-cycle correlation

Each relayed COBS request receives a local sequence number. `summarizeRelayResponse()` validates the COBS pointer chain, examines only the first decoded byte of each frame, and counts aircraft-position, other, and malformed frames.

Aircraft-position responses V1, V2, and V3 (types 1, 8, and 9) count as
`serverTraffic`. Correlation runs for both V1 and V2 position requests (types
2 and 10), including the V3 responses requested by firmware 3.4.1-rc8.

For position requests, the following debug lines describe the full round trip:

```text
Relay cycle diagnostic: cycle=..., requestType=..., queueDelayMs=...,
serverRoundTripMs=..., responseBytes=..., responseFrames=...,
serverTraffic=..., other=..., malformed=..., bleWriteMs=...
```

```text
Relay cycle correlation: cycle=..., serverTraffic=...,
returnedGdl90Traffic=..., gatasTurnaroundMs=...,
localForwardMs=..., result=gdl90-returned
```

Interpret the correlation results as follows:

- `no-traffic-from-server`: the response to a position request contained no aircraft-position frame.
- `gdl90-returned`: a traffic-bearing response was written to GATAS and at least one subsequent GDL90 traffic report was observed.
- `no-gdl90-before-next-server-response`: another traffic-bearing server response was written before any GDL90 traffic report was observed for the preceding cycle.
- `failed`: the Internet relay exchange failed before a usable response was received.

The existing protocol does not carry the local sequence number through GATAS. Correlation is therefore temporal. It proves whether traffic reappears between consecutive server cycles, but it cannot prove a one-to-one mapping between every server aircraft record and every returned GDL90 report. Exact record-level correlation would require a compatible protocol and firmware change.

## BLE lifecycle diagnostics

Kable implements CoreBluetooth's restoration callback. The iOS entry point configures the shared central manager with state restoration before it is first accessed, providing the restore identifier required by CoreBluetooth.

Coroutine cancellation is normal when leaving the scan screen, stopping the bridge, or replacing a job. `CancellationException` is rethrown and is not reported as a scanner or connection failure. Actual exceptions continue to update status and appear in the log.

BLE framing is bounded. Oversized COBS or NMEA frames are discarded, buffers are reset, and drop counters are updated. Fragmented frames and multiple frames per BLE notification are handled by `BleFrameAssembler`.

## Live Activity diagnostics

The Live Activity extension is embedded in the application bundle and uses the same version/build values as the main application. Existing activities are recovered through `Activity.activities`, stale duplicates are ended, and request, update, and end failures are logged in debug builds.

The manager is owned by the application delegate, rather than a SwiftUI view,
so a WatchConnectivity background launch also recovers existing activities.
Ordinary background messages cannot request a new activity: requests wait for
`UIApplication.didBecomeActiveNotification`. The Watch reports this separately
from bridge health. A failed foreground request is logged once and can be retried
on the next foreground activation or bridge session, instead of failing every
second. ActivityKit operations from the timer, foreground activation and remote
commands are serialized.

The Dynamic Island icon is a self-contained SwiftUI vector with explicit dimensions and a visible foreground color. It does not depend on the main application's asset catalog or accent color. A Live Activity is a status surface only; it does not guarantee continuous background execution.

## Xcode messages investigated during stabilization

- CoreBluetooth restore-identifier API misuse: corrected by configuring restoration before central-manager creation.
- Scanner `StandaloneCoroutine was cancelled`: corrected by treating cancellation as expected lifecycle behavior.
- Missing Kotlin framework search path: stale search-path configuration was removed.
- Kotlin build script always running: the build phase is explicitly marked as intentionally running because Gradle owns its dependency analysis.
- Extension embedding recommendation: the phase uses the current `Embed Foundation Extensions` name and embeds the signed `.appex` under `PlugIns`.
- Empty dSYM warning: this describes a binary that contains no matching debug information; it is a symbolication/build-artifact concern, not a runtime transport failure.
- `com.apple.app_launch_measurement.ExtendedLaunchMetrics`: this is Apple launch-metrics instrumentation and is not treated as an application transport error.

## Reproducing a diagnostic session

1. Select the `GatasCompanion` scheme and the connected iPhone or iPad in Xcode.
2. Confirm `Product > Scheme > Edit Scheme > Run > Build Configuration` is `Debug`.
3. Run with `Command-R`.
4. Enable GDL90 and start the bridge.
5. Open the EFB on the same device.
6. In Xcode, activate the debug console and filter for `diagnostic` or `Relay cycle`.
7. Capture at least 30 to 60 seconds, including a visible traffic delay.

Useful filters are:

```text
GDL90 diagnostic
UDP relay diagnostic
Relay cycle diagnostic
Relay cycle correlation
GDL90 UDP bridge failed
```

## Automated verification

The relevant automated checks are:

```text
./gradlew :composeApp:allTests
./gradlew :composeApp:compileKotlinIosArm64
git diff --check
```

Tests cover GDL90 byte preservation and message classification, malformed and truncated inputs, relay-response summaries, frame assembly, queue-related state, and bridge status transitions.

## Retention decision

The production fixes, status counters, privacy-preserving summaries, correlation logic, and tests should remain part of normal development rather than being abandoned on a diagnostic-only branch. They protect behavior that previously failed and provide evidence when the same multi-stage path regresses.

Detailed parsing, correlation, and log output are enabled only in debug builds. The code and tests remain in the normal development line so the diagnostics can be enabled immediately when the multi-stage traffic path needs to be investigated again.

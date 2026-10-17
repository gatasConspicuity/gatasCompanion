# Send phone barometer pressure to each enabled GATAS device

GATAS Companion can send the phone's ambient pressure directly over Bluetooth,
allowing GATAS to calculate pressure altitude without an onboard BMP280. **Use
phone barometer** in GATAS Settings is saved by Bluetooth device identifier,
defaults off, survives bridge restarts/reconnection, and is removed when the
saved device is deleted. Device names and aircraft selection do not affect it.

The implementation supports iOS CMAltimeter and Android TYPE_PRESSURE in the
existing foreground bridge service. It sends fresh type-11 pressure frames at
most once per second, serializes whole BLE packets with relay/control writes,
and discards old measurements when the connection changes. Android non-wake-up
sensors hold a partial wake lock only during measurement. Purpose text explains
the feature and iOS Motion & Fitness access; measurements are not persisted or
sent to an Internet server. Requires OpenAce 3.5.1 or compatible pressure support;
a fresh internal pressure sensor keeps priority. No firmware changes are included.

Both Debug and Release builds expose the feature and its diagnostic counters.
Build configuration does not restrict tester access. A successful Bluetooth
write is displayed as sent; it is not a protocol acknowledgement from GATAS.

## Validation already performed

- Automated protocol, settings, connection/session, lifecycle, and serialized
  packet-write tests: **63 tests each** on Android Debug/Release and **64 tests**
  on the iOS arm64 simulator, all passing.
- Android Debug and unsigned Release APKs and the iOS arm64 Release framework
  build successfully. Android lint completes with two pre-existing warnings;
  the acceptance record documents existing Kotlin metadata analysis diagnostics.
- iPad mini, iPadOS 27.0.1, without debugger: 35m 34s run including at least
  30 minutes continuously locked, maximum pressure-send gap 1,442 ms.
- ForeFlight foreground / Companion background: 46m 22s run, maximum
  pressure-send gap 2,375 ms.
- User confirmed no active internal pressure sensor and ForeFlight source GATAS:
  uncorrected Pressure Alt -50 ft, corrected BARO ALT 243 ft at 1024 hPa setting.
  Phone pressure 1015.1 hPa is consistent with the uncorrected altitude. This is
  a functional/plausibility observation, not an independent calibration result.

## Android device testing requested

No Android phone with a barometer was available locally. Please test a physical
device without an attached debugger and record the phone, Android version,
firmware version, internal pressure-sensor presence, and the diagnostics below.

The physical offline test is intentionally skipped at the user's request and
must not be counted as passed. Automated no-Internet/no-traffic coverage remains.

- [ ] Enable for device A and leave device B off; verify A → B → A, reconnect,
  app/bridge restart, equal device names, rename, and delete/re-add. Choices must
  follow identifiers and no old measurement may reach the next device.
- [ ] Run at least 30 minutes with the display locked. Capture elapsed time,
  samples/sent, maximum sensor/send gaps, delivery age, and stale/error state.
- [ ] Repeat at least 30 minutes with a navigation app in the foreground.
- [ ] Repeat without aircraft-traffic updates. Pressure sending must continue
  independently of traffic events.
- [ ] Disable the feature, stop the bridge/service, interrupt Bluetooth, and
  reconnect. Check that sensor registration and any partial wake lock are
  released, and that reconnect starts a fresh measurement session.
- [ ] Verify missing-sensor handling. If available, exercise both wake-up and
  non-wake-up pressure sensors; only the latter require the partial wake lock.
- [ ] Without an internal pressure sensor, verify the GATAS/GDL90 pressure
  altitude at the receiver. With an internal BMP280, verify its priority.
- [ ] Compare with an independent pressure reference and record deviations and
  measurement intervals. There is no automatic calibration.

For a stable connection, successful pressure-send gaps must not expire GATAS's
five-second validity window. The detailed
[acceptance record](phone-pressure-background-trial.md) distinguishes completed
checks from remaining physical checks on both platforms.

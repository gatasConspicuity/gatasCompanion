# Phone barometer

On iOS, **Use phone barometer** in the current device's **GATAS Settings** sends
your phone's ambient air pressure directly to GATAS over Bluetooth. GATAS can
then calculate pressure altitude without an onboard BMP280. An available, fresh
internal pressure sensor continues to take priority.

The setting is saved separately for each Bluetooth device using its identifier.
New devices start with the setting off. A renamed device keeps its choice; an
aircraft change does not affect it. Starting the app/bridge again or reconnecting
loads that device's choice. Forgetting a saved device removes its setting.

## Permission and prerequisites

- GATAS must run OpenAce 3.5.1 or compatible COBS type-11 pressure support. The
  current protocol cannot automatically confirm firmware compatibility.
- The phone must have a barometer. iOS groups this access under **Motion &
  Fitness**. The system permission applies to the app; the transmission choice
  remains separate for each GATAS device.
- Enable the switch while the app is visible and Bluetooth is connected to
  request sensor access. Reconnection does not request permission. If access is
  denied, enable Motion & Fitness for GatasCompanion in iOS Settings, then switch
  the feature off and on while the app is visible.

Measurement runs while the selected device's setting is on and its Bluetooth
connection is active, including with the display locked or another app in the
foreground. This may increase battery use. Pressure measurements are held only
in memory, sent directly over Bluetooth, and not stored or sent to the Internet.
Internet-relay responses and flight-traffic updates are not required by the
phone-pressure sender.

## Status and limitations

The connection screen shows the measured pressure, sample age, and operating
state. Sensor failures are separate from Internet-relay status. **Pressure sent
over Bluetooth** confirms a successful Bluetooth write; the protocol does not
acknowledge which pressure GATAS accepted or used.

Only new measurements younger than five seconds are sent, at most once per
second. Stopping the feature or losing usable sensor data invalidates the sent
pressure over an existing connection. After Bluetooth loss, GATAS expires the
last value after five seconds. A new connection starts with a new sensor session
and does not receive queued measurements from the previous one.

There is no automatic calibration. ForeFlight's uncorrected Pressure Alt is
standard-pressure altitude; its Baro Alt display additionally applies a barometric
correction. A plausible altitude display is not an independent accuracy test.

The feature, setting, and diagnostic counters are available in both Debug and
Release builds on iOS and Android. This app is distributed to testers; build
configuration does not restrict access to the feature. The locked-display and
ForeFlight background tests have passed on an iPad mini. Physical Android
background validation remains open and is listed in the
[upstream PR handoff](phone-pressure-pr.md). Android uses the existing foreground bridge service and
holds a partial wake lock only when required by a non-wake-up pressure sensor.

The [hardware acceptance record](phone-pressure-background-trial.md) lists the
tested setup, observations, and physical checks that remain open, including
no-traffic operation, device-switch/restart checks, reference accuracy, and
internal BMP280 priority. The physical offline test is intentionally skipped
at the user's request; it is not counted as passed.

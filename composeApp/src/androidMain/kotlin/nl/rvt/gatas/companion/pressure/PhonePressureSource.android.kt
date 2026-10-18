package nl.rvt.gatas.companion.pressure

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import nl.rvt.gatas.appContext

actual fun phonePressureNowMillis(): Long = SystemClock.elapsedRealtime()
actual fun createPhonePressureSource(): PhonePressureSource = AndroidPhonePressureSource()
actual fun phonePressurePlatformNotice(): String =
    "Background operation has not yet been verified on a physical Android phone."

private class AndroidPhonePressureSource : PhonePressureSource, SensorEventListener {
    private val mutableState = MutableStateFlow(PressureSensorState())
    override val state = mutableState.asStateFlow()
    private val manager = appContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private var wakeLock: PowerManager.WakeLock? = null
    private var started = false
    private var startedAtMillis = 0L

    override fun start(allowPermissionRequest: Boolean) {
        stop()
        // Prefer a wake-up barometer when the manufacturer supplies one.
        val sensor = manager.getDefaultSensor(Sensor.TYPE_PRESSURE, true)
            ?: manager.getDefaultSensor(Sensor.TYPE_PRESSURE)
        if (sensor == null) {
            mutableState.value = PressureSensorState(PressureSensorCondition.Unavailable)
            return
        }
        mutableState.value = PressureSensorState(PressureSensorCondition.Waiting)
        try {
            if (!sensor.isWakeUpSensor) {
                val power = appContext.getSystemService(Context.POWER_SERVICE) as PowerManager
                wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "gatas:phone-pressure")
                    .apply { setReferenceCounted(false); acquire() }
            }
            started = true
            startedAtMillis = phonePressureNowMillis()
            if (!manager.registerListener(this, sensor, 200_000, 0, Handler(Looper.getMainLooper()))) {
                throw IllegalStateException("Unable to start the phone barometer")
            }
        } catch (error: Exception) {
            stop()
            mutableState.value = PressureSensorState(PressureSensorCondition.Error, message = error.message)
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (!started || event.sensor.type != Sensor.TYPE_PRESSURE) return
        val sample = PhonePressureSample(event.values[0], event.timestamp / 1_000_000)
        if (sample.measuredAtMillis < startedAtMillis) return
        mutableState.value = if (sample.isFresh(phonePressureNowMillis())) {
            PressureSensorState(PressureSensorCondition.Available, sample)
        } else {
            PressureSensorState(PressureSensorCondition.Error, message = "Invalid or stale barometer measurement")
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    override fun stop() {
        started = false
        manager.unregisterListener(this)
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        mutableState.value = PressureSensorState()
    }
}

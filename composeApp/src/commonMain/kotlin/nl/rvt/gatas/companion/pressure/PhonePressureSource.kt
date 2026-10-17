package nl.rvt.gatas.companion.pressure

import kotlinx.coroutines.flow.StateFlow

/** The timestamp uses the platform's boot clock, including time spent asleep. */
data class PhonePressureSample(val pressureHpa: Float, val measuredAtMillis: Long)

enum class PressureSensorCondition {
    Stopped, Waiting, Available, Unavailable, PermissionRequired, PermissionDenied, Error,
}

data class PressureSensorState(
    val condition: PressureSensorCondition = PressureSensorCondition.Stopped,
    val sample: PhonePressureSample? = null,
    val message: String? = null,
)

/** Lifecycle calls and callbacks run on Main; callbacks only publish the latest sample. */
interface PhonePressureSource {
    val state: StateFlow<PressureSensorState>
    fun start(allowPermissionRequest: Boolean)
    fun stop()
}

expect fun createPhonePressureSource(): PhonePressureSource
expect fun phonePressureNowMillis(): Long
expect fun phonePressurePlatformNotice(): String

const val PHONE_PRESSURE_MAX_AGE_MILLIS = 5_000L

fun PhonePressureSample.isFresh(nowMillis: Long): Boolean =
    pressureHpa.isFinite() && pressureHpa >= 0.05f && pressureHpa <= 6553.4f &&
        measuredAtMillis <= nowMillis && nowMillis - measuredAtMillis < PHONE_PRESSURE_MAX_AGE_MILLIS

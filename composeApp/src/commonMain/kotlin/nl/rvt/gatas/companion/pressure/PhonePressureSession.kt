package nl.rvt.gatas.companion.pressure

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

data class PhonePressureStatus(
    val enabled: Boolean = false,
    val condition: PressureSensorCondition = PressureSensorCondition.Stopped,
    val pressureHpa: Float? = null,
    val sampleAgeMillis: Long? = null,
    val stale: Boolean = false,
    val samplesReceived: Long = 0,
    val packetsSent: Long = 0,
    val maximumSampleGapMillis: Long = 0,
    val maximumSendGapMillis: Long = 0,
    val maximumDeliveryAgeMillis: Long = 0,
    val elapsedMillis: Long = 0,
    val message: String? = null,
)

/** A single sensor and Bluetooth connection; no sample survives a session change. */
class PhonePressureSession(
    private val source: PhonePressureSource,
    private val nowMillis: () -> Long = ::phonePressureNowMillis,
    // The transport must check freshness again inside its packet write lock.
    private val send: suspend (PhonePressureSample?) -> Boolean,
    private val publish: (PhonePressureStatus) -> Unit,
) {
    suspend fun run(allowPermissionRequest: Boolean) {
        val startedAt = nowMillis()
        var status = PhonePressureStatus(enabled = true)
        var observedTimestamp: Long? = null
        var latestSample: PhonePressureSample? = null
        var sentTimestamp: Long? = null
        var lastSendAt: Long? = null
        var hasAcceptedPressure = false
        try {
            source.start(allowPermissionRequest)
            while (currentCoroutineContext().isActive) {
                val now = nowMillis()
                val sensor = source.state.value
                val incoming = sensor.sample
                if (sensor.condition != PressureSensorCondition.Available || incoming == null) {
                    latestSample = null
                } else if (latestSample == null || incoming.measuredAtMillis > latestSample.measuredAtMillis) {
                    latestSample = incoming
                }
                val sample = latestSample
                val fresh = sensor.condition == PressureSensorCondition.Available && sample?.isFresh(now) == true
                if (fresh && (observedTimestamp == null || sample.measuredAtMillis > observedTimestamp)) {
                    status = status.copy(
                        samplesReceived = status.samplesReceived + 1,
                        maximumSampleGapMillis = maxOf(status.maximumSampleGapMillis,
                            observedTimestamp?.let { sample.measuredAtMillis - it } ?: 0),
                        maximumDeliveryAgeMillis = maxOf(status.maximumDeliveryAgeMillis, now - sample.measuredAtMillis),
                    )
                    observedTimestamp = sample.measuredAtMillis
                }
                status = status.copy(
                    condition = sensor.condition,
                    pressureHpa = sample?.pressureHpa,
                    sampleAgeMillis = sample?.let { (now - it.measuredAtMillis).coerceAtLeast(0) },
                    stale = sample != null && !fresh,
                    elapsedMillis = now - startedAt,
                    message = sensor.message,
                )
                if (fresh && (sentTimestamp == null || sample.measuredAtMillis > sentTimestamp) &&
                    (lastSendAt == null || now - lastSendAt >= 1_000)) {
                    if (send(sample)) {
                        val sentAt = nowMillis()
                        status = status.copy(
                            packetsSent = status.packetsSent + 1,
                            maximumSendGapMillis = maxOf(status.maximumSendGapMillis,
                                lastSendAt?.let { sentAt - it } ?: 0),
                            message = null,
                        )
                        sentTimestamp = sample.measuredAtMillis
                        lastSendAt = sentAt
                        hasAcceptedPressure = true
                    }
                } else if (!fresh && hasAcceptedPressure) {
                    if (send(null)) hasAcceptedPressure = false
                }
                publish(status)
                if (sensor.condition == PressureSensorCondition.Error ||
                    sensor.condition == PressureSensorCondition.PermissionDenied ||
                    sensor.condition == PressureSensorCondition.Unavailable) return
                delay(250)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            status = status.copy(condition = PressureSensorCondition.Error, message = error.message ?: "Phone barometer failed")
            publish(status)
        } finally {
            // Stop measurement and release Android's wake lock before any BLE cleanup.
            source.stop()
            if (hasAcceptedPressure) {
                withContext(NonCancellable) {
                    withTimeoutOrNull(500) {
                        try { send(null) } catch (_: Exception) { /* Firmware also expires the sample. */ }
                    }
                }
            }
        }
    }
}

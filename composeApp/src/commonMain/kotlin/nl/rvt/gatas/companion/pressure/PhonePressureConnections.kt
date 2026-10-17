package nl.rvt.gatas.companion.pressure

import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine

/** A new connection token always starts with a new sensor and no retained sample. */
internal suspend fun <Connection : Any> runPhonePressureConnections(
    preference: DevicePhonePressurePreference,
    connections: StateFlow<Connection?>,
    createSource: () -> PhonePressureSource = ::createPhonePressureSource,
    nowMillis: () -> Long = ::phonePressureNowMillis,
    send: suspend (Connection, PhonePressureSample?) -> Boolean,
    publish: (PhonePressureStatus) -> Unit,
) {
    combine(preference.changes, connections) { choice, connection -> choice to connection }
        .collectLatest { (choice, connection) ->
            if (!choice.enabled || connection == null) {
                publish(PhonePressureStatus(enabled = preference.enabled))
                return@collectLatest
            }
            PhonePressureSession(
                source = createSource(),
                nowMillis = nowMillis,
                send = { sample ->
                    // Cleanup may invalidate the current link after disabling, but never another link.
                    if (connections.value !== connection) false else send(connection, sample)
                },
                publish = { status ->
                    if (connections.value === connection && preference.enabled) publish(status)
                },
            ).run(preference.takePermissionRequest())
        }
}

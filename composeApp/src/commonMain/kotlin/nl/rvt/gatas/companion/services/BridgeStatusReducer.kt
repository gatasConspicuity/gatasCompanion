package nl.rvt.gatas.companion.services

/**
 * Applies protocol events to immutable UI state.
 *
 * Keeping these transitions outside the BLE service prevents an unrelated
 * relay success from clearing a GDL90 error and makes each state transition
 * deterministic in unit tests.
 */
object BridgeStatusReducer {
    /** Settings can change from the Watch while no new packet is arriving. */
    fun applyGdl90Setting(status: BridgeStatus, enabled: Boolean): BridgeStatus =
        if (status.gdl90.enabled == enabled) status else status.copy(
            gdl90 = if (enabled) initialGdl90(true) else status.gdl90.copy(
                enabled = false, state = Gdl90State.Disabled,
                lastEvent = "GDL90 forwarding disabled", lastError = null,
            ),
        )

    /** A missing echo is an unknown result, not proof that GATAS rejected it. */
    fun wifiChangeUnconfirmed(status: BridgeStatus, target: nl.rvantwisk.gatas.lib.models.WifiMode, message: String): BridgeStatus =
        if (status.wifiModeChangeTarget != target) status else status.copy(
            wifiModeChangeTarget = null, wifiModeStatusMessage = message,
        )

    fun initialGdl90(enabled: Boolean): Gdl90BridgeStatus = Gdl90BridgeStatus(
        enabled = enabled,
        state = if (enabled) Gdl90State.WaitingForFrames else Gdl90State.Disabled,
        lastEvent = if (enabled) {
            "Waiting for GDL90 frames from GATAS"
        } else {
            "GDL90 forwarding disabled"
        },
    )

    fun reduceGdl90(status: BridgeStatus, result: Gdl90ForwardResult): BridgeStatus =
        when (result) {
            Gdl90ForwardResult.Disabled -> status.copy(gdl90 = Gdl90BridgeStatus())
            Gdl90ForwardResult.NotGdl90 -> status
            is Gdl90ForwardResult.Sent -> {
                val current = status.gdl90
                status.copy(
                    gdl90 = current.copy(
                        enabled = true,
                        state = Gdl90State.Sending,
                        framesReceived = current.framesReceived + 1,
                        packetsSent = current.packetsSent + 1,
                        bytesSent = current.bytesSent + result.byteCount,
                        activityTick = current.activityTick + 1,
                        lastEvent = "GDL90 packet sent to this device on UDP 4000",
                        lastError = null,
                    ),
                    lastEvent = "GDL90 packet sent to 127.0.0.1:4000 (${result.byteCount} bytes)",
                )
            }
            is Gdl90ForwardResult.DecodeError -> {
                val current = status.gdl90
                status.copy(gdl90 = current.copy(
                    enabled = true,
                    state = Gdl90State.Error,
                    framesReceived = current.framesReceived + 1,
                    decodeErrors = current.decodeErrors + 1,
                    lastEvent = "Invalid GDL90 frame received",
                    lastError = result.message,
                ))
            }
            is Gdl90ForwardResult.SendError -> {
                val current = status.gdl90
                status.copy(gdl90 = current.copy(
                    enabled = true,
                    state = Gdl90State.Error,
                    framesReceived = current.framesReceived + 1,
                    sendErrors = current.sendErrors + 1,
                    lastEvent = "GDL90 forwarding error",
                    lastError = result.message,
                ))
            }
        }
}

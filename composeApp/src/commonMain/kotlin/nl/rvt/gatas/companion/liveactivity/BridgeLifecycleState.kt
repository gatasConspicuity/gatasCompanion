package nl.rvt.gatas.companion.liveactivity

/** Lifecycle only: packet counters and relay errors must not trigger disconnect alerts. */
data class BridgeLifecycleState(
    val running: Boolean = false,
    val bluetoothConnected: Boolean = false,
    val unexpectedDisconnectActive: Boolean = false,
    val unexpectedDisconnectCount: Long = 0,
) {
    fun withRunning(running: Boolean): BridgeLifecycleState =
        copy(running = running, bluetoothConnected = running && bluetoothConnected,
            unexpectedDisconnectActive = running && unexpectedDisconnectActive)

    fun withBluetoothConnected(connected: Boolean, expectedDisconnect: Boolean): BridgeLifecycleState {
        val lostConnection = running && bluetoothConnected && !connected
        return copy(
            bluetoothConnected = running && connected,
            unexpectedDisconnectActive = running && !connected &&
                if (lostConnection) !expectedDisconnect else unexpectedDisconnectActive,
            unexpectedDisconnectCount = unexpectedDisconnectCount +
                if (lostConnection && !expectedDisconnect) 1 else 0,
        )
    }
}

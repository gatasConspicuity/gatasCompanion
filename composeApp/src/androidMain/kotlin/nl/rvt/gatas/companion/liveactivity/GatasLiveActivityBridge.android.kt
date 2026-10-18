package nl.rvt.gatas.companion.liveactivity

actual object GatasLiveActivityBridge {
    actual fun setBluetoothConnected(connected: Boolean, expectedDisconnect: Boolean) = Unit
    actual fun setBridgeRunning(running: Boolean) = Unit
    actual fun recordPacket() = Unit
    actual fun consumeRotationTick(): Boolean = false
    actual fun isBridgeRunning(): Boolean = false
    actual fun rotationDegrees(): Int = 0
    actual fun reset() = Unit
}

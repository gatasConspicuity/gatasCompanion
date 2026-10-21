package nl.rvt.gatas.companion.liveactivity

expect object GatasLiveActivityBridge {
    fun setBridgeRunning(running: Boolean)
    fun setBluetoothConnected(connected: Boolean, expectedDisconnect: Boolean)
    fun recordPacket()
    fun consumeRotationTick(): Boolean
    fun isBridgeRunning(): Boolean
    fun rotationDegrees(): Int
    fun reset()
}

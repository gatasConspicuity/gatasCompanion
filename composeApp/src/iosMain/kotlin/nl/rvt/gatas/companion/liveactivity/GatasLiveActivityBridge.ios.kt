package nl.rvt.gatas.companion.liveactivity

import platform.Foundation.NSLock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

actual object GatasLiveActivityBridge {
    private val lock = NSLock()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var lifecycle = BridgeLifecycleState()
    // Registered once by the process-owned native manager, on Main.
    private var stateObserver: (() -> Unit)? = null
    private var pendingPackets: Int = 0
    private var rotationDegreesValue: Int = 0

    actual fun setBridgeRunning(running: Boolean) {
        lock.withLock {
            lifecycle = lifecycle.withRunning(running)
            if (!running) {
                pendingPackets = 0
                rotationDegreesValue = 0
            }
        }
        stateChanged()
    }

    actual fun setBluetoothConnected(connected: Boolean, expectedDisconnect: Boolean) {
        lock.withLock {
            lifecycle = lifecycle.withBluetoothConnected(connected, expectedDisconnect)
        }
        stateChanged()
    }

    fun lifecycleState(): BridgeLifecycleState = lock.withLock { lifecycle }

    fun observeState(observer: () -> Unit) {
        stateObserver = observer
        stateChanged()
    }

    private fun stateChanged() {
        // Lifecycle changes must wake native handling directly. A timer may not
        // run after a background stop. Read the latest state in the observer;
        // the monotonic counter preserves losses even across a quick reconnect.
        scope.launch { stateObserver?.invoke() }
    }

    actual fun recordPacket() {
        lock.withLock {
            if (lifecycle.running) {
                pendingPackets += 1
            }
        }
    }

    actual fun consumeRotationTick(): Boolean =
        lock.withLock {
            if (!lifecycle.running || pendingPackets <= 0) {
                return@withLock false
            }

            pendingPackets = 0
            rotationDegreesValue = (rotationDegreesValue + 30) % 360
            true
        }

    actual fun isBridgeRunning(): Boolean =
        lock.withLock { lifecycle.running }

    actual fun rotationDegrees(): Int =
        lock.withLock { rotationDegreesValue }

    actual fun reset() {
        lock.withLock {
            lifecycle = lifecycle.withRunning(false)
            pendingPackets = 0
            rotationDegreesValue = 0
        }
        stateChanged()
    }

    private inline fun <T> NSLock.withLock(block: () -> T): T {
        lock()
        return try {
            block()
        } finally {
            unlock()
        }
    }
}

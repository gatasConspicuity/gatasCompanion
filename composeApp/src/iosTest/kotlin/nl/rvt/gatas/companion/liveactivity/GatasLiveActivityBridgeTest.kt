package nl.rvt.gatas.companion.liveactivity

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class GatasLiveActivityBridgeTest {
    @Test fun stopNotifiesTheNativeOwnerWithoutWaitingForPacketsOrATimer() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val states = mutableListOf<BridgeLifecycleState>()
            GatasLiveActivityBridge.reset()
            GatasLiveActivityBridge.observeState { states += GatasLiveActivityBridge.lifecycleState() }
            runCurrent()
            GatasLiveActivityBridge.setBridgeRunning(true)
            runCurrent()
            assertTrue(states.last().running)
            val changes = states.size
            GatasLiveActivityBridge.recordPacket()
            runCurrent()
            assertEquals(changes, states.size)
            assertTrue(GatasLiveActivityBridge.consumeRotationTick())
            GatasLiveActivityBridge.reset()
            runCurrent()
            assertEquals(changes + 1, states.size)
            assertFalse(states.last().running)
            assertEquals(0, GatasLiveActivityBridge.rotationDegrees())
            assertFalse(GatasLiveActivityBridge.consumeRotationTick())
        } finally {
            GatasLiveActivityBridge.observeState {}
            runCurrent()
            Dispatchers.resetMain()
        }
    }

    @Test fun queuedLifecycleCallbacksSeeRecoveryAndRetainTheLossCounter() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            GatasLiveActivityBridge.reset()
            val before = GatasLiveActivityBridge.lifecycleState().unexpectedDisconnectCount
            val states = mutableListOf<BridgeLifecycleState>()
            GatasLiveActivityBridge.observeState { states += GatasLiveActivityBridge.lifecycleState() }
            GatasLiveActivityBridge.setBridgeRunning(true)
            GatasLiveActivityBridge.setBluetoothConnected(true, expectedDisconnect = false)
            GatasLiveActivityBridge.setBluetoothConnected(false, expectedDisconnect = false)
            GatasLiveActivityBridge.setBluetoothConnected(true, expectedDisconnect = false)
            runCurrent()
            assertTrue(states.last().bluetoothConnected)
            assertFalse(states.last().unexpectedDisconnectActive)
            assertEquals(before + 1, states.last().unexpectedDisconnectCount)
        } finally {
            GatasLiveActivityBridge.observeState {}
            GatasLiveActivityBridge.reset()
            runCurrent()
            Dispatchers.resetMain()
        }
    }
}

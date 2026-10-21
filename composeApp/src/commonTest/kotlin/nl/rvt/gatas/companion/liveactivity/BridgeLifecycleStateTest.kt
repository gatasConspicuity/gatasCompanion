package nl.rvt.gatas.companion.liveactivity

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BridgeLifecycleStateTest {
    private val connected = BridgeLifecycleState().withRunning(true)
        .withBluetoothConnected(true, expectedDisconnect = false)

    @Test fun initialConnectionFailuresDoNotCountAsAnInterruptedConnection() {
        var state = BridgeLifecycleState().withRunning(true)
        repeat(5) { state = state.withBluetoothConnected(false, expectedDisconnect = false) }
        assertTrue(state.running)
        assertEquals(0L, state.unexpectedDisconnectCount)
    }

    @Test fun aDisconnectCountsOnceAcrossRepeatedReconnectFailures() {
        var state = connected.withBluetoothConnected(false, expectedDisconnect = false)
        repeat(5) { state = state.withBluetoothConnected(false, expectedDisconnect = false) }
        assertTrue(state.running)
        assertFalse(state.bluetoothConnected)
        assertTrue(state.unexpectedDisconnectActive)
        assertEquals(1L, state.unexpectedDisconnectCount)
    }

    @Test fun aNewLossAfterReconnectionCanAlertAgain() {
        val state = connected.withBluetoothConnected(false, expectedDisconnect = false)
            .withBluetoothConnected(true, expectedDisconnect = false)
            .withBluetoothConnected(false, expectedDisconnect = false)
        assertEquals(2L, state.unexpectedDisconnectCount)
    }

    @Test fun manualStopAndItsLateBluetoothCallbacksDoNotAlertOrReviveTheBridge() {
        val stopped = connected.withRunning(false)
        val lateDisconnect = stopped.withBluetoothConnected(false, expectedDisconnect = false)
        val lateConnection = lateDisconnect.withBluetoothConnected(true, expectedDisconnect = false)
        assertFalse(lateConnection.running)
        assertFalse(lateConnection.bluetoothConnected)
        assertFalse(lateConnection.unexpectedDisconnectActive)
        assertEquals(0L, lateConnection.unexpectedDisconnectCount)
    }

    @Test fun expectedRestartDoesNotHideTheNextUnexpectedLoss() {
        val restarting = connected.withBluetoothConnected(false, expectedDisconnect = true)
        assertEquals(0L, restarting.unexpectedDisconnectCount)
        assertFalse(restarting.unexpectedDisconnectActive)
        val interrupted = restarting.withBluetoothConnected(true, expectedDisconnect = false)
            .withBluetoothConnected(false, expectedDisconnect = false)
        assertEquals(1L, interrupted.unexpectedDisconnectCount)
    }

    @Test fun stopStartRetainsTheCounterButClearsTheConnection() {
        val state = connected.withBluetoothConnected(false, expectedDisconnect = false)
            .withRunning(false).withRunning(true)
        assertFalse(state.bluetoothConnected)
        assertFalse(state.unexpectedDisconnectActive)
        assertEquals(1L, state.unexpectedDisconnectCount)
        assertEquals(2L, state.withBluetoothConnected(true, expectedDisconnect = false)
            .withBluetoothConnected(false, expectedDisconnect = false).unexpectedDisconnectCount)
    }

    @Test fun aFastRecoveryFollowedByAnExpectedDisconnectClearsTheEarlierAlert() {
        val state = connected.withBluetoothConnected(false, expectedDisconnect = false)
            .withBluetoothConnected(true, expectedDisconnect = false)
            .withBluetoothConnected(false, expectedDisconnect = true)
            .withBluetoothConnected(false, expectedDisconnect = false)
        assertEquals(1L, state.unexpectedDisconnectCount)
        assertFalse(state.unexpectedDisconnectActive)
    }
}

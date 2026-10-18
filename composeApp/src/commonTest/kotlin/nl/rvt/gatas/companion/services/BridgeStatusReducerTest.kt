package nl.rvt.gatas.companion.services

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BridgeStatusReducerTest {
    @Test
    fun enabledBridgeStartsWaitingWithoutClaimingDelivery() {
        val status = BridgeStatusReducer.initialGdl90(enabled = true)

        assertEquals(Gdl90State.WaitingForFrames, status.state)
        assertEquals(0, status.packetsSent)
        assertNull(status.lastError)
    }

    @Test
    fun successfulSendClearsOnlyTheGdl90Error() {
        val original = BridgeStatus(
            lastError = "Relay timeout",
            gdl90 = Gdl90BridgeStatus(
                enabled = true,
                state = Gdl90State.Error,
                sendErrors = 1,
                lastError = "Socket failed",
            ),
        )

        val updated = BridgeStatusReducer.reduceGdl90(original, Gdl90ForwardResult.Sent(42))

        assertEquals("Relay timeout", updated.lastError)
        assertNull(updated.gdl90.lastError)
        assertEquals(1, updated.gdl90.packetsSent)
        assertEquals(42, updated.gdl90.bytesSent)
        assertEquals(1, updated.gdl90.sendErrors)
    }

    @Test
    fun sendAndDecodeFailuresUseIndependentCounters() {
        val waiting = BridgeStatus(gdl90 = BridgeStatusReducer.initialGdl90(enabled = true))
        val decodeFailed = BridgeStatusReducer.reduceGdl90(
            waiting,
            Gdl90ForwardResult.DecodeError("Malformed frame"),
        )
        val sendFailed = BridgeStatusReducer.reduceGdl90(
            decodeFailed,
            Gdl90ForwardResult.SendError("Socket failed"),
        )

        assertEquals(1, sendFailed.gdl90.decodeErrors)
        assertEquals(1, sendFailed.gdl90.sendErrors)
        assertEquals(2, sendFailed.gdl90.framesReceived)
        assertEquals("Socket failed", sendFailed.gdl90.lastError)
    }
}

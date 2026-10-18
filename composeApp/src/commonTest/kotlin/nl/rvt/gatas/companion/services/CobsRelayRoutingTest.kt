package nl.rvt.gatas.companion.services

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import nl.rvantwisk.gatas.lib.extensions.cobsEncode

class CobsRelayRoutingTest {
    @Test
    fun forwardsVersionedAndFutureCobsRequests() {
        for (type in listOf(2, 5, 10, 14)) {
            val frame = byteArrayOf(type.toByte(), 0, 42).cobsEncode().dropLast(1).toByteArray()
            val decision = relayDecision("COBS", frame)
            assertTrue(decision.shouldRelay, "COBS type $type should reach the server")
            assertEquals(type, decision.messageType)
        }
    }

    @Test
    fun keepsGdl90LocalAndNmeaOffTheCobsSocket() {
        val gdl90Frame = byteArrayOf(6).cobsEncode().dropLast(1).toByteArray()
        assertFalse(relayDecision("COBS", gdl90Frame).shouldRelay)
        for (type in listOf(12, 13)) {
            assertFalse(relayDecision("COBS", byteArrayOf(type.toByte(), 1).cobsEncode().dropLast(1).toByteArray()).shouldRelay)
        }
        assertFalse(relayDecision("NMEA", "test\r\n".encodeToByteArray()).shouldRelay)
    }

    @Test
    fun doesNotRequireTheOldCobsDecoderSizeLimit() {
        val payload = byteArrayOf(10) + ByteArray(300) { 42 }
        assertTrue(relayDecision("COBS", payload.cobsEncode().dropLast(1).toByteArray()).shouldRelay)
    }

    @Test
    fun udpRepliesCannotInjectDeviceActionsAmongTrafficFrames() {
        val traffic = byteArrayOf(8, 1, 0, 42).cobsEncode()
        assertFalse(containsDeviceActionFrame(traffic))
        assertFalse(containsDeviceActionFrame(byteArrayOf(14, 1).cobsEncode()))
        assertFalse(containsDeviceActionFrame(byteArrayOf()))
        for (type in listOf(12, 13)) {
            val control = byteArrayOf(type.toByte(), 1, 0, 42).cobsEncode()
            assertTrue(containsDeviceActionFrame(control))
            assertTrue(containsDeviceActionFrame(traffic + control + traffic))
            assertTrue(containsDeviceActionFrame(traffic + control.dropLast(1).toByteArray()))
        }
    }
}

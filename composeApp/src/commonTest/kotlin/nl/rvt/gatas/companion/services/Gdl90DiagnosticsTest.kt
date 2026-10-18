package nl.rvt.gatas.companion.services

import kotlin.test.Test
import kotlin.test.assertEquals

class Gdl90DiagnosticsTest {
    @Test
    fun countsMessageTypesAcrossMultipleFramedMessages() {
        val payload = byteArrayOf(
            0x7e, 0x00, 0x01, 0x7e,
            0x14, 0x02, 0x7e,
            0x15, 0x03, 0x7e,
            0x0a, 0x04, 0x7e,
            0x63, 0x05, 0x7e,
        )

        val summary = summarizeGdl90Messages(payload)

        assertEquals(1, summary.heartbeats)
        assertEquals(1, summary.ownshipReports)
        assertEquals(2, summary.trafficReports)
        assertEquals(1, summary.otherMessages)
    }

    @Test
    fun decodesEscapedMessageIdentifier() {
        val summary = summarizeGdl90Messages(
            byteArrayOf(0x7e, 0x7d, (0x14 xor 0x20).toByte(), 0x01, 0x7e)
        )

        assertEquals(1, summary.trafficReports)
    }

    @Test
    fun acceptsUnframedDiagnosticFallback() {
        val summary = summarizeGdl90Messages(byteArrayOf(0x15, 0x01, 0x02))

        assertEquals(1, summary.trafficReports)
    }
}

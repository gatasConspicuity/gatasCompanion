package nl.rvt.gatas.companion.services

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import nl.rvantwisk.gatas.lib.extensions.serializeGDL90V1
import nl.rvantwisk.gatas.lib.extensions.serializeSetWifiModeV1
import nl.rvantwisk.gatas.lib.models.WifiMode

class Gdl90ForwarderTest {
    @Test
    fun defaultDestinationUsesIpv4Loopback() {
        assertEquals("127.0.0.1", Gdl90Destination().host)
        assertEquals(4000, Gdl90Destination().port)
    }

    @Test
    fun forwardsRawGdl90PayloadWithoutChangingBytes() = runSuspendTest {
        val sender = RecordingSender()
        val forwarder = Gdl90Forwarder(sender)
        val expected = byteArrayOf(0x7E, 0x10, 0x00, 0x20, 0x7E)

        val result = forwarder.forward(serializeGDL90V1(expected).withoutDelimiter(), enabled = true)

        val sent = assertIs<Gdl90ForwardResult.Sent>(result)
        assertEquals(expected.size, sent.byteCount)
        assertEquals(1, sent.messageSummary.otherMessages)
        assertContentEquals(expected, sender.payload)
    }

    @Test
    fun doesNotSendWhenForwardingIsDisabled() = runSuspendTest {
        val sender = RecordingSender()
        val result = Gdl90Forwarder(sender).forward(
            serializeGDL90V1(byteArrayOf(0x7E, 0x7E)).withoutDelimiter(),
            enabled = false,
        )

        assertEquals(Gdl90ForwardResult.Disabled, result)
        assertEquals(null, sender.payload)
    }

    @Test
    fun ignoresOtherCobsMessageTypes() = runSuspendTest {
        val sender = RecordingSender()
        val result = Gdl90Forwarder(sender).forward(
            WifiMode.AP.serializeSetWifiModeV1().withoutDelimiter(),
            enabled = true,
        )

        assertEquals(Gdl90ForwardResult.NotGdl90, result)
        assertEquals(null, sender.payload)
    }

    @Test
    fun reportsEmptyAndMalformedPayloads() = runSuspendTest {
        val forwarder = Gdl90Forwarder(RecordingSender())

        assertIs<Gdl90ForwardResult.DecodeError>(forwarder.forward(byteArrayOf(), enabled = true))
        assertIs<Gdl90ForwardResult.DecodeError>(
            forwarder.forward(serializeGDL90V1(byteArrayOf()).withoutDelimiter(), enabled = true)
        )
        assertIs<Gdl90ForwardResult.DecodeError>(
            forwarder.forward(byteArrayOf(8, 1, 2), enabled = true)
        )
    }

    @Test
    fun reportsSenderFailure() = runSuspendTest {
        val sender = RecordingSender(failure = IllegalStateException("socket unavailable"))
        val result = Gdl90Forwarder(sender).forward(
            serializeGDL90V1(byteArrayOf(0x7E, 0x7E)).withoutDelimiter(),
            enabled = true,
        )

        assertEquals(Gdl90ForwardResult.SendError("socket unavailable"), result)
    }

    private class RecordingSender(
        private val failure: Throwable? = null,
    ) : Gdl90DatagramSender {
        var payload: ByteArray? = null

        override suspend fun send(payload: ByteArray) {
            failure?.let { throw it }
            this.payload = payload.copyOf()
        }

        override suspend fun stop() = Unit
    }
}

private fun ByteArray.withoutDelimiter(): ByteArray {
    require(lastOrNull() == 0.toByte())
    return dropLast(1).toByteArray()
}

private fun runSuspendTest(block: suspend () -> Unit) {
    kotlinx.coroutines.test.runTest { block() }
}

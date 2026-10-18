package nl.rvt.gatas.companion.services

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class BleFrameAssemblerTest {
    @Test
    fun reassemblesFragmentedCobsFrameAndRemovesDelimiter() {
        val assembler = BleFrameAssembler(BleFrameProtocol.Cobs)

        assertEquals(0, assembler.accept(byteArrayOf(1, 2)).frames.size)
        val result = assembler.accept(byteArrayOf(3, 0))

        assertEquals(1, result.frames.size)
        assertContentEquals(byteArrayOf(1, 2, 3), result.frames.single())
    }

    @Test
    fun emitsMultipleNmeaFramesWithDelimiters() {
        val result = BleFrameAssembler(BleFrameProtocol.Nmea).accept(
            "A\nB\n".encodeToByteArray()
        )

        assertEquals(2, result.frames.size)
        assertContentEquals("A\n".encodeToByteArray(), result.frames[0])
        assertContentEquals("B\n".encodeToByteArray(), result.frames[1])
    }

    @Test
    fun dropsOversizedFrameThroughItsDelimiterAndRecovers() {
        val assembler = BleFrameAssembler(BleFrameProtocol.Cobs)
        val oversized = ByteArray(BleFrameProtocol.Cobs.maxFrameBytes + 2) { 1 }

        val dropped = assembler.accept(oversized + byteArrayOf(0))
        val recovered = assembler.accept(byteArrayOf(2, 3, 0))

        assertEquals(1, dropped.droppedFrames)
        assertEquals(0, dropped.frames.size)
        assertContentEquals(byteArrayOf(2, 3), recovered.frames.single())
    }

    @Test
    fun acceptsMaximumLengthCobsFrame() {
        val assembler = BleFrameAssembler(BleFrameProtocol.Cobs)
        val payload = ByteArray(BleFrameProtocol.Cobs.maxFrameBytes) { 1 }

        val result = assembler.accept(payload + byteArrayOf(0))

        assertEquals(0, result.droppedFrames)
        assertContentEquals(payload, result.frames.single())
    }
}

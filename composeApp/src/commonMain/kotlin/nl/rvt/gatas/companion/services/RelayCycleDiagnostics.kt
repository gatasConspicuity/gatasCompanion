package nl.rvt.gatas.companion.services

import nl.rvantwisk.gatas.lib.extensions.CobsByteArray
import nl.rvantwisk.gatas.lib.extensions.MessageType

/**
 * Privacy-preserving description of the COBS frames returned by the relay.
 *
 * Only the first decoded byte of each frame is inspected. This is sufficient to
 * establish whether the Internet relay returned aircraft positions without
 * logging aircraft identifiers, coordinates, callsigns, or other payload data.
 */
data class RelayResponseSummary(
    val aircraftPositions: Int = 0,
    val otherMessages: Int = 0,
    val malformedFrames: Int = 0,
) {
    val totalFrames: Int
        get() = aircraftPositions + otherMessages + malformedFrames
}

/**
 * Summarizes a relay response containing one or more zero-terminated COBS frames.
 *
 * A truncated or structurally invalid frame is counted as malformed before the
 * existing decoder sees it. The normal protocol path is left untouched and
 * remains responsible for accepting or rejecting the actual response data.
 */
fun summarizeRelayResponse(payload: ByteArray): RelayResponseSummary {
    if (payload.isEmpty()) return RelayResponseSummary()

    var aircraftPositions = 0
    var otherMessages = 0
    var malformedFrames = 0
    var frameStart = 0

    fun inspectFrame(frameEndExclusive: Int) {
        if (frameEndExclusive <= frameStart) return

        val frame = payload.copyOfRange(frameStart, frameEndExclusive)
        val messageType = if (frame.isStructurallyValidCobsFrame()) {
            runCatching { CobsByteArray(frame).peekAhead() }.getOrNull()
        } else {
            null
        }
        when (messageType) {
            MessageType.AIRCRAFT_POSITION_TYPE_V1.value,
            MessageType.AIRCRAFT_POSITION_TYPE_V2.value,
            MessageType.AIRCRAFT_POSITION_TYPE_V3.value -> aircraftPositions++
            null -> malformedFrames++
            else -> otherMessages++
        }
    }

    payload.forEachIndexed { index, byte ->
        if (byte == 0.toByte()) {
            inspectFrame(index + 1)
            frameStart = index + 1
        }
    }
    if (frameStart < payload.size) {
        inspectFrame(payload.size)
    }

    return RelayResponseSummary(
        aircraftPositions = aircraftPositions,
        otherMessages = otherMessages,
        malformedFrames = malformedFrames,
    )
}

/**
 * Validates the COBS pointer chain without inspecting decoded payload bytes.
 */
private fun ByteArray.isStructurallyValidCobsFrame(): Boolean {
    if (size < 2 || last() != 0.toByte()) return false

    val encodedLength = size - 1
    var index = 0
    while (index < encodedLength) {
        val code = this[index].toUByte().toInt()
        if (code == 0) return false

        index += code
        if (index > encodedLength) return false
    }
    return index == encodedLength
}

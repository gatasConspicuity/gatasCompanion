package nl.rvt.gatas.companion.services

enum class BleFrameProtocol(
    val delimiter: Byte,
    val maxFrameBytes: Int,
    val includeDelimiter: Boolean,
) {
    Nmea('\n'.code.toByte(), 1_024, true),
    Cobs(0, 253, false),
}

data class AssembledFrames(
    val frames: List<ByteArray>,
    val droppedFrames: Int,
)

/**
 * Reassembles frames split across arbitrary BLE notifications.
 *
 * A malformed stream without a delimiter must not grow memory indefinitely.
 * After an oversized frame, bytes are ignored through the next delimiter so a
 * truncated tail cannot accidentally be interpreted as a new message.
 */
class BleFrameAssembler(
    private val protocol: BleFrameProtocol,
) {
    private val buffer = mutableListOf<Byte>()
    private var discardingOversizedFrame = false

    fun accept(chunk: ByteArray): AssembledFrames {
        val frames = mutableListOf<ByteArray>()
        var dropped = 0

        chunk.forEach { byte ->
            if (discardingOversizedFrame) {
                if (byte == protocol.delimiter) discardingOversizedFrame = false
                return@forEach
            }

            if (byte == protocol.delimiter) {
                if (protocol.includeDelimiter) buffer += byte
                if (buffer.isNotEmpty()) frames += buffer.toByteArray()
                buffer.clear()
                return@forEach
            }

            buffer += byte
            if (buffer.size > protocol.maxFrameBytes) {
                buffer.clear()
                dropped += 1
                discardingOversizedFrame = true
            }
        }

        return AssembledFrames(frames, dropped)
    }
}

package nl.rvt.gatas.companion.services

/**
 * Privacy-preserving counters extracted from one UDP datagram containing GDL90.
 *
 * Only message identifiers are inspected. Aircraft addresses, coordinates,
 * altitudes, callsigns, and all other payload fields remain untouched and are
 * never written to diagnostic output.
 */
data class Gdl90MessageSummary(
    val heartbeats: Int = 0,
    val ownshipReports: Int = 0,
    val trafficReports: Int = 0,
    val otherMessages: Int = 0,
)

/**
 * Counts GDL90 message types without decoding any position-bearing fields.
 *
 * GDL90 uses 0x7e as a frame delimiter and 0x7d as an escape byte. A datagram
 * may contain one or multiple frames, so the first unescaped byte after every
 * delimiter is collected as a message identifier. Unframed input is accepted
 * as a diagnostic fallback by treating its first unescaped byte as the ID.
 */
fun summarizeGdl90Messages(payload: ByteArray): Gdl90MessageSummary {
    val messageIds = mutableListOf<Int>()
    var insideFrame = false
    var waitingForMessageId = false
    var escaped = false

    payload.forEach { rawByte ->
        val value = rawByte.toUByte().toInt()
        if (value == FRAME_DELIMITER) {
            insideFrame = true
            waitingForMessageId = true
            escaped = false
            return@forEach
        }
        if (!insideFrame || !waitingForMessageId) return@forEach

        if (!escaped && value == ESCAPE_BYTE) {
            escaped = true
            return@forEach
        }

        messageIds += if (escaped) value xor ESCAPE_MASK else value
        waitingForMessageId = false
        escaped = false
    }

    if (messageIds.isEmpty() && payload.isNotEmpty() && payload[0].toUByte().toInt() != FRAME_DELIMITER) {
        val first = payload[0].toUByte().toInt()
        val messageId = if (first == ESCAPE_BYTE && payload.size > 1) {
            payload[1].toUByte().toInt() xor ESCAPE_MASK
        } else {
            first
        }
        messageIds += messageId
    }

    return Gdl90MessageSummary(
        heartbeats = messageIds.count { it == HEARTBEAT_MESSAGE_ID },
        ownshipReports = messageIds.count { it in OWNSHIP_MESSAGE_IDS },
        trafficReports = messageIds.count { it in TRAFFIC_MESSAGE_IDS },
        otherMessages = messageIds.count {
            it != HEARTBEAT_MESSAGE_ID &&
                it !in OWNSHIP_MESSAGE_IDS &&
                it !in TRAFFIC_MESSAGE_IDS
        },
    )
}

private const val FRAME_DELIMITER = 0x7e
private const val ESCAPE_BYTE = 0x7d
private const val ESCAPE_MASK = 0x20
private const val HEARTBEAT_MESSAGE_ID = 0
private val OWNSHIP_MESSAGE_IDS = setOf(10, 11)
private val TRAFFIC_MESSAGE_IDS = setOf(20, 21)

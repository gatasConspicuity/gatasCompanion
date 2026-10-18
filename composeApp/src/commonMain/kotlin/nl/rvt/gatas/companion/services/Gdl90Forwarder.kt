package nl.rvt.gatas.companion.services

import nl.rvantwisk.gatas.lib.extensions.CobsByteArray
import nl.rvantwisk.gatas.lib.extensions.MessageType
import nl.rvantwisk.gatas.lib.extensions.deserializeGDL90V1

/**
 * The UDP destination used by EFB applications running on the same device.
 *
 * A numeric IPv4 loopback address is intentional. Using `localhost` allows the
 * resolver to select IPv6 (`::1`), while some EFB applications only bind an
 * IPv4 UDP socket. This traffic must never be changed to Wi-Fi broadcast: the
 * companion and the receiving EFB are expected to run on the same phone or
 * tablet.
 */
data class Gdl90Destination(
    val host: String = "127.0.0.1",
    val port: Int = 4000,
)

interface Gdl90DatagramSender {
    suspend fun send(payload: ByteArray)
    suspend fun stop()
}

sealed interface Gdl90ForwardResult {
    data object Disabled : Gdl90ForwardResult
    data object NotGdl90 : Gdl90ForwardResult
    data class Sent(
        val byteCount: Int,
        val messageSummary: Gdl90MessageSummary = Gdl90MessageSummary(),
    ) : Gdl90ForwardResult
    data class DecodeError(val message: String) : Gdl90ForwardResult
    data class SendError(val message: String) : Gdl90ForwardResult
}

/** Decodes a GATAS COBS envelope and forwards only raw GDL90 bytes. */
class Gdl90Forwarder(
    private val sender: Gdl90DatagramSender,
) {
    suspend fun forward(cobsPayload: ByteArray, enabled: Boolean): Gdl90ForwardResult {
        if (!enabled) return Gdl90ForwardResult.Disabled
        if (!cobsPayload.isStructurallyValidCobsFrame()) {
            return Gdl90ForwardResult.DecodeError("COBS frame is empty, truncated, or malformed")
        }

        val messageType = runCatching { CobsByteArray(cobsPayload).peekAhead() }
            .getOrElse { return Gdl90ForwardResult.DecodeError(it.message ?: "Invalid COBS frame") }
        if (messageType != MessageType.GDL90_V1.value) return Gdl90ForwardResult.NotGdl90

        val gdl90 = runCatching { deserializeGDL90V1(cobsPayload) }
            .getOrElse { return Gdl90ForwardResult.DecodeError(it.message ?: "Invalid GDL90 frame") }
        if (gdl90.isEmpty()) return Gdl90ForwardResult.DecodeError("GDL90 payload is empty")

        return runCatching {
            sender.send(gdl90)
            Gdl90ForwardResult.Sent(
                byteCount = gdl90.size,
                messageSummary = summarizeGdl90Messages(gdl90),
            )
        }.getOrElse { Gdl90ForwardResult.SendError(it.message ?: "GDL90 UDP send failed") }
    }

    /**
     * Validates COBS code-byte jumps before the legacy decoder is invoked.
     * The BLE assembler has already removed the zero delimiter, so any zero in
     * this array is invalid. A jump beyond the array denotes a truncated frame.
     */
    private fun ByteArray.isStructurallyValidCobsFrame(): Boolean {
        if (isEmpty()) return false

        var codeIndex = 0
        while (codeIndex < size) {
            val code = this[codeIndex].toUByte().toInt()
            if (code == 0) return false
            codeIndex += code
            if (codeIndex > size) return false
        }
        return codeIndex == size
    }
}

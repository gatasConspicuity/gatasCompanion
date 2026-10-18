package nl.rvt.gatas.companion.services

import kotlinx.coroutines.channels.Channel
import nl.rvantwisk.gatas.lib.extensions.MessageType

internal enum class RelayEnqueueResult {
    Accepted,
    ReplacedStalePosition,
    Rejected,
}

internal fun isAircraftPositionRequest(messageType: Int): Boolean =
    messageType == MessageType.AIRCRAFT_POSITION_REQUEST_V1.value ||
        messageType == MessageType.AIRCRAFT_POSITION_REQUEST_V2.value

/**
 * Keeps the newest work in place of a stale V1 or V2 position request, while
 * preserving queued configuration/control messages. The COBS observer is the
 * only producer; the relay worker may consume the pending item at any time.
 */
internal fun <T : Any> replaceStalePositionRequest(
    relayQueue: Channel<T>,
    newWorkItem: T,
    messageTypeOf: (T) -> Int,
): RelayEnqueueResult {
    val pending = relayQueue.tryReceive().getOrNull()
        ?: return if (relayQueue.trySend(newWorkItem).isSuccess) {
            RelayEnqueueResult.Accepted
        } else {
            RelayEnqueueResult.Rejected
        }

    return if (isAircraftPositionRequest(messageTypeOf(pending))) {
        if (relayQueue.trySend(newWorkItem).isSuccess) {
            RelayEnqueueResult.ReplacedStalePosition
        } else {
            RelayEnqueueResult.Rejected
        }
    } else {
        // Restore the control message and report the new drop.
        check(relayQueue.trySend(pending).isSuccess)
        RelayEnqueueResult.Rejected
    }
}

package nl.rvt.gatas.companion.services

import kotlinx.coroutines.channels.Channel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RelayQueueTest {
    private data class WorkItem(val messageType: Int, val sequence: Int)

    @Test
    fun newestPositionRequestReplacesEitherPendingVersion() {
        for (pendingType in listOf(2, 10)) {
            for (newType in listOf(2, 10)) {
                val queue = Channel<WorkItem>(1)
                assertTrue(queue.trySend(WorkItem(pendingType, 1)).isSuccess)
                val latest = WorkItem(newType, 2)

                assertEquals(
                    RelayEnqueueResult.ReplacedStalePosition,
                    replaceStalePositionRequest(queue, latest) { it.messageType },
                )
                assertEquals(latest, queue.tryReceive().getOrNull())
                assertTrue(queue.tryReceive().isFailure)
            }
        }
    }

    @Test
    fun configurationCanReplaceEitherStalePositionVersion() {
        for (pendingType in listOf(2, 10)) {
            val queue = Channel<WorkItem>(1)
            assertTrue(queue.trySend(WorkItem(pendingType, 1)).isSuccess)
            val configuration = WorkItem(5, 2)

            assertEquals(
                RelayEnqueueResult.ReplacedStalePosition,
                replaceStalePositionRequest(queue, configuration) { it.messageType },
            )
            assertEquals(configuration, queue.tryReceive().getOrNull())
        }
    }

    @Test
    fun positionRequestsPreserveQueuedControlMessages() {
        for (controlType in listOf(4, 5, 7)) {
            for (positionType in listOf(2, 10)) {
                val queue = Channel<WorkItem>(1)
                val control = WorkItem(controlType, 1)
                assertTrue(queue.trySend(control).isSuccess)

                assertEquals(
                    RelayEnqueueResult.Rejected,
                    replaceStalePositionRequest(queue, WorkItem(positionType, 2)) { it.messageType },
                )
                assertEquals(control, queue.tryReceive().getOrNull())
                assertTrue(queue.tryReceive().isFailure)
            }
        }
    }

    @Test
    fun acceptsWorkIfConsumerHasAlreadyEmptiedTheQueue() {
        val queue = Channel<WorkItem>(1)
        val latest = WorkItem(10, 2)

        assertEquals(
            RelayEnqueueResult.Accepted,
            replaceStalePositionRequest(queue, latest) { it.messageType },
        )
        assertEquals(latest, queue.tryReceive().getOrNull())
    }

    @Test
    fun rejectsWorkAfterQueueIsClosed() {
        val queue = Channel<WorkItem>(1)
        queue.close()

        assertEquals(
            RelayEnqueueResult.Rejected,
            replaceStalePositionRequest(queue, WorkItem(10, 2)) { it.messageType },
        )
    }
}

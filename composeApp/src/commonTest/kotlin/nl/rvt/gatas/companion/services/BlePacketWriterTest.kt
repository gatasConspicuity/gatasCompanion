@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package nl.rvt.gatas.companion.services

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class BlePacketWriterTest {
    @Test
    fun pressureAndControlCannotSplitAFragmentedRelayPacket() = runTest {
        val writer = BlePacketWriter()
        val resume = CompletableDeferred<Unit>()
        val chunks = mutableListOf<ByteArray>()
        val relay = launch { writer.write(byteArrayOf(1, 2, 3, 4, 0), 2, { true }) { chunk ->
            chunks.add(chunk)
            if (chunks.size == 1) resume.await()
        } }
        runCurrent()
        val pressure = launch { writer.write(byteArrayOf(11, 0), 2, { true }) { chunks.add(it) } }
        val control = launch { writer.write(byteArrayOf(7, 0), 2, { true }) { chunks.add(it) } }
        runCurrent()
        assertEquals(1, chunks.size)
        resume.complete(Unit)
        relay.join(); pressure.join(); control.join()
        assertContentEquals(byteArrayOf(1, 2, 3, 4, 0, 11, 0, 7, 0), chunks.flatMap { it.asList() }.toByteArray())
    }

    @Test
    fun rechecksFreshnessAfterWaitingForTheWriteLock() = rejectsQueuedFrame(expireSample = true)

    @Test
    fun rechecksConnectionAfterWaitingForTheWriteLock() = rejectsQueuedFrame(expireSample = false)

    private fun rejectsQueuedFrame(expireSample: Boolean) = runTest {
        val writer = BlePacketWriter()
        val resume = CompletableDeferred<Unit>()
        val holder = launch { writer.write(byteArrayOf(1, 0), 2, { true }) { resume.await() } }
        runCurrent()
        var connectionCurrent = true
        val chunks = mutableListOf<ByteArray>()
        val pressure = launch {
            assertFalse(writer.write(byteArrayOf(11, 0), 2,
                { connectionCurrent && testScheduler.currentTime < 5000 }) { chunks.add(it) })
        }
        runCurrent()
        if (expireSample) advanceTimeBy(6000) else connectionCurrent = false
        resume.complete(Unit)
        holder.join(); pressure.join()
        assertEquals(0, chunks.size)
    }

    @Test
    fun cancelledQueuedCommandDoesNotReachTheNextConnection() = runTest {
        val writer = BlePacketWriter()
        val resume = CompletableDeferred<Unit>()
        val holder = launch { writer.write(byteArrayOf(1, 0), 2, { true }) { resume.await() } }
        runCurrent()
        val chunks = mutableListOf<ByteArray>()
        val pressure = launch { writer.write(byteArrayOf(11, 0), 2, { true }) { chunks.add(it) } }
        runCurrent()
        pressure.cancelAndJoin()
        resume.complete(Unit)
        holder.join()
        assertEquals(0, chunks.size)
    }
}

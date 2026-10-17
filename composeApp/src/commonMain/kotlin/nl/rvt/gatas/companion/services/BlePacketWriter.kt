package nl.rvt.gatas.companion.services

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Serialize complete packets, including MTU fragments and their delimiter. */
class BlePacketWriter {
    private val mutex = Mutex()

    suspend fun write(
        payload: ByteArray,
        chunkSize: Int,
        mayWrite: () -> Boolean,
        writeChunk: suspend (ByteArray) -> Unit,
    ): Boolean = mutex.withLock {
        currentCoroutineContext().ensureActive()
        if (!mayWrite()) return@withLock false
        require(chunkSize > 0)
        var offset = 0
        while (offset < payload.size) {
            currentCoroutineContext().ensureActive()
            val end = minOf(offset + chunkSize, payload.size)
            writeChunk(payload.copyOfRange(offset, end))
            offset = end
        }
        true
    }
}

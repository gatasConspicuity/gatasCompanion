package nl.rvt.gatas.companion.services

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import nl.rvantwisk.gatas.lib.extensions.CobsByteArray
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DeviceActionsTest {
    private val boot = "0102030405060708"
    private fun raw(bytes: ByteArray) = CobsByteArray(if (bytes.last() == 0.toByte()) bytes else bytes + 0)
    private fun hex(text: String) = text.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    private fun response(request: ByteArray, result: Int, dirty: Boolean = true, bootId: String = boot): ByteArray {
        val cobs = raw(request)
        cobs.getInt1()
        val action = cobs.getInt1()
        cobs.getInt1()
        val id = cobs.getUInt4()
        return CobsByteArray(17).apply {
            put1(13); put1(action); putInt4(id.toInt()); put1(result); put1(7)
            bootId.chunked(2).forEach { put1(it.toInt(16)) }; put1(if (dirty) 1 else 0)
        }.getCobs().dropLast(1).toByteArray()
    }

    @Test fun wireVectorsMatchFirmwareAndMalformedResultsAreRejected() {
        val encoded = DeviceActionCodec.encode(DeviceAction.Save, false, 0x12345678u, boot)
        assertContentEquals(hex("0c010012345678010203040506070800"), raw(encoded).getBuffer())
        val reply = response(encoded, 0)
        assertContentEquals(hex("0d01123456780007010203040506070801"), raw(reply).getBuffer())
        assertEquals(boot, DeviceActionCodec.decode(reply).bootId)
        assertEquals(0x12345678u, DeviceActionCodec.decode(reply).requestId)
        assertTrue(runCatching { DeviceActionCodec.decode(reply.dropLast(1).toByteArray()) }.isFailure)
    }

    @Test fun saveWaitsForPrepareThenVerifiedCompletionAndDoesNotRepeat() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val sent = mutableListOf<ByteArray>()
            val client = DeviceActionClient(backgroundScope)
            val session = Any()
            client.connected(session) { sent += it; true }
            advanceTimeBy(250); runCurrent()
            client.receive(session, response(sent.last(), 0)); runCurrent()
            assertTrue(client.request(DeviceActionCommand("save-1", DeviceAction.Save, "a", boot)))
            runCurrent()
            assertEquals(0, raw(sent.last()).getBuffer()[2].toInt())
            assertEquals("preparing", client.state.value.operation?.phase)
            client.receive(session, response(sent.last(), 1)); runCurrent()
            assertEquals(1, raw(sent.last()).getBuffer()[2].toInt(), client.state.value.toString())
            client.receive(session, response(sent.last(), 1)); runCurrent()
            assertEquals("executing", client.state.value.operation?.phase)
            assertFalse(client.request(DeviceActionCommand("save-2", DeviceAction.Save, "a", boot)))
            val completed = response(sent.last(), 0, dirty = false)
            client.receive(session, completed); runCurrent()
            assertEquals("completed", client.state.value.operation?.phase)
            assertEquals(false, client.state.value.hasUnsavedChanges)
            val count = sent.size
            client.receive(session, completed); runCurrent()
            assertEquals(count, sent.size)
            assertTrue(client.request(DeviceActionCommand("save-from-phone", DeviceAction.Save, "a", boot)))
            runCurrent()
            assertEquals("save-from-phone", client.state.value.operation?.id)
            assertEquals("completed", client.state.value.recentResults.single { it.id == "save-1" }.phase)
        } finally { Dispatchers.resetMain() }
    }

    @Test fun disconnectBeforeAcceptanceCannotExecuteOnAnotherSession() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val sent = mutableListOf<ByteArray>()
            val client = DeviceActionClient(backgroundScope)
            val old = Any()
            client.connected(old) { sent += it; true }
            advanceTimeBy(250); runCurrent()
            client.receive(old, response(sent.last(), 0)); runCurrent()
            client.request(DeviceActionCommand("save-1", DeviceAction.Save, "a", boot)); runCurrent()
            val late = response(sent.last(), 1)
            client.disconnected(old); runCurrent()
            val next = Any()
            client.connected(next) { sent += it; true }
            client.receive(old, late); runCurrent()
            advanceTimeBy(250); runCurrent()
            assertEquals("unknown", client.state.value.operation?.phase)
            assertTrue(sent.all { raw(it).getBuffer()[2] == 0.toByte() })
        } finally { Dispatchers.resetMain() }
    }

    @Test fun restartRequiresANewBootNotJustAReconnection() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val sent = mutableListOf<ByteArray>()
            val client = DeviceActionClient(backgroundScope)
            val first = Any()
            client.connected(first) { sent += it; true }
            advanceTimeBy(250); runCurrent()
            client.receive(first, response(sent.last(), 0)); runCurrent()
            client.request(DeviceActionCommand("restart-1", DeviceAction.Restart, "a", boot)); runCurrent()
            client.receive(first, response(sent.last(), 1)); runCurrent()
            client.disconnected(first)
            val second = Any()
            client.connected(second) { sent += it; true }
            advanceTimeBy(250); runCurrent()
            client.receive(second, response(sent.last(), 0)); runCurrent()
            assertEquals("restarting", client.state.value.operation?.phase)
            advanceTimeBy(5_000); runCurrent()
            client.receive(second, response(sent.last(), 0, bootId = "1112131415161718")); runCurrent()
            assertEquals("completed", client.state.value.operation?.phase)
        } finally { Dispatchers.resetMain() }
    }

    @Test fun restartWithoutANewBootTimesOutUnconfirmed() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val sent = mutableListOf<ByteArray>()
            val client = DeviceActionClient(backgroundScope)
            val session = Any()
            client.connected(session) { sent += it; true }
            advanceTimeBy(250); runCurrent()
            client.receive(session, response(sent.last(), 0)); runCurrent()
            client.request(DeviceActionCommand("restart-timeout", DeviceAction.Restart, "a", boot)); runCurrent()
            client.receive(session, response(sent.last(), 1)); runCurrent()
            client.disconnected(session)
            advanceTimeBy(60_000); runCurrent()
            assertEquals("unknown", client.state.value.operation?.phase)
            assertTrue(client.state.value.operation?.message?.startsWith("Restart not confirmed") == true)
        } finally { Dispatchers.resetMain() }
    }

    @Test fun usbSaveFailureKeepsBridgeAndSubmittedUsbDisconnectStopsIt() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val sent = mutableListOf<ByteArray>()
            val client = DeviceActionClient(backgroundScope)
            val session = Any()
            client.connected(session) { sent += it; true }
            advanceTimeBy(250); runCurrent()
            client.receive(session, response(sent.last(), 0)); runCurrent()
            assertTrue(client.request(DeviceActionCommand("usb-save", DeviceAction.UsbBoot, "a", boot, DeviceUpdatePolicy.SaveFirst)))
            runCurrent()
            client.receive(session, response(sent.last(), 1)); runCurrent()
            assertEquals(1, raw(sent.last()).getBuffer()[2].toInt())
            assertEquals(1, raw(sent.last()).getBuffer().last().toInt())
            client.receive(session, response(sent.last(), 5)); runCurrent()
            assertEquals("failed", client.state.value.operation?.phase)
            assertTrue(client.state.value.operation?.message?.contains("Firmware mode was not started") == true)
            assertFalse(client.disconnected(session))
            client.connected(session) { sent += it; true }
            advanceTimeBy(250); runCurrent()
            client.receive(session, response(sent.last(), 0)); runCurrent()
            assertTrue(client.request(DeviceActionCommand("usb-unsaved", DeviceAction.UsbBoot, "a", boot, DeviceUpdatePolicy.AllowUnsaved)))
            runCurrent()
            client.receive(session, response(sent.last(), 1)); runCurrent()
            assertEquals(2, raw(sent.last()).getBuffer().last().toInt())
            assertTrue(client.disconnected(session)); runCurrent()
            assertEquals("requested", client.state.value.operation?.phase)
        } finally { Dispatchers.resetMain() }
    }

    @Test fun stuckBluetoothWriteDoesNotLeaveAnActionBusyOrExecuteLater() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val sent = mutableListOf<ByteArray>()
            val client = DeviceActionClient(backgroundScope)
            val session = Any()
            var stalled = false
            client.connected(session) { sent += it; if (stalled) awaitCancellation() else true }
            advanceTimeBy(250); runCurrent()
            client.receive(session, response(sent.last(), 0)); runCurrent()
            stalled = true
            client.request(DeviceActionCommand("stalled-save", DeviceAction.Save, "a", boot)); runCurrent()
            advanceTimeBy(5_000); runCurrent()
            assertEquals("unknown", client.state.value.operation?.phase)
            assertTrue(sent.all { raw(it).getBuffer()[2] == 0.toByte() })
        } finally { Dispatchers.resetMain() }
    }

    @Test fun unsupportedFirmwareOnlyReceivesReadOnlyProbes() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val sent = mutableListOf<ByteArray>()
            val client = DeviceActionClient(backgroundScope)
            client.connected(Any()) { sent += it; true }
            advanceTimeBy(6_000); runCurrent()
            assertFalse(client.state.value.checking)
            assertFalse(client.request(DeviceActionCommand("save", DeviceAction.Save, "a", boot)))
            assertTrue(sent.all { raw(it).getBuffer()[1] == 0.toByte() })
        } finally { Dispatchers.resetMain() }
    }
}

@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package nl.rvt.gatas.companion.pressure

import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PhonePressureConnectionsTest {
    @Test
    fun switchingAToBToARestoresChoicesAndNeverReusesTheOldMeasurement() = runTest {
        val settings = PhonePressureSettings(MemoryStorage()).apply { setEnabled("a", true) }
        val sent = mutableListOf<Pair<String, PhonePressureSample?>>()
        val sources = mutableListOf<FakeSource>()
        fun startDevice(id: String) = launch {
            runPhonePressureConnections(DevicePhonePressurePreference(id, settings), MutableStateFlow<Any?>(Any()),
                createSource = { FakeSource().also { sources.add(it) } },
                nowMillis = { testScheduler.currentTime },
                send = { _, sample -> sent.add(id to sample); true }, publish = {})
        }
        val firstA = startDevice("a")
        runCurrent()
        val oldA = sources.single()
        oldA.sample(1000f, 0)
        advanceTimeBy(250)
        runCurrent()
        firstA.cancelAndJoin()

        val b = startDevice("b")
        runCurrent()
        assertEquals(1, sources.size)
        oldA.sample(1100f, testScheduler.currentTime)
        advanceTimeBy(1000)
        runCurrent()
        assertTrue(sent.none { it.first == "b" })
        b.cancelAndJoin()

        val secondA = startDevice("a")
        runCurrent()
        assertEquals(2, sources.size)
        assertEquals(false, sources[1].permissionAllowed)
        val beforeNewMeasurement = sent.size
        oldA.sample(1200f, testScheduler.currentTime)
        advanceTimeBy(1000)
        runCurrent()
        assertEquals(beforeNewMeasurement, sent.size)
        sources[1].sample(900f, testScheduler.currentTime)
        advanceTimeBy(250)
        runCurrent()
        assertEquals("a", sent.last().first)
        assertEquals(900f, sent.last().second?.pressureHpa)
        secondA.cancelAndJoin()
        assertEquals(1, oldA.stops)
        assertEquals(1, sources[1].stops)
    }

    @Test
    fun restoredChoicesDoNotRequestPermissionAndOtherDeviceCommandsAreIgnored() {
        val settings = PhonePressureSettings(MemoryStorage())
        settings.setEnabled("a", true)
        val a = DevicePhonePressurePreference("a", settings)
        assertTrue(a.enabled)
        assertFalse(a.takePermissionRequest())
        assertFalse(a.change("b", false))
        assertTrue(a.enabled)
        assertFalse(settings.isEnabled("b"))

        a.change("a", false)
        assertFalse(a.takePermissionRequest())
        a.change("a", true)
        assertTrue(a.takePermissionRequest())
        assertFalse(a.takePermissionRequest())
        a.reload()
        assertTrue(a.enabled)
        assertFalse(a.takePermissionRequest())
        assertFalse(DevicePhonePressurePreference("b", settings).enabled)
        assertTrue(DevicePhonePressurePreference("a", settings).enabled)
    }

    @Test
    fun reconnectionReplacesTheSensorEvenWhenTheDisconnectedStateWasConflated() = runTest {
        val settings = PhonePressureSettings(MemoryStorage()).apply { setEnabled("a", true) }
        val preference = DevicePhonePressurePreference("a", settings)
        val first = Any()
        val second = Any()
        val connections = MutableStateFlow<Any?>(first)
        val sources = mutableListOf<FakeSource>()
        val sent = mutableListOf<Pair<Any, PhonePressureSample?>>()
        val job = launch {
            runPhonePressureConnections(preference, connections,
                createSource = { FakeSource().also { sources.add(it) } },
                nowMillis = { testScheduler.currentTime },
                send = { connection, sample -> sent.add(connection to sample); true }, publish = {})
        }
        runCurrent()
        sources[0].sample(1000f, 0)
        advanceTimeBy(250)
        runCurrent()
        assertEquals(first, sent.single().first)
        assertEquals(false, sources[0].permissionAllowed)

        connections.value = second
        runCurrent()
        assertEquals(1, sources[0].stops)
        assertEquals(2, sources.size)
        sources[0].sample(1100f, testScheduler.currentTime)
        advanceTimeBy(1000)
        runCurrent()
        assertEquals(1, sent.size)
        assertEquals(false, sources[1].permissionAllowed)
        sources[1].sample(900f, testScheduler.currentTime)
        advanceTimeBy(250)
        runCurrent()
        assertEquals(second, sent.last().first)
        assertEquals(900f, sent.last().second?.pressureHpa)
        connections.value = null
        runCurrent()
        job.cancelAndJoin()
        assertEquals(1, sources[1].stops)
    }

    @Test
    fun quickOffOnStartsAFreshSensorAndStopKeepsTheSavedChoice() = runTest {
        val settings = PhonePressureSettings(MemoryStorage()).apply { setEnabled("a", true) }
        val preference = DevicePhonePressurePreference("a", settings)
        val connections = MutableStateFlow<Any?>(Any())
        val sources = mutableListOf<FakeSource>()
        val sent = mutableListOf<PhonePressureSample?>()
        val job = launch {
            runPhonePressureConnections(preference, connections,
                createSource = { FakeSource().also { sources.add(it) } },
                nowMillis = { testScheduler.currentTime },
                send = { _, sample -> sent.add(sample); true }, publish = {})
        }
        runCurrent()
        sources[0].sample(1000f, 0)
        advanceTimeBy(250)
        runCurrent()
        preference.change("a", false)
        preference.change("a", true)
        runCurrent()
        assertEquals(1, sources[0].stops)
        assertEquals(2, sources.size)
        assertEquals(true, sources[1].permissionAllowed)
        assertEquals(listOf(PhonePressureSample(1000f, 0), null), sent)
        job.cancelAndJoin()
        assertEquals(1, sources[1].stops)
        assertTrue(settings.isEnabled("a"))
        assertTrue(DevicePhonePressurePreference("a", settings).enabled)
        assertFalse(DevicePhonePressurePreference("a", settings).takePermissionRequest())
    }

    @Test
    fun sendsWithNoInternetOrTrafficEventsAndDisablingInvalidatesOnlyOnce() = runTest {
        val settings = PhonePressureSettings(MemoryStorage())
        val preference = DevicePhonePressurePreference("a", settings)
        val connections = MutableStateFlow<Any?>(Any())
        val source = FakeSource()
        val sent = mutableListOf<PhonePressureSample?>()
        var status = PhonePressureStatus()
        var starts = 0
        val job = launch {
            runPhonePressureConnections(preference, connections,
                createSource = { starts++; source }, nowMillis = { testScheduler.currentTime },
                send = { _, sample -> sent.add(sample); true }, publish = { status = it })
        }
        runCurrent()
        assertEquals(0, starts)
        preference.change("a", true)
        runCurrent()
        repeat(10) {
            source.sample(1000f + it, testScheduler.currentTime)
            advanceTimeBy(1000)
            runCurrent()
        }
        assertEquals(10, sent.size)
        assertTrue(status.enabled)
        preference.change("a", false)
        runCurrent()
        assertFalse(status.enabled)
        assertEquals(1, source.stops)
        assertEquals(null, sent.last())
        val afterStop = sent.size
        source.sample(1100f, testScheduler.currentTime)
        advanceTimeBy(10_000)
        runCurrent()
        assertEquals(afterStop, sent.size)
        assertFalse(settings.isEnabled("a"))
        job.cancelAndJoin()
        assertEquals(1, source.stops)
    }

    private class FakeSource : PhonePressureSource {
        override val state = MutableStateFlow(PressureSensorState(PressureSensorCondition.Waiting))
        var stops = 0
        var permissionAllowed: Boolean? = null
        override fun start(allowPermissionRequest: Boolean) { permissionAllowed = allowPermissionRequest }
        override fun stop() { stops++ }
        fun sample(pressure: Float, timestamp: Long) {
            state.value = PressureSensorState(PressureSensorCondition.Available, PhonePressureSample(pressure, timestamp))
        }
    }

    private class MemoryStorage : PhonePressureStorage {
        private val values = mutableMapOf<String, Boolean>()
        override fun isEnabled(key: String): Boolean = values[key] ?: false
        override fun setEnabled(key: String, enabled: Boolean) { values[key] = enabled }
        override fun remove(key: String) { values.remove(key) }
    }
}

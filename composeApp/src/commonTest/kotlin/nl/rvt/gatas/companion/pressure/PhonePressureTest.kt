@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package nl.rvt.gatas.companion.pressure

import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import nl.rvantwisk.gatas.lib.extensions.CobsByteArray
import nl.rvantwisk.gatas.lib.extensions.serializeOwnshipPressureV1
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PhonePressureTest {
    @Test
    fun serializesTheExactSixByteFirmwareFormat() {
        // 1013.25 -> 1013.3 hPa. The frame includes COBS and its delimiter.
        assertContentEquals(byteArrayOf(7, 11, 0x27, 0x95.toByte(), -1, -1, 2, 0), serializeOwnshipPressureV1(1013.25f))
        assertContentEquals(byteArrayOf(11, 0x23, 0x28, -1, -1, 2),
            CobsByteArray(serializeOwnshipPressureV1(900f)).getBuffer())
        assertContentEquals(byteArrayOf(11, -1, -1, -1, -1, 2),
            CobsByteArray(serializeOwnshipPressureV1(null)).getBuffer())
        assertContentEquals(byteArrayOf(11, 0, 1, -1, -1, 2),
            CobsByteArray(serializeOwnshipPressureV1(0.1f)).getBuffer())
    }

    @Test
    fun rejectsInvalidOrUnrepresentablePressure() {
        for (pressure in listOf(0f, -1f, Float.NaN, Float.POSITIVE_INFINITY, 6553.5f, 0.01f)) {
            assertFailsWith<IllegalArgumentException> { serializeOwnshipPressureV1(pressure) }
        }
    }

    @Test
    fun freshnessRejectsFutureAndExpiredSamples() {
        assertTrue(PhonePressureSample(1000f, 100).isFresh(5099))
        assertFalse(PhonePressureSample(1000f, 100).isFresh(5100))
        assertFalse(PhonePressureSample(1000f, 100).isFresh(99))
        assertFalse(PhonePressureSample(Float.NaN, 100).isFresh(100))
        assertFalse(PhonePressureSample(0.01f, 100).isFresh(100))
    }

    @Test
    fun frozenSensorIsNotResentAndInvalidatesOnce() = runTest {
        val source = FakeSource(PhonePressureSample(1000f, 0))
        val sent = mutableListOf<PhonePressureSample?>()
        var status = PhonePressureStatus()
        val job = launch {
            PhonePressureSession(source, { testScheduler.currentTime }, { sent.add(it); true }, { status = it }).run(true)
        }
        runCurrent()
        advanceTimeBy(10_000)
        runCurrent()
        assertEquals(listOf(PhonePressureSample(1000f, 0), null), sent)
        assertTrue(status.stale)
        assertEquals(1, status.packetsSent)
        job.cancelAndJoin()
        assertEquals(2, sent.size)
        assertEquals(1, source.stops)
        assertTrue(source.requestAllowed)
    }

    @Test
    fun sendsOnlyTheLatestSampleAtMostOncePerSecond() = runTest {
        val source = FakeSource(PhonePressureSample(1000f, 0))
        val sent = mutableListOf<PhonePressureSample?>()
        val job = launch {
            PhonePressureSession(source, { testScheduler.currentTime }, { sent.add(it); true }, {}).run(false)
        }
        runCurrent()
        advanceTimeBy(100)
        source.sample(1001f, 100)
        advanceTimeBy(100)
        source.sample(1002f, 200)
        advanceTimeBy(800)
        runCurrent()
        assertEquals(listOf(1000f, 1002f), sent.map { it?.pressureHpa })
        assertFalse(source.requestAllowed)
        // An out-of-order callback cannot replace the newer measurement.
        source.sample(1001f, 100)
        advanceTimeBy(1500)
        runCurrent()
        assertEquals(2, sent.size)
        job.cancelAndJoin()
        assertEquals(null, sent.last())
    }

    @Test
    fun anOutOfOrderCallbackCannotReplaceANewerUnsentSample() = runTest {
        val source = FakeSource(PhonePressureSample(1000f, 0))
        val sent = mutableListOf<PhonePressureSample?>()
        val job = launch {
            PhonePressureSession(source, { testScheduler.currentTime }, { sent.add(it); true }, {}).run(false)
        }
        runCurrent()
        advanceTimeBy(250)
        source.sample(1002f, 200)
        runCurrent()
        advanceTimeBy(250)
        source.sample(1001f, 100)
        runCurrent()
        advanceTimeBy(500)
        runCurrent()
        assertEquals(listOf(1000f, 1002f), sent.map { it?.pressureHpa })
        job.cancelAndJoin()
    }

    @Test
    fun sensorFailureReleasesTheSourceAndInvalidatesAcceptedPressure() = runTest {
        val source = FakeSource(PhonePressureSample(1000f, 0))
        val sent = mutableListOf<PhonePressureSample?>()
        var status = PhonePressureStatus()
        val job = launch {
            PhonePressureSession(source, { testScheduler.currentTime }, { sent.add(it); true }, { status = it }).run(false)
        }
        runCurrent()
        source.state.value = PressureSensorState(PressureSensorCondition.Error, message = "sensor unavailable")
        advanceTimeBy(250)
        runCurrent()
        job.join()
        assertEquals(1, source.stops)
        assertEquals(2, sent.size)
        assertEquals(null, sent.last())
        assertEquals(PressureSensorCondition.Error, status.condition)
    }

    @Test
    fun missingSensorAndDeniedPermissionDoNotSendFrames() = runTest {
        for (condition in listOf(PressureSensorCondition.Unavailable, PressureSensorCondition.PermissionDenied)) {
            val source = FakeSource(null, condition)
            val sent = mutableListOf<PhonePressureSample?>()
            PhonePressureSession(source, { testScheduler.currentTime }, { sent.add(it); true }, {}).run(false)
            assertTrue(sent.isEmpty())
            assertEquals(1, source.stops)
        }
    }

    @Test
    fun bluetoothFailureStopsTheSourceWithoutAffectingOtherServices() = runTest {
        val source = FakeSource(PhonePressureSample(1000f, 0))
        var status = PhonePressureStatus()
        PhonePressureSession(source, { testScheduler.currentTime },
            { throw IllegalStateException("Bluetooth write failed") }, { status = it }).run(false)
        assertEquals(1, source.stops)
        assertEquals(PressureSensorCondition.Error, status.condition)
        assertEquals("Bluetooth write failed", status.message)
    }

    @Test
    fun aNewConnectionWaitsForItsOwnNewMeasurement() = runTest {
        val sentA = mutableListOf<PhonePressureSample?>()
        val sentB = mutableListOf<PhonePressureSample?>()
        val sourceA = FakeSource(PhonePressureSample(1000f, 0))
        val a = launch { PhonePressureSession(sourceA, { testScheduler.currentTime }, { sentA.add(it); true }, {}).run(false) }
        runCurrent()
        a.cancelAndJoin()
        val sourceB = FakeSource(null)
        val b = launch { PhonePressureSession(sourceB, { testScheduler.currentTime }, { sentB.add(it); true }, {}).run(false) }
        runCurrent()
        sourceA.sample(1100f, testScheduler.currentTime)
        advanceTimeBy(1000)
        runCurrent()
        assertTrue(sentB.isEmpty())
        sourceB.sample(900f, testScheduler.currentTime)
        advanceTimeBy(250)
        runCurrent()
        assertEquals(900f, sentB.single()?.pressureHpa)
        assertEquals(1000f, sentA.first()?.pressureHpa)
        b.cancelAndJoin()
    }

    private class FakeSource(sample: PhonePressureSample?, condition: PressureSensorCondition =
        if (sample == null) PressureSensorCondition.Waiting else PressureSensorCondition.Available) : PhonePressureSource {
        override val state = MutableStateFlow(PressureSensorState(condition, sample))
        var stops = 0
        var requestAllowed = false
        override fun start(allowPermissionRequest: Boolean) { requestAllowed = allowPermissionRequest }
        override fun stop() { stops++ }
        fun sample(pressure: Float, timestamp: Long) {
            state.value = PressureSensorState(PressureSensorCondition.Available, PhonePressureSample(pressure, timestamp))
        }
    }
}

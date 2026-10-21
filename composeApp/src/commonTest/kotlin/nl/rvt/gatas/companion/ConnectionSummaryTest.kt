package nl.rvt.gatas.companion

import nl.rvt.gatas.companion.pressure.PhonePressureStatus
import nl.rvt.gatas.companion.pressure.PressureSensorCondition
import nl.rvt.gatas.companion.services.BridgeStatus
import nl.rvt.gatas.companion.services.Gdl90BridgeStatus
import nl.rvt.gatas.companion.services.Gdl90State
import kotlin.test.Test
import kotlin.test.assertEquals

class ConnectionSummaryTest {
    @Test
    fun optionalFeaturesAreDisabledRatherThanReportedAsFailures() {
        val indicators = connectionIndicators(BridgeStatus())
        assertEquals(listOf("Bluetooth", "UDP Relay", "GDL90", "Phone Barometer"), indicators.map { it.title })
        assertEquals(listOf(ConnectionIndicatorState.Attention, ConnectionIndicatorState.Attention,
            ConnectionIndicatorState.Disabled, ConnectionIndicatorState.Disabled), indicators.map { it.state })
        assertEquals(listOf("Disabled", "Disabled"), indicators.takeLast(2).map { it.text })
    }

    @Test
    fun allHealthySignalsAreGreen() {
        val status = BridgeStatus(
            bleConnected = true, udpHealthy = true,
            gdl90 = Gdl90BridgeStatus(enabled = true, state = Gdl90State.Sending, packetsSent = 1),
            phonePressure = PhonePressureStatus(enabled = true, condition = PressureSensorCondition.Available, packetsSent = 1),
        )
        assertEquals(List(4) { ConnectionIndicatorState.Healthy }, connectionIndicators(status).map { it.state })
    }

    @Test
    fun waitingAndErrorsDoNotBecomeHealthyFromOldCounters() {
        val status = BridgeStatus(
            connecting = true,
            gdl90 = Gdl90BridgeStatus(enabled = true, state = Gdl90State.Error, packetsSent = 10, lastError = "Send failed"),
            phonePressure = PhonePressureStatus(enabled = true, condition = PressureSensorCondition.Available, packetsSent = 10),
        )
        val indicators = connectionIndicators(status)
        assertEquals(List(4) { ConnectionIndicatorState.Attention }, indicators.map { it.state })
        assertEquals("Connecting…", indicators[0].text)
        assertEquals("Forwarding error", indicators[2].text)
        assertEquals("Waiting for Bluetooth", indicators[3].text)
    }

    @Test
    fun enablingGdl90RequiresASuccessfulSend() {
        val indicator = connectionIndicators(BridgeStatus(bleConnected = true, gdl90 = Gdl90BridgeStatus(enabled = true)))[2]
        assertEquals(ConnectionIndicatorState.Attention, indicator.state)
        assertEquals("Waiting for GDL90 frames", indicator.text)
    }

    @Test
    fun bluetoothLossDoesNotLeaveGdl90GreenFromOldCounters() {
        val indicator = connectionIndicators(BridgeStatus(
            bleConnected = false,
            gdl90 = Gdl90BridgeStatus(enabled = true, state = Gdl90State.Sending, packetsSent = 10),
        ))[2]
        assertEquals(ConnectionIndicatorState.Attention, indicator.state)
        assertEquals("Waiting for Bluetooth", indicator.text)
    }

    @Test
    fun pressureRequiresFreshAvailableDataAndSuccessfulDelivery() {
        val available = PhonePressureStatus(enabled = true, condition = PressureSensorCondition.Available, packetsSent = 1)
        listOf(
            available.copy(stale = true),
            available.copy(condition = PressureSensorCondition.PermissionDenied),
            available.copy(condition = PressureSensorCondition.PermissionRequired),
            available.copy(condition = PressureSensorCondition.Unavailable),
            available.copy(condition = PressureSensorCondition.Error),
            available.copy(condition = PressureSensorCondition.Waiting),
            available.copy(packetsSent = 0),
        ).forEach { pressure ->
            assertEquals(ConnectionIndicatorState.Attention,
                connectionIndicators(BridgeStatus(bleConnected = true, phonePressure = pressure))[3].state)
        }
    }

    @Test
    fun udpActivityRequiresAReceivedResponseRatherThanAnAttemptedRequest() {
        val confirmed = BridgeStatus(udpHealthy = true, framesRelayed = 4, serverActivityTick = 8)
        val requestStarted = confirmed.copy(serverActivityTick = 9)
        val responseReceived = requestStarted.copy(framesRelayed = 5, serverActivityTick = 10)

        assertEquals(4, connectionIndicators(confirmed)[1].activityCount)
        assertEquals(4, connectionIndicators(requestStarted)[1].activityCount)
        assertEquals(5, connectionIndicators(responseReceived)[1].activityCount)
    }

    @Test
    fun pressureActivityRequiresDeliveryRatherThanJustAnotherSensorSample() {
        val sent = PhonePressureStatus(enabled = true, condition = PressureSensorCondition.Available,
            samplesReceived = 10, packetsSent = 1)
        fun activity(pressure: PhonePressureStatus) =
            connectionIndicators(BridgeStatus(bleConnected = true, phonePressure = pressure))[3].activityCount

        assertEquals(1, activity(sent))
        assertEquals(1, activity(sent.copy(samplesReceived = 11)))
        assertEquals(2, activity(sent.copy(samplesReceived = 11, packetsSent = 2)))
    }
}

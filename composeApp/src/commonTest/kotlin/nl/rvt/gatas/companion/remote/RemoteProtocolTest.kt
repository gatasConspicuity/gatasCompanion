package nl.rvt.gatas.companion.remote

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import nl.rvantwisk.gatas.lib.models.OwnshipAircraftConfiguration
import nl.rvantwisk.gatas.lib.models.WifiMode
import nl.rvt.gatas.companion.CompanionState
import nl.rvt.gatas.companion.GaTasDevice
import nl.rvt.gatas.companion.bluetooth.DiscoveryState
import nl.rvt.gatas.companion.services.BridgeStatus
import nl.rvt.gatas.companion.services.BridgeStatusReducer

class RemoteProtocolTest {
    @Test fun deviceActionsRequireCapabilitiesAndObservedBootAndExcludeUsbBoot() {
        val control = nl.rvt.gatas.companion.services.DeviceControlState(capabilities = 7, bootId = "0102030405060708", hasUnsavedChanges = true)
        val supported = connected.copy(bridge = connected.bridge.copy(deviceControl = control))
        for (action in listOf("saveConfiguration", "restartGatas")) {
            val request = command(action).copy(expectedBootId = control.bootId)
            RemoteCommandPolicy.validate(request, supported)
            assertFailsWith<IllegalArgumentException> { RemoteCommandPolicy.validate(request, connected) }
            assertFailsWith<IllegalArgumentException> { RemoteCommandPolicy.validate(request.copy(expectedBootId = "old-boot"), supported) }
            assertFailsWith<IllegalArgumentException> { RemoteCommandPolicy.validate(request.copy(expectedDeviceId = "another-device"), supported) }
        }
        assertFailsWith<IllegalStateException> { RemoteCommandPolicy.validate(command("usbBoot"), supported) }
        assertFailsWith<IllegalStateException> { RemoteCommandPolicy.validate(command("enterFirmwareUpdateMode"), supported) }
    }
    @Test fun gdl90SettingIsVisibleWithoutWaitingForAnotherPacket() {
        val enabled = BridgeStatusReducer.applyGdl90Setting(BridgeStatus(), true)
        assertEquals(true, enabled.gdl90.enabled)
        assertEquals(nl.rvt.gatas.companion.services.Gdl90State.WaitingForFrames, enabled.gdl90.state)
        val disabled = BridgeStatusReducer.applyGdl90Setting(enabled, false)
        assertEquals(false, disabled.gdl90.enabled)
        assertEquals(nl.rvt.gatas.companion.services.Gdl90State.Disabled, disabled.gdl90.state)
    }

    @Test fun deviceActionBlocksConfigurationChangesButKeepsStopAvailable() {
        val busy = connected.copy(bridge = connected.bridge.copy(deviceControl =
            nl.rvt.gatas.companion.services.DeviceControlState(capabilities = 7, bootId = "0102030405060708",
                operation = nl.rvt.gatas.companion.services.DeviceOperation("save", "saveConfiguration", "executing", "Saving"))))
        RemoteCommandPolicy.validate(command("stop"), busy)
        for (request in listOf(command("setAircraft", aircraft = 456), command("setWifi", mode = "CLIENT"),
            command("saveConfiguration"), command("restartGatas"))) {
            assertFailsWith<IllegalArgumentException> { RemoteCommandPolicy.validate(request, busy) }
        }
    }

    @Test fun wifiTimeoutClearsOnlyItsOwnPendingRequest() {
        val pending = BridgeStatus(wifiModeChangeTarget = WifiMode.CLIENT)
        val unknown = BridgeStatusReducer.wifiChangeUnconfirmed(pending, WifiMode.CLIENT, "Not confirmed")
        assertEquals(null, unknown.wifiModeChangeTarget)
        assertEquals("Not confirmed", unknown.wifiModeStatusMessage)
        assertEquals(pending, BridgeStatusReducer.wifiChangeUnconfirmed(pending, WifiMode.AP, "Old timeout"))
    }

    private val device = GaTasDevice("GATAS", "device-a")
    private val configuration = OwnshipAircraftConfiguration(1u, 0u, 123, null, listOf(123, 456), 0u, 0, 1, WifiMode.AP)
    private val connected = CompanionState(setOf(device), true,
        bridge = BridgeStatus(running = true, bleConnected = true, ownshipConfiguration = configuration), activeDevice = device)

    private fun command(action: String, deviceId: String? = null, expected: String? = device.identifier,
                        aircraft: Long? = null, mode: String? = null) =
        RemoteCommand(id = "request-1", action = action, expiresAt = 1010.0, deviceId = deviceId, expectedDeviceId = expected,
            icaoAddress = aircraft, mode = mode)

    @Test fun expiredMissingOrFarFutureCommandsAreNeverExecuted() {
        var executions = 0
        val processor = RemoteCommandProcessor(now = { 1000.0 }) { executions++; RemoteReply(it.id, "completed", "Done") }
        for (expiration in listOf(null, 999.0, 1040.0)) {
            val reply = remoteJson.decodeFromString<RemoteReply>(processor.handle(remoteJson.encodeToString(
                command("stop").copy(expiresAt = expiration))))
            assertEquals("rejected", reply.status)
        }
        assertEquals(0, executions)
    }

    @Test fun repeatedDeliveryExecutesExactlyOnce() {
        var executions = 0
        val processor = RemoteCommandProcessor(now = { 1000.0 }) { executions++; RemoteReply(it.id, "accepted", "Waiting for hardware") }
        val encoded = remoteJson.encodeToString(command("stop"))
        assertEquals(processor.handle(encoded), processor.handle(encoded))
        assertEquals(1, executions)
    }

    @Test fun reusingAnIdForAnotherActionIsRejected() {
        var executions = 0
        val processor = RemoteCommandProcessor(now = { 1000.0 }) { executions++; RemoteReply(it.id, "completed", "Done") }
        processor.handle(remoteJson.encodeToString(command("stop")))
        val reply = remoteJson.decodeFromString<RemoteReply>(processor.handle(remoteJson.encodeToString(command("start"))))
        assertEquals("rejected", reply.status)
        assertEquals(1, executions)
    }

    @Test fun malformedOrFutureMessagesCannotReachTheDevice() {
        var executions = 0
        val processor = RemoteCommandProcessor(now = { 1000.0 }) { executions++; RemoteReply(it.id, "completed", "Done") }
        for (encoded in listOf("not json", "{}", remoteJson.encodeToString(command("stop").copy(version = 2)),
            remoteJson.encodeToString(command("stop").copy(id = "")))) {
            assertEquals("rejected", remoteJson.decodeFromString<RemoteReply>(processor.handle(encoded)).status)
        }
        assertEquals(0, executions)
    }

    @Test fun policyErrorsProduceAnExplicitReply() {
        val processor = RemoteCommandProcessor(now = { 1000.0 }) { RemoteCommandPolicy.validate(it, connected); RemoteReply(it.id, "completed", "Done") }
        val reply = remoteJson.decodeFromString<RemoteReply>(processor.handle(remoteJson.encodeToString(command("flash"))))
        assertEquals("rejected", reply.status)
        assertEquals("Unsupported command", reply.message)
    }

    @Test fun staleCommandsCannotStopOrReconfigureAnotherDevice() {
        for (action in listOf("stop", "setAircraft", "setWifi")) {
            assertFailsWith<IllegalArgumentException> {
                RemoteCommandPolicy.validate(command(action, expected = "previous-device", aircraft = 456, mode = "CLIENT"), connected)
            }
        }
    }

    @Test fun switchingDevicesRequiresTheObservedCurrentDevice() {
        RemoteCommandPolicy.validate(command("start", deviceId = device.identifier), connected)
        assertFailsWith<IllegalArgumentException> {
            RemoteCommandPolicy.validate(command("start", deviceId = device.identifier, expected = null), connected)
        }
        RemoteCommandPolicy.validate(command("start", deviceId = device.identifier, expected = null), connected.copy(activeDevice = null))
    }

    @Test fun disconnectedHardwareRejectsChangesButAllowsStop() {
        val disconnected = connected.copy(bridge = connected.bridge.copy(bleConnected = false))
        RemoteCommandPolicy.validate(command("stop"), disconnected)
        assertFailsWith<IllegalArgumentException> { RemoteCommandPolicy.validate(command("setAircraft", aircraft = 456), disconnected) }
        assertFailsWith<IllegalArgumentException> { RemoteCommandPolicy.validate(command("setWifi", mode = "CLIENT"), disconnected) }
    }

    @Test fun aircraftMustStillExistInDeviceConfiguration() {
        RemoteCommandPolicy.validate(command("setAircraft", aircraft = 456), connected)
        assertFailsWith<IllegalArgumentException> { RemoteCommandPolicy.validate(command("setAircraft", aircraft = 999), connected) }
        assertFailsWith<IllegalArgumentException> { RemoteCommandPolicy.validate(command("setAircraft"), connected) }
    }

    @Test fun oldFirmwareAndUnknownWifiModesCannotBeControlled() {
        RemoteCommandPolicy.validate(command("setWifi", mode = "CLIENT"), connected)
        for (mode in listOf(null, WifiMode.NC)) {
            assertFailsWith<IllegalArgumentException> {
                RemoteCommandPolicy.validate(command("setWifi", mode = "CLIENT"), connected.copy(bridge = connected.bridge.copy(
                    ownshipConfiguration = configuration.copy(wifiMode = mode))))
            }
        }
        assertFailsWith<IllegalArgumentException> { RemoteCommandPolicy.validate(command("setWifi", mode = "NC"), connected) }
    }

    @Test fun deviceManagementCannotDeleteActiveOrInventDevices() {
        assertFailsWith<IllegalArgumentException> { RemoteCommandPolicy.validate(command("deleteDevice", device.identifier), connected) }
        assertFailsWith<IllegalArgumentException> { RemoteCommandPolicy.validate(command("saveDevice", device.identifier), connected) }
        RemoteCommandPolicy.validate(command("saveDevice", device.identifier), connected.copy(discovery = DiscoveryState(listOf(device))))
        RemoteCommandPolicy.validate(command("deleteDevice", device.identifier), connected.copy(activeDevice = null))
    }
}

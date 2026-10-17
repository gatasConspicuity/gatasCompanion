package nl.rvt.gatas.companion

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import nl.rvantwisk.gatas.lib.models.WifiMode
import nl.rvt.gatas.companion.background.BridgeBackgroundComponent
import nl.rvt.gatas.companion.bluetooth.DeviceDiscovery
import nl.rvt.gatas.companion.bluetooth.DiscoveryState
import nl.rvt.gatas.companion.remote.RemoteCommand
import nl.rvt.gatas.companion.remote.RemoteCommandPolicy
import nl.rvt.gatas.companion.remote.RemoteCommandProcessor
import nl.rvt.gatas.companion.remote.RemoteReply
import nl.rvt.gatas.companion.remote.WatchSnapshot
import nl.rvt.gatas.companion.remote.remoteJson
import nl.rvt.gatas.companion.services.BridgeStatus
import nl.rvt.gatas.companion.services.BridgeStatusReducer
import nl.rvt.gatas.companion.services.DeviceAction
import nl.rvt.gatas.companion.services.DeviceActionCommand
import nl.rvt.gatas.companion.services.DeviceUpdatePolicy
import kotlin.uuid.Uuid
import kotlin.uuid.ExperimentalUuidApi
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

data class CompanionState(
    val devices: Set<GaTasDevice>,
    val gdl90Enabled: Boolean,
    val bridge: BridgeStatus = BridgeStatus(),
    val activeDevice: GaTasDevice? = null,
    val discovery: DiscoveryState = DiscoveryState(),
    val aircraft: List<AircraftPickerEntry> = emptyList(),
    val aircraftLoading: Boolean = false,
    val aircraftError: String? = null,
    val actionError: String? = null,
)

/** App-scoped control plane. All entry points and state mutations run on Main. */
object CompanionController {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val mutableState = MutableStateFlow(CompanionState(
        KnownDeviceStore.loadDevices(), Gdl90BridgeSettings.isEnabled(),
    ))
    val state = mutableState.asStateFlow()
    private var aircraftJob: Job? = null
    private val processor = RemoteCommandProcessor(execute = ::execute)

    init {
        scope.launch { BridgeBackgroundComponent.status.collect { bridge ->
            mutableState.value = state.value.copy(bridge = BridgeStatusReducer.applyGdl90Setting(bridge, state.value.gdl90Enabled))
        } }
        scope.launch { BridgeBackgroundComponent.activeDevice.collect { device ->
            mutableState.value = state.value.copy(activeDevice = device)
        } }
        scope.launch { DeviceDiscovery.state.collect { discovery ->
            mutableState.value = state.value.copy(discovery = discovery)
        } }
        scope.launch {
            state.map { it.activeDevice?.identifier to it.bridge.ownshipConfiguration?.icaoAddressList }
                .distinctUntilChanged().collect { (_, addresses) -> loadAircraft(addresses.orEmpty()) }
        }
    }

    fun initialize() = Unit

    fun connect(device: GaTasDevice): Boolean {
        if (state.value.devices.none { it.identifier == device.identifier }) {
            mutableState.value = state.value.copy(actionError = "Device is no longer saved")
            return false
        }
        KnownDeviceStore.saveLastUsedDevice(device)
        BridgeBackgroundComponent.start(device)
        return true
    }

    fun stop() = BridgeBackgroundComponent.stop()

    fun saveDevice(device: GaTasDevice) {
        val devices = state.value.devices.filterNot { it.identifier == device.identifier }.toSet() + device
        KnownDeviceStore.saveDevices(devices)
        mutableState.value = state.value.copy(devices = devices)
    }

    fun deleteDevice(device: GaTasDevice) {
        // The phone's confirmation dialog may have been opened before a Watch
        // command connected this device. Report that conflict without crashing.
        if (BridgeBackgroundComponent.activeDevice.value?.identifier == device.identifier) {
            mutableState.value = state.value.copy(actionError = "Stop the bridge before removing this device")
            return
        }
        val devices = state.value.devices.filterNot { it.identifier == device.identifier }.toSet()
        KnownDeviceStore.saveDevices(devices)
        if (KnownDeviceStore.loadLastUsedDevice()?.identifier == device.identifier) KnownDeviceStore.saveLastUsedDevice(null)
        mutableState.value = state.value.copy(devices = devices)
    }

    fun dismissActionError() { mutableState.value = state.value.copy(actionError = null) }

    fun setGdl90Enabled(enabled: Boolean) {
        Gdl90BridgeSettings.setEnabled(enabled)
        mutableState.value = state.value.copy(gdl90Enabled = enabled,
            bridge = BridgeStatusReducer.applyGdl90Setting(state.value.bridge, enabled))
    }

    fun changeAircraft(address: Long) = BridgeBackgroundComponent.requestAircraftChange(address)
    fun changeWifiMode(mode: WifiMode) = BridgeBackgroundComponent.requestWifiModeChange(mode)

    @OptIn(ExperimentalUuidApi::class)
    fun requestDeviceAction(action: DeviceAction, deviceId: String?, bootId: String?,
                            policy: DeviceUpdatePolicy = DeviceUpdatePolicy.RequireSaved,
                            id: String = Uuid.random().toString()): Boolean {
        val bridge = BridgeBackgroundComponent.status.value
        val active = BridgeBackgroundComponent.activeDevice.value?.identifier
        val valid = deviceId != null && deviceId == active && bootId != null &&
            bridge.bleConnected && bridge.deviceControl.bootId == bootId &&
            bridge.deviceControl.supports(action) && bridge.deviceControl.operation?.busy != true
        val accepted = valid && BridgeBackgroundComponent.requestDeviceAction(
            DeviceActionCommand(id, action, requireNotNull(deviceId), requireNotNull(bootId), policy))
        if (!accepted) mutableState.value = state.value.copy(actionError = "GATAS connection changed or is busy. Refresh before trying again.")
        return accepted
    }
    fun refreshAircraft() = loadAircraft(state.value.bridge.ownshipConfiguration?.icaoAddressList.orEmpty())

    private fun loadAircraft(addresses: List<Long>) {
        aircraftJob?.cancel()
        mutableState.value = state.value.copy(aircraft = emptyList(), aircraftLoading = addresses.isNotEmpty(), aircraftError = null)
        if (addresses.isEmpty()) return
        aircraftJob = scope.launch {
            val cached = AircraftLookupService.loadCachedAircraftPickerEntries(addresses).associateBy { it.icaoAddress }
            mutableState.value = state.value.copy(aircraft = addresses.distinct().map { address ->
                cached[address] ?: AircraftPickerEntry(address, address.toString(16).uppercase().padStart(6, '0'), "-", "-")
            })
            try {
                val entries = AircraftLookupService.loadAircraftPickerEntries(addresses)
                mutableState.value = state.value.copy(aircraft = entries, aircraftLoading = false)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                mutableState.value = state.value.copy(aircraftLoading = false, aircraftError = error.message ?: "Unable to load aircraft")
            }
        }
    }

    fun handleRemoteCommand(encoded: String): String = processor.handle(encoded)

    private fun execute(command: RemoteCommand): RemoteReply {
        // Read the service directly so validation cannot race its Flow collector.
        RemoteCommandPolicy.validate(command, state.value.copy(
            bridge = BridgeBackgroundComponent.status.value,
            activeDevice = BridgeBackgroundComponent.activeDevice.value,
            discovery = DeviceDiscovery.state.value,
        ))
        fun completed(message: String) = RemoteReply(command.id, "completed", message)
        fun accepted(message: String) = RemoteReply(command.id, "accepted", message)
        return when (command.action) {
            "start" -> {
                connect(state.value.devices.first { it.identifier == command.deviceId })
                accepted("Connection requested. Waiting for Bluetooth.")
            }
            "stop" -> { stop(); completed("Bridge stopped") }
            "setAircraft" -> {
                changeAircraft(requireNotNull(command.icaoAddress))
                accepted("Aircraft change requested. Waiting for GATAS confirmation.")
            }
            "setWifi" -> {
                changeWifiMode(WifiMode.valueOf(requireNotNull(command.mode)))
                accepted("Wi-Fi change requested. Waiting for GATAS confirmation.")
            }
            "saveConfiguration", "restartGatas" -> {
                val action = if (command.action == "saveConfiguration") DeviceAction.Save else DeviceAction.Restart
                check(requestDeviceAction(action, command.expectedDeviceId, command.expectedBootId, id = command.id)) {
                    "GATAS connection changed or is busy. Refresh before trying again."
                }
                accepted("Waiting for GATAS confirmation.")
            }
            "setGdl90" -> { setGdl90Enabled(requireNotNull(command.enabled)); completed("GDL90 setting saved") }
            "startScan" -> { DeviceDiscovery.start("watch"); accepted("Searching near your iPhone for 30 seconds") }
            "stopScan" -> { DeviceDiscovery.stop("watch"); completed("Watch search stopped") }
            "saveDevice" -> {
                saveDevice(DeviceDiscovery.state.value.devices.first { it.identifier == command.deviceId })
                completed("Device saved")
            }
            "deleteDevice" -> {
                deleteDevice(state.value.devices.first { it.identifier == command.deviceId })
                completed("Device removed")
            }
            "refreshAircraft" -> { refreshAircraft(); accepted("Refreshing aircraft details") }
            else -> error("Unsupported command")
        }
    }

    @OptIn(ExperimentalTime::class)
    fun snapshotJson(): String {
        // Replies are produced in the same Main turn as command dispatch. Read
        // service values directly, before their UI Flow collectors catch up.
        val current = state.value.copy(
            bridge = BridgeBackgroundComponent.status.value,
            activeDevice = BridgeBackgroundComponent.activeDevice.value,
            discovery = DeviceDiscovery.state.value,
        )
        val bridge = BridgeStatusReducer.applyGdl90Setting(current.bridge, current.gdl90Enabled)
        val gdl = bridge.gdl90
        val config = bridge.ownshipConfiguration
        val diagnostics = linkedMapOf(
            "UDP NMEA / COBS" to "${bridge.udpNmeaPackets} / ${bridge.udpCobsPackets}",
            "BLE NMEA / COBS" to "${bridge.bleNmeaPackets} / ${bridge.bleCobsPackets}",
            "Frames received / relayed" to "${bridge.framesReceived} / ${bridge.framesRelayed}",
            "Bytes received / relayed" to "${bridge.bytesReceived} / ${bridge.bytesRelayed}",
            "Relay queue drops" to "${bridge.relayQueueDrops}",
            "Relay queue delay (ms)" to (bridge.lastRelayQueueDelayMillis?.toString() ?: "—"),
            "Relay round trip (ms)" to (bridge.lastRelayRoundTripMillis?.toString() ?: "—"),
            "GDL90 state" to if (current.gdl90Enabled) gdl.state.name else "Disabled",
            "GDL90 packets / bytes" to "${gdl.packetsSent} / ${gdl.bytesSent}",
            "Traffic / ownship / heartbeat" to "${gdl.trafficMessages} / ${gdl.ownshipMessages} / ${gdl.heartbeatMessages}",
            "Other GDL90 messages" to "${gdl.otherMessages}",
            "GDL90 decode / send errors" to "${gdl.decodeErrors} / ${gdl.sendErrors}",
            "GDL90 dropped frames" to "${gdl.droppedFrames}",
            "Traffic interval / maximum (ms)" to "${gdl.lastTrafficIntervalMillis ?: 0} / ${gdl.maximumTrafficIntervalMillis}",
            "GDL90 forward time (ms)" to (gdl.lastForwardDurationMillis?.toString() ?: "—"),
            "GDL90 event" to gdl.lastEvent,
            "GDL90 error" to (gdl.lastError ?: "None"),
            "Active stream" to (bridge.activeStream ?: "—"),
        )
        return remoteJson.encodeToString(WatchSnapshot(
            deviceControl = bridge.deviceControl,
            updatedAt = Clock.System.now().toEpochMilliseconds() / 1000.0,
            devices = current.devices.sortedBy { it.name }, discoveredDevices = current.discovery.devices,
            scanning = current.discovery.watchScanning, scanError = current.discovery.error,
            activeDevice = current.activeDevice, running = bridge.running, connecting = bridge.connecting,
            bleConnected = bridge.bleConnected, udpHealthy = bridge.udpHealthy,
            gatasId = config?.gatasId?.toString(16)?.uppercase()?.padStart(8, '0'),
            selectedIcaoAddress = config?.icaoAddress,
            aircraft = current.aircraft.filter { config?.icaoAddressList?.contains(it.icaoAddress) == true },
            aircraftLoading = current.aircraftLoading, aircraftError = current.aircraftError,
            pendingAircraft = bridge.aircraftChangeTargetIcaoAddress, wifiMode = config?.wifiMode?.name,
            pendingWifiMode = bridge.wifiModeChangeTarget?.name, wifiMessage = bridge.wifiModeStatusMessage,
            gdl90Enabled = current.gdl90Enabled, lastEvent = bridge.lastEvent, lastError = bridge.lastError,
            diagnostics = diagnostics,
        ))
    }
}

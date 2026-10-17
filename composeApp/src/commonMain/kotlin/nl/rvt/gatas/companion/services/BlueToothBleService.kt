@file:OptIn(ExperimentalStdlibApi::class)

package nl.rvt.gatas.companion.services

import co.touchlab.kermit.Logger
import com.juul.kable.Advertisement
import com.juul.kable.Characteristic
import com.juul.kable.Peripheral
import com.juul.kable.Scanner
import com.juul.kable.State
import com.juul.kable.WriteType
import com.juul.kable.indicate
import com.juul.kable.notify
import com.juul.kable.characteristicOf
import com.juul.kable.logs.Logging.Level.Warnings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import nl.rvantwisk.gatas.lib.extensions.CobsByteArray
import nl.rvantwisk.gatas.lib.extensions.MessageType
import nl.rvantwisk.gatas.lib.extensions.deserializeAircraftConfigurationV1
import nl.rvantwisk.gatas.lib.extensions.deserializeAircraftConfigurationV2
import nl.rvantwisk.gatas.lib.extensions.serializeSetIcaoAddressV1
import nl.rvantwisk.gatas.lib.extensions.serializeSetWifiModeV1
import nl.rvantwisk.gatas.lib.models.SetIcaoAddressV1
import nl.rvantwisk.gatas.lib.models.WifiMode
import nl.rvt.gatas.companion.GaTasDevice
import nl.rvt.gatas.companion.Gdl90BridgeSettings
import nl.rvt.gatas.areDetailedDiagnosticsEnabled
import nl.rvt.gatas.companion.pressure.DevicePhonePressurePreference
import nl.rvt.gatas.companion.pressure.PhonePressureStatus
import nl.rvt.gatas.companion.pressure.isFresh
import nl.rvt.gatas.companion.pressure.phonePressureNowMillis
import nl.rvt.gatas.companion.pressure.runPhonePressureConnections
import nl.rvantwisk.gatas.lib.extensions.serializeOwnshipPressureV1
import nl.rvt.gatas.companion.bluetooth.GATAS_PRIMARY_DEVICE
import nl.rvt.gatas.companion.bluetooth.GATAS_COBS_CHARACTERISTIC
import nl.rvt.gatas.companion.bluetooth.GATAS_RXTX_CHARACTERISTIC
import nl.rvt.gatas.companion.liveactivity.GatasLiveActivityBridge
import nl.rvt.gatas.restorePeripheralIfPossible
import nl.rvt.gatas.requestMtuIfSupported
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import kotlin.uuid.ExperimentalUuidApi

private val log = Logger.withTag(BlueToothBleService::class.simpleName ?: "BlueToothBleService")

@OptIn(ExperimentalUuidApi::class)
class BlueToothBleService constructor(
    private val targetDevice: GaTasDevice,
    private val udpRelayService: GatasUdpRelayService = GatasUdpRelayService(),
    private val gdl90UdpBridgeService: Gdl90UdpBridgeService = Gdl90UdpBridgeService(),
) {
    private enum class LinkSide {
        Udp,
        Ble,
    }

    // Identity changes on every connection, including reconnection to the same peripheral.
    private class PressureConnection(val peripheral: Peripheral)

    private data class RelayWorkItem(
        val sequence: Long,
        val peripheral: Peripheral,
        val characteristic: Characteristic,
        val label: String,
        val messageType: Int,
        val payload: ByteArray,
        val enqueuedAt: TimeMark,
    )

    private data class PendingTrafficCycle(
        val sequence: Long,
        val serverAircraftPositions: Int,
        val sentToGatasAt: TimeMark,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _status = MutableStateFlow(BridgeStatus())
    val status: StateFlow<BridgeStatus> = _status.asStateFlow()

    private val nmeaCharacteristic: Characteristic =
        characteristicOf(GATAS_PRIMARY_DEVICE, GATAS_RXTX_CHARACTERISTIC)

    private val cobsCharacteristic: Characteristic =
        characteristicOf(GATAS_PRIMARY_DEVICE, GATAS_COBS_CHARACTERISTIC)

    private var connectedPeripheral: Peripheral? = null
    private val deviceActions = DeviceActionClient()
    private var deviceSession: Any? = null
    private val deviceActionScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    init {
        deviceActionScope.launch {
            deviceActions.state.collect { control -> _status.update { it.copy(deviceControl = control) } }
        }
    }

    // Main-confined reservation is shared by native phone and Watch controls.
    fun requestDeviceAction(command: DeviceActionCommand): Boolean {
        if (command.deviceId != targetDevice.identifier || !_status.value.bleConnected ||
            aircraftChangeJob?.isActive == true || wifiModeChangeJob?.isActive == true ||
            _status.value.aircraftChangeTargetIcaoAddress != null || _status.value.wifiModeChangeTarget != null) return false
        return deviceActions.request(command)
    }
    private var reconnectJob: Job? = null
    private var aircraftChangeJob: Job? = null
    private var wifiModeChangeJob: Job? = null
    private var stopJob: Job? = null
    private var phonePressureJob: Job? = null
    private val pressurePreference = DevicePhonePressurePreference(targetDevice.identifier)
    private val pressureConnections = MutableStateFlow<PressureConnection?>(null)
    private val packetWriter = BlePacketWriter()
    private var lastGdl90TrafficMark: TimeMark? = null
    private var gdl90DiagnosticWindowStartedAt = TimeSource.Monotonic.markNow()
    private var diagnosticHeartbeats = 0
    private var diagnosticOwnshipReports = 0
    private var diagnosticTrafficReports = 0
    private var diagnosticOtherMessages = 0
    private var nextRelaySequence = 1L
    private val trafficCycleMutex = Mutex()
    private var pendingTrafficCycle: PendingTrafficCycle? = null
    private val gdl90Forwarder = Gdl90Forwarder(gdl90UdpBridgeService)

    companion object {
        private const val RECONNECT_SCAN_TIMEOUT_MILLIS = 5_000L
        private const val RECONNECT_RETRY_DELAY_MILLIS = 1_000L
        private const val SERVICE_DISCOVERY_TIMEOUT_MILLIS = 5_000L
        private const val AIRCRAFT_CHANGE_TOTAL_TIMEOUT_MILLIS = 15_000L
        private const val AIRCRAFT_CHANGE_INITIAL_TIMEOUT_MILLIS = 6_000L
        private const val AIRCRAFT_CHANGE_RETRY_INTERVAL_MILLIS = 500L
        // Position requests are periodic and become stale quickly. One pending
        // request is sufficient; the queue replacement policy below preserves
        // control messages while allowing a newer position request to supersede
        // an older one.
        private const val RELAY_QUEUE_CAPACITY = 1
    }

    fun start() {
        if (_status.value.running || connectedPeripheral != null) {
            return
        }

        GatasLiveActivityBridge.setBridgeRunning(true)
        lastGdl90TrafficMark = null
        nextRelaySequence = 1L
        pendingTrafficCycle = null
        resetGdl90DiagnosticWindow()
        pressurePreference.reload()

        _status.update {
            it.copy(
                running = true,
                connecting = true,
                bleConnected = false,
                udpHealthy = false,
                activeStream = null,
                availableStreams = null,
                gdl90 = initialGdl90Status(),
                phonePressure = PhonePressureStatus(
                    enabled = pressurePreference.enabled,
                ),
                lastError = null,
                lastEvent = "Searching for GATAS BLE device..."
            )
        }

        val pendingStop = stopJob
        phonePressureJob = scope.launch(Dispatchers.Main) {
            pendingStop?.join()
            runPhonePressureConnections(
                preference = pressurePreference,
                connections = pressureConnections,
                send = { connection, sample ->
                    sendResponse(
                        connection.peripheral, cobsCharacteristic, "COBS", serializeOwnshipPressureV1(sample?.pressureHpa),
                        allowWhileStopping = sample == null,
                        payloadStillValid = {
                            pressureConnections.value === connection &&
                                (sample == null || (pressurePreference.enabled && sample.isFresh(phonePressureNowMillis())))
                        },
                    )
                },
                publish = { pressure -> _status.update { it.copy(phonePressure = pressure) } },
            )
        }
        reconnectJob = scope.launch {
            // UDP selectors and BLE observers from the previous run must be
            // fully closed before this run creates replacement resources.
            pendingStop?.join()
            while (true) {
                try {
                    connectedPeripheral = connectAndObserve()

                    val pressurePeripheral = connectedPeripheral
                    if (pressurePeripheral != null) {
                        pressureConnections.value = PressureConnection(pressurePeripheral)
                        _status.update {
                            it.copy(
                                connecting = false,
                                bleConnected = true,
                                activeStream = null,
                                gdl90 = initialGdl90Status(),
                                lastEvent = "Bluetooth connected, waiting for frames..."
                            )
                        }
                        connectedPeripheral?.state?.filterIsInstance<State.Disconnected>()
                            ?.first()
                        pressureConnections.value = null
                        val session = deviceSession
                        val usb = session != null && deviceActions.disconnected(session)
                        deviceSession = null
                        if (usb) {
                            withContext(Dispatchers.Main) { stop() }
                            return@launch
                        }
                        log.d("🔌 Disconnected. Reconnecting in ${RECONNECT_RETRY_DELAY_MILLIS}ms...")
                        _status.update {
                            it.copy(
                                bleConnected = false,
                                connecting = true,
                                activeStream = null,
                                availableStreams = null,
                                lastError = "Bluetooth disconnected",
                                lastEvent = "Bluetooth disconnected, reconnecting..."
                            )
                        }
                    }
                    connectedPeripheral?.close()
                    connectedPeripheral = null
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    pressureConnections.value = null
                    val session = deviceSession
                    val usb = session != null && deviceActions.disconnected(session)
                    deviceSession = null
                    // Some BLE stacks report the bootloader disconnect as an
                    // exception rather than a Disconnected state. Both paths
                    // must stop the bridge instead of reconnecting to USB boot.
                    if (usb) {
                        withContext(Dispatchers.Main) { stop() }
                        return@launch
                    }
                    log.e {
                        "⚠️ Connection attempt failed: ${e.message}. Retrying in ${RECONNECT_RETRY_DELAY_MILLIS}ms..."
                    }
                    _status.update {
                        it.copy(
                            connecting = true,
                            bleConnected = false,
                            activeStream = null,
                            availableStreams = null,
                            lastError = e.message ?: "Connection attempt failed",
                            lastEvent = "Failed to connect to Bluetooth device"
                        )
                    }
                }
                delay(RECONNECT_RETRY_DELAY_MILLIS)
            }
        }
    }

    fun stop() {
        if (!_status.value.running) return

        log.i { "Ble Service stopped" }
        val reconnectToStop = reconnectJob
        val aircraftChangeToStop = aircraftChangeJob
        val wifiChangeToStop = wifiModeChangeJob
        val pressureToStop = phonePressureJob
        reconnectJob = null
        aircraftChangeJob = null
        wifiModeChangeJob = null
        phonePressureJob = null
        deviceSession = null
        deviceActionScope.launch {
            deviceActions.stop()
            _status.update { it.copy(deviceControl = deviceActions.state.value) }
            deviceActionScope.cancel()
        }

        _status.update {
            it.copy(
                running = false,
                connecting = false,
                bleConnected = false,
                udpHealthy = false,
                activeStream = null,
                availableStreams = null,
                gdl90 = Gdl90BridgeStatus(),
                phonePressure = PhonePressureStatus(enabled = pressurePreference.enabled),
                lastEvent = "Bridge stopped",
                lastError = null
            )
        }
        GatasLiveActivityBridge.reset()

        stopJob = scope.launch {
            pressureToStop?.cancelAndJoin()
            pressureConnections.value = null
            aircraftChangeToStop?.cancelAndJoin()
            wifiChangeToStop?.cancelAndJoin()
            reconnectToStop?.cancelAndJoin()
            connectedPeripheral?.disconnect()
            connectedPeripheral?.close()
            connectedPeripheral = null
            udpRelayService.stop()
            gdl90UdpBridgeService.stop()
        }
    }

    fun requestPhonePressureChange(deviceId: String, enabled: Boolean) {
        if (!_status.value.running) return
        if (pressurePreference.change(deviceId, enabled)) {
            _status.update { it.copy(phonePressure = PhonePressureStatus(enabled = enabled)) }
        }
    }

    fun requestAircraftChange(icaoAddress: Long) {
        if (deviceActions.state.value.operation?.busy == true) return
        // Own the job before returning to Main, so a subsequent stop also
        // cancels commands which have not begun their Bluetooth write yet.
        val previous = aircraftChangeJob
        aircraftChangeJob = scope.launch {
            previous?.cancelAndJoin()
            if (!_status.value.running) return@launch
            try {
                val initialPeripheral = connectedPeripheral
                if (initialPeripheral == null) {
                    _status.update {
                        it.copy(
                            aircraftChangeTargetIcaoAddress = null,
                            lastError = "Bluetooth not connected",
                            lastEvent = "Cannot change aircraft while disconnected",
                        )
                    }
                    return@launch
                }

                val changeSucceeded = performAircraftChangeAttempt(
                    peripheral = initialPeripheral,
                    icaoAddress = icaoAddress,
                    attemptLabel = "Changing aircraft to ${icaoAddress.toIcaoHex()}",
                    timeoutMillis = AIRCRAFT_CHANGE_INITIAL_TIMEOUT_MILLIS,
                )
                if (changeSucceeded) {
                    return@launch
                }

                _status.update {
                    it.copy(
                        aircraftChangeTargetIcaoAddress = icaoAddress,
                        lastError = null,
                        lastEvent = "Aircraft change timed out, reconnecting Bluetooth to retry",
                    )
                }

                val retryPeripheral = reconnectBluetoothForAircraftChange()
                if (retryPeripheral == null) {
                    _status.update {
                        it.copy(
                            aircraftChangeTargetIcaoAddress = null,
                            lastError = "Timed out changing aircraft after reconnect",
                            lastEvent = "Aircraft change retry failed",
                        )
                    }
                    return@launch
                }

                if (!performAircraftChangeAttempt(
                        peripheral = retryPeripheral,
                        icaoAddress = icaoAddress,
                        attemptLabel = "Retrying aircraft change to ${icaoAddress.toIcaoHex()}",
                        timeoutMillis = AIRCRAFT_CHANGE_TOTAL_TIMEOUT_MILLIS - AIRCRAFT_CHANGE_INITIAL_TIMEOUT_MILLIS,
                    )
                ) {
                    _status.update {
                        it.copy(
                            aircraftChangeTargetIcaoAddress = null,
                            lastError = "Timed out changing aircraft",
                            lastEvent = "Aircraft change timed out",
                        )
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _status.update { it.copy(aircraftChangeTargetIcaoAddress = null,
                    lastError = error.message ?: "Bluetooth write failed",
                    lastEvent = "Aircraft change could not be confirmed") }
            }
        }
    }

    fun requestWifiModeChange(mode: WifiMode) {
        if (deviceActions.state.value.operation?.busy == true) return
        // Own the job before returning to Main, so a subsequent stop also
        // cancels commands which have not begun their Bluetooth write yet.
        val previous = wifiModeChangeJob
        wifiModeChangeJob = scope.launch {
            previous?.cancelAndJoin()
            if (!_status.value.running) return@launch
            val peripheral = connectedPeripheral
            if (peripheral == null) {
                _status.update { it.copy(wifiModeChangeTarget = null,
                    wifiModeStatusMessage = "Bluetooth not connected. Cannot change Wi-Fi mode.") }
                return@launch
            }
            if (_status.value.ownshipConfiguration?.wifiMode == mode) {
                _status.update { it.copy(wifiModeChangeTarget = null,
                    wifiModeStatusMessage = "GATAS is already using ${mode.displayName()} mode.") }
                return@launch
            }
            // Publish pending before the write: a fast configuration echo
            // must not be overwritten with a new pending flag afterwards.
            _status.update { it.copy(wifiModeChangeTarget = mode, lastError = null,
                wifiModeStatusMessage = "Request sent. Waiting for GATAS to report ${mode.displayName()} mode.") }
            try {
                sendWifiModeChangeCommand(peripheral, mode)
                val confirmed = withTimeoutOrNull(15_000) {
                    status.first { it.ownshipConfiguration?.wifiMode == mode }
                }
                if (confirmed == null) {
                    _status.update { BridgeStatusReducer.wifiChangeUnconfirmed(it, mode,
                        "GATAS has not confirmed the Wi-Fi change. Check its current mode before trying again.") }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _status.update { BridgeStatusReducer.wifiChangeUnconfirmed(it, mode,
                    "Wi-Fi command failed: ${error.message ?: "Bluetooth write failed"}") }
            }
        }
    }

    private suspend fun performAircraftChangeAttempt(
        peripheral: Peripheral,
        icaoAddress: Long,
        attemptLabel: String,
        timeoutMillis: Long,
    ): Boolean {
        _status.update {
            it.copy(
                aircraftChangeTargetIcaoAddress = icaoAddress,
                lastError = null,
                lastEvent = attemptLabel,
            )
        }

        val startedAt = TimeSource.Monotonic.markNow()
        while (startedAt.elapsedNow().inWholeMilliseconds < timeoutMillis) {
            if (_status.value.ownshipConfiguration?.icaoAddress == icaoAddress) {
                finalizeAircraftChange(
                    icaoAddress = icaoAddress,
                    lastEvent = "Aircraft changed to ${icaoAddress.toIcaoHex()}",
                )
                return true
            }

            sendAircraftChangeCommand(peripheral, icaoAddress)
            val remainingMillis = timeoutMillis - startedAt.elapsedNow().inWholeMilliseconds
            if (remainingMillis <= 0L) {
                break
            }
            delay(minOf(AIRCRAFT_CHANGE_RETRY_INTERVAL_MILLIS, remainingMillis))
        }

        if (_status.value.ownshipConfiguration?.icaoAddress == icaoAddress) {
            finalizeAircraftChange(
                icaoAddress = icaoAddress,
                lastEvent = "Aircraft changed to ${icaoAddress.toIcaoHex()}",
            )
            return true
        }

        return false
    }

    private suspend fun reconnectBluetoothForAircraftChange(): Peripheral? {
        val peripheral = connectedPeripheral ?: return null

        runCatching { peripheral.disconnect() }
        runCatching { peripheral.close() }
        connectedPeripheral = null

        val startedAt = TimeSource.Monotonic.markNow()
        while (startedAt.elapsedNow().inWholeMilliseconds < AIRCRAFT_CHANGE_TOTAL_TIMEOUT_MILLIS) {
            val reconnectedPeripheral = connectedPeripheral
            if (reconnectedPeripheral != null && _status.value.bleConnected) {
                return reconnectedPeripheral
            }
            delay(250)
        }

        return null
    }

    private suspend fun connectAndObserve(): Peripheral? {
        val peripheral = restorePeripheral() ?: run {
            val device = findReconnectTarget() ?: run {
                log.w { "❌ Device not found: ${targetDevice.identifier} (${targetDevice.name})" }
                _status.update {
                    it.copy(
                        lastError = "Bluetooth device not found",
                        lastEvent = "Saved Bluetooth ID not found, retrying..."
                    )
                }
                return null
            }

            log.i { "📡 Connecting to ${device.name} (${device.identifier})..." }
            Peripheral(device) {}
        }

        log.i { "📡 Connecting to ${targetDevice.name} (${peripheral.identifier})..." }
        val connectionScope = peripheral.connect()
        requestMtuIfSupported(peripheral)

        log.i { "✅ Connected to ${targetDevice.name}" }

        val nmeaObservable = observableCharacteristic(peripheral, nmeaCharacteristic, "NMEA")
        val cobsObservable = observableCharacteristic(peripheral, cobsCharacteristic, "COBS")
        val session = Any()
        deviceSession = session
        _status.update {
            it.copy(
                activeStream = buildList {
                    if (nmeaObservable != null) add("NMEA")
                    if (cobsObservable != null) add("COBS")
                }.takeIf { it.isNotEmpty() }?.joinToString(" / ") ?: "No observable stream"
            )
        }
        _status.update {
            it.copy(
                availableStreams = buildList {
                    if (nmeaObservable != null) add("NMEA")
                    if (cobsObservable != null) add("COBS")
                }.takeIf { it.isNotEmpty() }?.joinToString(" / ") ?: "No observable stream"
            )
        }

        connectionScope.launch {
            observeIncomingNotifications(
                connectionScope = connectionScope,
                peripheral = peripheral,
                nmeaObservable = nmeaObservable,
                cobsObservable = cobsObservable,
                deviceSession = session,
            )
        }
        deviceActions.connected(session) { bytes ->
            sendResponse(peripheral, cobsCharacteristic, "COBS", bytes,
                payloadStillValid = { deviceSession === session && connectedPeripheral === peripheral && _status.value.running })
        }
        return peripheral
    }

    private fun restorePeripheral(): Peripheral? {
        return restorePeripheralIfPossible(targetDevice.identifier)
    }

    private suspend fun observeIncomingNotifications(
        connectionScope: CoroutineScope,
        peripheral: Peripheral,
        nmeaObservable: Characteristic?,
        cobsObservable: Characteristic?,
        deviceSession: Any,
    ) {
        try {
            // Network round trips must not execute inside the BLE notification
            // collector. A slow or timing-out relay server would otherwise delay
            // subsequent GDL90 notifications before they reach the local EFB.
            val relayQueue = Channel<RelayWorkItem>(capacity = RELAY_QUEUE_CAPACITY)
            connectionScope.launch {
                for (workItem in relayQueue) {
                    processRelayWorkItem(workItem)
                }
            }

            if (nmeaObservable != null) {
                connectionScope.launchCharacteristicObserver(peripheral, nmeaObservable, "NMEA", relayQueue, deviceSession)
            } else {
                log.w { "NMEA characteristic is not notifiable; skipping observe()" }
            }

            if (cobsObservable != null) {
                connectionScope.launchCharacteristicObserver(peripheral, cobsObservable, "COBS", relayQueue, deviceSession)
            } else {
                log.w { "COBS characteristic is not notifiable; skipping observe()" }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            log.e(e) { "⚠️ Error observing notifications" }
        }
    }

    private fun CoroutineScope.launchCharacteristicObserver(
        peripheral: Peripheral,
        characteristic: Characteristic,
        label: String,
        relayQueue: Channel<RelayWorkItem>,
        deviceSession: Any,
    ) {
        launch {
            val protocol = if (label == "NMEA") BleFrameProtocol.Nmea else BleFrameProtocol.Cobs
            val frameAssembler = BleFrameAssembler(protocol)

            try {
                peripheral.observe(characteristic)
                    .collect { chunk ->
                        val assembled = frameAssembler.accept(chunk)
                        if (assembled.droppedFrames > 0) {
                            log.w { "Dropped ${assembled.droppedFrames} oversized $label frame(s)" }
                            if (label == "COBS") _status.update {
                                it.copy(gdl90 = it.gdl90.copy(
                                    droppedFrames = it.gdl90.droppedFrames + assembled.droppedFrames,
                                ))
                            }
                        }
                        assembled.frames.forEach { payload ->
                            GatasLiveActivityBridge.recordPacket()
                            _status.update {
                                it.recordPacket(
                                    linkSide = LinkSide.Ble,
                                    label = label,
                                    framesReceived = it.framesReceived + 1,
                                    bytesReceived = it.bytesReceived + payload.size,
                                    activeStream = label,
                                    gatasActivityTick = it.gatasActivityTick + 1,
                                    lastEvent = "$label frame received (${payload.size} bytes)"
                                )
                            }
                            handleFrame(peripheral, characteristic, label, payload, relayQueue, deviceSession)
                        }
                    }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                log.e(e) { "⚠️ Error observing $label notifications" }
            }
        }
    }

    private suspend fun handleFrame(
        peripheral: Peripheral,
        characteristic: Characteristic,
        label: String,
        payload: ByteArray,
        relayQueue: Channel<RelayWorkItem>,
        deviceSession: Any,
    ) {
        log.d { "Received $label BLE frame (${payload.size} bytes)" }
        if (label == "COBS" && payload.size >= 2 && (payload[1].toInt() and 255) == DeviceActionCodec.RESPONSE_TYPE) {
            deviceActions.receive(deviceSession, payload)
            return
        }
        maybeUpdateOwnshipConfiguration(label, payload)
        maybeBridgeGdl90Frame(label, payload)

        val relayDecision = relayDecision(label, payload)
        if (!relayDecision.shouldRelay) {
            log.i {
                "Skipping $label relay to gatasServer for message type ${relayDecision.messageType}"
            }
            _status.update {
                it.copy(
                    activeStream = label,
                    lastEvent = "Skipped $label relay for local-only or invalid message type ${relayDecision.messageType}"
                )
            }
            return
        }

        val workItem = RelayWorkItem(
            sequence = nextRelaySequence++,
            peripheral = peripheral,
            characteristic = characteristic,
            label = label,
            messageType = requireNotNull(relayDecision.messageType),
            payload = payload.copyOf(),
            enqueuedAt = TimeSource.Monotonic.markNow(),
        )
        val enqueueResult = if (relayQueue.trySend(workItem).isSuccess) {
            RelayEnqueueResult.Accepted
        } else {
            replaceStalePositionRequest(relayQueue, workItem) { it.messageType }
        }

        if (enqueueResult != RelayEnqueueResult.Rejected) {
            _status.update {
                it.copy(
                    relayQueueDrops = it.relayQueueDrops +
                        if (enqueueResult == RelayEnqueueResult.ReplacedStalePosition) 1 else 0,
                    lastEvent = "Queued $label frame for UDP relay (${payload.size} bytes)"
                )
            }
        } else {
            log.e { "Relay queue is full; dropping one $label request" }
            _status.update {
                it.copy(
                    relayQueueDrops = it.relayQueueDrops + 1,
                    lastEvent = "Relay queue full; dropped $label request",
                )
            }
        }
    }

    private suspend fun processRelayWorkItem(workItem: RelayWorkItem) {
        val queueDelayMillis = workItem.enqueuedAt.elapsedNow().inWholeMilliseconds
        _status.update {
            it.recordPacket(
                linkSide = LinkSide.Udp,
                label = workItem.label,
                serverActivityTick = it.serverActivityTick + 1,
                lastRelayQueueDelayMillis = queueDelayMillis,
                lastEvent = "Relaying ${workItem.label} frame to UDP (${workItem.payload.size} bytes)"
            )
        }

        val relayStartedAt = TimeSource.Monotonic.markNow()
        val response = try {
            udpRelayService.relay(workItem.payload)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            val roundTripMillis = relayStartedAt.elapsedNow().inWholeMilliseconds
            log.e(e) {
                "UDP relay diagnostic: cycle=${workItem.sequence}, label=${workItem.label}, " +
                    "queueDelayMs=$queueDelayMillis, " +
                    "roundTripMs=$roundTripMillis, result=failed"
            }
            _status.update {
                it.copy(
                    udpHealthy = false,
                    lastRelayRoundTripMillis = roundTripMillis,
                    lastError = e.message ?: "UDP relay failed",
                    lastEvent = "UDP relay failed"
                )
            }
            null
        } ?: return

        val roundTripMillis = relayStartedAt.elapsedNow().inWholeMilliseconds
        if (response.isEmpty()) {
            log.w {
                "UDP relay diagnostic: cycle=${workItem.sequence}, label=${workItem.label}, " +
                    "queueDelayMs=$queueDelayMillis, " +
                    "roundTripMs=$roundTripMillis, result=empty"
            }
            _status.update {
                it.copy(
                    udpHealthy = false,
                    lastRelayRoundTripMillis = roundTripMillis,
                    lastError = "UDP response was empty",
                    lastEvent = "No UDP response"
                )
            }
            return
        }

        // The Internet relay is never an authority for local device actions.
        // Reject the whole datagram if any concatenated COBS frame uses the
        // reserved management types, before it can reach the BLE write path.
        if (workItem.label == "COBS" && containsDeviceActionFrame(response)) {
            log.w { "Discarding local device-action message received from the UDP relay" }
            return
        }

        val responseSummary = if (areDetailedDiagnosticsEnabled()) {
            summarizeRelayResponse(response)
        } else {
            null
        }

        _status.update {
            it.recordPacket(
                linkSide = LinkSide.Udp,
                label = workItem.label,
                udpHealthy = true,
                activeStream = workItem.label,
                framesRelayed = it.framesRelayed + 1,
                bytesRelayed = it.bytesRelayed + response.size,
                serverActivityTick = it.serverActivityTick + 1,
                lastRelayQueueDelayMillis = queueDelayMillis,
                lastRelayRoundTripMillis = roundTripMillis,
                lastError = null,
                lastEvent = "UDP response received (${response.size} bytes)"
            )
        }
        GatasLiveActivityBridge.recordPacket()
        val bleWriteStartedAt = TimeSource.Monotonic.markNow()
        sendResponse(
            peripheral = workItem.peripheral,
            characteristic = workItem.characteristic,
            label = workItem.label,
            payload = response,
        )
        val bleWriteMillis = bleWriteStartedAt.elapsedNow().inWholeMilliseconds

        responseSummary?.let { summary ->
            log.d {
                "Relay cycle diagnostic: cycle=${workItem.sequence}, requestType=${workItem.messageType}, " +
                    "queueDelayMs=$queueDelayMillis, serverRoundTripMs=$roundTripMillis, " +
                    "responseBytes=${response.size}, responseFrames=${summary.totalFrames}, " +
                    "serverTraffic=${summary.aircraftPositions}, " +
                    "other=${summary.otherMessages}, malformed=${summary.malformedFrames}, " +
                    "bleWriteMs=$bleWriteMillis"
            }

            if (isAircraftPositionRequest(workItem.messageType)) {
                recordRelayResponseSentToGatas(workItem.sequence, summary)
            }
        }
    }

    private suspend fun maybeBridgeGdl90Frame(label: String, payload: ByteArray) {
        if (label != "COBS") return

        val enabled = Gdl90BridgeSettings.isEnabled()
        val forwardStartedAt = TimeSource.Monotonic.markNow()
        val result = gdl90Forwarder.forward(payload, enabled)
        val forwardDurationMillis = forwardStartedAt.elapsedNow().inWholeMilliseconds
        val summary = if (result is Gdl90ForwardResult.Sent) {
            result.messageSummary
        } else {
            null
        }
        val trafficIntervalMillis = if (summary?.trafficReports?.let { it > 0 } == true) {
            lastGdl90TrafficMark?.elapsedNow()?.inWholeMilliseconds
        } else {
            null
        }
        if (summary?.trafficReports?.let { it > 0 } == true) {
            lastGdl90TrafficMark = TimeSource.Monotonic.markNow()
            if (areDetailedDiagnosticsEnabled()) {
                correlateReturnedGdl90Traffic(summary, forwardDurationMillis)
            }
        }

        _status.update { currentStatus ->
            val reduced = BridgeStatusReducer.reduceGdl90(currentStatus, result)
            if (summary == null) {
                reduced
            } else {
                reduced.copy(
                    gdl90 = reduced.gdl90.copy(
                        heartbeatMessages = reduced.gdl90.heartbeatMessages + summary.heartbeats,
                        ownshipMessages = reduced.gdl90.ownshipMessages + summary.ownshipReports,
                        trafficMessages = reduced.gdl90.trafficMessages + summary.trafficReports,
                        otherMessages = reduced.gdl90.otherMessages + summary.otherMessages,
                        lastTrafficIntervalMillis = trafficIntervalMillis
                            ?: reduced.gdl90.lastTrafficIntervalMillis,
                        maximumTrafficIntervalMillis = maxOf(
                            reduced.gdl90.maximumTrafficIntervalMillis,
                            trafficIntervalMillis ?: 0,
                        ),
                        lastForwardDurationMillis = forwardDurationMillis,
                    )
                )
            }
        }

        summary?.takeIf { areDetailedDiagnosticsEnabled() }?.let {
            recordGdl90Diagnostics(
                summary = it,
                trafficIntervalMillis = trafficIntervalMillis,
                forwardDurationMillis = forwardDurationMillis,
            )
        }
    }

    /**
     * Records the latest server response that actually contained traffic. If a
     * newer traffic-bearing response reaches GATAS first, the previous cycle is
     * reported as having produced no observable GDL90 traffic in that interval.
     * This is a temporal correlation because the existing BLE protocol carries
     * no request identifier through the GATAS conversion step.
     */
    private suspend fun recordRelayResponseSentToGatas(
        sequence: Long,
        responseSummary: RelayResponseSummary,
    ) = trafficCycleMutex.withLock {
        if (responseSummary.aircraftPositions == 0) {
            log.d {
                "Relay cycle correlation: cycle=$sequence, serverTraffic=0, " +
                    "result=no-traffic-from-server"
            }
            return@withLock
        }

        pendingTrafficCycle?.let { previous ->
            log.d {
                "Relay cycle correlation: cycle=${previous.sequence}, " +
                    "serverTraffic=${previous.serverAircraftPositions}, " +
                    "waitedMs=${previous.sentToGatasAt.elapsedNow().inWholeMilliseconds}, " +
                    "result=no-gdl90-before-next-server-response"
            }
        }
        pendingTrafficCycle = PendingTrafficCycle(
            sequence = sequence,
            serverAircraftPositions = responseSummary.aircraftPositions,
            sentToGatasAt = TimeSource.Monotonic.markNow(),
        )
    }

    private suspend fun correlateReturnedGdl90Traffic(
        summary: Gdl90MessageSummary,
        forwardDurationMillis: Long,
    ) = trafficCycleMutex.withLock {
        val cycle = pendingTrafficCycle ?: return@withLock
        log.d {
            "Relay cycle correlation: cycle=${cycle.sequence}, " +
                "serverTraffic=${cycle.serverAircraftPositions}, " +
                "returnedGdl90Traffic=${summary.trafficReports}, " +
                "gatasTurnaroundMs=${cycle.sentToGatasAt.elapsedNow().inWholeMilliseconds}, " +
                "localForwardMs=$forwardDurationMillis, result=gdl90-returned"
        }
        pendingTrafficCycle = null
    }

    /**
     * Emits at most one diagnostic line per second. The summary contains only
     * protocol message categories and timings; no aircraft or position fields
     * are decoded or logged.
     */
    private fun recordGdl90Diagnostics(
        summary: Gdl90MessageSummary,
        trafficIntervalMillis: Long?,
        forwardDurationMillis: Long,
    ) {
        diagnosticHeartbeats += summary.heartbeats
        diagnosticOwnshipReports += summary.ownshipReports
        diagnosticTrafficReports += summary.trafficReports
        diagnosticOtherMessages += summary.otherMessages

        val windowDurationMillis = gdl90DiagnosticWindowStartedAt.elapsedNow().inWholeMilliseconds
        if (windowDurationMillis < 1_000) return

        log.d {
            "GDL90 diagnostic: windowMs=$windowDurationMillis, heartbeat=$diagnosticHeartbeats, " +
                "ownship=$diagnosticOwnshipReports, traffic=$diagnosticTrafficReports, " +
                "other=$diagnosticOtherMessages, trafficIntervalMs=${trafficIntervalMillis ?: -1}, " +
                "forwardMs=$forwardDurationMillis"
        }
        resetGdl90DiagnosticWindow()
    }

    private fun resetGdl90DiagnosticWindow() {
        gdl90DiagnosticWindowStartedAt = TimeSource.Monotonic.markNow()
        diagnosticHeartbeats = 0
        diagnosticOwnshipReports = 0
        diagnosticTrafficReports = 0
        diagnosticOtherMessages = 0
    }

    private fun initialGdl90Status(): Gdl90BridgeStatus {
        val enabled = Gdl90BridgeSettings.isEnabled()
        return BridgeStatusReducer.initialGdl90(enabled)
    }

    private fun maybeUpdateOwnshipConfiguration(label: String, payload: ByteArray) {
        if (label != "COBS") {
            return
        }

        val aircraftConfiguration = runCatching {
            val cobsByteArray = CobsByteArray(payload)
            when (cobsByteArray.peekAhead()) {
                MessageType.AIRCRAFT_CONFIGURATIONS_V2.value -> deserializeAircraftConfigurationV2(cobsByteArray)
                MessageType.AIRCRAFT_CONFIGURATIONS_V1.value -> deserializeAircraftConfigurationV1(cobsByteArray)
                else -> null
            }
        }.getOrElse { error ->
            log.w(error) { "Failed to decode aircraft configuration COBS frame" }
            null
        } ?: return

        _status.update {
            val clearedTarget = if (it.aircraftChangeTargetIcaoAddress == aircraftConfiguration.icaoAddress) {
                null
            } else {
                it.aircraftChangeTargetIcaoAddress
            }
            val wifiModeChangeApplied = it.wifiModeChangeTarget != null &&
                aircraftConfiguration.wifiMode == it.wifiModeChangeTarget
            it.copy(
                ownshipConfiguration = aircraftConfiguration,
                aircraftChangeTargetIcaoAddress = clearedTarget,
                wifiModeChangeTarget = if (wifiModeChangeApplied) null else it.wifiModeChangeTarget,
                wifiModeStatusMessage = when {
                    wifiModeChangeApplied -> "GATAS reported ${aircraftConfiguration.wifiMode.displayName()} mode. The change is active."
                    else -> it.wifiModeStatusMessage
                },
                lastEvent = "Aircraft configuration received"
            )
        }

        // The owning change job observes the echoed address itself. Cancelling
        // the current job from this notification could cancel a newer request
        // which has been scheduled but has not set its pending target yet.
    }

    private suspend fun sendResponse(
        peripheral: Peripheral,
        characteristic: Characteristic,
        label: String,
        payload: ByteArray,
        allowWhileStopping: Boolean = false,
        payloadStillValid: () -> Boolean = { true },
    ): Boolean {
        val framedPayload = when (label) {
            "NMEA" -> when {
                payload.endsWithBytes('\r'.code.toByte(), '\n'.code.toByte()) -> payload
                payload.lastOrNull() == '\n'.code.toByte() -> payload
                payload.lastOrNull() == '\r'.code.toByte() -> payload + '\n'.code.toByte()
                else -> payload + "\r\n".encodeToByteArray()
            }

            else -> if (payload.lastOrNull() == 0.toByte()) {
                payload
            } else {
                payload + 0
            }
        }

        val maxWriteSize = runCatching {
            peripheral.maximumWriteValueLengthForType(WriteType.WithoutResponse)
        }.getOrDefault(framedPayload.size.coerceAtLeast(1))

        // Relay traffic, barometer samples and device-action commands share
        // this writer. Revalidate queued work after acquiring its packet lock.
        val sent = packetWriter.write(framedPayload, maxWriteSize,
            mayWrite = {
                connectedPeripheral === peripheral && (_status.value.running || allowWhileStopping) && payloadStillValid()
            },
            writeChunk = { chunk ->
                log.d { "Sending $label BLE response chunk (${chunk.size} bytes)" }
                peripheral.write(characteristic, chunk, WriteType.WithoutResponse)
            },
        )
        if (!sent) return false
        _status.update {
            it.recordPacket(
                linkSide = LinkSide.Ble,
                label = label,
                gatasActivityTick = it.gatasActivityTick + 1,
                lastEvent = "BLE response sent (${payload.size} bytes)"
            )
        }
        return true
    }

    private fun ByteArray.endsWithBytes(vararg suffix: Byte): Boolean {
        if (size < suffix.size) {
            return false
        }
        return suffix.indices.all { index ->
            this[size - suffix.size + index] == suffix[index]
        }
    }

    private suspend fun sendAircraftChangeCommand(peripheral: Peripheral, icaoAddress: Long) {
        val payload = SetIcaoAddressV1(icaoAddress).serializeSetIcaoAddressV1()
        sendResponse(
            peripheral = peripheral,
            characteristic = cobsCharacteristic,
            label = "COBS",
            payload = payload,
        )
        _status.update {
            it.copy(lastEvent = "Requested aircraft ${icaoAddress.toIcaoHex()}")
        }
    }

    private suspend fun sendWifiModeChangeCommand(peripheral: Peripheral, mode: WifiMode) {
        val payload = mode.serializeSetWifiModeV1()
        sendResponse(
            peripheral = peripheral,
            characteristic = cobsCharacteristic,
            label = "COBS",
            payload = payload,
        )
    }

    private fun finalizeAircraftChange(icaoAddress: Long, lastEvent: String) {
        _status.update {
            it.copy(
                aircraftChangeTargetIcaoAddress = null,
                lastError = null,
                lastEvent = lastEvent,
            )
        }
        aircraftChangeJob = null
    }

    private fun Long.toIcaoHex(): String = toString(16).uppercase().padStart(6, '0')
    private fun WifiMode.displayName(): String = when (this) {
        WifiMode.NC -> "Not configured"
        WifiMode.AP -> "AP"
        WifiMode.CLIENT -> "Client"
    }

    private fun BridgeStatus.recordPacket(
        linkSide: LinkSide,
        label: String,
        framesReceived: Long = this.framesReceived,
        framesRelayed: Long = this.framesRelayed,
        bytesReceived: Long = this.bytesReceived,
        bytesRelayed: Long = this.bytesRelayed,
        activeStream: String? = this.activeStream,
        serverActivityTick: Long = this.serverActivityTick,
        gatasActivityTick: Long = this.gatasActivityTick,
        udpHealthy: Boolean = this.udpHealthy,
        lastEvent: String = this.lastEvent,
        lastError: String? = this.lastError,
        lastRelayQueueDelayMillis: Long? = this.lastRelayQueueDelayMillis,
        lastRelayRoundTripMillis: Long? = this.lastRelayRoundTripMillis,
    ): BridgeStatus {
        return when (linkSide) {
            LinkSide.Udp -> when (label) {
                "NMEA" -> copy(
                    framesReceived = framesReceived,
                    framesRelayed = framesRelayed,
                    bytesReceived = bytesReceived,
                    bytesRelayed = bytesRelayed,
                    activeStream = activeStream,
                    serverActivityTick = serverActivityTick,
                    gatasActivityTick = gatasActivityTick,
                    udpHealthy = udpHealthy,
                    lastEvent = lastEvent,
                    lastError = lastError,
                    lastRelayQueueDelayMillis = lastRelayQueueDelayMillis,
                    lastRelayRoundTripMillis = lastRelayRoundTripMillis,
                    udpNmeaPackets = udpNmeaPackets + 1,
                    udpNmeaActivityTick = udpNmeaActivityTick + 1,
                )

                "COBS" -> copy(
                    framesReceived = framesReceived,
                    framesRelayed = framesRelayed,
                    bytesReceived = bytesReceived,
                    bytesRelayed = bytesRelayed,
                    activeStream = activeStream,
                    serverActivityTick = serverActivityTick,
                    gatasActivityTick = gatasActivityTick,
                    udpHealthy = udpHealthy,
                    lastEvent = lastEvent,
                    lastError = lastError,
                    lastRelayQueueDelayMillis = lastRelayQueueDelayMillis,
                    lastRelayRoundTripMillis = lastRelayRoundTripMillis,
                    udpCobsPackets = udpCobsPackets + 1,
                    udpCobsActivityTick = udpCobsActivityTick + 1,
                )

                else -> copy(
                    framesReceived = framesReceived,
                    framesRelayed = framesRelayed,
                    bytesReceived = bytesReceived,
                    bytesRelayed = bytesRelayed,
                    activeStream = activeStream,
                    serverActivityTick = serverActivityTick,
                    gatasActivityTick = gatasActivityTick,
                    udpHealthy = udpHealthy,
                    lastEvent = lastEvent,
                    lastError = lastError,
                    lastRelayQueueDelayMillis = lastRelayQueueDelayMillis,
                    lastRelayRoundTripMillis = lastRelayRoundTripMillis,
                )
            }

            LinkSide.Ble -> when (label) {
                "NMEA" -> copy(
                    framesReceived = framesReceived,
                    framesRelayed = framesRelayed,
                    bytesReceived = bytesReceived,
                    bytesRelayed = bytesRelayed,
                    activeStream = activeStream,
                    serverActivityTick = serverActivityTick,
                    gatasActivityTick = gatasActivityTick,
                    udpHealthy = udpHealthy,
                    lastEvent = lastEvent,
                    lastError = lastError,
                    bleNmeaPackets = bleNmeaPackets + 1,
                    bleNmeaActivityTick = bleNmeaActivityTick + 1,
                )

                "COBS" -> copy(
                    framesReceived = framesReceived,
                    framesRelayed = framesRelayed,
                    bytesReceived = bytesReceived,
                    bytesRelayed = bytesRelayed,
                    activeStream = activeStream,
                    serverActivityTick = serverActivityTick,
                    gatasActivityTick = gatasActivityTick,
                    udpHealthy = udpHealthy,
                    lastEvent = lastEvent,
                    lastError = lastError,
                    bleCobsPackets = bleCobsPackets + 1,
                    bleCobsActivityTick = bleCobsActivityTick + 1,
                )

                else -> copy(
                    framesReceived = framesReceived,
                    framesRelayed = framesRelayed,
                    bytesReceived = bytesReceived,
                    bytesRelayed = bytesRelayed,
                    activeStream = activeStream,
                    serverActivityTick = serverActivityTick,
                    gatasActivityTick = gatasActivityTick,
                    udpHealthy = udpHealthy,
                    lastEvent = lastEvent,
                    lastError = lastError,
                )
            }
        }
    }

    private suspend fun observableCharacteristic(
        peripheral: Peripheral,
        expected: Characteristic,
        label: String,
    ): Characteristic? {
        val services = withTimeoutOrNull(SERVICE_DISCOVERY_TIMEOUT_MILLIS) {
            peripheral.services.first { it != null }
        }
        if (services == null) {
            log.w { "No GATT services available yet for $label" }
            return null
        }

        val service = services.firstOrNull { it.serviceUuid == GATAS_PRIMARY_DEVICE }
        if (service == null) {
            log.w { "No GATAS service found while looking for $label" }
            return null
        }

        val discovered = service.characteristics.firstOrNull {
            it.characteristicUuid == expected.characteristicUuid
        }
        if (discovered == null) {
            log.w {
                "$label characteristic ${expected.characteristicUuid} not found in discovered profile"
            }
            return null
        }

        val properties = discovered.properties
        val canObserve = properties.notify || properties.indicate
        if (!canObserve) {
            log.w {
                "$label characteristic ${expected.characteristicUuid} is not notifiable or indicative; " +
                    "properties=${properties}"
            }
            return null
        }

        log.i {
            "$label characteristic ${expected.characteristicUuid} supports observation; properties=${properties}"
        }
        return expected
    }

    private suspend fun findReconnectTarget(): Advertisement? {
        val scanner = Scanner {
            filters {
                match {
                    services = listOf(GATAS_PRIMARY_DEVICE)
                }
            }
            logging {
                level = Warnings
            }
        }

        return withTimeoutOrNull(RECONNECT_SCAN_TIMEOUT_MILLIS) {
            scanner.advertisements
                .filter { it.identifier.toString() == targetDevice.identifier }
                .first()
        }
    }

}

internal data class RelayDecision(val shouldRelay: Boolean, val messageType: Int?)

internal fun containsDeviceActionFrame(payload: ByteArray): Boolean {
    var start = 0
    for (end in 0..payload.size) {
        if (end == payload.size || payload[end] == 0.toByte()) {
            if (end - start >= 2 && (payload[start + 1].toInt() and 255) in
                listOf(DeviceActionCodec.REQUEST_TYPE, DeviceActionCodec.RESPONSE_TYPE)) return true
            start = end + 1
        }
    }
    return false
}

internal fun relayDecision(label: String, payload: ByteArray): RelayDecision {
    if (label != "COBS" || payload.size < 2 || (payload[0].toInt() and 0xff) < 2) {
        return RelayDecision(shouldRelay = false, messageType = null)
    }

    // A nonzero COBS message type is the first byte after the initial code byte.
    // Reading it directly keeps routing independent of the payload format and size.
    val type = payload[1].toInt() and 0xff
    return RelayDecision(
        shouldRelay = type !in listOf(MessageType.GDL90_V1.value, DeviceActionCodec.REQUEST_TYPE, DeviceActionCodec.RESPONSE_TYPE),
        messageType = type,
    )
}

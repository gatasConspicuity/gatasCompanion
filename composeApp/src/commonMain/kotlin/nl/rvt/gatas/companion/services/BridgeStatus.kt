package nl.rvt.gatas.companion.services

import nl.rvantwisk.gatas.lib.models.OwnshipAircraftConfiguration
import nl.rvantwisk.gatas.lib.models.WifiMode

data class BridgeStatus(
    val running: Boolean = false,
    val connecting: Boolean = false,
    val bleConnected: Boolean = false,
    val udpHealthy: Boolean = false,
    val framesReceived: Long = 0,
    val framesRelayed: Long = 0,
    val bytesReceived: Long = 0,
    val bytesRelayed: Long = 0,
    val relayQueueDrops: Long = 0,
    val lastRelayQueueDelayMillis: Long? = null,
    val lastRelayRoundTripMillis: Long? = null,
    val activeStream: String? = null,
    val availableStreams: String? = null,
    val serverActivityTick: Long = 0,
    val gatasActivityTick: Long = 0,
    val udpNmeaPackets: Long = 0,
    val udpCobsPackets: Long = 0,
    val udpNmeaActivityTick: Long = 0,
    val udpCobsActivityTick: Long = 0,
    val bleNmeaPackets: Long = 0,
    val bleCobsPackets: Long = 0,
    val bleNmeaActivityTick: Long = 0,
    val bleCobsActivityTick: Long = 0,
    val gdl90: Gdl90BridgeStatus = Gdl90BridgeStatus(),
    val ownshipConfiguration: OwnshipAircraftConfiguration? = null,
    val aircraftChangeTargetIcaoAddress: Long? = null,
    val wifiModeChangeTarget: WifiMode? = null,
    val wifiModeStatusMessage: String? = null,
    val lastEvent: String = "Idle",
    val lastError: String? = null,
) {
    val bridgeHealthy: Boolean
        get() = running && bleConnected && udpHealthy && lastError == null

    val udpPacketCount: Long
        get() = udpNmeaPackets + udpCobsPackets

    val blePacketCount: Long
        get() = bleNmeaPackets + bleCobsPackets
}

enum class Gdl90State {
    Disabled,
    WaitingForFrames,
    Sending,
    Error,
}

data class Gdl90BridgeStatus(
    val enabled: Boolean = false,
    val state: Gdl90State = Gdl90State.Disabled,
    val framesReceived: Long = 0,
    val packetsSent: Long = 0,
    val bytesSent: Long = 0,
    val decodeErrors: Long = 0,
    val sendErrors: Long = 0,
    val droppedFrames: Long = 0,
    val activityTick: Long = 0,
    val heartbeatMessages: Long = 0,
    val ownshipMessages: Long = 0,
    val trafficMessages: Long = 0,
    val otherMessages: Long = 0,
    val lastTrafficIntervalMillis: Long? = null,
    val maximumTrafficIntervalMillis: Long = 0,
    val lastForwardDurationMillis: Long? = null,
    val lastEvent: String = "GDL90 forwarding disabled",
    val lastError: String? = null,
)

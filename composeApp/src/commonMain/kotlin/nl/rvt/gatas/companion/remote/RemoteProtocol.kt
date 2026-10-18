package nl.rvt.gatas.companion.remote

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import nl.rvt.gatas.companion.AircraftPickerEntry
import nl.rvt.gatas.companion.GaTasDevice
import nl.rvt.gatas.companion.services.DeviceControlState
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

val remoteJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

@Serializable
data class RemoteCommand(
    val version: Int = 1,
    val id: String,
    val action: String,
    val expiresAt: Double? = null,
    val deviceId: String? = null,
    val expectedDeviceId: String? = null,
    val icaoAddress: Long? = null,
    val mode: String? = null,
    val enabled: Boolean? = null,
    val expectedBootId: String? = null,
)

@Serializable
data class RemoteReply(val id: String, val status: String, val message: String)

/** Process-local replay protection. Control messages are never persisted or retried. */
@OptIn(ExperimentalTime::class)
class RemoteCommandProcessor(
    private val now: () -> Double = { Clock.System.now().toEpochMilliseconds() / 1000.0 },
    private val execute: (RemoteCommand) -> RemoteReply,
) {
    private val replies = linkedMapOf<String, Pair<RemoteCommand, RemoteReply>>()

    fun handle(encoded: String): String {
        val command = runCatching { remoteJson.decodeFromString<RemoteCommand>(encoded) }
            .getOrElse { return remoteJson.encodeToString(RemoteReply("", "rejected", "Invalid command")) }
        val reply = when {
            command.version != 1 -> RemoteReply(command.id, "rejected", "Unsupported protocol version")
            command.id.isBlank() || command.id.length > 80 -> RemoteReply(command.id, "rejected", "Invalid command ID")
            replies.containsKey(command.id) -> {
                val (original, cached) = replies.getValue(command.id)
                if (original == command) cached else RemoteReply(command.id, "rejected", "Command ID already used")
            }
            // WCSession itself may briefly delay delivery. Reject commands
            // after the Watch has stopped waiting, even across a reconnect.
            command.expiresAt == null || command.expiresAt < now() || command.expiresAt > now() + 30 ->
                RemoteReply(command.id, "rejected", "Command expired. Refresh and try again.")
            else -> {
                val result = runCatching { execute(command) }.getOrElse {
                    RemoteReply(command.id, "rejected", it.message ?: "Command failed")
                }
                replies[command.id] = command to result
                if (replies.size > 128) replies.remove(replies.keys.first())
                result
            }
        }
        return remoteJson.encodeToString(reply)
    }
}

/** Deliberately contains display data only, rather than the raw firmware configuration. */
@Serializable
data class WatchSnapshot(
    val deviceControl: DeviceControlState? = null,
    val version: Int = 1,
    val updatedAt: Double,
    val devices: List<GaTasDevice>,
    val discoveredDevices: List<GaTasDevice>,
    val scanning: Boolean,
    val scanError: String?,
    val activeDevice: GaTasDevice?,
    val running: Boolean,
    val connecting: Boolean,
    val bleConnected: Boolean,
    val udpHealthy: Boolean,
    val gatasId: String?,
    val selectedIcaoAddress: Long?,
    val aircraft: List<AircraftPickerEntry>,
    val aircraftLoading: Boolean,
    val aircraftError: String?,
    val pendingAircraft: Long?,
    val wifiMode: String?,
    val pendingWifiMode: String?,
    val wifiMessage: String?,
    val gdl90Enabled: Boolean,
    val lastEvent: String,
    val lastError: String?,
    val diagnostics: Map<String, String>,
)

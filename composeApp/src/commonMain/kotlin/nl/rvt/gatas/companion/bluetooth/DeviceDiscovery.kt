package nl.rvt.gatas.companion.bluetooth

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import nl.rvt.gatas.companion.GaTasDevice

data class DiscoveryState(
    val devices: List<GaTasDevice> = emptyList(),
    val scanning: Boolean = false,
    val error: String? = null,
    val watchScanning: Boolean = false,
)

/** A shared scanner with independent leases for the phone screen and Watch. */
@OptIn(kotlin.uuid.ExperimentalUuidApi::class)
object DeviceDiscovery {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val mutableState = MutableStateFlow(DiscoveryState())
    val state = mutableState.asStateFlow()
    private val owners = mutableSetOf<String>()
    private var scanJob: Job? = null
    private var watchLease: Job? = null
    private var generation = 0

    fun start(owner: String) {
        owners.add(owner)
        if (owner == "watch") {
            mutableState.value = mutableState.value.copy(watchScanning = true)
            watchLease?.cancel()
            watchLease = scope.launch { delay(30_000); stop("watch") }
        }
        if (scanJob?.isActive == true) return
        mutableState.value = DiscoveryState(scanning = true, watchScanning = "watch" in owners)
        val scanGeneration = ++generation
        scanJob = scope.launch {
            try {
                BleScannerViewModel.scanner.advertisements.collect { advertisement ->
                    val id = advertisement.identifier.toString()
                    if (mutableState.value.devices.none { it.identifier == id }) {
                        // The native scanner already filters by GATAS service UUID.
                        // Some background advertisements omit the local name.
                        val device = GaTasDevice(advertisement.name?.takeIf { it.isNotBlank() } ?: "GATAS", id)
                        mutableState.value = mutableState.value.copy(devices = mutableState.value.devices + device)
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                mutableState.value = mutableState.value.copy(error = error.message ?: "Bluetooth scan failed")
            } finally {
                if (generation == scanGeneration) {
                    mutableState.value = mutableState.value.copy(scanning = false, watchScanning = false)
                    owners.clear()
                }
            }
        }
    }

    fun stop(owner: String) {
        owners.remove(owner)
        if (owner == "watch") mutableState.value = mutableState.value.copy(watchScanning = false)
        if (owners.isEmpty()) {
            // An old cancelled scan must not clear the leases of a new scan.
            generation++
            mutableState.value = mutableState.value.copy(scanning = false)
            scanJob?.cancel()
            scanJob = null
        }
    }
}

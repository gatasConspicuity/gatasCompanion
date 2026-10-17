package nl.rvt.gatas.companion.services

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import nl.rvantwisk.gatas.lib.extensions.CobsByteArray
import nl.rvantwisk.gatas.lib.extensions.MessageType
import kotlin.random.Random

enum class DeviceAction(val code: Int, val wireName: String, val capability: Int) {
    Status(0, "status", 0), Save(1, "saveConfiguration", 1),
    Restart(2, "restartGatas", 2), UsbBoot(3, "usbBoot", 4),
}
enum class DeviceUpdatePolicy(val code: Int) { RequireSaved(0), SaveFirst(1), AllowUnsaved(2) }

data class DeviceActionCommand(
    val id: String, val action: DeviceAction, val deviceId: String, val bootId: String,
    val updatePolicy: DeviceUpdatePolicy = DeviceUpdatePolicy.RequireSaved,
)

@Serializable
data class DeviceOperation(
    val id: String, val action: String, val phase: String, val message: String,
) {
    val busy: Boolean get() = phase in listOf("preparing", "executing", "restarting")
}

@Serializable
data class DeviceControlState(
    val checking: Boolean = false,
    val capabilities: Int = 0,
    val bootId: String? = null,
    val hasUnsavedChanges: Boolean? = null,
    val operation: DeviceOperation? = null,
    val recentResults: List<DeviceOperation> = emptyList(),
) {
    fun supports(action: DeviceAction): Boolean = bootId != null && capabilities and action.capability != 0

    // Snapshots are coalesced to 1 Hz. Retain correlated terminal results so a
    // fast subsequent phone action cannot hide the Watch's own confirmation.
    fun withOperation(value: DeviceOperation?): DeviceControlState = copy(operation = value,
        recentResults = if (value != null && !value.busy)
            (recentResults.filterNot { it.id == value.id } + value).takeLast(16) else recentResults)
}

data class DeviceActionResponse(
    val action: DeviceAction, val requestId: UInt, val result: Int,
    val capabilities: Int, val bootId: String, val dirty: Boolean,
)

object DeviceActionCodec {
    val REQUEST_TYPE = MessageType.DEVICE_ACTION_REQUEST_V1.value
    val RESPONSE_TYPE = MessageType.DEVICE_ACTION_RESPONSE_V1.value
    const val OK = 0
    const val ACCEPTED = 1

    // The firmware uses the same 16/17-byte, big-endian layouts. Keep the
    // boot nonce as hex in JSON so Swift never loses unsigned 64-bit precision.
    fun encode(action: DeviceAction, execute: Boolean, id: UInt, bootId: String,
               policy: DeviceUpdatePolicy = DeviceUpdatePolicy.RequireSaved): ByteArray = CobsByteArray(16).apply {
        require(bootId.length == 16)
        put1(REQUEST_TYPE); put1(action.code); put1(if (execute) 1 else 0); putInt4(id.toInt())
        bootId.chunked(2).forEach { put1(it.toInt(16)) }
        put1(policy.code)
    }.getCobs()

    fun decode(frame: ByteArray): DeviceActionResponse {
        // BleFrameAssembler removes the delimiter. The existing COBS decoder
        // needs it to preserve a final zero (notably dirty=false/result=OK).
        val cobs = CobsByteArray(if (frame.lastOrNull() == 0.toByte()) frame else frame + 0)
        require(cobs.getBuffer().size == 17 && cobs.getInt1() == RESPONSE_TYPE)
        val actionCode = cobs.getInt1() and 255
        val action = DeviceAction.entries.first { it.code == actionCode }
        val id = cobs.getUInt4()
        val result = cobs.getInt1() and 255
        val capabilities = cobs.getInt1() and 255
        val bootId = (1..8).joinToString("") { (cobs.getInt1() and 255).toString(16).padStart(2, '0') }
        val dirty = cobs.getInt1() and 255
        require(result in 0..8 && dirty in 0..1 && bootId != "0000000000000000")
        return DeviceActionResponse(action, id, result, capabilities, bootId, dirty == 1)
    }

    fun error(result: Int): String = when (result) {
        2 -> "This GATAS firmware does not support this action."
        3 -> "GATAS rejected the request. Refresh before trying again."
        4 -> "GATAS is busy. Wait for its current action to finish."
        5 -> "GATAS could not verify the configuration save."
        6 -> "GATAS restarted since this screen was opened. Refresh and try again."
        7 -> "Configuration changed. Choose whether to save it before firmware mode."
        8 -> "The prepared action expired. Refresh before trying again."
        else -> "GATAS returned an unexpected action result."
    }
}

/** Main-confined transaction owner. Only status probes retry; control commands
 * and their execution permits are never replayed after a lost response. */
class DeviceActionClient(
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
) {
    private data class Pending(val action: DeviceAction, val execute: Boolean,
                               val reply: CompletableDeferred<DeviceActionResponse>)
    private val mutableState = MutableStateFlow(DeviceControlState())
    val state = mutableState.asStateFlow()
    private val pending = mutableMapOf<UInt, Pending>()
    private var token: Any? = null
    private var sender: (suspend (ByteArray) -> Boolean)? = null
    private var probeJob: Job? = null
    private var operationJob: Job? = null
    private var executeSubmitted = false
    private var requestCounter = Random.nextLong(1, 0x1_0000_0000L).toUInt()

    suspend fun connected(session: Any, send: suspend (ByteArray) -> Boolean) = withContext(Dispatchers.Main) {
        token = session
        sender = send
        mutableState.value = DeviceControlState(checking = true, operation = state.value.operation,
            recentResults = state.value.recentResults)
        probeJob?.cancel()
        probeJob = scope.launch {
            delay(250) // Let notification subscription become active first.
            var supported = false
            repeat(2) {
                if (!supported && token === session) supported = probe(session)
            }
            if (token !== session) return@launch
            mutableState.value = state.value.copy(checking = false)
            while (supported && token === session) {
                delay(5_000)
                if (state.value.operation?.busy != true || state.value.operation?.action == DeviceAction.Restart.wireName) probe(session)
            }
        }
    }

    private suspend fun probe(session: Any): Boolean {
        val reply = exchange(session, DeviceAction.Status, false, nextId(), "0000000000000000",
            DeviceUpdatePolicy.RequireSaved, 2_500)
        return reply?.result == DeviceActionCodec.OK
    }

    fun receive(session: Any, frame: ByteArray) {
        val response = runCatching { DeviceActionCodec.decode(frame) }.getOrNull() ?: return
        scope.launch {
            if (token !== session) return@launch
            val waiter = pending[response.requestId] ?: return@launch
            if (response.action != waiter.action ||
                (waiter.execute && response.result == DeviceActionCodec.ACCEPTED)) return@launch
            if (response.result in listOf(DeviceActionCodec.OK, DeviceActionCodec.ACCEPTED) || response.bootId == state.value.bootId) {
                mutableState.value = state.value.copy(checking = false, capabilities = response.capabilities,
                    bootId = response.bootId, hasUnsavedChanges = response.dirty)
            }
            waiter.reply.complete(response)
            if (waiter.execute && waiter.action == DeviceAction.Restart && response.result != DeviceActionCodec.OK) {
                val operation = state.value.operation
                if (operation?.busy == true) mutableState.value = state.value.withOperation(operation.copy(
                    phase = "failed", message = DeviceActionCodec.error(response.result)))
            }
        }
    }

    // Called synchronously on Main before launching: phone and Watch cannot
    // both reserve an action while their Flow collectors are catching up.
    fun request(command: DeviceActionCommand): Boolean {
        val current = state.value
        val session = token ?: return false
        if (command.action == DeviceAction.Status || current.operation?.busy == true ||
            !current.supports(command.action) || current.bootId != command.bootId) return false
        executeSubmitted = false
        setOperation(command, "preparing", "Waiting for GATAS to accept the action…")
        operationJob = scope.launch {
            val id = nextId()
            val prepared = exchange(session, command.action, false, id, command.bootId, command.updatePolicy, 5_000)
            if (prepared == null) {
                finish(command, "unknown", "Action not confirmed. Refresh before trying again.")
                return@launch
            }
            if (prepared.result != DeviceActionCodec.ACCEPTED) {
                finish(command, "failed", failureMessage(command, prepared.result)); return@launch
            }
            if (token !== session) {
                finish(command, "unknown", "Connection changed before execution. Refresh before trying again."); return@launch
            }
            setOperation(command, if (command.action == DeviceAction.Restart) "restarting" else "executing",
                if (command.action == DeviceAction.Restart) "Restarting GATAS. Waiting for reconnection…" else "Waiting for GATAS confirmation…")
            // Only this matching Prepare reply permits Execute. Register the
            // waiter before writing so a fast Save response cannot be lost.
            executeSubmitted = true
            if (command.action == DeviceAction.Restart) {
                val waiter = Pending(command.action, true, CompletableDeferred())
                pending[id] = waiter
                val confirmed = withTimeoutOrNull(60_000) {
                    // Include a stuck GATT write in the deadline. A write
                    // timeout is ambiguous, so still wait for a new boot ID.
                    withTimeoutOrNull(5_000) {
                        write(session, DeviceActionCodec.encode(command.action, true, id, command.bootId, command.updatePolicy))
                    }
                    state.first { (it.bootId != null && it.bootId != command.bootId) || it.operation?.busy != true }
                }
                pending.remove(id)
                if (state.value.operation?.busy == true) {
                    finish(command, if (confirmed != null) "completed" else "unknown",
                        if (confirmed != null) "GATAS restart confirmed." else "Restart not confirmed. Refresh to check GATAS.")
                }
            } else {
                val response = exchange(session, command.action, true, id, command.bootId, command.updatePolicy, 15_000)
                if (state.value.operation?.busy == true) {
                    when {
                        response == null -> finish(command, "unknown", "Result unknown. Refresh and check GATAS before trying again.")
                        response.result != DeviceActionCodec.OK -> finish(command, "failed", failureMessage(command, response.result))
                        command.action == DeviceAction.Save -> finish(command, "completed", "GATAS configuration saved and verified.")
                        else -> finish(command, "requested", "Firmware mode requested. Check the USB drive on your computer.")
                    }
                }
            }
        }
        return true
    }

    suspend fun disconnected(session: Any): Boolean = withContext(Dispatchers.Main) {
        if (token !== session) return@withContext false
        token = null; sender = null; probeJob?.cancel()
        pending.values.forEach { it.reply.cancel() }; pending.clear()
        val operation = state.value.operation
        val usb = operation?.action == DeviceAction.UsbBoot.wireName && operation.busy && executeSubmitted
        val result = when {
            usb -> operation.copy(phase = "requested", message = "Firmware mode requested. Check the USB drive on your computer.")
            operation?.busy == true && operation.action != DeviceAction.Restart.wireName ->
                operation.copy(phase = "unknown", message = "Connection lost. Result unknown; refresh before trying again.")
            else -> operation
        }
        mutableState.value = DeviceControlState(recentResults = state.value.recentResults).withOperation(result)
        usb
    }

    fun stop() {
        probeJob?.cancel(); operationJob?.cancel()
        pending.values.forEach { it.reply.cancel() }; pending.clear()
        token = null; sender = null
        val operation = state.value.operation
        mutableState.value = DeviceControlState(recentResults = state.value.recentResults).withOperation(if (operation?.busy == true)
            operation.copy(phase = "unknown", message = "Bridge stopped. Check GATAS before repeating the action.") else operation)
    }

    private suspend fun exchange(session: Any, action: DeviceAction, execute: Boolean, id: UInt,
                                 boot: String, policy: DeviceUpdatePolicy, timeout: Long): DeviceActionResponse? {
        if (token !== session) return null
        val waiter = Pending(action, execute, CompletableDeferred())
        pending[id] = waiter
        return try {
            // Bound the complete exchange, including Bluetooth's write call;
            // otherwise a wedged stack could leave all action controls busy.
            withTimeoutOrNull(timeout) {
                if (!write(session, DeviceActionCodec.encode(action, execute, id, boot, policy))) null
                else waiter.reply.await()
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            if (!kotlinx.coroutines.currentCoroutineContext().isActive) throw cancelled
            null // The connection closed, cancelling the reply, not this job.
        } finally { pending.remove(id) }
    }

    private suspend fun write(session: Any, bytes: ByteArray): Boolean {
        if (token !== session) return false
        return try { sender?.invoke(bytes) == true }
        catch (cancelled: kotlinx.coroutines.CancellationException) {
            if (!kotlinx.coroutines.currentCoroutineContext().isActive) throw cancelled
            false
        } catch (_: Exception) { false }
    }

    private fun nextId(): UInt {
        if (++requestCounter == 0u) ++requestCounter
        return requestCounter
    }
    private fun failureMessage(command: DeviceActionCommand, result: Int): String = DeviceActionCodec.error(result) +
        if (command.action == DeviceAction.UsbBoot && result == 5) " Firmware mode was not started." else ""
    private fun setOperation(command: DeviceActionCommand, phase: String, message: String) {
        mutableState.value = state.value.withOperation(DeviceOperation(command.id, command.action.wireName, phase, message))
    }
    private fun finish(command: DeviceActionCommand, phase: String, message: String) = setOperation(command, phase, message)
}

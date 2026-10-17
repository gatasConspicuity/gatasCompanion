package nl.rvt.gatas.companion.pressure

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal data class PhonePressureChoice(val enabled: Boolean, val revision: Long = 0)

/** One saved Bluetooth target; only an explicit change can request sensor access. */
internal class DevicePhonePressurePreference(
    private val deviceId: String,
    private val settings: PhonePressureSettings = PhonePressureSettings(),
) {
    private val mutableChoice = MutableStateFlow(PhonePressureChoice(settings.isEnabled(deviceId)))
    val changes = mutableChoice.asStateFlow()
    val enabled: Boolean get() = changes.value.enabled
    private var permissionRequested = false

    fun reload() {
        permissionRequested = false
        mutableChoice.value = PhonePressureChoice(settings.isEnabled(deviceId), changes.value.revision + 1)
    }

    fun change(targetDeviceId: String, enabled: Boolean): Boolean {
        if (targetDeviceId != deviceId) return false
        settings.setEnabled(deviceId, enabled)
        permissionRequested = enabled
        mutableChoice.value = PhonePressureChoice(enabled, changes.value.revision + 1)
        return true
    }

    fun takePermissionRequest(): Boolean {
        val requested = permissionRequested
        permissionRequested = false
        return requested
    }
}

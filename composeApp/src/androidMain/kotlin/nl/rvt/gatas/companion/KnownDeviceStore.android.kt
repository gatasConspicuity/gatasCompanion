package nl.rvt.gatas.companion

import androidx.core.content.edit
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import nl.rvt.gatas.appContext

actual object KnownDeviceStore {
    private const val PREFERENCES_NAME = "gatas_companion_devices"
    private const val DEVICES_KEY = "known_devices"
    private const val LAST_USED_DEVICE_KEY = "last_used_device"
    private val json = Json { ignoreUnknownKeys = true }

    actual fun loadDevices(): Set<GaTasDevice> {
        val encoded = appContext.getSharedPreferences(PREFERENCES_NAME, 0)
            .getString(DEVICES_KEY, null) ?: return emptySet()
        return runCatching {
            json.decodeFromString(ListSerializer(GaTasDevice.serializer()), encoded).toSet()
        }.getOrDefault(emptySet())
    }

    actual fun saveDevices(devices: Set<GaTasDevice>) {
        val encoded = json.encodeToString(
            ListSerializer(GaTasDevice.serializer()),
            devices.sortedBy { it.name },
        )
        appContext.getSharedPreferences(PREFERENCES_NAME, 0).edit {
            putString(DEVICES_KEY, encoded)
        }
    }

    actual fun loadLastUsedDevice(): GaTasDevice? {
        val encoded = appContext.getSharedPreferences(PREFERENCES_NAME, 0)
            .getString(LAST_USED_DEVICE_KEY, null) ?: return null
        return runCatching { json.decodeFromString(GaTasDevice.serializer(), encoded) }.getOrNull()
    }

    actual fun saveLastUsedDevice(device: GaTasDevice?) {
        appContext.getSharedPreferences(PREFERENCES_NAME, 0).edit {
            if (device == null) {
                remove(LAST_USED_DEVICE_KEY)
            } else {
                putString(LAST_USED_DEVICE_KEY, json.encodeToString(GaTasDevice.serializer(), device))
            }
        }
    }
}

package nl.rvt.gatas.companion

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import platform.Foundation.NSUserDefaults

actual object KnownDeviceStore {
    private const val DEVICES_KEY = "known_devices"
    private const val LAST_USED_DEVICE_KEY = "last_used_device"
    private val json = Json { ignoreUnknownKeys = true }

    actual fun loadDevices(): Set<GaTasDevice> {
        val encoded = NSUserDefaults.standardUserDefaults.stringForKey(DEVICES_KEY)
            ?: return emptySet()
        return runCatching {
            json.decodeFromString(ListSerializer(GaTasDevice.serializer()), encoded).toSet()
        }.getOrDefault(emptySet())
    }

    actual fun saveDevices(devices: Set<GaTasDevice>) {
        val encoded = json.encodeToString(
            ListSerializer(GaTasDevice.serializer()),
            devices.sortedBy { it.name },
        )
        NSUserDefaults.standardUserDefaults.setObject(encoded, forKey = DEVICES_KEY)
    }

    actual fun loadLastUsedDevice(): GaTasDevice? {
        val encoded = NSUserDefaults.standardUserDefaults.stringForKey(LAST_USED_DEVICE_KEY)
            ?: return null
        return runCatching { json.decodeFromString(GaTasDevice.serializer(), encoded) }.getOrNull()
    }

    actual fun saveLastUsedDevice(device: GaTasDevice?) {
        if (device == null) {
            NSUserDefaults.standardUserDefaults.removeObjectForKey(LAST_USED_DEVICE_KEY)
        } else {
            val encoded = json.encodeToString(GaTasDevice.serializer(), device)
            NSUserDefaults.standardUserDefaults.setObject(encoded, forKey = LAST_USED_DEVICE_KEY)
        }
    }
}

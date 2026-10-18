package nl.rvt.gatas.companion.pressure

/** Saves only the transmission choice, keyed by the saved Bluetooth identifier. */
class PhonePressureSettings internal constructor(private val storage: PhonePressureStorage) {
    constructor() : this(createPhonePressureStorage())

    fun isEnabled(deviceId: String): Boolean = storage.isEnabled(key(deviceId))

    fun setEnabled(deviceId: String, enabled: Boolean) {
        storage.setEnabled(key(deviceId), enabled)
    }

    fun remove(deviceId: String) {
        storage.remove(key(deviceId))
    }

    private fun key(deviceId: String): String {
        require(deviceId.isNotBlank()) { "A Bluetooth device identifier is required" }
        return "phone_pressure_enabled.$deviceId"
    }
}

internal interface PhonePressureStorage {
    fun isEnabled(key: String): Boolean
    fun setEnabled(key: String, enabled: Boolean)
    fun remove(key: String)
}

internal expect fun createPhonePressureStorage(): PhonePressureStorage

package nl.rvt.gatas.companion.pressure

import platform.Foundation.NSUserDefaults

internal actual fun createPhonePressureStorage(): PhonePressureStorage = IosPhonePressureStorage()

internal class IosPhonePressureStorage(
    private val preferences: NSUserDefaults = NSUserDefaults.standardUserDefaults,
) : PhonePressureStorage {
    override fun isEnabled(key: String): Boolean = preferences.boolForKey(key)

    override fun setEnabled(key: String, enabled: Boolean) {
        preferences.setBool(enabled, key)
    }

    override fun remove(key: String) {
        preferences.removeObjectForKey(key)
    }
}

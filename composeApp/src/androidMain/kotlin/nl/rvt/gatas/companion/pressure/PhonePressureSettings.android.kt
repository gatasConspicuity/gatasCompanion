package nl.rvt.gatas.companion.pressure

import androidx.core.content.edit
import nl.rvt.gatas.appContext

internal actual fun createPhonePressureStorage(): PhonePressureStorage = AndroidPhonePressureStorage()

private class AndroidPhonePressureStorage : PhonePressureStorage {
    private val preferences
        get() = appContext.getSharedPreferences("gatas_companion_settings", 0)

    override fun isEnabled(key: String): Boolean = preferences.getBoolean(key, false)

    override fun setEnabled(key: String, enabled: Boolean) {
        preferences.edit { putBoolean(key, enabled) }
    }

    override fun remove(key: String) {
        preferences.edit { remove(key) }
    }
}

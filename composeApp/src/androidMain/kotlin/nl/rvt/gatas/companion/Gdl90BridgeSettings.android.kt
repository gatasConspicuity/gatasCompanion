package nl.rvt.gatas.companion

import androidx.core.content.edit
import nl.rvt.gatas.appContext

actual object Gdl90BridgeSettings {
    private const val PREFERENCES_NAME = "gatas_companion_settings"
    private const val ENABLED_KEY = "gdl90_bridge_enabled"

    actual fun isEnabled(): Boolean =
        appContext
            .getSharedPreferences(PREFERENCES_NAME, 0)
            .getBoolean(ENABLED_KEY, false)

    actual fun setEnabled(enabled: Boolean) {
        appContext.getSharedPreferences(PREFERENCES_NAME, 0).edit {
            putBoolean(ENABLED_KEY, enabled)
        }
    }
}

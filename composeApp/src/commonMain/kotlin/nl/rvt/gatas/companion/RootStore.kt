package nl.rvt.gatas.companion

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Navigation belongs to the screen; device control belongs to CompanionController. */
class RootStore {
    var screen by mutableStateOf(Screen.Landing)
        private set
    private var activeDeviceId: String? = null

    fun landing() { screen = Screen.Landing }
    fun connected(device: GaTasDevice) {
        if (CompanionController.connect(device)) screen = Screen.Connected
    }
    fun showConnected() { screen = Screen.Connected }
    fun connectionInfo() {
        if (screen.isConnected) screen = Screen.ConnectionInfo
    }
    fun blueTooth() { screen = Screen.BlueTooth }
    fun settings() { screen = Screen.Settings }
    fun about() { screen = Screen.About }
    fun back() {
        screen = if (screen == Screen.ConnectionInfo) Screen.Connected else Screen.Landing
    }

    fun activeDeviceChanged(identifier: String?) {
        if (identifier != null && identifier != activeDeviceId) showConnected()
        else if (identifier == null && screen.isConnected) landing()
        activeDeviceId = identifier
    }

    enum class Screen {
        Landing, Connected, ConnectionInfo, BlueTooth, Settings, About;

        val isConnected: Boolean get() = this == Connected || this == ConnectionInfo
    }
}

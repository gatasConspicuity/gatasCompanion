package nl.rvt.gatas.companion

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Navigation belongs to the screen; device control belongs to CompanionController. */
class RootStore {
    var screen by mutableStateOf(Screen.Landing)
        private set
    var gatasDetailsId by mutableStateOf<String?>(null)
        private set
    private var activeDeviceId: String? = null

    private fun navigate(target: Screen) {
        gatasDetailsId = null
        screen = target
    }

    fun landing() { navigate(Screen.Landing) }
    fun connected(device: GaTasDevice) {
        if (CompanionController.connect(device)) showConnected()
    }
    fun showConnected() { navigate(Screen.Connected) }
    fun connectionInfo() {
        if (screen.isConnected) navigate(Screen.ConnectionInfo)
    }
    fun gatasDetails(hexCode: String) {
        if (screen.isConnected) {
            gatasDetailsId = hexCode
            screen = Screen.GatasDetails
        }
    }
    fun blueTooth() { navigate(Screen.BlueTooth) }
    fun settings() { navigate(Screen.Settings) }
    fun about() { navigate(Screen.About) }
    fun back() {
        if (screen == Screen.ConnectionInfo || screen == Screen.GatasDetails) showConnected() else landing()
    }

    fun activeDeviceChanged(identifier: String?) {
        if (identifier != null && identifier != activeDeviceId) showConnected()
        else if (identifier == null && screen.isConnected) landing()
        activeDeviceId = identifier
    }

    enum class Screen {
        Landing, Connected, ConnectionInfo, GatasDetails, BlueTooth, Settings, About;

        val isConnected: Boolean get() = this == Connected || this == ConnectionInfo || this == GatasDetails
    }
}

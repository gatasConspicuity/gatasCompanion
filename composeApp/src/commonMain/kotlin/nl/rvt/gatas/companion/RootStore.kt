package nl.rvt.gatas.companion

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Navigation belongs to the screen; device control belongs to CompanionController. */
class RootStore {
    var screen by mutableStateOf(Screen.Landing)
        private set
    fun landing() { screen = Screen.Landing }
    fun connected(device: GaTasDevice) {
        if (CompanionController.connect(device)) screen = Screen.Connected
    }
    fun showConnected() { screen = Screen.Connected }
    fun blueTooth() { screen = Screen.BlueTooth }
    fun settings() { screen = Screen.Settings }
    enum class Screen { Landing, Connected, BlueTooth, Settings }
}

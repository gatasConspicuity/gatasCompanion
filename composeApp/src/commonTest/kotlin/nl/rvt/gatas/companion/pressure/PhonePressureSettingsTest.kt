package nl.rvt.gatas.companion.pressure

import nl.rvt.gatas.companion.GaTasDevice
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PhonePressureSettingsTest {
    @Test
    fun newDevicesDefaultOffAndChoicesFollowIdentifiersAcrossDeviceSwitches() {
        val settings = PhonePressureSettings(MemoryStorage())
        val a = GaTasDevice(name = "GATAS", identifier = "device-a")
        val b = GaTasDevice(name = "GATAS", identifier = "device-b")
        assertFalse(settings.isEnabled(a.identifier))
        assertFalse(settings.isEnabled(b.identifier))

        settings.setEnabled(a.identifier, true)
        assertTrue(settings.isEnabled(a.identifier))
        assertFalse(settings.isEnabled(b.identifier))
        assertTrue(settings.isEnabled(a.identifier))

        // Renaming or receiving a replacement device object does not change its choice.
        assertTrue(settings.isEnabled(a.copy(name = "Renamed GATAS").identifier))
        assertFalse(settings.isEnabled(b.copy(name = "Renamed GATAS").identifier))
    }

    @Test
    fun recreatedSettingsReadBothEnabledAndDisabledChoicesFromStorage() {
        val storage = MemoryStorage()
        PhonePressureSettings(storage).apply {
            setEnabled("device-a", true)
            setEnabled("device-b", true)
            setEnabled("device-b", false)
        }
        val restored = PhonePressureSettings(storage)
        assertTrue(restored.isEnabled("device-a"))
        assertFalse(restored.isEnabled("device-b"))
        assertFalse(restored.isEnabled("new-device"))
    }

    @Test
    fun forgettingAndReaddingOneDeviceKeepsTheOtherDevicesChoice() {
        val storage = MemoryStorage()
        val settings = PhonePressureSettings(storage)
        settings.setEnabled("device", true)
        settings.setEnabled("device.other", true)
        settings.remove("device")

        val reloaded = PhonePressureSettings(storage)
        assertFalse(reloaded.isEnabled("device"))
        assertTrue(reloaded.isEnabled("device.other"))
        reloaded.setEnabled("device", true)
        assertTrue(reloaded.isEnabled("device"))
    }

    private class MemoryStorage : PhonePressureStorage {
        private val values = mutableMapOf<String, Boolean>()
        override fun isEnabled(key: String): Boolean = values[key] ?: false
        override fun setEnabled(key: String, enabled: Boolean) { values[key] = enabled }
        override fun remove(key: String) { values.remove(key) }
    }
}

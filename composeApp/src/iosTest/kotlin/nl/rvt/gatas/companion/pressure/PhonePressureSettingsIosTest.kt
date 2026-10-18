package nl.rvt.gatas.companion.pressure

import platform.Foundation.NSUserDefaults
import platform.Foundation.NSUUID
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PhonePressureSettingsIosTest {
    @Test
    fun independentSettingsInstancesRestoreAndRemoveNativeDeviceChoices() {
        val suiteName = "gatas.phone-pressure.tests.${NSUUID().UUIDString}"
        val preferences = NSUserDefaults(suiteName = suiteName)
        try {
            val first = PhonePressureSettings(IosPhonePressureStorage(preferences))
            assertFalse(first.isEnabled("device-a"))
            assertFalse(first.isEnabled("device-b"))
            first.setEnabled("device-a", true)
            first.setEnabled("device-b", true)
            first.setEnabled("device-b", false)

            val restored = PhonePressureSettings(IosPhonePressureStorage(NSUserDefaults(suiteName = suiteName)))
            assertTrue(restored.isEnabled("device-a"))
            assertFalse(restored.isEnabled("device-b"))
            restored.setEnabled("device-b", true)
            restored.remove("device-a")
            assertFalse(first.isEnabled("device-a"))
            assertTrue(first.isEnabled("device-b"))
        } finally {
            preferences.removePersistentDomainForName(suiteName)
        }
    }
}

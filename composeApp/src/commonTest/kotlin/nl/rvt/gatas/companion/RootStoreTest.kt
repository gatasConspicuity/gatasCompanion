package nl.rvt.gatas.companion

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RootStoreTest {
    @Test
    fun webDetailsAndBackKeepTheActiveSession() {
        val store = RootStore()
        store.activeDeviceChanged("device-one")
        store.gatasDetails("A1B2C3D4")
        assertEquals(RootStore.Screen.GatasDetails, store.screen)
        assertEquals("A1B2C3D4", store.gatasDetailsId)
        assertTrue(store.screen.isConnected)
        store.activeDeviceChanged("device-one")
        assertEquals(RootStore.Screen.GatasDetails, store.screen)
        assertEquals("A1B2C3D4", store.gatasDetailsId)
        store.back()
        assertEquals(RootStore.Screen.Connected, store.screen)
        assertNull(store.gatasDetailsId)
    }

    @Test
    fun endingOrReplacingTheDeviceClosesItsWebDetails() {
        listOf(null, "device-two").forEach { nextDevice ->
            val store = RootStore()
            store.activeDeviceChanged("device-one")
            store.gatasDetails("A1B2C3D4")
            store.activeDeviceChanged(nextDevice)
            assertEquals(if (nextDevice == null) RootStore.Screen.Landing else RootStore.Screen.Connected, store.screen)
            assertNull(store.gatasDetailsId)
        }
    }

    @Test
    fun webDetailsCannotOpenOutsideTheConnectedArea() {
        val store = RootStore()
        store.gatasDetails("A1B2C3D4")
        assertEquals(RootStore.Screen.Landing, store.screen)
        assertNull(store.gatasDetailsId)
    }

    @Test
    fun detailsAndBackKeepTheActiveSession() {
        val store = RootStore()
        store.activeDeviceChanged("device-one")
        store.connectionInfo()
        assertEquals(RootStore.Screen.ConnectionInfo, store.screen)
        store.activeDeviceChanged("device-one")
        assertEquals(RootStore.Screen.ConnectionInfo, store.screen)
        store.back()
        assertEquals(RootStore.Screen.Connected, store.screen)
    }

    @Test
    fun stoppingWhileViewingDetailsReturnsHome() {
        val store = RootStore()
        store.activeDeviceChanged("device-one")
        store.connectionInfo()
        store.activeDeviceChanged(null)
        assertEquals(RootStore.Screen.Landing, store.screen)
        store.connectionInfo()
        assertEquals(RootStore.Screen.Landing, store.screen)
    }

    @Test
    fun aDifferentDeviceReturnsToItsOverview() {
        val store = RootStore()
        store.activeDeviceChanged("device-one")
        store.connectionInfo()
        store.activeDeviceChanged("device-two")
        assertEquals(RootStore.Screen.Connected, store.screen)
    }

    @Test
    fun secondaryPagesReturnHome() {
        val store = RootStore()
        store.about()
        assertEquals(RootStore.Screen.About, store.screen)
        store.back()
        assertEquals(RootStore.Screen.Landing, store.screen)
        store.settings()
        store.back()
        assertEquals(RootStore.Screen.Landing, store.screen)
        store.blueTooth()
        store.back()
        assertEquals(RootStore.Screen.Landing, store.screen)
    }

    @Test
    fun sessionEndingDoesNotReplaceAboutOrSettings() {
        val store = RootStore()
        store.activeDeviceChanged("device-one")
        store.about()
        store.activeDeviceChanged(null)
        assertEquals(RootStore.Screen.About, store.screen)
    }
}

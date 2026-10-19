package nl.rvt.gatas.companion

import kotlin.test.Test
import kotlin.test.assertEquals

class RootStoreTest {
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

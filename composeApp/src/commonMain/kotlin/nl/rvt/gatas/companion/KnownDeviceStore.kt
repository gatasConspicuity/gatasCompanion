package nl.rvt.gatas.companion

expect object KnownDeviceStore {
    fun loadDevices(): Set<GaTasDevice>
    fun saveDevices(devices: Set<GaTasDevice>)
    fun loadLastUsedDevice(): GaTasDevice?
    fun saveLastUsedDevice(device: GaTasDevice?)
}

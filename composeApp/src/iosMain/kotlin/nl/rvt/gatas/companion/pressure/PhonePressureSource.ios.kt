@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package nl.rvt.gatas.companion.pressure

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.CoreMotion.CMAltimeter
import platform.CoreMotion.CMAuthorizationStatusAuthorized
import platform.CoreMotion.CMAuthorizationStatusDenied
import platform.CoreMotion.CMAuthorizationStatusRestricted
import platform.Foundation.NSOperationQueue
import platform.posix.CLOCK_MONOTONIC_RAW
import platform.posix.CLOCK_UPTIME_RAW
import platform.posix.clock_gettime_nsec_np
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationState.UIApplicationStateActive

actual fun phonePressureNowMillis(): Long = (clock_gettime_nsec_np(CLOCK_MONOTONIC_RAW.toUInt()) / 1_000_000u).toLong()
actual fun createPhonePressureSource(): PhonePressureSource = IosPhonePressureSource()
actual fun phonePressurePlatformNotice(): String =
    "On iOS, barometer access uses the app-wide Motion & Fitness permission. " +
        "Sending is enabled separately for each GATAS device."

private class IosPhonePressureSource : PhonePressureSource {
    private val mutableState = MutableStateFlow(PressureSensorState())
    override val state = mutableState.asStateFlow()
    private val altimeter = CMAltimeter()
    private var generation = 0L

    override fun start(allowPermissionRequest: Boolean) {
        stop()
        if (!CMAltimeter.isRelativeAltitudeAvailable()) {
            mutableState.value = PressureSensorState(PressureSensorCondition.Unavailable)
            return
        }
        val authorization = CMAltimeter.authorizationStatus()
        if (authorization == CMAuthorizationStatusDenied || authorization == CMAuthorizationStatusRestricted) {
            mutableState.value = PressureSensorState(PressureSensorCondition.PermissionDenied)
            return
        }
        if (authorization != CMAuthorizationStatusAuthorized &&
            (!allowPermissionRequest || UIApplication.sharedApplication.applicationState != UIApplicationStateActive)) {
            mutableState.value = PressureSensorState(PressureSensorCondition.PermissionRequired)
            return
        }
        val session = generation
        var clockOffset = phonePressureNowMillis() - uptimeMillis()
        var minimumTimestamp = uptimeMillis()
        mutableState.value = PressureSensorState(PressureSensorCondition.Waiting)
        altimeter.startRelativeAltitudeUpdatesToQueue(NSOperationQueue.mainQueue) { data, error ->
            if (generation != session) return@startRelativeAltitudeUpdatesToQueue
            val currentAuthorization = CMAltimeter.authorizationStatus()
            if (currentAuthorization == CMAuthorizationStatusDenied || currentAuthorization == CMAuthorizationStatusRestricted) {
                mutableState.value = PressureSensorState(PressureSensorCondition.PermissionDenied)
            } else if (error != null) {
                mutableState.value = PressureSensorState(PressureSensorCondition.Error, message = error.localizedDescription)
            } else if (data != null) {
                val now = phonePressureNowMillis()
                val uptime = uptimeMillis()
                val offset = now - uptime
                // CMLogItem uses Mach absolute time, which pauses during system sleep.
                // After sleep, discard queued values until a measurement taken after wake.
                if (offset - clockOffset > 100) {
                    minimumTimestamp = uptime
                    mutableState.value = PressureSensorState(PressureSensorCondition.Waiting)
                }
                clockOffset = offset
                val timestamp = (data.timestamp * 1000).toLong()
                if (timestamp < minimumTimestamp) return@startRelativeAltitudeUpdatesToQueue
                val sample = PhonePressureSample(data.pressure.floatValue * 10f, now - (uptime - timestamp))
                if (sample.isFresh(now)) mutableState.value = PressureSensorState(PressureSensorCondition.Available, sample)
            }
        }
    }

    private fun uptimeMillis(): Long = (clock_gettime_nsec_np(CLOCK_UPTIME_RAW.toUInt()) / 1_000_000u).toLong()

    override fun stop() {
        generation++
        altimeter.stopRelativeAltitudeUpdates()
        mutableState.value = PressureSensorState()
    }
}

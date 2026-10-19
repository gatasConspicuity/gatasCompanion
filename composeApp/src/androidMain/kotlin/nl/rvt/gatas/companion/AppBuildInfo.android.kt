package nl.rvt.gatas.companion

import androidx.core.content.pm.PackageInfoCompat
import nl.rvt.gatas.appContext

actual fun appBuildInfo(): AppBuildInfo {
    val info = appContext.packageManager.getPackageInfo(appContext.packageName, 0)
    return AppBuildInfo(info.versionName ?: "Unknown", PackageInfoCompat.getLongVersionCode(info).toString())
}

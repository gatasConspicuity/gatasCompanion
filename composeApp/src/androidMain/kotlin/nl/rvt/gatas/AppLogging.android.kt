package nl.rvt.gatas

import android.content.pm.ApplicationInfo

internal actual fun isDebugBuild(): Boolean =
    appContext.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

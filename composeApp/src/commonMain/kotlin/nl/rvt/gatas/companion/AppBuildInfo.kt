package nl.rvt.gatas.companion

data class AppBuildInfo(val version: String, val buildNumber: String)

expect fun appBuildInfo(): AppBuildInfo

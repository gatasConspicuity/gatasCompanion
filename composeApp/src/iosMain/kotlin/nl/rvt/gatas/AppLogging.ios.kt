package nl.rvt.gatas

import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.Platform

@OptIn(ExperimentalNativeApi::class)
internal actual fun isDebugBuild(): Boolean = Platform.isDebugBinary

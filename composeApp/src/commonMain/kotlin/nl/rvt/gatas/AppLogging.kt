package nl.rvt.gatas

import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity

private var loggingConfigured = false
private var detailedDiagnosticsEnabled = false

fun initializeLogging() {
    if (loggingConfigured) {
        return
    }

    // The timing diagnostics used to distinguish relay delays from local GDL90
    // forwarding delays are intentionally logged at Debug severity. Keep those
    // measurements available to developers while preventing verbose protocol
    // activity from being emitted by production builds.
    detailedDiagnosticsEnabled = isDebugBuild()
    Logger.setMinSeverity(if (detailedDiagnosticsEnabled) Severity.Debug else Severity.Warn)
    loggingConfigured = true
}

/**
 * Returns whether privacy-preserving timing and protocol summaries may run.
 *
 * Callers use this in addition to the logger severity so release builds avoid
 * the parsing, allocations, and correlation state required only for diagnosis.
 */
internal fun areDetailedDiagnosticsEnabled(): Boolean = detailedDiagnosticsEnabled

/**
 * Returns whether the current application binary was built for debugging.
 *
 * This is platform-specific because Android exposes it through the application's
 * debuggable flag, while Kotlin/Native records it directly in the binary.
 */
internal expect fun isDebugBuild(): Boolean

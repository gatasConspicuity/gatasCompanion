package nl.rvt.gatas.companion.remote

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch
import nl.rvt.gatas.companion.CompanionController
import nl.rvt.gatas.initializeCompanionRuntime

/** Swift boundary: callers use MainActor; no Compose view is required. */
object WatchRemoteBridge {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun initialize() { initializeCompanionRuntime(); CompanionController.initialize() }
    fun snapshot(): String = CompanionController.snapshotJson()
    fun handleCommand(encoded: String): String = CompanionController.handleRemoteCommand(encoded)

    @OptIn(FlowPreview::class)
    fun observe(onState: (String) -> Unit): WatchSubscription {
        val job = scope.launch {
            CompanionController.state.sample(1_000).collect { onState(snapshot()) }
        }
        return WatchSubscription(job)
    }
}

class WatchSubscription internal constructor(private val job: Job) {
    fun cancel() { job.cancel() }
}

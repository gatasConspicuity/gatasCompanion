package nl.rvt.gatas.companion

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import nl.rvt.gatas.companion.services.BridgeStatus
import nl.rvt.gatas.companion.services.DeviceAction
import nl.rvt.gatas.companion.services.DeviceControlState
import nl.rvt.gatas.companion.services.DeviceUpdatePolicy

private data class ObservedDeviceAction(val action: DeviceAction, val deviceId: String?, val control: DeviceControlState)
private data class DeviceActionsTarget(val deviceId: String?, val bootId: String?)

@Composable
fun DeviceActionsMenu(status: BridgeStatus, deviceId: String?, content: @Composable (onOpen: () -> Unit) -> Unit) {
    val control = status.deviceControl
    var menuTarget by remember { mutableStateOf<DeviceActionsTarget?>(null) }
    var confirmation by remember { mutableStateOf<ObservedDeviceAction?>(null) }
    val available = status.bleConnected && control.operation?.busy != true &&
        status.aircraftChangeTargetIcaoAddress == null && status.wifiModeChangeTarget == null
    content { menuTarget = DeviceActionsTarget(deviceId, control.bootId) }

    menuTarget?.let { target ->
        // Opening a menu must not silently retarget its actions if the phone
        // changes devices or GATAS restarts while the menu is still visible.
        val sameDevice = target.deviceId == deviceId && target.bootId == control.bootId
        val menuAvailable = available && sameDevice
        fun select(action: DeviceAction) {
            menuTarget = null
            if (action == DeviceAction.Save) {
                CompanionController.requestDeviceAction(action, target.deviceId, target.bootId)
            } else {
                confirmation = ObservedDeviceAction(action, target.deviceId, control)
            }
        }
        DeviceActionsDialog(
            status = status,
            sameDevice = sameDevice,
            available = menuAvailable,
            onSelect = ::select,
            onDismiss = { menuTarget = null },
        )
    }

    confirmation?.let { observed ->
        fun submit(policy: DeviceUpdatePolicy) {
            // Use what the person saw when opening the dialog, never silently
            // retarget its destructive action to a newer selected device/boot.
            CompanionController.requestDeviceAction(observed.action, observed.deviceId, observed.control.bootId, policy)
            confirmation = null
        }
        DeviceActionConfirmationDialog(
            action = observed.action,
            hasUnsavedChanges = observed.control.hasUnsavedChanges == true,
            onSubmit = ::submit,
            onDismiss = { confirmation = null },
        )
    }
}

@Composable
fun DeviceActionFeedback(status: BridgeStatus, modifier: Modifier = Modifier) {
    // Feedback stays visible after the action menu closes, including on detail pages.
    status.deviceControl.operation?.let { operation ->
        Row(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (operation.busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            Text(operation.message, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f),
                color = if (operation.phase in listOf("failed", "unknown"))
                    MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

package nl.rvt.gatas.companion

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.icerock.moko.permissions.PermissionsController

@Composable
fun App(permissionsController: PermissionsController) {
    RootContent(modifier = Modifier.fillMaxSize(), permissionsController = permissionsController)
}

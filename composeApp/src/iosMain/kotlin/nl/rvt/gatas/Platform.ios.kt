package nl.rvt.gatas

import androidx.compose.runtime.Composable
import com.juul.kable.ExperimentalApi
import com.juul.kable.Peripheral
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import platform.Foundation.*
import platform.posix.memcpy
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

actual suspend fun requestMtuIfSupported(peripheral: Peripheral) {
    // CoreBluetooth negotiates the ATT MTU internally on iOS and does not expose an
    // application API equivalent to Android's requestMtu(). No action is required.
}

@OptIn(ExperimentalApi::class, ExperimentalUuidApi::class)
actual fun restorePeripheralIfPossible(identifier: String): Peripheral? {
    return runCatching {
        Peripheral(Uuid.parse(identifier)) {}
    }.getOrNull()
}

actual suspend fun loadKoins() {

}

@Composable
actual fun PlatformKeepScreenOnEffect() {
    // The Android implementation owns a window flag. Compose Multiplatform does not
    // expose the corresponding UIApplication idle-timer setting here, so iOS keeps
    // its normal system-controlled display behaviour.
}

//fun listFilesRecursively(path: String): List<String> {
//    val fileManager = NSFileManager.defaultManager
//    val enumerator = fileManager.enumeratorAtPath(path) ?: return emptyList()
//
//    val result = mutableListOf<String>()
//    while (true) {
//        val next = enumerator.nextObject() ?: break
//        if (next is NSString) {
//            result.add(next.toString())
//        }
//    }
//    return result
//}

@OptIn(ExperimentalForeignApi::class)
actual suspend fun loadEgm2008Bin(): ByteArray {
//    val resourcePath = NSBundle.mainBundle.resourcePath!!
//    val paths = listFilesRecursively(resourcePath)
//    println(paths)

    val path = NSBundle.mainBundle.pathForResource("compose-resources/composeResources/gatascompanion.composeapp.generated.resources/files/egm2008", "bin")
        ?: error("egm2008.bin not found")
    val data = NSData.dataWithContentsOfFile(path)
        ?: error("Failed to load egm2008.bin")
    val byteArray = ByteArray(data.length.toInt())

    byteArray.usePinned { pinned ->
        memcpy(pinned.addressOf(0), data.bytes, data.length)
    }

    return byteArray
}

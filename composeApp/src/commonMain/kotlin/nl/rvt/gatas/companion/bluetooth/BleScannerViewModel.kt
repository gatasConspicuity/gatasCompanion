package nl.rvt.gatas.companion.bluetooth

import androidx.lifecycle.ViewModel
import com.juul.kable.Scanner
import com.juul.kable.logs.Logging.Level.Warnings
import com.juul.kable.logs.SystemLogEngine
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
val GATAS_PRIMARY_DEVICE: Uuid =
    Uuid.parse("0000ffe0-0000-1000-8000-00805f9b34fb")

@OptIn(ExperimentalUuidApi::class)
val GATAS_RXTX_CHARACTERISTIC: Uuid =
    Uuid.parse("0000ffe1-0000-1000-8000-00805f9b34fb")

@OptIn(ExperimentalUuidApi::class)
val GATAS_COBS_CHARACTERISTIC: Uuid =
    Uuid.parse("0000ffe2-0000-1000-8000-00805f9b34fb")


class BleScannerViewModel : ViewModel() {
    companion object {
        @OptIn(ExperimentalUuidApi::class)
        val scanner by lazy {
            Scanner {
                filters {
                    match {
                        services = listOf(GATAS_PRIMARY_DEVICE)
                    }
                }
                logging {
                    engine = SystemLogEngine
                    level = Warnings
                }
            }
        }
    }



    val discovery = DeviceDiscovery.state
    fun startScanning() = DeviceDiscovery.start("phone")
    fun stopScanning() = DeviceDiscovery.stop("phone")
    override fun onCleared() { stopScanning() }
}

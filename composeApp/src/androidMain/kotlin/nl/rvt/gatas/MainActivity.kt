package nl.rvt.gatas

import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Surface
import dev.icerock.moko.permissions.PermissionsController
import io.ktor.client.HttpClient
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders.ContentEncoding
//import io.ktor.client.plugins.compression.ContentEncoding
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import nl.rvt.gatas.companion.App
import org.koin.core.component.KoinComponent
import org.koin.core.context.GlobalContext.startKoin
import org.koin.core.qualifier.named
import org.koin.dsl.module
import kotlin.invoke


object KtorClient : KoinComponent {
    fun getHttpClient(timeout: Long = 1500): HttpClient = HttpClient {
        install(HttpTimeout) {
            requestTimeoutMillis = timeout
            connectTimeoutMillis = 1500
            socketTimeoutMillis = 900
        }

//        ContentEncoding()

        install(ContentNegotiation) {
            json(Json {
                prettyPrint = true
                isLenient = true
                ignoreUnknownKeys = true
            })
        }

        install(DefaultRequest) {
//            headers.append("Accept", "application/json, */*;q=0.8")
//            headers.append("Accept-Encoding", "gzip, deflate")
//            headers.append("Accept-Language", "en-GB,en;q=0.9")
//            headers.append("Priority", "u=0, i")
//            headers.append("Sec-Fetch-Dest", "document")
//            headers.append("Sec-Fetch-Mode", "navigate")
//            headers.append("Sec-Fetch-Site", "none")
            headers.append("User-Agent", "GATAS Server http://github.com/rvt/openace")
        }
    }
}

val appModule = module {
    single<HttpClient>(named("adsbClient")) {
        KtorClient.getHttpClient(1500)
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        appContext = applicationContext
        initializeLogging()

        startKoin {
//        androidContext(appContext)

//            androidLogger()
//            androidContext(this@MainApplication)
            modules(appModule)


        }

        val permissionsController = PermissionsController(applicationContext)
//        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);

//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
//            // Define aspect ratio or width/height
//            val aspectRatio = Rational(9, 9) // 16:9 aspect ratio for example
//
//            val pictureInPictureParams = PictureInPictureParams.Builder()
//                .setAspectRatio(aspectRatio)
//                .build()
//
//            // Start PiP mode
//            enterPictureInPictureMode(pictureInPictureParams)
//        }

        setContent {
            Surface(
            ) {
                App(
                    permissionsController = permissionsController
                )
            }
        }
    }
}

//@Preview
//@Composable
//fun AppAndroidPreview() {
//    val permissionsController = PermissionsController(applicationContext)
//    App(
//        permissionsController = permissionsController
//    )
//}

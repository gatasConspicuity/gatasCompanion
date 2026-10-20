package nl.rvt.gatas

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnPreDraw
import dev.icerock.moko.permissions.PermissionsController
import kotlinx.coroutines.CompletableDeferred
import nl.rvt.gatas.companion.App
import kotlin.math.roundToInt

/** Show a native loading frame before constructing the Compose hierarchy. */
internal fun ComponentActivity.setGatasContent(permissionsController: PermissionsController) {
    val container = FrameLayout(this)
    val loading = GatasStartupView(this)
    container.addView(loading, FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT,
    ))
    setContentView(container)

    loading.doOnPreDraw {
        container.post {
            if (isDestroyed) return@post
            val compose = ComposeView(this).apply {
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
                setContent {
                    val firstDraw = remember { CompletableDeferred<Unit>() }
                    LaunchedEffect(firstDraw) {
                        firstDraw.await()
                        // Let the first app frame reach the window before
                        // uncovering it. No minimum splash duration is imposed.
                        withFrameNanos { }
                        container.removeView(loading)
                    }
                    Box(Modifier.fillMaxSize().drawWithContent {
                        drawContent()
                        firstDraw.complete(Unit)
                    }) {
                        App(permissionsController)
                    }
                }
            }
            container.addView(compose, 0, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT,
            ))
        }
    }
}

private class GatasStartupView(context: Context) : FrameLayout(context) {
    private val logo: ImageView

    init {
        setBackgroundColor(context.getColor(R.color.gatas_launch_background))
        isClickable = true
        LayoutInflater.from(context).inflate(R.layout.gatas_startup, this, true)
        logo = findViewById(R.id.startup_logo)
        ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
            val safe = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            setPadding(safe.left, 0, safe.right, 0)
            resizeLogo(width - paddingLeft - paddingRight)
            insets
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        resizeLogo(w - paddingLeft - paddingRight)
    }

    private fun resizeLogo(availableWidth: Int) {
        if (availableWidth <= 0) return
        val density = resources.displayMetrics.density
        val logoWidth = minOf((320 * density).roundToInt(), (availableWidth - 48 * density).roundToInt().coerceAtLeast(1))
        if (logo.layoutParams.width == logoWidth) return
        logo.layoutParams = logo.layoutParams.apply {
            width = logoWidth
            height = (logoWidth * 324f / 1024).roundToInt()
        }
    }
}

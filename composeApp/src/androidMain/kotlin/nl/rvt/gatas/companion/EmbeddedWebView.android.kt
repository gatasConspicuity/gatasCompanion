package nl.rvt.gatas.companion

import android.annotation.SuppressLint
import android.webkit.WebResourceRequest
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

@SuppressLint("SetJavaScriptEnabled")
@Composable
actual fun EmbeddedWebView(
    url: String,
    modifier: Modifier,
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView,
                        request: WebResourceRequest,
                    ): Boolean = !request.url.isAllowedGatasUrl()
                }
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
                loadUrl(url)
            }
        },
        update = { webView ->
            if (webView.url != url) {
                webView.loadUrl(url)
            }
        },
    )
}

/**
 * Keeps the embedded browser inside the one HTTPS origin used by the aircraft
 * details screen. Returning `true` from the client blocks every other origin,
 * including custom schemes and redirects to clear-text HTTP.
 */
private fun android.net.Uri.isAllowedGatasUrl(): Boolean =
    scheme == "https" && host.equals("gatas.vantwisk.nl", ignoreCase = true)

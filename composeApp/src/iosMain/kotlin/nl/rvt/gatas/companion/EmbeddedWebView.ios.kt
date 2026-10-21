package nl.rvt.gatas.companion

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSURL
import platform.Foundation.NSURLRequest
import platform.UIKit.UIColor
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun EmbeddedWebView(
    url: String,
    modifier: Modifier,
) {
    UIKitView(
        modifier = modifier,
        factory = {
            WKWebView(
                frame = CGRectMake(0.0, 0.0, 0.0, 0.0),
                configuration = WKWebViewConfiguration(),
            ).apply {
                opaque = false
                backgroundColor = UIColor.clearColor
                NSURL.URLWithString(url)?.let { targetUrl ->
                    loadRequest(NSURLRequest.requestWithURL(targetUrl))
                }
            }
        },
        update = { webView ->
            val currentUrl = webView.URL?.absoluteString
            if (currentUrl != url) {
                NSURL.URLWithString(url)?.let { targetUrl ->
                    webView.loadRequest(NSURLRequest.requestWithURL(targetUrl))
                }
            }
        },
    )
}

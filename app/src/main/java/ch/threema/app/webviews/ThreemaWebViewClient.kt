package ch.threema.app.webviews

import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.net.toUri

class ThreemaWebViewClient(
    private val onNavigationRequestListener: OnNavigationRequestListener,
) : WebViewClient() {
    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        if (!request.isForMainFrame || request.isRedirect) {
            return false
        }
        return onNavigationRequestListener.onNavigationRequested(request.url)
    }

    @Deprecated("Deprecated in Android API 21")
    override fun shouldOverrideUrlLoading(view: WebView?, url: String): Boolean =
        onNavigationRequestListener.onNavigationRequested(url.toUri())

    fun interface OnNavigationRequestListener {
        /**
         * Called when a navigation to a different page is requested, e.g. because the user clicked a link.
         * @return True to intercept the request and prevent loading, false to load the requested page normally
         */
        fun onNavigationRequested(url: Uri): Boolean
    }
}

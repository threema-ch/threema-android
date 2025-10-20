/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2025 Threema GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

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

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

package ch.threema.app.onprem

import android.net.http.X509TrustManagerExtensions
import com.datatheorem.android.trustkit.TrustKit
import com.datatheorem.android.trustkit.pinning.SystemTrustManager
import java.security.cert.X509Certificate

class TrustManagerDelegate {

    private val trustManagerExtensions = X509TrustManagerExtensions(SystemTrustManager.getInstance())

    fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?, hostname: String) {
        // When an app defines static cert pins (which we do), Android requires that [X509TrustManagerExtensions.checkServerTrusted] is called
        // before the request is made to ensure that the cert pinning can be enforced.
        // TrustKit does this under the hood, but ONLY when a configuration (i.e. static pinning rules) exist for the given hostname.
        // Therefore, we need to check here whether we need to invoke [X509TrustManagerExtensions.checkServerTrusted] ourselves or
        // whether we can delegate it to TrustKit's own TrustManager.
        if (TrustKit.getInstance().configuration.getPolicyForHostname(hostname) == null) {
            trustManagerExtensions.checkServerTrusted(chain, authType, hostname)
        } else {
            TrustKit.getInstance().getTrustManager(hostname).checkServerTrusted(chain, authType)
        }
    }

    fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        SystemTrustManager.getInstance().checkClientTrusted(chain, authType)
    }

    fun getAcceptedIssuers(): Array<X509Certificate> =
        SystemTrustManager.getInstance().acceptedIssuers
}

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
import ch.threema.app.utils.SSLUtil
import java.security.cert.X509Certificate

class TrustManagerDelegate {

    private val trustManager = SSLUtil.defaultTrustManager
    private val trustManagerExtensions = X509TrustManagerExtensions(trustManager)

    fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?, hostname: String) {
        trustManagerExtensions.checkServerTrusted(chain, authType, hostname)
    }

    fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        trustManager.checkClientTrusted(chain, authType)
    }

    fun getAcceptedIssuers(): Array<X509Certificate> =
        trustManager.acceptedIssuers
}

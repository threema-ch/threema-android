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

package ch.threema.app.utils

import ch.threema.app.onprem.OnPremSSLSocketFactoryProvider
import java.security.KeyStore
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

object SSLUtil {
    private val defaultSSLSocketFactory: SSLSocketFactory

    val defaultTrustManager: X509TrustManager

    init {
        val trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        trustManagerFactory.init(null as KeyStore?)
        val trustManagers = trustManagerFactory.trustManagers
        defaultTrustManager = trustManagers.filterIsInstance<X509TrustManager>().first()
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, trustManagers, null)
        defaultSSLSocketFactory = sslContext.socketFactory
    }

    /**
     * Get a Socket Factory for certificate pinning and forced TLS version upgrade.
     */
    @JvmStatic
    fun getSSLSocketFactory(host: String): SSLSocketFactory {
        val sslSocketFactory = if (ConfigUtils.isOnPremBuild()) {
            OnPremSSLSocketFactoryProvider.instance.getSslSocketFactory(host)
        } else {
            defaultSSLSocketFactory
        }
        return TLSUpgradeSocketFactoryWrapper(sslSocketFactory)
    }
}

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

import ch.threema.app.services.OnPremConfigFetcherProvider
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.X509TrustManager
import okhttp3.OkHttpClient

object OnPremCertPinning {
    fun createClientWithCertPinning(
        baseClient: OkHttpClient,
        onPremConfigFetcherProvider: OnPremConfigFetcherProvider,
    ): OkHttpClient {
        val hostnameProvider = ThreadLocalHostnameProvider()
        val trustManager = OnPremCertPinningTrustManager(
            onPremConfigFetcherProvider = onPremConfigFetcherProvider,
            hostnameProvider = hostnameProvider,
        )
        return baseClient.newBuilder()
            .sslSocketFactory(createSocketFactory(trustManager), trustManager)
            .addInterceptor(OnPremCertPinningInterceptor(hostnameProvider))
            .build()
    }

    fun createSocketFactory(trustManager: X509TrustManager): SSLSocketFactory {
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, arrayOf(trustManager), null)
        return sslContext.socketFactory
    }
}

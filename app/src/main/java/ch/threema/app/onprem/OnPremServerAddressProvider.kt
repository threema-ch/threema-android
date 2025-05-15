/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2021-2025 Threema GmbH
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

import ch.threema.app.BuildConfig
import ch.threema.base.ThreemaException
import ch.threema.domain.onprem.OnPremConfig
import ch.threema.domain.onprem.OnPremConfigFetcher
import ch.threema.domain.onprem.OnPremConfigMediator
import ch.threema.domain.protocol.ServerAddressProvider
import ch.threema.domain.protocol.connection.d2m.MultiDevicePropertyProvider
import ch.threema.domain.protocol.urls.AppRatingUrl
import ch.threema.domain.protocol.urls.BlobUrl
import ch.threema.domain.protocol.urls.DeviceGroupUrl
import ch.threema.domain.protocol.urls.MapPoiAroundUrl
import ch.threema.domain.protocol.urls.MapPoiNamesUrl

class OnPremServerAddressProvider(
    private val fetcherProvider: FetcherProvider,
) : ServerAddressProvider {
    fun interface FetcherProvider {
        @Throws(ThreemaException::class)
        fun getFetcher(): OnPremConfigFetcher
    }

    @Throws(ThreemaException::class)
    override fun getChatServerNamePrefix(ipv6: Boolean) = ""

    @Throws(ThreemaException::class)
    override fun getChatServerNameSuffix(ipv6: Boolean): String =
        fetch().chat.hostname

    @Throws(ThreemaException::class)
    override fun getChatServerPorts(): IntArray =
        fetch().chat.ports

    override fun getChatServerUseServerGroups() = false

    @Throws(ThreemaException::class)
    override fun getChatServerPublicKey(): ByteArray =
        fetch().chat.publicKey

    @Throws(ThreemaException::class)
    override fun getChatServerPublicKeyAlt(): ByteArray =
        // No alternate public key for OnPrem, as it can easily be switched in OPPF
        fetch().chat.publicKey

    @Throws(ThreemaException::class)
    override fun getDirectoryServerUrl(ipv6: Boolean): String =
        fetch().directory.url

    @Throws(ThreemaException::class)
    override fun getWorkServerUrl(ipv6: Boolean): String =
        fetch().work.url

    @Throws(ThreemaException::class)
    override fun getBlobServerDownloadUrl(useIpV6: Boolean): BlobUrl =
        fetch().blob.downloadUrl

    @Throws(ThreemaException::class)
    override fun getBlobServerUploadUrl(useIpV6: Boolean): String =
        fetch().blob.uploadUrl

    @Throws(ThreemaException::class)
    override fun getBlobServerDoneUrl(useIpV6: Boolean): BlobUrl =
        fetch().blob.doneUrl

    @Throws(ThreemaException::class)
    override fun getBlobMirrorServerDownloadUrl(multiDevicePropertyProvider: MultiDevicePropertyProvider): BlobUrl =
        fetchMediatorConfig().blob.downloadUrl

    @Throws(ThreemaException::class)
    override fun getBlobMirrorServerUploadUrl(multiDevicePropertyProvider: MultiDevicePropertyProvider): String =
        fetchMediatorConfig().blob.uploadUrl

    @Throws(ThreemaException::class)
    override fun getBlobMirrorServerDoneUrl(multiDevicePropertyProvider: MultiDevicePropertyProvider): BlobUrl =
        fetchMediatorConfig().blob.doneUrl

    @Throws(ThreemaException::class)
    override fun getAvatarServerUrl(ipv6: Boolean): String =
        fetch().avatar.url

    @Throws(ThreemaException::class)
    override fun getSafeServerUrl(ipv6: Boolean): String =
        fetch().safe.url

    @Throws(ThreemaException::class)
    override fun getWebServerUrl(): String =
        fetch().web?.url
            ?: throw ThreemaException("Unable to fetch Threema Web server url")

    @Throws(ThreemaException::class)
    override fun getWebOverrideSaltyRtcHost(): String? =
        fetch().web?.overrideSaltyRtcHost

    @Throws(ThreemaException::class)
    override fun getWebOverrideSaltyRtcPort(): Int =
        fetch().web?.overrideSaltyRtcPort ?: 0

    @Throws(ThreemaException::class)
    override fun getThreemaPushPublicKey(): ByteArray? {
        // TODO(ONPREM-164): Allow to configure for OnPrem
        return null
    }

    @Throws(ThreemaException::class)
    override fun getMediatorUrl(): DeviceGroupUrl =
        fetchMediatorConfig().url

    @Throws(ThreemaException::class)
    override fun getAppRatingUrl(): AppRatingUrl {
        throw ThreemaException("App rating is not supported in onprem")
    }

    @Throws(ThreemaException::class)
    private fun fetch(): OnPremConfig = fetcherProvider.getFetcher().fetch()

    @Throws(ThreemaException::class)
    private fun fetchMediatorConfig(): OnPremConfigMediator =
        fetch().mediator
            ?: throw ThreemaException("No mediator config available")

    @Throws(ThreemaException::class)
    override fun getMapStyleUrl(): String? = fetch().maps?.styleUrl
        ?: BuildConfig.MAP_STYLES_URL // TODO(ANDR-3805): Remove the fallback

    @Throws(ThreemaException::class)
    override fun getMapPoiAroundUrl() = fetch().maps?.poiAroundUrl
        ?: MapPoiAroundUrl(BuildConfig.MAP_POI_AROUND_URL) // TODO(ANDR-3805): Remove the fallback

    @Throws(ThreemaException::class)
    override fun getMapPoiNamesUrl() = fetch().maps?.poiNamesUrl
        ?: MapPoiNamesUrl(BuildConfig.MAP_POI_NAMES_URL) // TODO(ANDR-3805): Remove the fallback
}

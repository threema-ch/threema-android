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

package ch.threema.app.services

import ch.threema.app.BuildConfig
import ch.threema.base.ThreemaException
import ch.threema.domain.protocol.ServerAddressProvider
import ch.threema.domain.protocol.connection.d2m.MultiDevicePropertyProvider
import ch.threema.domain.protocol.urls.AppRatingUrl
import ch.threema.domain.protocol.urls.BlobUrl
import ch.threema.domain.protocol.urls.DeviceGroupUrl
import ch.threema.domain.protocol.urls.MapPoiAroundUrl
import ch.threema.domain.protocol.urls.MapPoiNamesUrl

class DefaultServerAddressProvider : ServerAddressProvider {
    override fun getChatServerNamePrefix(ipv6: Boolean): String =
        if (ipv6) {
            BuildConfig.CHAT_SERVER_IPV6_PREFIX
        } else {
            BuildConfig.CHAT_SERVER_PREFIX
        }

    override fun getChatServerNameSuffix(ipv6: Boolean): String = BuildConfig.CHAT_SERVER_SUFFIX

    override fun getChatServerPorts(): IntArray = BuildConfig.CHAT_SERVER_PORTS

    override fun getChatServerUseServerGroups() = BuildConfig.CHAT_SERVER_GROUPS

    override fun getChatServerPublicKey(): ByteArray =
        BuildConfig.SERVER_PUBKEY ?: error("Chat server public key was unexpectedly missing from BuildConfig")

    override fun getChatServerPublicKeyAlt(): ByteArray =
        BuildConfig.SERVER_PUBKEY_ALT ?: error("Chat server alt public key was unexpectedly missing from BuildConfig")

    override fun getDirectoryServerUrl(ipv6: Boolean): String =
        if (ipv6) {
            BuildConfig.DIRECTORY_SERVER_IPV6_URL
        } else {
            BuildConfig.DIRECTORY_SERVER_URL
        }

    override fun getWorkServerUrl(ipv6: Boolean): String? =
        if (ipv6) {
            BuildConfig.WORK_SERVER_IPV6_URL
        } else {
            BuildConfig.WORK_SERVER_URL
        }

    /**
     * @throws ThreemaException if the build config field for the correct base url is missing in current build flavor. This
     * is the case if this implementation is incorrectly used in an on-prem build.
     */
    @Throws(ThreemaException::class)
    private fun getBlobBaseUrlDefaultServer(useIpV6: Boolean): String {
        // TODO(ANDR-3753): Remove suppression and comment
        @Suppress("RedundantNullableReturnType")
        val baseUrlRawValue: String? = if (useIpV6) BuildConfig.BLOB_SERVER_IPV6_URL else BuildConfig.BLOB_SERVER_URL
        if (baseUrlRawValue == null) {
            // Could actually be null, if the build-config field was explicitly set to value "null" in build.gradle.kts
            throw ThreemaException("Missing value for blob server url in current build flavor")
        }
        return baseUrlRawValue
    }

    /**
     * @throws ThreemaException if the build config field for the correct base url is missing in current build flavor. This
     * is the case if this implementation is incorrectly used in an on-prem build.
     */
    @Throws(ThreemaException::class)
    private fun getBlobBaseUrlMirrorServer(multiDevicePropertyProvider: MultiDevicePropertyProvider): String {
        // TODO(ANDR-3753): Remove suppression and comment
        // Could actually be null, if the build-config field was explicitly set to value "null" in build.gradle.kts
        @Suppress("SENSELESS_COMPARISON")
        if (BuildConfig.BLOB_MIRROR_SERVER_URL == null) {
            throw ThreemaException("Missing value for blob server url in current build flavor")
        }
        try {
            return DeviceGroupUrl(BuildConfig.BLOB_MIRROR_SERVER_URL)
                .get(deviceGroupId = multiDevicePropertyProvider.get().keys.dgid)
        } catch (e: IllegalArgumentException) {
            throw ThreemaException(e.message, e)
        }
    }

    @Throws(ThreemaException::class)
    override fun getBlobServerDownloadUrl(useIpV6: Boolean): BlobUrl {
        val blobBaseUrlDefaultServer = getBlobBaseUrlDefaultServer(useIpV6)
        return BlobUrl("$blobBaseUrlDefaultServer/{blobId}")
    }

    override fun getBlobServerUploadUrl(useIpV6: Boolean): String =
        if (useIpV6) {
            BuildConfig.BLOB_SERVER_IPV6_URL_UPLOAD
        } else {
            BuildConfig.BLOB_SERVER_URL_UPLOAD
        }

    @Throws(ThreemaException::class)
    override fun getBlobServerDoneUrl(useIpV6: Boolean): BlobUrl {
        val blobBaseUrlDefaultServer = getBlobBaseUrlDefaultServer(useIpV6)
        return BlobUrl("$blobBaseUrlDefaultServer/{blobId}/done")
    }

    @Throws(ThreemaException::class)
    override fun getBlobMirrorServerDownloadUrl(multiDevicePropertyProvider: MultiDevicePropertyProvider): BlobUrl {
        val blobBaseUrlMirrorServer = getBlobBaseUrlMirrorServer(multiDevicePropertyProvider)
        return BlobUrl("$blobBaseUrlMirrorServer/{blobId}")
    }

    @Throws(ThreemaException::class)
    override fun getBlobMirrorServerUploadUrl(
        multiDevicePropertyProvider: MultiDevicePropertyProvider,
    ): String {
        val blobBaseUrlMirrorServer = getBlobBaseUrlMirrorServer(multiDevicePropertyProvider)
        return "$blobBaseUrlMirrorServer/upload"
    }

    @Throws(ThreemaException::class)
    override fun getBlobMirrorServerDoneUrl(multiDevicePropertyProvider: MultiDevicePropertyProvider): BlobUrl {
        val blobBaseUrlMirrorServer = getBlobBaseUrlMirrorServer(multiDevicePropertyProvider)
        return BlobUrl("$blobBaseUrlMirrorServer/{blobId}/done")
    }

    override fun getAvatarServerUrl(ipv6: Boolean): String = BuildConfig.AVATAR_FETCH_URL

    override fun getSafeServerUrl(ipv6: Boolean): String = BuildConfig.SAFE_SERVER_URL

    override fun getWebServerUrl(): String = BuildConfig.WEB_SERVER_URL

    override fun getWebOverrideSaltyRtcHost(): String? = null

    override fun getWebOverrideSaltyRtcPort(): Int = 0

    override fun getThreemaPushPublicKey(): ByteArray? = BuildConfig.THREEMA_PUSH_PUBLIC_KEY

    override fun getMediatorUrl() = DeviceGroupUrl(BuildConfig.MEDIATOR_SERVER_URL)

    override fun getAppRatingUrl(): AppRatingUrl =
        AppRatingUrl(BuildConfig.APP_RATING_URL)

    override fun getMapStyleUrl(): String = BuildConfig.MAP_STYLES_URL

    override fun getMapPoiAroundUrl() = MapPoiAroundUrl(BuildConfig.MAP_POI_AROUND_URL)

    override fun getMapPoiNamesUrl() = MapPoiNamesUrl(BuildConfig.MAP_POI_NAMES_URL)
}

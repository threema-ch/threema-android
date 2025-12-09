/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2020-2025 Threema GmbH
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

package ch.threema.domain.protocol

import ch.threema.base.SessionScoped
import ch.threema.base.ThreemaException
import ch.threema.domain.protocol.connection.d2m.MultiDevicePropertyProvider
import ch.threema.domain.protocol.urls.AppRatingUrl
import ch.threema.domain.protocol.urls.BlobUrl
import ch.threema.domain.protocol.urls.DeviceGroupUrl
import ch.threema.domain.protocol.urls.MapPoiAroundUrl
import ch.threema.domain.protocol.urls.MapPoiNamesUrl

@SessionScoped
interface ServerAddressProvider {
    @Throws(ThreemaException::class)
    fun getChatServerNamePrefix(ipv6: Boolean): String

    @Throws(ThreemaException::class)
    fun getChatServerNameSuffix(ipv6: Boolean): String

    @Throws(ThreemaException::class)
    fun getChatServerPorts(): IntArray

    @Throws(ThreemaException::class)
    fun getChatServerUseServerGroups(): Boolean

    @Throws(ThreemaException::class)
    fun getChatServerPublicKey(): ByteArray

    @Throws(ThreemaException::class)
    fun getChatServerPublicKeyAlt(): ByteArray

    @Throws(ThreemaException::class)
    fun getDirectoryServerUrl(ipv6: Boolean): String

    @Throws(ThreemaException::class)
    fun getWorkServerUrl(ipv6: Boolean): String?

    @Throws(ThreemaException::class)
    fun getBlobServerDownloadUrl(useIpV6: Boolean): BlobUrl

    @Throws(ThreemaException::class)
    fun getBlobServerUploadUrl(useIpV6: Boolean): String

    @Throws(ThreemaException::class)
    fun getBlobServerDoneUrl(useIpV6: Boolean): BlobUrl

    @Throws(ThreemaException::class, IllegalArgumentException::class)
    fun getBlobMirrorServerDownloadUrl(multiDevicePropertyProvider: MultiDevicePropertyProvider): BlobUrl

    @Throws(ThreemaException::class)
    fun getBlobMirrorServerUploadUrl(multiDevicePropertyProvider: MultiDevicePropertyProvider): String

    @Throws(ThreemaException::class, IllegalArgumentException::class)
    fun getBlobMirrorServerDoneUrl(multiDevicePropertyProvider: MultiDevicePropertyProvider): BlobUrl

    @Throws(ThreemaException::class)
    fun getAvatarServerUrl(ipv6: Boolean): String

    @Throws(ThreemaException::class)
    fun getSafeServerUrl(ipv6: Boolean): String

    @Throws(ThreemaException::class)
    fun getWebServerUrl(): String?

    @Throws(ThreemaException::class)
    fun getWebOverrideSaltyRtcHost(): String?

    @Throws(ThreemaException::class)
    fun getWebOverrideSaltyRtcPort(): Int

    @Throws(ThreemaException::class)
    fun getThreemaPushPublicKey(): ByteArray?

    @Throws(ThreemaException::class)
    fun getMediatorUrl(): DeviceGroupUrl

    @Throws(ThreemaException::class)
    fun getAppRatingUrl(): AppRatingUrl

    @Throws(ThreemaException::class)
    fun getMapStyleUrl(): String?

    @Throws(ThreemaException::class)
    fun getMapPoiNamesUrl(): MapPoiNamesUrl?

    @Throws(ThreemaException::class)
    fun getMapPoiAroundUrl(): MapPoiAroundUrl?
}

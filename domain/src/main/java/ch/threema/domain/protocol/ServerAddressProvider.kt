package ch.threema.domain.protocol

import ch.threema.base.SessionScoped
import ch.threema.base.ThreemaException
import ch.threema.domain.protocol.connection.d2m.MultiDevicePropertyProvider
import ch.threema.domain.protocol.urls.AppRatingUrl
import ch.threema.domain.protocol.urls.BlobUrl
import ch.threema.domain.protocol.urls.MapPoiAroundUrl
import ch.threema.domain.protocol.urls.MapPoiNamesUrl
import ch.threema.domain.protocol.urls.MediatorUrl

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
    fun getMediatorUrl(): MediatorUrl

    @Throws(ThreemaException::class)
    fun getAppRatingUrl(): AppRatingUrl

    @Throws(ThreemaException::class)
    fun getMapStyleUrl(): String?

    @Throws(ThreemaException::class)
    fun getMapPoiNamesUrl(): MapPoiNamesUrl?

    @Throws(ThreemaException::class)
    fun getMapPoiAroundUrl(): MapPoiAroundUrl?
}

/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2023-2025 Threema GmbH
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

package ch.threema.domain.protocol.connection

import ch.threema.base.crypto.NaCl
import ch.threema.domain.protocol.ServerAddressProvider
import ch.threema.domain.protocol.connection.csp.DeviceCookieManager
import ch.threema.domain.protocol.connection.csp.socket.ChatServerAddressProvider
import ch.threema.domain.protocol.connection.d2m.MultiDevicePropertyProvider
import ch.threema.domain.protocol.connection.data.CspMessage
import ch.threema.domain.protocol.connection.data.OutboundMessage
import ch.threema.domain.protocol.csp.coders.MessageBox
import ch.threema.domain.protocol.urls.AppRatingUrl
import ch.threema.domain.protocol.urls.BlobUrl
import ch.threema.domain.protocol.urls.DeviceGroupUrl
import ch.threema.domain.protocol.urls.MapPoiAroundUrl
import ch.threema.domain.protocol.urls.MapPoiNamesUrl
import ch.threema.domain.stores.IdentityStore
import ch.threema.domain.types.Identity
import ch.threema.testhelpers.MUST_NOT_BE_CALLED
import java.io.InputStream
import java.io.OutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketAddress

internal class TestIdentityStore : IdentityStore {
    companion object {
        const val CLIENT_IDENTITY = "ABCD1234"
        val ckPublic = ByteArray(NaCl.PUBLIC_KEY_BYTES)
        val ckSecret = ByteArray(NaCl.SECRET_KEY_BYTES)

        private val cache: MutableMap<ByteArray, NaCl> = mutableMapOf()

        fun getFromCache(publicKey: ByteArray): NaCl = cache
            .computeIfAbsent(publicKey) { NaCl(ckSecret, it) }
    }

    override fun encryptData(
        plaintext: ByteArray,
        nonce: ByteArray,
        receiverPublicKey: ByteArray,
    ): ByteArray =
        getFromCache(receiverPublicKey)
            .encrypt(
                data = plaintext,
                nonce = nonce,
            )

    override fun decryptData(
        ciphertext: ByteArray,
        nonce: ByteArray,
        senderPublicKey: ByteArray,
    ): ByteArray =
        getFromCache(senderPublicKey)
            .decrypt(
                data = ciphertext,
                nonce = nonce,
            )

    override fun calcSharedSecret(publicKey: ByteArray): ByteArray {
        return getFromCache(publicKey).sharedSecret
    }

    override fun getIdentity(): Identity {
        return CLIENT_IDENTITY
    }

    override fun getServerGroup(): String {
        MUST_NOT_BE_CALLED()
    }

    override fun getPublicKey(): ByteArray = ckPublic

    override fun getPrivateKey(): ByteArray = ckSecret

    override fun getPublicNickname(): String = "Test"

    override fun storeIdentity(
        identity: Identity,
        serverGroup: String,
        privateKey: ByteArray,
    ) {
        MUST_NOT_BE_CALLED()
    }

    override fun setPublicNickname(publicNickname: String) {
        MUST_NOT_BE_CALLED()
    }

    override fun clear() {
        MUST_NOT_BE_CALLED()
    }
}

internal class TestServerAddressProvider(
    private val skPublic: ByteArray,
    private val skPublicAlt: ByteArray,
    private val mediatorUrl: String? = null,
) : ServerAddressProvider {
    var keyFetchCount = 0
    var altKeyFetchCount = 0

    override fun getChatServerNamePrefix(ipv6: Boolean): String = "prefix"

    override fun getChatServerNameSuffix(ipv6: Boolean): String = "suffix"

    override fun getChatServerPorts(): IntArray = intArrayOf(1234)

    override fun getChatServerUseServerGroups(): Boolean = false

    override fun getChatServerPublicKey(): ByteArray {
        keyFetchCount++
        return skPublic
    }

    override fun getChatServerPublicKeyAlt(): ByteArray {
        altKeyFetchCount++
        return skPublicAlt
    }

    override fun getMediatorUrl(): DeviceGroupUrl {
        return DeviceGroupUrl(mediatorUrl ?: MUST_NOT_BE_CALLED())
    }

    override fun getAppRatingUrl(): AppRatingUrl {
        MUST_NOT_BE_CALLED()
    }

    // The following methods should not be used by the connection
    override fun getDirectoryServerUrl(ipv6: Boolean): String {
        MUST_NOT_BE_CALLED()
    }

    override fun getWorkServerUrl(ipv6: Boolean): String {
        MUST_NOT_BE_CALLED()
    }

    override fun getBlobServerDownloadUrl(useIpV6: Boolean): BlobUrl {
        MUST_NOT_BE_CALLED()
    }

    override fun getBlobServerUploadUrl(useIpV6: Boolean): String {
        MUST_NOT_BE_CALLED()
    }

    override fun getBlobServerDoneUrl(useIpV6: Boolean): BlobUrl {
        MUST_NOT_BE_CALLED()
    }

    override fun getBlobMirrorServerDownloadUrl(multiDevicePropertyProvider: MultiDevicePropertyProvider): BlobUrl {
        MUST_NOT_BE_CALLED()
    }

    override fun getBlobMirrorServerUploadUrl(multiDevicePropertyProvider: MultiDevicePropertyProvider): String {
        MUST_NOT_BE_CALLED()
    }

    override fun getBlobMirrorServerDoneUrl(multiDevicePropertyProvider: MultiDevicePropertyProvider): BlobUrl {
        MUST_NOT_BE_CALLED()
    }

    override fun getAvatarServerUrl(ipv6: Boolean): String {
        MUST_NOT_BE_CALLED()
    }

    override fun getSafeServerUrl(ipv6: Boolean): String {
        MUST_NOT_BE_CALLED()
    }

    override fun getWebServerUrl(): String {
        MUST_NOT_BE_CALLED()
    }

    override fun getWebOverrideSaltyRtcHost(): String {
        MUST_NOT_BE_CALLED()
    }

    override fun getWebOverrideSaltyRtcPort(): Int {
        MUST_NOT_BE_CALLED()
    }

    override fun getThreemaPushPublicKey(): ByteArray? {
        MUST_NOT_BE_CALLED()
    }

    override fun getMapStyleUrl(): String? {
        MUST_NOT_BE_CALLED()
    }

    override fun getMapPoiNamesUrl(): MapPoiNamesUrl {
        MUST_NOT_BE_CALLED()
    }

    override fun getMapPoiAroundUrl(): MapPoiAroundUrl {
        MUST_NOT_BE_CALLED()
    }
}

internal class TestSocket : Socket() {
    private val outputPipe = PipedInputStream(UShort.MAX_VALUE.toInt() * 2)
    private val outputStream = PipedOutputStream(outputPipe)

    private val inputPipe = PipedOutputStream()
    private val inputStream = PipedInputStream(inputPipe, UShort.MAX_VALUE.toInt() * 2)

    var closed = false

    fun read(length: Int): ByteArray {
        return outputPipe.readNBytes(length)
    }

    fun write(bytes: ByteArray) {
        inputPipe.write(bytes)
        inputPipe.flush()
    }

    override fun connect(endpoint: SocketAddress?, timeout: Int) {}
    override fun connect(endpoint: SocketAddress?) {}

    override fun close() {
        outputPipe.close()
        outputStream.close()
        inputPipe.close()
        inputStream.close()
        closed = true
    }

    override fun getInputStream(): InputStream = inputStream

    override fun getOutputStream(): OutputStream = outputStream

    override fun setSoTimeout(timeout: Int) {}
}

internal class TestChatServerAddressProvider : ChatServerAddressProvider {
    private var updated = false
    override fun advance() {
        // No-op
    }

    override fun get(): InetSocketAddress? {
        return if (updated) {
            InetSocketAddress.createUnresolved("dummy", 4711)
        } else {
            null
        }
    }

    override fun update() {
        updated = true
    }
}

internal class TestNoopDeviceCookieManager : DeviceCookieManager {
    override fun obtainDeviceCookie() = ByteArray(16)
    override fun changeIndicationReceived() {
        MUST_NOT_BE_CALLED()
    }

    override fun deleteDeviceCookie() {
        MUST_NOT_BE_CALLED()
    }
}

fun getFromOutboundMessage(message: OutboundMessage): MessageBox? {
    return try {
        val cspMessage = message as CspMessage
        MessageBox.parseBinary(cspMessage.toCspContainer().data)
    } catch (e: Exception) {
        throw AssertionError("Could not parse csp message: " + e.message)
    }
}

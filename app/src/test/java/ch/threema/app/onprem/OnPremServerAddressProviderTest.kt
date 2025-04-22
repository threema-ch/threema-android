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

import ch.threema.base.ThreemaException
import ch.threema.domain.onprem.OnPremConfig
import ch.threema.domain.onprem.OnPremConfigAvatar
import ch.threema.domain.onprem.OnPremConfigBlob
import ch.threema.domain.onprem.OnPremConfigChat
import ch.threema.domain.onprem.OnPremConfigDirectory
import ch.threema.domain.onprem.OnPremConfigFetcher
import ch.threema.domain.onprem.OnPremConfigMediator
import ch.threema.domain.onprem.OnPremConfigSafe
import ch.threema.domain.onprem.OnPremConfigWeb
import ch.threema.domain.onprem.OnPremConfigWork
import ch.threema.domain.protocol.urls.BlobUrl
import ch.threema.domain.protocol.urls.DeviceGroupUrl
import io.mockk.every
import io.mockk.mockk
import java.time.Instant
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull

class OnPremServerAddressProviderTest {

    private lateinit var fetcherProvider: OnPremServerAddressProvider.FetcherProvider
    private lateinit var onPremConfigFetcher: OnPremConfigFetcher
    private lateinit var serverAddressProvider: OnPremServerAddressProvider

    private val onPremConfig = OnPremConfig(
        validUntil = Instant.ofEpochMilli(999L),
        license = mockk(),
        domains = null,
        chatConfig = OnPremConfigChat(
            hostname = "chat.threemaonprem.initrode.com",
            ports = intArrayOf(5222, 443),
            publicKey = byteArrayOf(0x1, 0x2, 0x3),
        ),
        directoryConfig = OnPremConfigDirectory(
            url = "https://dir.threemaonprem.initrode.com/directory",
        ),
        blobConfig = OnPremConfigBlob(
            uploadUrl = "https://blob.threemaonprem.initrode.com/blob/upload",
            downloadUrl = BlobUrl("https://blob-{blobIdPrefix}.threemaonprem.initrode.com/blob/{blobId}"),
            doneUrl = BlobUrl("https://blob-{blobIdPrefix}.threemaonprem.initrode.com/blob/{blobId}/done"),
        ),
        workConfig = OnPremConfigWork(
            url = "https://work.threemaonprem.initrode.com/",
        ),
        avatarConfig = OnPremConfigAvatar(
            url = "https://avatar.threemaonprem.initrode.com/",
        ),
        safeConfig = OnPremConfigSafe(
            url = "https://safe.threemaonprem.initrode.com/",
        ),
        webConfig = OnPremConfigWeb(
            url = "https://web.threemaonprem.initrode.com/",
            overrideSaltyRtcHost = null,
            overrideSaltyRtcPort = 0,
        ),
        mediatorConfig = OnPremConfigMediator(
            url = DeviceGroupUrl("https://mediator.threemaonprem.initrode.com/"),
            blob = OnPremConfigBlob(
                uploadUrl = "https://mediator.threemaonprem.initrode.com/blob/upload",
                downloadUrl = BlobUrl("https://mediator.threemaonprem.initrode.com/blob/{blobId}"),
                doneUrl = BlobUrl("https://mediator.threemaonprem.initrode.com/blob/{blobId}/done"),
            ),
        ),
    )

    @BeforeTest
    fun setUp() {
        onPremConfigFetcher = mockk {
            every { fetch() } returns onPremConfig
        }
        fetcherProvider = OnPremServerAddressProvider.FetcherProvider { onPremConfigFetcher }
        serverAddressProvider = OnPremServerAddressProvider(fetcherProvider)
    }

    @Test
    fun `test chat server name prefix`() {
        assertEquals("", serverAddressProvider.getChatServerNamePrefix(ipv6 = false))
    }

    @Test
    fun `test chat server name suffix`() {
        assertEquals("chat.threemaonprem.initrode.com", serverAddressProvider.getChatServerNameSuffix(false))
    }

    @Test
    fun `test chat server ports`() {
        assertContentEquals(intArrayOf(5222, 443), serverAddressProvider.getChatServerPorts())
    }

    @Test
    fun `test chat server use server groups`() {
        assertFalse(serverAddressProvider.getChatServerUseServerGroups())
    }

    @Test
    fun `test chat server public key`() {
        assertContentEquals(byteArrayOf(0x1, 0x2, 0x3), serverAddressProvider.getChatServerPublicKey())
        assertContentEquals(byteArrayOf(0x1, 0x2, 0x3), serverAddressProvider.getChatServerPublicKeyAlt())
    }

    @Test
    fun `test directory server url`() {
        assertEquals(
            "https://dir.threemaonprem.initrode.com/directory",
            serverAddressProvider.getDirectoryServerUrl(ipv6 = false),
        )
    }

    @Test
    fun `test work server url`() {
        assertEquals(
            "https://work.threemaonprem.initrode.com/",
            serverAddressProvider.getWorkServerUrl(ipv6 = false),
        )
    }

    @Test
    fun `test blob server download url`() {
        assertEquals(
            BlobUrl("https://blob-{blobIdPrefix}.threemaonprem.initrode.com/blob/{blobId}"),
            serverAddressProvider.getBlobServerDownloadUrl(useIpV6 = false),
        )
    }

    @Test
    fun `test blob server upload url`() {
        assertEquals(
            "https://blob.threemaonprem.initrode.com/blob/upload",
            serverAddressProvider.getBlobServerUploadUrl(useIpV6 = false),
        )
    }

    @Test
    fun `test blob server done url`() {
        assertEquals(
            BlobUrl("https://blob-{blobIdPrefix}.threemaonprem.initrode.com/blob/{blobId}/done"),
            serverAddressProvider.getBlobServerDoneUrl(useIpV6 = false),
        )
    }

    @Test
    fun `test blob mirror server download url`() {
        assertEquals(
            BlobUrl("https://mediator.threemaonprem.initrode.com/blob/{blobId}"),
            serverAddressProvider.getBlobMirrorServerDownloadUrl(multiDevicePropertyProvider = mockk()),
        )
    }

    @Test
    fun `test blob mirror server upload url`() {
        assertEquals(
            "https://mediator.threemaonprem.initrode.com/blob/upload",
            serverAddressProvider.getBlobMirrorServerUploadUrl(multiDevicePropertyProvider = mockk()),
        )
    }

    @Test
    fun `test blob mirror server done url`() {
        assertEquals(
            BlobUrl("https://mediator.threemaonprem.initrode.com/blob/{blobId}/done"),
            serverAddressProvider.getBlobMirrorServerDoneUrl(multiDevicePropertyProvider = mockk()),
        )
    }

    @Test
    fun `test avatar server url`() {
        assertEquals(
            "https://avatar.threemaonprem.initrode.com/",
            serverAddressProvider.getAvatarServerUrl(ipv6 = false),
        )
    }

    @Test
    fun `test safe server url`() {
        assertEquals(
            "https://safe.threemaonprem.initrode.com/",
            serverAddressProvider.getSafeServerUrl(ipv6 = false),
        )
    }

    @Test
    fun `test web server url`() {
        assertEquals(
            "https://web.threemaonprem.initrode.com/",
            serverAddressProvider.getWebServerUrl(),
        )
    }

    @Test
    fun `test web override salty rtc host`() {
        assertNull(serverAddressProvider.getWebOverrideSaltyRtcHost())
    }

    @Test
    fun `test web override salty rtc port`() {
        assertEquals(0, serverAddressProvider.getWebOverrideSaltyRtcPort())
    }

    @Test
    fun `test threema push public key`() {
        assertNull(serverAddressProvider.getThreemaPushPublicKey())
    }

    @Test
    fun `test mediator url`() {
        assertEquals(
            DeviceGroupUrl("https://mediator.threemaonprem.initrode.com/"),
            serverAddressProvider.getMediatorUrl(),
        )
    }

    @Test
    fun `test mediator url when no mediator config available`() {
        onPremConfigFetcher = mockk {
            every { fetch() } returns onPremConfig.copy(
                mediatorConfig = null,
            )
        }

        assertFailsWith<ThreemaException> {
            serverAddressProvider.getMediatorUrl()
        }
    }

    @Test
    fun `test no app rating url available`() {
        assertFailsWith<ThreemaException> {
            serverAddressProvider.getAppRatingUrl()
        }
    }
}

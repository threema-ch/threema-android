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

package ch.threema.domain.onprem

import ch.threema.base.utils.Base64
import ch.threema.base.utils.plus
import ch.threema.base.utils.withoutLastLine
import ch.threema.domain.protocol.urls.BlobUrl
import ch.threema.domain.protocol.urls.DeviceGroupUrl
import ch.threema.domain.protocol.urls.MapPoiAroundUrl
import ch.threema.domain.protocol.urls.MapPoiNamesUrl
import ch.threema.testutils.TestTimeProvider
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.hours
import org.json.JSONObject

class OnPremConfigParserTest {

    private val testTimeProvider = TestTimeProvider()
    private lateinit var config: OnPremConfig

    @BeforeTest
    fun setUp() {
        config = createParser().parse(JSONObject(OnPremConfigTestData.goodOppf.withoutLastLine()))
    }

    private fun createParser(): OnPremConfigParser =
        OnPremConfigParser(
            timeProvider = testTimeProvider,
        )

    @Test
    fun testInvalidConfig() {
        val parser = createParser()

        assertFailsWith<OnPremConfigParseException> {
            parser.parse(JSONObject("{}"))
        }
    }

    @Test
    fun testRefresh() {
        assertEquals(testTimeProvider.get() + 24.hours, config.validUntil)
    }

    @Test
    fun testChatConfig() {
        val chatConfig = config.chat
        assertEquals("chat.threemaonprem.initrode.com", chatConfig.hostname)
        assertContentEquals(intArrayOf(5222, 443), chatConfig.ports)
        assertContentEquals(Base64.decode("r9utIHN9ngo21q9OlZcotsQu1f2HwAW2Wi+u6Psp4Wc="), chatConfig.publicKey)
    }

    @Test
    fun testDirectoryConfig() {
        val directoryConfig = config.directory
        assertEquals("https://dir.threemaonprem.initrode.com/directory", directoryConfig.url)
    }

    @Test
    fun testBlobConfig() {
        val blobConfig = config.blob
        assertEquals("https://blob.threemaonprem.initrode.com/blob/upload", blobConfig.uploadUrl)
        assertEquals(BlobUrl("https://blob-{blobIdPrefix}.threemaonprem.initrode.com/blob/{blobId}"), blobConfig.downloadUrl)
        assertEquals(BlobUrl("https://blob-{blobIdPrefix}.threemaonprem.initrode.com/blob/{blobId}/done"), blobConfig.doneUrl)
    }

    @Test
    fun testWorkConfig() {
        val workConfig = config.work
        assertEquals("https://work.threemaonprem.initrode.com/", workConfig.url)
    }

    @Test
    fun testAvatarConfig() {
        val avatarConfig = config.avatar
        assertEquals("https://avatar.threemaonprem.initrode.com/", avatarConfig.url)
    }

    @Test
    fun testSafeConfig() {
        val safeConfig = config.safe
        assertEquals("https://safe.threemaonprem.initrode.com/", safeConfig.url)
    }

    @Test
    fun testWebConfig() {
        val webConfig = config.web!!
        assertEquals("https://web.threemaonprem.initrode.com/", webConfig.url)
    }

    @Test
    fun testMediatorConfig() {
        val mediatorConfig = config.mediator!!
        assertEquals(DeviceGroupUrl("https://mediator.threemaonprem.initrode.com/"), mediatorConfig.url)
        assertEquals("https://mediator.threemaonprem.initrode.com/blob/upload", mediatorConfig.blob.uploadUrl)
        assertEquals(BlobUrl("https://mediator.threemaonprem.initrode.com/blob/{blobId}"), mediatorConfig.blob.downloadUrl)
        assertEquals(BlobUrl("https://mediator.threemaonprem.initrode.com/blob/{blobId}/done"), mediatorConfig.blob.doneUrl)
    }

    @Test
    fun testDomainRulesConfig() {
        assertEquals(
            OnPremConfigDomains(
                rules = listOf(
                    OnPremConfigDomainRule(
                        fqdn = "threemaonprem.initrode.com",
                        matchMode = OnPremConfigDomainRuleMatchMode.INCLUDE_SUBDOMAINS,
                        spkis = listOf(
                            OnPremConfigDomainRuleSpki(
                                value = "DTJU4+0HObYPrx9lF4Kz8hhjcJL3WBL4k829L++UlSk=",
                                algorithm = OnPremConfigDomainRuleSpkiAlgorithm.SHA256,
                            ),
                            OnPremConfigDomainRuleSpki(
                                value = "C19RmQgZXzwovKRRJ2st7bsokiRchKcYjBo3m63fvn8=",
                                algorithm = OnPremConfigDomainRuleSpkiAlgorithm.SHA256,
                            ),
                        ),
                    ),
                    OnPremConfigDomainRule(
                        fqdn = "another-host.initrode.com",
                        matchMode = OnPremConfigDomainRuleMatchMode.EXACT,
                        spkis = listOf(
                            OnPremConfigDomainRuleSpki(
                                value = "XIglSWPJ6aJ7LeIz6KsOrr0fNgNZ0PzGgDCDEZq5/U4=",
                                algorithm = OnPremConfigDomainRuleSpkiAlgorithm.SHA256,
                            ),
                        ),
                    ),
                ),
            ),
            config.domains,
        )
    }

    @Test
    fun testMapsConfig() {
        assertEquals(
            OnPremConfigMaps(
                styleUrl = "https://map.initrode.com/styles/threema/style.json",
                poiNamesUrl = MapPoiNamesUrl("https://poi.initrode.com/names/{latitude}/{longitude}/{query}/"),
                poiAroundUrl = MapPoiAroundUrl("https://poi.initrode.com/around/{latitude}/{longitude}/{radius}/"),
            ),
            config.maps,
        )
    }

    @Test
    fun `test minimal oppf`() {
        val config = createParser().parse(JSONObject(OnPremConfigTestData.minimalOppf.withoutLastLine()))

        assertEquals(testTimeProvider.get() + 24.hours, config.validUntil)
        assertNull(config.maps)
        assertNull(config.mediator)
        assertNull(config.domains)
    }
}

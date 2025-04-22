/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2017-2025 Threema GmbH
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

package ch.threema.domain.protocol.api

import ch.threema.domain.protocol.api.work.WorkDirectoryCategory
import ch.threema.domain.protocol.api.work.WorkDirectoryFilter
import ch.threema.domain.stores.IdentityStoreInterface
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import java.util.Date
import kotlin.math.absoluteValue
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class APIConnectorTest {
    @RelaxedMockK
    private lateinit var httpRequester: HttpRequester

    private lateinit var connector: APIConnector

    @BeforeTest
    fun setUp() {
        connector = APIConnector(
            /* ipv6 = */
            false,
            /* serverAddressProvider = */
            mockk {
                every { getDirectoryServerUrl(any()) } returns "https://server.url/"
                every { getWorkServerUrl(any()) } returns "https://api-work.threema.ch/"
            },
            /* isWork = */
            false,
            httpRequester,
            /* version = */
            mockk(),
        )
    }

    @Test
    fun testFetchWorkData_Support() {
        mockPostRequest(
            url = "https://api-work.threema.ch/fetch2",
            expectedPayload = payload,
            response = """{"support":"the-support-url"}""",
        )

        val result = connector.fetchWorkData(username, password, arrayOf(identity1, identity2))
        assertNotNull(result)
        assertEquals("the-support-url", result.supportUrl)
        assertNull(result.logoDark)
        assertNull(result.logoLight)
        assertEquals(0, result.workContacts.size.toLong())
        assertNotNull(result.mdm)
        assertFalse(result.mdm.override)
        assertNotNull(result.mdm.parameters)
        assertEquals(0, result.mdm.parameters.size.toLong())
    }

    @Test
    fun testFetchWorkData_Logo_Dark() {
        mockPostRequest(
            url = "https://api-work.threema.ch/fetch2",
            expectedPayload = payload,
            response = """{"logo":{"dark": "the-dark-logo"}}""",
        )

        val result = connector.fetchWorkData(username, password, arrayOf(identity1, identity2))

        assertNotNull(result)
        assertNull(result.supportUrl)
        assertEquals("the-dark-logo", result.logoDark)
        assertNull(result.logoLight)
        assertEquals(0, result.workContacts.size.toLong())
        assertNotNull(result.mdm)
        assertFalse(result.mdm.override)
        assertNotNull(result.mdm.parameters)
        assertEquals(0, result.mdm.parameters.size.toLong())
    }

    @Test
    fun testFetchWorkData_Logo_Light() {
        mockPostRequest(
            url = "https://api-work.threema.ch/fetch2",
            expectedPayload = payload,
            response = """{"logo":{"light": "the-light-logo"}}""",
        )

        val result = connector.fetchWorkData(username, password, arrayOf(identity1, identity2))

        assertNotNull(result)
        assertNull(result.supportUrl)
        assertNull(result.logoDark)
        assertEquals("the-light-logo", result.logoLight)
        assertEquals(0, result.workContacts.size.toLong())
        assertNotNull(result.mdm)
        assertFalse(result.mdm.override)
        assertNotNull(result.mdm.parameters)
        assertEquals(0, result.mdm.parameters.size.toLong())
    }

    @Test
    fun testFetchWorkData_Contacts() {
        mockPostRequest(
            url = "https://api-work.threema.ch/fetch2",
            expectedPayload = payload,
            response = """{"contacts":[{"id":"id1","pk":"AQ=="},{"id":"id2","pk":"Aq==","first":"id2-firstname"},
                {"id":"id3","pk":"Aw==","last":"id3-lastname"},{"id":"id4","pk":"BA==","first": "id4-firstname", "last":"id4-lastname"}]}""",
        )

        val result = connector.fetchWorkData(username, password, arrayOf(identity1, identity2))
        assertNotNull(result)
        assertNull(result.supportUrl)
        assertNull(result.logoDark)
        assertNull(result.logoLight)
        assertEquals(4, result.workContacts.size.toLong())
        assertNotNull(result.mdm)
        assertFalse(result.mdm.override)
        assertNotNull(result.mdm.parameters)
        assertEquals(0, result.mdm.parameters.size.toLong())

        // Verify contacts
        assertEquals("id1", result.workContacts[0].threemaId)
        assertContentEquals(byteArrayOf(0x01), result.workContacts[0].publicKey)
        assertNull(result.workContacts[0].firstName)
        assertNull(result.workContacts[0].lastName)

        assertEquals("id2", result.workContacts[1].threemaId)
        assertContentEquals(byteArrayOf(0x02), result.workContacts[1].publicKey)
        assertEquals("id2-firstname", result.workContacts[1].firstName)
        assertNull(result.workContacts[1].lastName)

        assertEquals("id3", result.workContacts[2].threemaId)
        assertContentEquals(byteArrayOf(0x03), result.workContacts[2].publicKey)
        assertNull(result.workContacts[2].firstName)
        assertEquals("id3-lastname", result.workContacts[2].lastName)

        assertEquals("id4", result.workContacts[3].threemaId)
        assertContentEquals(byteArrayOf(0x04), result.workContacts[3].publicKey)
        assertEquals("id4-firstname", result.workContacts[3].firstName)
        assertEquals("id4-lastname", result.workContacts[3].lastName)
    }

    @Test
    fun testFetchWorkData_MDM() {
        mockPostRequest(
            url = "https://api-work.threema.ch/fetch2",
            expectedPayload = payload,
            response = """{"mdm":{"override": true,"params":{"param-string": "string-param","param-bool": true,"param-int": 123}}}""",
        )

        val result = connector.fetchWorkData(username, password, arrayOf(identity1, identity2))

        assertNotNull(result)
        assertNull(result.supportUrl)
        assertNull(result.logoDark)
        assertNull(result.logoLight)
        assertEquals(0, result.workContacts.size.toLong())
        assertNotNull(result.mdm)
        assertTrue(result.mdm.override)
        assertNotNull(result.mdm.parameters)
        assertEquals(3, result.mdm.parameters.size.toLong())

        assertTrue(result.mdm.parameters.containsKey("param-string"))
        assertEquals("string-param", result.mdm.parameters["param-string"])
        assertTrue(result.mdm.parameters.containsKey("param-bool"))
        assertEquals(true, result.mdm.parameters["param-bool"])
        assertTrue(result.mdm.parameters.containsKey("param-int"))
        assertEquals(123, result.mdm.parameters["param-int"])
    }

    @Test
    fun testFetchWorkContacts_InvalidJSON() {
        mockPostRequest(
            url = "https://api-work.threema.ch/identities",
            expectedPayload = payload,
            response = "i-am-not-a-json",
        )

        assertFailsWith<JSONException> {
            connector.fetchWorkContacts(username, password, arrayOf(identity1, identity2))
        }
    }

    @Test
    fun testFetchWorkContacts() {
        mockPostRequest(
            url = "https://api-work.threema.ch/identities",
            expectedPayload = payload,
            response = """{"contacts":[{"id":"id1","pk":"AQ=="},{"id":"id2","pk":"Aq==","first":"id2-firstname"},
                {"id":"id3","pk":"Aw==","last":"id3-lastname"},{"id":"id4","pk":"BA==","first": "id4-firstname", "last":"id4-lastname"}]}""",
        )

        val contacts = connector.fetchWorkContacts(username, password, arrayOf(identity1, identity2))
        assertNotNull(contacts)
        assertEquals(4, contacts.size.toLong())

        // Verify contacts
        assertEquals("id1", contacts[0].threemaId)
        assertContentEquals(byteArrayOf(0x01), contacts[0].publicKey)
        assertNull(contacts[0].firstName)
        assertNull(contacts[0].lastName)

        assertEquals("id2", contacts[1].threemaId)
        assertContentEquals(byteArrayOf(0x02), contacts[1].publicKey)
        assertEquals("id2-firstname", contacts[1].firstName)
        assertNull(contacts[1].lastName)

        assertEquals("id3", contacts[2].threemaId)
        assertContentEquals(byteArrayOf(0x03), contacts[2].publicKey)
        assertNull(contacts[2].firstName)
        assertEquals("id3-lastname", contacts[2].lastName)

        assertEquals("id4", contacts[3].threemaId)
        assertContentEquals(byteArrayOf(0x04), contacts[3].publicKey)
        assertEquals("id4-firstname", contacts[3].firstName)
        assertEquals("id4-lastname", contacts[3].lastName)
    }

    @Test
    fun testFetchIdentity() {
        mockGetRequest(
            url = "https://server.url/identity/ERIC4911",
            response = """{"identity": "ERIC4911","publicKey": "aGVsbG8=","featureLevel": 3,"featureMask": 15,"state": 1,"type": 2}""",
        )

        val result = connector.fetchIdentity("ERIC4911")

        assertNotNull(result)
        assertEquals("ERIC4911", result.identity)
        assertEquals(15, result.featureMask)
        assertEquals(1, result.state.toLong())
        assertEquals(2, result.type.toLong())
    }

    @Test
    fun testObtainTurnServers() {
        val identityStoreMock = mockk<IdentityStoreInterface> {
            every { identity } returns "FOOBAR12"
            every { calcSharedSecret(any()) } returns ByteArray(32)
        }
        every {
            httpRequester.post("https://server.url/identity/turn_cred", any(), any())
        } answers {
            HttpRequesterResult.Success("""{"token": "/wAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA==","tokenRespKeyPub": "dummy"}""")
        } andThenAnswer {
            HttpRequesterResult.Success(
                """{"success": true,"turnUrls": ["turn:foo", "turn:bar"],"turnUrlsDualStack": ["turn:ds-foo", "turn:ds-bar"],
                "turnUsername": "s00perturnuser","turnPassword": "t0psecret","expiration": 86400}""",
            )
        }

        val result = connector.obtainTurnServers(identityStoreMock, "voip")
        assertNotNull(result)
        assertContentEquals(arrayOf("turn:foo", "turn:bar"), result.turnUrls)
        assertContentEquals(arrayOf("turn:ds-foo", "turn:ds-bar"), result.turnUrlsDualStack)
        assertEquals("s00perturnuser", result.turnUsername)
        assertEquals("t0psecret", result.turnPassword)

        val expectedExpirationDate = Date(Date().time + 86400 * 1000)
        assertTrue((expectedExpirationDate.time - result.expirationDate.time).toDouble().absoluteValue < 10000)
    }

    @Test
    fun testFetchWorkData_Organization() {
        mockPostRequest(
            url = "https://api-work.threema.ch/fetch2",
            expectedPayload = payload,
            response = """{"org":{"name": "monkeybusiness"}}""",
        )

        val result = connector.fetchWorkData(username, password, arrayOf(identity1, identity2))

        assertNotNull(result)
        assertNotNull(result.organization)
        assertEquals("monkeybusiness", result.organization.name)
    }

    @Test
    fun testFetchWorkData_NoOrganization() {
        mockPostRequest(
            url = "https://api-work.threema.ch/fetch2",
            expectedPayload = payload,
            response = "{}",
        )

        val result = connector.fetchWorkData(username, password, arrayOf(identity1, identity2))

        assertNotNull(result)
        assertNotNull(result.organization)
        assertNull(result.organization.name)
    }

    @Test
    fun testFetchWorkData_Directory() {
        mockPostRequest(
            url = "https://api-work.threema.ch/fetch2",
            expectedPayload = payload,
            response = """{directory:{enabled: true,cat: {"c1": "Category 1","c2": "Category 2","c3": "Category 3"}}}""",
        )

        val result = connector.fetchWorkData(username, password, arrayOf(identity1, identity2))

        assertNotNull(result)
        assertNotNull(result.organization)
        assertNull(result.organization.name)
        assertTrue(result.directory.enabled)
        assertNotNull(result.directory.categories)
        assertEquals(3, result.directory.categories.size.toLong())

        var c1 = false
        var c2 = false
        var c3 = false

        for (c in result.directory.categories) {
            when (c.id) {
                "c1" -> {
                    assertFalse(c1, "c1 already found")
                    c1 = true
                    assertEquals("Category 1", c.name)
                }

                "c2" -> {
                    assertFalse(c2, "c2 already found")
                    c2 = true
                    assertEquals("Category 2", c.name)
                }

                "c3" -> {
                    assertFalse(c3, "c3 already found")
                    c3 = true
                    assertEquals("Category 3", c.name)
                }

                else -> fail("Invalid category ${c.id}")
            }
        }
    }

    @Test
    fun testFetchWorkData_Directory2() {
        val identityStoreMock = mockk<IdentityStoreInterface> {
            every { identity } returns "IDENTITY"
        }
        mockPostRequest(
            url = "https://api-work.threema.ch/directory",
            expectedPayload = jsonObject {
                put("username", username)
                put("password", password)
                put("identity", "IDENTITY")
                put("query", "Query String")
                put("categories", (JSONArray()).put("c100"))
                put(
                    "sort",
                    jsonObject {
                        put("asc", true)
                        put("by", "firstName")
                    },
                )
                put("page", 1)
            },
            response = """{
   "contacts": [
      {
         "id": "ECHOECHO",
         "pk": "base64",
         "first": "Hans",
         "last": "Nötig",
         "csi": "CSI_NR",
         "org": { "name": "Name der Firma/Organisation" },
         "cat": [
            "catId1",
            "catId2"
         ]
      }
   ],
   "paging": {
      "size": 10,
      "total": 8923,
      "next": 2,
      "prev": 0
   }
}""",
        )

        val filter = WorkDirectoryFilter().apply {
            addCategory(WorkDirectoryCategory("c100", "Category 100"))
            query("Query String")
            page(1)
        }
        val result = connector.fetchWorkDirectory(username, password, identityStoreMock, filter)

        assertNotNull(result)
        assertEquals(1, result.workContacts.size.toLong())
        assertEquals("ECHOECHO", result.workContacts[0].threemaId)
        assertEquals("Hans", result.workContacts[0].firstName)
        assertEquals("Nötig", result.workContacts[0].lastName)
        assertEquals("CSI_NR", result.workContacts[0].csi)
        assertEquals("Name der Firma/Organisation", result.workContacts[0].organization.name)
        assertEquals(2, result.workContacts[0].categoryIds.size.toLong())
        assertEquals("catId1", result.workContacts[0].categoryIds[0])
        assertEquals("catId2", result.workContacts[0].categoryIds[1])
        assertEquals(10, result.pageSize.toLong())
        assertEquals(8923, result.totalRecord.toLong())
        assertEquals(2, result.nextFilter!!.page.toLong())
        assertEquals(filter.query, result.nextFilter!!.query)
        assertEquals(filter.categories, result.nextFilter!!.categories)
        assertEquals(0, result.previousFilter!!.page.toLong())
        assertEquals(filter.query, result.previousFilter!!.query)
        assertEquals(filter.categories, result.previousFilter!!.categories)
    }

    private val username = "u"
    private val password = "eric"
    private val identity1 = "identity1"
    private val identity2 = "identity2"
    private val payload = jsonObject {
        put("username", username)
        put("password", password)
        put("contacts", JSONArray(listOf(identity1, identity2)))
    }

    private fun jsonObject(builder: JSONObject.() -> Unit): JSONObject =
        JSONObject().apply(builder)

    private fun mockGetRequest(url: String, response: String) {
        every { httpRequester.get(url, any()) } returns response
    }

    private fun mockPostRequest(url: String, expectedPayload: JSONObject, response: String) {
        every {
            httpRequester.post(url, match { it.toString() == expectedPayload.toString() }, any())
        } returns HttpRequesterResult.Success(response)
    }
}

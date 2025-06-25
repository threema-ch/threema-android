/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2018-2025 Threema GmbH
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

package ch.threema.app.threemasafe

import ch.threema.app.BuildConfig
import ch.threema.app.preference.service.PreferenceService
import ch.threema.app.services.ApiService
import ch.threema.app.services.BlockedIdentitiesService
import ch.threema.app.services.ContactService
import ch.threema.app.services.DistributionListService
import ch.threema.app.services.ExcludedSyncIdentitiesService
import ch.threema.app.services.GroupService
import ch.threema.app.stores.IdentityStore
import ch.threema.base.utils.JSONUtil
import ch.threema.base.utils.Utils
import ch.threema.data.ModelTypeCache
import ch.threema.data.repositories.ContactModelRepository
import ch.threema.domain.models.GroupId
import ch.threema.storage.models.ContactModel
import ch.threema.storage.models.DistributionListModel
import ch.threema.storage.models.GroupModel
import ch.threema.testhelpers.nonSecureRandomArray
import io.mockk.every
import io.mockk.mockk
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.apache.commons.lang3.LocaleUtils
import org.json.JSONArray
import org.json.JSONObject

class ThreemaSafeServiceTest {
    private val preferenceServiceMock: PreferenceService = mockk(relaxed = true)
    private val contactServiceMock: ContactService = mockk(relaxed = true)
    private val groupServiceMock: GroupService = mockk(relaxed = true)
    private val distributionListServiceMock: DistributionListService = mockk(relaxed = true)
    private val identityStoreMock: IdentityStore = mockk()
    private val apiService: ApiService = mockk()
    private val blockedIdentitiesServiceMock: BlockedIdentitiesService = mockk()
    private val excludedSyncIdentitiesServiceMock: ExcludedSyncIdentitiesService = mockk()

    private val contactModelRepository: ContactModelRepository = ContactModelRepository(
        cache = ModelTypeCache(),
        databaseBackend = mockk(),
        coreServiceManager = mockk(),
    )

    private lateinit var testDate1: Date
    private lateinit var testDate2: Date
    private var testDate1Timestamp: Long = 0
    private var testDate2Timestamp: Long = 0

    private val threemaSafeServiceImpl: ThreemaSafeServiceImpl = ThreemaSafeServiceImpl(
        /* context = */
        mockk(),
        /* preferenceService = */
        preferenceServiceMock,
        /* userService = */
        mockk(relaxed = true),
        /* contactService = */
        contactServiceMock,
        /* groupService = */
        groupServiceMock,
        /* distributionListService = */
        distributionListServiceMock,
        /* localeService = */
        mockk(),
        /* fileService = */
        mockk(),
        /* blockedIdentitiesService = */
        blockedIdentitiesServiceMock,
        /* excludedSyncIdentitiesService = */
        excludedSyncIdentitiesServiceMock,
        /* profilePicRecipientsService = */
        mockk(),
        /* databaseService = */
        mockk(),
        /* identityStore = */
        identityStoreMock,
        /* apiService = */
        apiService,
        /* apiConnector = */
        mockk(),
        /* conversationCategoryService = */
        mockk(relaxed = true),
        /* serverAddressProvider = */
        mockk(),
        /* preferenceStore = */
        mockk(),
        /* contactModelRepository = */
        contactModelRepository,
    )

    @Suppress("DEPRECATION")
    @BeforeTest
    fun prepareMocks() {
        every { identityStoreMock.privateKey } returns TEST_PRIVATE_KEY_BYTES
        every { blockedIdentitiesServiceMock.getAllBlockedIdentities() } returns emptySet()
        every { excludedSyncIdentitiesServiceMock.getExcludedIdentities() } returns emptySet()

        every { threemaSafeServiceImpl.threemaSafeBackupId } answers { callOriginal() }
        every { threemaSafeServiceImpl.threemaSafeEncryptionKey } answers { callOriginal() }

        val localeSwitzerland = LocaleUtils.toLocale("de_CH")
        Locale.setDefault(localeSwitzerland)
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Zurich"))

        testDate1 = Date(2020 - 1900, 11, 1, 13, 14, 15)
        testDate2 = Date(2021 - 1900, 11, 1, 13, 14, 15)
        testDate1Timestamp = 1606824855000L
        testDate2Timestamp = 1638360855000L
    }

    @Test
    fun testDeriveMasterKey() {
        // arrange
        val threemaSafeServiceMock = mockk<ThreemaSafeServiceImpl>()
        every { threemaSafeServiceMock.deriveMasterKey(any(), any()) } answers { callOriginal() }

        // act
        // Test case as defined in specification (see confluence)
        val masterKey: ByteArray? = threemaSafeServiceMock.deriveMasterKey("shootdeathstar", "ECHOECHO")
        val masterKeyHex: String = Utils.byteArrayToHexString(masterKey).lowercase(Locale.getDefault())

        // assert
        assertEquals(MASTER_KEY_HEX, masterKeyHex)
    }

    @Test
    fun testGetThreemaSafeBackupIdNull() {
        every { preferenceServiceMock.threemaSafeMasterKey } returns null
        val backupId1: ByteArray? = threemaSafeServiceImpl.threemaSafeBackupId
        assertNull(backupId1)

        every { preferenceServiceMock.threemaSafeMasterKey } returns byteArrayOf()
        val backupId2: ByteArray? = threemaSafeServiceImpl.threemaSafeBackupId
        assertNull(backupId2)

        every { preferenceServiceMock.threemaSafeMasterKey } returns nonSecureRandomArray(32)
        val backupId3: ByteArray? = threemaSafeServiceImpl.threemaSafeBackupId
        assertNull(backupId3)
    }

    @Test
    fun testGetThreemaSafeBackupId() {
        every { preferenceServiceMock.threemaSafeMasterKey } returns Utils.hexStringToByteArray(MASTER_KEY_HEX)

        val backupId: ByteArray? = threemaSafeServiceImpl.threemaSafeBackupId
        val backupIdHex: String = Utils.byteArrayToHexString(backupId).lowercase(Locale.getDefault())
        assertEquals(
            expected = "066384d3695fbbd9f31a7d533900fd0cd8d1373beb6a28678522d2a49980c9c3",
            actual = backupIdHex,
        )
    }

    @Test
    fun testGetThreemaSafeEncryptionKeyNull() {
        every { preferenceServiceMock.threemaSafeMasterKey } returns null
        val encryptionKey1: ByteArray? = threemaSafeServiceImpl.threemaSafeEncryptionKey
        assertNull(encryptionKey1)

        every { preferenceServiceMock.threemaSafeMasterKey } returns byteArrayOf()
        val encryptionKey2: ByteArray? = threemaSafeServiceImpl.threemaSafeEncryptionKey
        assertNull(encryptionKey2)

        every { preferenceServiceMock.threemaSafeMasterKey } returns nonSecureRandomArray(32)
        val encryptionKey3: ByteArray? = threemaSafeServiceImpl.threemaSafeEncryptionKey
        assertNull(encryptionKey3)
    }

    @Test
    fun testGetThreemaSafeEncryptionKey() {
        every { preferenceServiceMock.threemaSafeMasterKey } returns Utils.hexStringToByteArray(MASTER_KEY_HEX)

        val encryptionKey: ByteArray? = threemaSafeServiceImpl.threemaSafeEncryptionKey
        val encryptionKeyHex: String = Utils.byteArrayToHexString(encryptionKey).lowercase(Locale.getDefault())
        assertEquals(
            expected = "51c3d8d752fb6e1fd3199ead7f0895d6e3893ff691f2a5ee1976ed0897fc2f66",
            actual = encryptionKeyHex,
        )
    }

    @Test
    fun testSafeJsonContainsInfoValues() {
        val infoJson: JSONObject = requireParsedThreemaSafeJson().getJSONObject("info")
        assertEquals(
            expected = 1,
            actual = infoJson.getInt("version").toLong(),
        )
        assertEquals(
            expected = BuildConfig.VERSION_NAME + "A/de_CH",
            actual = infoJson.getString("device"),
        )
    }

    @Test
    fun testSafeJsonContainsUserValues() {
        val userJson: JSONObject = requireParsedThreemaSafeJson().getJSONObject("user")
        assertEquals(
            expected = TEST_PRIVATE_KEY_BASE64,
            actual = userJson.getString("privatekey"),
        )
        assertEquals(
            expected = 0,
            actual = userJson.getJSONArray("links").length(),
        )
    }

    @Test
    fun testSafeJsonContainsContactValues() {
        // arrange
        every { contactServiceMock.all } returns listOf(
            ContactModel.create("HELLO123", nonSecureRandomArray(32)).setLastUpdate(null),
            ContactModel.create("HELLO234", nonSecureRandomArray(32)).setLastUpdate(testDate1),
            ContactModel.create("HELLO345", nonSecureRandomArray(32)).setLastUpdate(testDate2),
        )

        // act
        val contactsJson: JSONArray = requireParsedThreemaSafeJson().getJSONArray("contacts")

        // assert
        assertEquals(expected = 3, actual = contactsJson.length())
        contactsJson.getJSONObject(0).let { contact1 ->
            assertEquals(expected = "HELLO123", actual = contact1.getString("identity"))
            assertFalse(contact1.has("lastUpdate"))
        }
        contactsJson.getJSONObject(1).let { contact2 ->
            assertEquals(expected = "HELLO234", actual = contact2.getString("identity"))
            assertEquals(testDate1Timestamp, contact2.getLong("lastUpdate"))
        }
        contactsJson.getJSONObject(2).let { contact3 ->
            assertEquals(expected = "HELLO345", actual = contact3.getString("identity"))
            assertEquals(testDate2Timestamp, contact3.getLong("lastUpdate"))
        }
    }

    @Test
    fun testSafeJsonDoesNotContainRemovedContacts() {
        // arrange
        // Contact 1 - not removed, in no common group
        val contact1 = ContactModel.create("CONTACT1", nonSecureRandomArray(32)).apply {
            setAcquaintanceLevel(ContactModel.AcquaintanceLevel.DIRECT)
        }

        // Contact 2 - not removed, in a common group
        val contact2 = ContactModel.create("CONTACT2", nonSecureRandomArray(32)).apply {
            setAcquaintanceLevel(ContactModel.AcquaintanceLevel.DIRECT)
        }

        // Contact 3 - removed, in a common group
        val contact3 = ContactModel.create("CONTACT3", nonSecureRandomArray(32)).apply {
            setAcquaintanceLevel(ContactModel.AcquaintanceLevel.GROUP)
        }

        // Contact 4 - removed, in no common group
        val contact4 = ContactModel.create("CONTACT4", nonSecureRandomArray(32)).apply {
            setAcquaintanceLevel(ContactModel.AcquaintanceLevel.GROUP)
        }

        // Contact 5 - removed, in no common group
        val contact5 = ContactModel.create("CONTACT5", nonSecureRandomArray(32)).apply {
            setAcquaintanceLevel(ContactModel.AcquaintanceLevel.GROUP)
        }

        every { contactServiceMock.removedContacts } returns setOf("CONTACT4", "CONTACT5")
        every { contactServiceMock.all } returns listOf(contact1, contact2, contact3, contact4, contact5)

        // act
        val contactsJson: JSONArray = requireParsedThreemaSafeJson().getJSONArray("contacts")

        // assert
        assertEquals(expected = 3, actual = contactsJson.length(), message = contactsJson.toString())
        assertEquals(expected = "CONTACT1", actual = contactsJson.getJSONObject(0).getString("identity"))
        assertEquals(expected = "CONTACT2", actual = contactsJson.getJSONObject(1).getString("identity"))
        assertEquals(expected = "CONTACT3", actual = contactsJson.getJSONObject(2).getString("identity"))
    }

    @Test
    fun testSafeJsonContainsGroupValues() {
        // arrange
        val groupModel1: GroupModel = GroupModel().setApiGroupId(GroupId(1L)).setCreatorIdentity("GROUPER1").setLastUpdate(null)
        val groupModel2: GroupModel = GroupModel().setApiGroupId(GroupId(2L)).setCreatorIdentity("GROUPER2").setLastUpdate(testDate1)
        val groupModel3: GroupModel = GroupModel().setApiGroupId(GroupId(3L)).setCreatorIdentity("GROUPER3").setLastUpdate(testDate2)

        every { groupServiceMock.getAll(any()) } returns listOf(groupModel1, groupModel2, groupModel3)

        every { groupServiceMock.getGroupMemberIdentities(groupModel1) } returns arrayOf("GROUPER1", "MEMBER01")
        every { groupServiceMock.getGroupMemberIdentities(groupModel2) } returns arrayOf("GROUPER2")
        every { groupServiceMock.getGroupMemberIdentities(groupModel3) } returns arrayOf("GROUPER3", "MEMBER01", "MEMBER02")

        // act
        val groups: JSONArray = requireParsedThreemaSafeJson().getJSONArray("groups")

        // assert
        assertEquals(expected = 3, actual = groups.length())
        groups.getJSONObject(0).let { group1 ->
            assertEquals(expected = "0100000000000000", actual = group1.getString("id"))
            assertEquals(expected = "GROUPER1", actual = group1.getString("creator"))
            assertEquals(expected = 0L, actual = group1.getLong("lastUpdate"))
        }
        groups.getJSONObject(1).let { group2 ->
            assertEquals(expected = "0200000000000000", actual = group2.getString("id"))
            assertEquals(expected = "GROUPER2", actual = group2.getString("creator"))
            assertEquals(expected = testDate1Timestamp, actual = group2.getLong("lastUpdate"))
        }
        groups.getJSONObject(2).let { group3 ->
            assertEquals(expected = "0300000000000000", actual = group3.getString("id"))
            assertEquals(expected = "GROUPER3", actual = group3.getString("creator"))
            assertEquals(expected = testDate2Timestamp, actual = group3.getLong("lastUpdate"))
        }
    }

    @Test
    fun testSafeJsonContainsDistributionListValues() {
        // arrange
        val distributionListModel1: DistributionListModel = DistributionListModel().setId(1L).setLastUpdate(null)
        val distributionListModel2: DistributionListModel = DistributionListModel().setId(2L).setLastUpdate(testDate1)
        val distributionListModel3: DistributionListModel = DistributionListModel().setId(3L).setLastUpdate(testDate2)

        every { distributionListServiceMock.getAll(any()) } returns listOf(distributionListModel1, distributionListModel2, distributionListModel3)
        every { distributionListServiceMock.getDistributionListIdentities(distributionListModel1) } returns arrayOf("MEMBER11")
        every { distributionListServiceMock.getDistributionListIdentities(distributionListModel2) } returns arrayOf("MEMBER21")
        every { distributionListServiceMock.getDistributionListIdentities(distributionListModel3) } returns arrayOf(
            "MEMBER31",
            "MEMBER32",
            "MEMBER33",
        )

        // act
        val distributionLists: JSONArray = requireParsedThreemaSafeJson().getJSONArray("distributionlists")

        // assert
        assertEquals(
            expected = 3,
            actual = distributionLists.length().toLong(),
        )
        distributionLists.getJSONObject(0).let { distributionList1 ->
            assertEquals(
                expected = "0100000000000000",
                actual = distributionList1.getString("id"),
            )
            assertContentEquals(
                expected = arrayOf("MEMBER11"),
                actual = JSONUtil.getStringArray(distributionList1.getJSONArray("members")),
            )
            assertEquals(
                expected = 0L,
                actual = distributionList1.getLong("lastUpdate"),
            )
        }
        distributionLists.getJSONObject(1).let { distributionList2 ->
            assertEquals(
                expected = "0200000000000000",
                actual = distributionList2.getString("id"),
            )
            assertContentEquals(
                expected = arrayOf("MEMBER21"),
                actual = JSONUtil.getStringArray(distributionList2.getJSONArray("members")),
            )
            assertEquals(
                expected = testDate1Timestamp,
                actual = distributionList2.getLong("lastUpdate"),
            )
        }
        distributionLists.getJSONObject(2).let { distributionList3 ->
            assertEquals(
                expected = "0300000000000000",
                actual = distributionList3.getString("id"),
            )
            assertContentEquals(
                expected = arrayOf("MEMBER31", "MEMBER32", "MEMBER33"),
                actual = JSONUtil.getStringArray(distributionList3.getJSONArray("members")),
            )
            assertEquals(
                expected = testDate2Timestamp,
                actual = distributionList3.getLong("lastUpdate"),
            )
        }
    }

    @Test
    fun testSafeJsonSettingsContainBlockedContacts() {
        // arrange
        val blockedIdentities: Set<String> = setOf("NONONONO", "BLOCKED0")
        every { blockedIdentitiesServiceMock.getAllBlockedIdentities() } returns blockedIdentities

        // act
        val settingsJson: JSONObject = requireParsedThreemaSafeJson().getJSONObject("settings")
        val identitiesJson: JSONArray = settingsJson.getJSONArray("blockedContacts")
        val actualBlockedIdentities: Set<String> = setOf(*JSONUtil.getStringArray(identitiesJson))

        // assert
        assertTrue(blockedIdentities.containsAll(actualBlockedIdentities))
        assertEquals(
            expected = blockedIdentities.size,
            actual = actualBlockedIdentities.size,
        )
    }

    @Test
    fun testSafeJsonSettingsContainsSyncExcludedIds() {
        // arrange
        val syncExcludedIds = setOf("ECHOECHO", "OCHEOCHE")
        every { excludedSyncIdentitiesServiceMock.getExcludedIdentities() } returns syncExcludedIds

        // act
        val settings: JSONObject = requireParsedThreemaSafeJson().getJSONObject("settings")
        val identities: JSONArray = settings.getJSONArray("syncExcludedIds")

        // assert
        assertContentEquals(
            expected = syncExcludedIds.toTypedArray(),
            actual = JSONUtil.getStringArray(identities),
        )
    }

    private fun requireParsedThreemaSafeJson(): JSONObject = JSONObject(threemaSafeServiceImpl.safeJson!!)

    companion object {
        // Test vector: Password "shootdeathstar" and salt "ECHOECHO" should result in this master key
        private const val MASTER_KEY_HEX: String =
            "066384d3695fbbd9f31a7d533900fd0cd8d1373beb6a28678522d2a49980c9c351c3d8d752fb6e1fd3199ead7f0895d6e3893ff691f2a5ee1976ed0897fc2f66"

        // Test data
        private val TEST_PRIVATE_KEY_BYTES: ByteArray = byteArrayOf(
            1, 2, 3, 4, 5, 6, 7, 8,
            100, 101, 102, 103, 104, 105, 106, 107,
            1, 2, 3, 4, 5, 6, 7, 8,
            100, 101, 102, 103, 104, 105, 106, 107,
        )
        private const val TEST_PRIVATE_KEY_BASE64: String = "AQIDBAUGBwhkZWZnaGlqawECAwQFBgcIZGVmZ2hpams="
    }
}

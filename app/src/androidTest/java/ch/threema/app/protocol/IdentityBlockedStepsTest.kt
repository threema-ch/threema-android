/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2024-2025 Threema GmbH
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

package ch.threema.app.protocol

import ch.threema.app.DangerousTest
import ch.threema.app.TestCoreServiceManager
import ch.threema.app.TestTaskManager
import ch.threema.app.ThreemaApplication
import ch.threema.app.preference.service.PreferenceService
import ch.threema.app.preference.service.PreferenceServiceImpl
import ch.threema.app.services.BlockedIdentitiesService
import ch.threema.app.services.GroupService
import ch.threema.app.testutils.TestHelpers
import ch.threema.app.testutils.TestHelpers.TestContact
import ch.threema.app.testutils.clearDatabaseAndCaches
import ch.threema.app.utils.AppVersionProvider
import ch.threema.data.TestDatabaseService
import ch.threema.data.models.ContactModelData
import ch.threema.data.repositories.ContactModelRepository
import ch.threema.data.repositories.ModelRepositories
import ch.threema.domain.helpers.UnusedTaskCodec
import ch.threema.domain.models.ContactSyncState
import ch.threema.domain.models.GroupId
import ch.threema.domain.models.IdentityState
import ch.threema.domain.models.IdentityType
import ch.threema.domain.models.ReadReceiptPolicy
import ch.threema.domain.models.TypingIndicatorPolicy
import ch.threema.domain.models.VerificationLevel
import ch.threema.domain.models.WorkVerificationLevel
import ch.threema.domain.stores.ContactStore
import ch.threema.storage.DatabaseService
import ch.threema.storage.models.ContactModel
import ch.threema.storage.models.GroupMemberModel
import ch.threema.storage.models.GroupModel
import java.util.Date
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking

@DangerousTest
class IdentityBlockedStepsTest {
    private lateinit var contactModelRepository: ContactModelRepository
    private lateinit var contactStore: ContactStore
    private lateinit var groupService: GroupService
    private lateinit var blockUnknownPreferenceService: PreferenceService
    private lateinit var noBlockPreferenceService: PreferenceService
    private lateinit var blockedIdentitiesService: BlockedIdentitiesService

    private val myContact = TestHelpers.TEST_CONTACT
    private val knownContact = TestContact("12345678")
    private val unknownContact = TestContact("TESTTEST")
    private val specialContact = TestContact("*3MAPUSH")
    private val explicitlyBlockedContact = TestContact("23456789")
    private val inGroup = TestContact("ABCDEFGH")
    private val inNoGroup = TestContact("********")
    private val inLeftGroup = TestContact("--------")

    @BeforeTest
    fun setup() {
        val serviceManager = ThreemaApplication.requireServiceManager()

        clearDatabaseAndCaches(serviceManager)

        assert(myContact.identity == TestHelpers.ensureIdentity(serviceManager))

        val databaseService = TestDatabaseService()
        val coreServiceManager = TestCoreServiceManager(
            version = AppVersionProvider.appVersion,
            databaseService = databaseService,
            preferenceStore = serviceManager.preferenceStore,
            taskManager = TestTaskManager(UnusedTaskCodec()),
        )
        contactModelRepository = ModelRepositories(coreServiceManager).contacts
        contactStore = serviceManager.contactStore
        groupService = serviceManager.groupService
        blockedIdentitiesService = serviceManager.blockedIdentitiesService
        blockedIdentitiesService.blockIdentity(explicitlyBlockedContact.identity)

        blockUnknownPreferenceService = object : PreferenceServiceImpl(
            ThreemaApplication.getAppContext(),
            serviceManager.preferenceStore,
            coreServiceManager.taskManager,
            coreServiceManager.multiDeviceManager,
            coreServiceManager.nonceFactory,
        ) {
            override fun isBlockUnknown(): Boolean {
                return true
            }
        }

        noBlockPreferenceService = object : PreferenceServiceImpl(
            ThreemaApplication.getAppContext(),
            serviceManager.preferenceStore,
            coreServiceManager.taskManager,
            coreServiceManager.multiDeviceManager,
            coreServiceManager.nonceFactory,
        ) {
            override fun isBlockUnknown(): Boolean {
                return false
            }
        }

        addKnownContacts()
        addGroups(serviceManager.databaseService)
    }

    @Test
    fun testExplicitlyBlockedContact() {
        assertEquals(
            BlockState.EXPLICITLY_BLOCKED,
            runIdentityBlockedSteps(explicitlyBlockedContact.identity, noBlockPreferenceService),
        )
        assertEquals(
            BlockState.EXPLICITLY_BLOCKED,
            runIdentityBlockedSteps(
                explicitlyBlockedContact.identity,
                blockUnknownPreferenceService,
            ),
        )
    }

    @Test
    fun testImplicitlyBlockedContact() {
        assertEquals(
            BlockState.IMPLICITLY_BLOCKED,
            runIdentityBlockedSteps(unknownContact.identity, blockUnknownPreferenceService),
        )
    }

    @Test
    fun testImplicitlyBlockedSpecialContact() {
        assertEquals(
            BlockState.NOT_BLOCKED,
            runIdentityBlockedSteps(specialContact.identity, blockUnknownPreferenceService),
        )
    }

    @Test
    fun testGroupContactWithGroup() {
        assertEquals(
            BlockState.NOT_BLOCKED,
            runIdentityBlockedSteps(inGroup.identity, blockUnknownPreferenceService),
        )
    }

    @Test
    fun testGroupContactWithoutGroup() {
        assertEquals(
            BlockState.IMPLICITLY_BLOCKED,
            runIdentityBlockedSteps(inNoGroup.identity, blockUnknownPreferenceService),
        )
    }

    @Test
    fun testGroupContactWithLeftGroup() {
        assertEquals(
            BlockState.IMPLICITLY_BLOCKED,
            runIdentityBlockedSteps(inLeftGroup.identity, blockUnknownPreferenceService),
        )
    }

    @Test
    fun testKnownContact() {
        assertEquals(
            BlockState.NOT_BLOCKED,
            runIdentityBlockedSteps(knownContact.identity, blockUnknownPreferenceService),
        )
    }

    @Test
    fun testWithoutBlockingUnknown() {
        assertEquals(
            BlockState.NOT_BLOCKED,
            runIdentityBlockedSteps(knownContact.identity, noBlockPreferenceService),
        )
        assertEquals(
            BlockState.NOT_BLOCKED,
            runIdentityBlockedSteps(unknownContact.identity, noBlockPreferenceService),
        )
        assertEquals(
            BlockState.NOT_BLOCKED,
            runIdentityBlockedSteps(specialContact.identity, noBlockPreferenceService),
        )
        assertEquals(
            BlockState.NOT_BLOCKED,
            runIdentityBlockedSteps(inGroup.identity, noBlockPreferenceService),
        )
        assertEquals(
            BlockState.NOT_BLOCKED,
            runIdentityBlockedSteps(inNoGroup.identity, noBlockPreferenceService),
        )
        assertEquals(
            BlockState.NOT_BLOCKED,
            runIdentityBlockedSteps(inLeftGroup.identity, noBlockPreferenceService),
        )
    }

    private fun runIdentityBlockedSteps(
        identity: String,
        preferenceService: PreferenceService,
    ) = runIdentityBlockedSteps(
        identity,
        contactModelRepository,
        contactStore,
        groupService,
        blockedIdentitiesService,
        preferenceService,
    )

    private fun addKnownContacts() = runBlocking {
        contactModelRepository.createFromLocal(
            ContactModelData(
                identity = knownContact.identity,
                publicKey = knownContact.publicKey,
                createdAt = Date(),
                firstName = "",
                lastName = "",
                nickname = "",
                colorIndex = 0u,
                verificationLevel = VerificationLevel.UNVERIFIED,
                workVerificationLevel = WorkVerificationLevel.NONE,
                identityType = IdentityType.NORMAL,
                acquaintanceLevel = ContactModel.AcquaintanceLevel.DIRECT,
                activityState = IdentityState.ACTIVE,
                syncState = ContactSyncState.INITIAL,
                featureMask = 0u,
                readReceiptPolicy = ReadReceiptPolicy.DEFAULT,
                typingIndicatorPolicy = TypingIndicatorPolicy.DEFAULT,
                isArchived = false,
                androidContactLookupKey = null,
                localAvatarExpires = null,
                isRestored = false,
                profilePictureBlobId = null,
                jobTitle = null,
                department = null,
                notificationTriggerPolicyOverride = null,
            ),
        )
        contactModelRepository.createFromLocal(
            ContactModelData(
                identity = inGroup.identity,
                publicKey = inGroup.publicKey,
                createdAt = Date(),
                firstName = "",
                lastName = "",
                nickname = "",
                colorIndex = 0u,
                verificationLevel = VerificationLevel.UNVERIFIED,
                workVerificationLevel = WorkVerificationLevel.NONE,
                identityType = IdentityType.NORMAL,
                acquaintanceLevel = ContactModel.AcquaintanceLevel.GROUP,
                activityState = IdentityState.ACTIVE,
                syncState = ContactSyncState.INITIAL,
                featureMask = 0u,
                readReceiptPolicy = ReadReceiptPolicy.DEFAULT,
                typingIndicatorPolicy = TypingIndicatorPolicy.DEFAULT,
                isArchived = false,
                androidContactLookupKey = null,
                localAvatarExpires = null,
                isRestored = false,
                profilePictureBlobId = null,
                jobTitle = null,
                department = null,
                notificationTriggerPolicyOverride = null,
            ),
        )
        contactModelRepository.createFromLocal(
            ContactModelData(
                identity = inNoGroup.identity,
                publicKey = inNoGroup.publicKey,
                createdAt = Date(),
                firstName = "",
                lastName = "",
                nickname = "",
                colorIndex = 0u,
                verificationLevel = VerificationLevel.UNVERIFIED,
                workVerificationLevel = WorkVerificationLevel.NONE,
                identityType = IdentityType.NORMAL,
                acquaintanceLevel = ContactModel.AcquaintanceLevel.GROUP,
                activityState = IdentityState.ACTIVE,
                syncState = ContactSyncState.INITIAL,
                featureMask = 0u,
                readReceiptPolicy = ReadReceiptPolicy.DEFAULT,
                typingIndicatorPolicy = TypingIndicatorPolicy.DEFAULT,
                isArchived = false,
                androidContactLookupKey = null,
                localAvatarExpires = null,
                isRestored = false,
                profilePictureBlobId = null,
                jobTitle = null,
                department = null,
                notificationTriggerPolicyOverride = null,
            ),
        )
        contactModelRepository.createFromLocal(
            ContactModelData(
                identity = inLeftGroup.identity,
                publicKey = inLeftGroup.publicKey,
                createdAt = Date(),
                firstName = "",
                lastName = "",
                nickname = "",
                colorIndex = 0u,
                verificationLevel = VerificationLevel.UNVERIFIED,
                workVerificationLevel = WorkVerificationLevel.NONE,
                identityType = IdentityType.NORMAL,
                acquaintanceLevel = ContactModel.AcquaintanceLevel.GROUP,
                activityState = IdentityState.ACTIVE,
                syncState = ContactSyncState.INITIAL,
                featureMask = 0u,
                readReceiptPolicy = ReadReceiptPolicy.DEFAULT,
                typingIndicatorPolicy = TypingIndicatorPolicy.DEFAULT,
                isArchived = false,
                androidContactLookupKey = null,
                localAvatarExpires = null,
                isRestored = false,
                profilePictureBlobId = null,
                jobTitle = null,
                department = null,
                notificationTriggerPolicyOverride = null,
            ),
        )
    }

    private fun addGroups(databaseService: DatabaseService) = runBlocking {
        databaseService.groupModelFactory.apply {
            create(
                GroupModel()
                    .setApiGroupId(GroupId(0))
                    .setCreatorIdentity(myContact.identity)
                    .setUserState(GroupModel.UserState.MEMBER)
                    .setCreatedAt(Date()),
            )
            create(
                GroupModel()
                    .setApiGroupId(GroupId(1))
                    .setCreatorIdentity(myContact.identity)
                    .setUserState(GroupModel.UserState.LEFT)
                    .setCreatedAt(Date()),
            )
        }
        val memberGroup = databaseService.groupModelFactory.getByApiGroupIdAndCreator(
            GroupId(0).toString(),
            myContact.identity,
        )
        val leftGroup = databaseService.groupModelFactory.getByApiGroupIdAndCreator(
            GroupId(1).toString(),
            myContact.identity,
        )
        databaseService.groupMemberModelFactory.apply {
            create(
                GroupMemberModel()
                    .setGroupId(memberGroup.id)
                    .setIdentity(inGroup.identity),
            )
            create(
                GroupMemberModel()
                    .setGroupId(leftGroup.id)
                    .setIdentity(inLeftGroup.identity),
            )
        }
    }
}

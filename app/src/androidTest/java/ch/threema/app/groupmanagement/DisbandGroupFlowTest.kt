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

package ch.threema.app.groupmanagement

import ch.threema.app.DangerousTest
import ch.threema.app.groupflows.GroupDisbandIntent
import ch.threema.app.groupflows.GroupFlowResult
import ch.threema.app.tasks.OutgoingGroupDisbandTask
import ch.threema.app.tasks.ReflectGroupSyncDeleteTask
import ch.threema.app.tasks.ReflectLocalGroupLeaveOrDisband
import ch.threema.app.testutils.TestHelpers
import ch.threema.app.testutils.clearDatabaseAndCaches
import ch.threema.data.models.ContactModelData
import ch.threema.data.models.GroupIdentity
import ch.threema.data.models.GroupModel
import ch.threema.data.models.GroupModelData
import ch.threema.domain.helpers.ControlledTaskManager
import ch.threema.domain.models.ContactSyncState
import ch.threema.domain.models.IdentityState
import ch.threema.domain.models.IdentityType
import ch.threema.domain.models.ReadReceiptPolicy
import ch.threema.domain.models.TypingIndicatorPolicy
import ch.threema.domain.models.VerificationLevel
import ch.threema.domain.models.WorkVerificationLevel
import ch.threema.domain.taskmanager.Task
import ch.threema.domain.taskmanager.TaskCodec
import ch.threema.storage.models.ContactModel.AcquaintanceLevel
import ch.threema.storage.models.GroupModel.UserState
import java.util.Date
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.fail
import kotlinx.coroutines.test.runTest

@DangerousTest
class DisbandGroupFlowTest : GroupFlowTest() {
    private val myContact = TestHelpers.TEST_CONTACT

    private val initialContactData = ContactModelData(
        identity = "12345678",
        publicKey = ByteArray(32),
        createdAt = Date(),
        firstName = "",
        lastName = "",
        verificationLevel = VerificationLevel.SERVER_VERIFIED,
        workVerificationLevel = WorkVerificationLevel.NONE,
        nickname = null,
        identityType = IdentityType.NORMAL,
        acquaintanceLevel = AcquaintanceLevel.DIRECT,
        activityState = IdentityState.ACTIVE,
        syncState = ContactSyncState.INITIAL,
        featureMask = 255u,
        readReceiptPolicy = ReadReceiptPolicy.DEFAULT,
        typingIndicatorPolicy = TypingIndicatorPolicy.DEFAULT,
        isArchived = false,
        profilePictureBlobId = null,
        androidContactLookupKey = null,
        localAvatarExpires = null,
        isRestored = false,
        jobTitle = null,
        department = null,
        notificationTriggerPolicyOverride = null,
    )

    private val myInitialGroupModelData = GroupModelData(
        groupIdentity = GroupIdentity(myContact.identity, 42),
        name = "MyExistingGroup",
        createdAt = Date(),
        synchronizedAt = null,
        lastUpdate = Date(),
        isArchived = false,
        groupDescription = null,
        groupDescriptionChangedAt = null,
        otherMembers = setOf(initialContactData.identity),
        userState = UserState.MEMBER,
        notificationTriggerPolicyOverride = null,
    )

    private val initialGroupModelData = myInitialGroupModelData.copy(
        groupIdentity = GroupIdentity(initialContactData.identity, 43),
        name = "ExistingGroup",
    )

    private val myInitialLeftGroupModelData = myInitialGroupModelData.copy(
        groupIdentity = GroupIdentity(myContact.identity, 44),
        name = "LeftGroup",
        userState = UserState.LEFT,
    )

    private val myInitialKickedGroupModelData = myInitialGroupModelData.copy(
        groupIdentity = GroupIdentity(myContact.identity, 45),
        name = "KickedGroup",
        userState = UserState.KICKED,
    )

    private val myInitialNotesGroupModelData = myInitialGroupModelData.copy(
        groupIdentity = GroupIdentity(myContact.identity, 46),
        name = "NotesGroup",
        userState = UserState.MEMBER,
        otherMembers = emptySet(),
    )

    @BeforeTest
    fun setup() {
        clearDatabaseAndCaches(serviceManager)

        assert(myContact.identity == TestHelpers.ensureIdentity(serviceManager))

        // Note that we use from sync to prevent any reflection. This is only acceptable in tests.
        contactModelRepository.createFromSync(initialContactData)
        groupModelRepository.apply {
            createFromSync(myInitialGroupModelData)
            createFromSync(initialGroupModelData)
            createFromSync(myInitialLeftGroupModelData)
            createFromSync(myInitialKickedGroupModelData)
            createFromSync(myInitialNotesGroupModelData)
        }
    }

    @Test
    fun testGroupDisbandMd() = runTest {
        val groupModel =
            groupModelRepository.getByGroupIdentity(myInitialGroupModelData.groupIdentity)
        assertNotNull(groupModel)
        assertEquals(myContact.identity, groupModel.groupIdentity.creatorIdentity)
        assertSuccessfulDisband(
            groupModel,
            GroupDisbandIntent.DISBAND,
            ReflectionExpectation.REFLECTION_SUCCESS,
        )
    }

    @Test
    fun testGroupDisbandNonMd() = runTest {
        val groupModel =
            groupModelRepository.getByGroupIdentity(myInitialGroupModelData.groupIdentity)
        assertNotNull(groupModel)
        assertEquals(myContact.identity, groupModel.groupIdentity.creatorIdentity)
        assertSuccessfulDisband(
            groupModel,
            GroupDisbandIntent.DISBAND,
            ReflectionExpectation.REFLECTION_SKIPPED,
        )
    }

    @Test
    fun testGroupDisbandAndRemoveMd() = runTest {
        val groupModel =
            groupModelRepository.getByGroupIdentity(myInitialGroupModelData.groupIdentity)
        assertNotNull(groupModel)
        assertEquals(myContact.identity, groupModel.groupIdentity.creatorIdentity)
        assertSuccessfulDisband(
            groupModel,
            GroupDisbandIntent.DISBAND_AND_REMOVE,
            ReflectionExpectation.REFLECTION_SUCCESS,
        )
    }

    @Test
    fun testGroupDisbandAndRemoveNonMd() = runTest {
        val groupModel =
            groupModelRepository.getByGroupIdentity(myInitialGroupModelData.groupIdentity)
        assertNotNull(groupModel)
        assertEquals(myContact.identity, groupModel.groupIdentity.creatorIdentity)
        assertSuccessfulDisband(
            groupModel,
            GroupDisbandIntent.DISBAND_AND_REMOVE,
            ReflectionExpectation.REFLECTION_SKIPPED,
        )
    }

    @Test
    fun testGroupDisbandForeignGroupMd() = runTest {
        val groupModel =
            groupModelRepository.getByGroupIdentity(initialGroupModelData.groupIdentity)
        assertNotNull(groupModel)
        assertNotEquals(myContact.identity, groupModel.groupIdentity.creatorIdentity)
        assertUnsuccessfulDisband(
            groupModel,
            GroupDisbandIntent.DISBAND,
            ReflectionExpectation.REFLECTION_FAIL,
        )
    }

    @Test
    fun testDisbandForeignGroupNonMd() = runTest {
        val groupModel =
            groupModelRepository.getByGroupIdentity(initialGroupModelData.groupIdentity)
        assertNotNull(groupModel)
        assertNotEquals(myContact.identity, groupModel.groupIdentity.creatorIdentity)
        assertUnsuccessfulDisband(
            groupModel,
            GroupDisbandIntent.DISBAND,
            ReflectionExpectation.REFLECTION_SKIPPED,
        )
    }

    @Test
    fun testDisbandLeftGroupMd() = runTest {
        val groupModel =
            groupModelRepository.getByGroupIdentity(myInitialLeftGroupModelData.groupIdentity)
        assertNotNull(groupModel)
        assertEquals(UserState.LEFT, groupModel.data.value?.userState)
        assertUnsuccessfulDisband(
            groupModel,
            GroupDisbandIntent.DISBAND,
            ReflectionExpectation.REFLECTION_FAIL,
        )
    }

    @Test
    fun testDisbandLeftGroupNonMd() = runTest {
        val groupModel =
            groupModelRepository.getByGroupIdentity(myInitialLeftGroupModelData.groupIdentity)
        assertNotNull(groupModel)
        assertEquals(UserState.LEFT, groupModel.data.value?.userState)
        assertUnsuccessfulDisband(
            groupModel,
            GroupDisbandIntent.DISBAND,
            ReflectionExpectation.REFLECTION_SKIPPED,
        )
    }

    @Test
    fun testDisbandRemovedGroupMd() = runTest {
        val groupModel =
            groupModelRepository.getByGroupIdentity(myInitialGroupModelData.groupIdentity)
        assertNotNull(groupModel)

        groupModelRepository.persistRemovedGroup(groupModel.groupIdentity)
        assertNull(groupModel.data.value)

        assertUnsuccessfulDisband(
            groupModel,
            GroupDisbandIntent.DISBAND,
            ReflectionExpectation.REFLECTION_SUCCESS,
        )
    }

    @Test
    fun testDisbandRemovedGroupNonMd() = runTest {
        val groupModel =
            groupModelRepository.getByGroupIdentity(myInitialGroupModelData.groupIdentity)
        assertNotNull(groupModel)

        groupModelRepository.persistRemovedGroup(groupModel.groupIdentity)
        assertNull(groupModel.data.value)

        assertUnsuccessfulDisband(
            groupModel,
            GroupDisbandIntent.DISBAND,
            ReflectionExpectation.REFLECTION_SKIPPED,
        )
    }

    @Test
    fun testDisbandNotesGroupMd() = runTest {
        val groupModel =
            groupModelRepository.getByGroupIdentity(myInitialNotesGroupModelData.groupIdentity)
        assertNotNull(groupModel)
        assertEquals(myContact.identity, groupModel.groupIdentity.creatorIdentity)
        assertEquals(emptySet(), groupModel.data.value?.otherMembers)
        assertEquals(UserState.MEMBER, groupModel.data.value?.userState)

        assertSuccessfulDisband(
            groupModel,
            GroupDisbandIntent.DISBAND,
            ReflectionExpectation.REFLECTION_SUCCESS,
        )
    }

    @Test
    fun testDisbandNotesGroupNonMd() = runTest {
        val groupModel =
            groupModelRepository.getByGroupIdentity(myInitialNotesGroupModelData.groupIdentity)
        assertNotNull(groupModel)
        assertEquals(myContact.identity, groupModel.groupIdentity.creatorIdentity)
        assertEquals(emptySet(), groupModel.data.value?.otherMembers)
        assertEquals(UserState.MEMBER, groupModel.data.value?.userState)

        assertSuccessfulDisband(
            groupModel,
            GroupDisbandIntent.DISBAND,
            ReflectionExpectation.REFLECTION_SKIPPED,
        )
    }

    @Test
    fun testDisbandAndRemoveNotesGroupMd() = runTest {
        val groupModel =
            groupModelRepository.getByGroupIdentity(myInitialNotesGroupModelData.groupIdentity)
        assertNotNull(groupModel)
        assertEquals(myContact.identity, groupModel.groupIdentity.creatorIdentity)
        assertEquals(emptySet(), groupModel.data.value?.otherMembers)
        assertEquals(UserState.MEMBER, groupModel.data.value?.userState)

        assertSuccessfulDisband(
            groupModel,
            GroupDisbandIntent.DISBAND_AND_REMOVE,
            ReflectionExpectation.REFLECTION_SUCCESS,
        )
    }

    @Test
    fun testDisbandAndRemoveNotesGroupNonMd() = runTest {
        val groupModel =
            groupModelRepository.getByGroupIdentity(myInitialNotesGroupModelData.groupIdentity)
        assertNotNull(groupModel)
        assertEquals(myContact.identity, groupModel.groupIdentity.creatorIdentity)
        assertEquals(emptySet(), groupModel.data.value?.otherMembers)
        assertEquals(UserState.MEMBER, groupModel.data.value?.userState)

        assertSuccessfulDisband(
            groupModel,
            GroupDisbandIntent.DISBAND_AND_REMOVE,
            ReflectionExpectation.REFLECTION_SKIPPED,
        )
    }

    @Test
    fun shouldNotDisbandGroupWhenMdActiveButConnectionIsLost() = runTest {
        // arrange
        val groupModel = groupModelRepository.getByGroupIdentity(myInitialGroupModelData.groupIdentity)
        val taskManager = ControlledTaskManager(emptyList())
        val groupFlowDispatcher = getGroupFlowDispatcher(
            setupConfig = SetupConfig.MULTI_DEVICE_ENABLED,
            taskManager = taskManager,
            connection = ConnectionDisconnected,
        )

        // act
        val groupFlowResult = groupFlowDispatcher
            .runDisbandGroupFlow(GroupDisbandIntent.DISBAND, groupModel!!)
            .await()

        // assert
        assertIs<GroupFlowResult.Failure.Network>(groupFlowResult)
    }

    private suspend fun assertSuccessfulDisband(
        groupModel: GroupModel,
        intent: GroupDisbandIntent,
        reflectionExpectation: ReflectionExpectation,
    ) {
        assertIs<GroupFlowResult.Success>(
            runGroupDisband(groupModel, intent, reflectionExpectation),
        )

        when (intent) {
            GroupDisbandIntent.DISBAND -> assertEquals(
                UserState.LEFT,
                groupModel.data.value?.userState,
            )

            GroupDisbandIntent.DISBAND_AND_REMOVE -> assertNull(groupModel.data.value)
        }
    }

    private suspend fun assertUnsuccessfulDisband(
        groupModel: GroupModel,
        intent: GroupDisbandIntent,
        reflectionExpectation: ReflectionExpectation,
    ) {
        val groupModelDataBefore = groupModel.data.value
        assertIs<GroupFlowResult.Failure>(
            runGroupDisband(groupModel, intent, reflectionExpectation),
        )
        val groupModelDataAfter = groupModel.data.value
        // Assert that the group model has not changed
        assertEquals(groupModelDataBefore, groupModelDataAfter)
    }

    private suspend fun runGroupDisband(
        groupModel: GroupModel,
        intent: GroupDisbandIntent,
        reflectionExpectation: ReflectionExpectation,
    ): GroupFlowResult {
        val groupModelData = groupModel.data.value

        // Prepare task manager and group flow dispatcher
        val taskManager = ControlledTaskManager(
            getExpectedTaskAssertions(groupModelData, intent, reflectionExpectation),
        )
        val groupFlowDispatcher = getGroupFlowDispatcher(
            reflectionExpectation.setupConfig,
            taskManager,
        )

        // Run disband group flow
        val groupFlowResult = groupFlowDispatcher.runDisbandGroupFlow(
            intent,
            groupModel,
        ).await()

        taskManager.pendingTaskAssertions.size.let { size ->
            if (size > 0) {
                fail("There are $size pending task assertions left")
            }
        }

        return groupFlowResult
    }

    private fun getExpectedTaskAssertions(
        groupModelData: GroupModelData?,
        intent: GroupDisbandIntent,
        reflectionExpectation: ReflectionExpectation,
    ): MutableList<(Task<*, TaskCodec>) -> Unit> {
        if (groupModelData == null ||
            groupModelData.groupIdentity.creatorIdentity != myContact.identity ||
            groupModelData.userState != UserState.MEMBER
        ) {
            return mutableListOf()
        }

        val scheduledTaskAssertions: MutableList<(Task<*, TaskCodec>) -> Unit> = mutableListOf()
        // If multi device is enabled, then we expect a reflection
        if (reflectionExpectation.setupConfig == SetupConfig.MULTI_DEVICE_ENABLED) {
            scheduledTaskAssertions.add { task ->
                when (intent) {
                    GroupDisbandIntent.DISBAND -> assertIs<ReflectLocalGroupLeaveOrDisband>(task)
                    GroupDisbandIntent.DISBAND_AND_REMOVE -> assertIs<ReflectGroupSyncDeleteTask>(
                        task,
                    )
                }
            }
        }

        // If the reflection fails, we do not expect a task that sends out csp messages
        if (reflectionExpectation != ReflectionExpectation.REFLECTION_FAIL) {
            scheduledTaskAssertions.add { task ->
                assertIs<OutgoingGroupDisbandTask>(task)
            }
        }

        return scheduledTaskAssertions
    }
}

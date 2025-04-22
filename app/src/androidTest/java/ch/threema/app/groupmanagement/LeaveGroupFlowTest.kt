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
import ch.threema.app.groupflows.GroupLeaveIntent
import ch.threema.app.tasks.OutgoingGroupLeaveTask
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
import ch.threema.storage.models.ContactModel
import ch.threema.storage.models.GroupModel.UserState
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

@DangerousTest
class LeaveGroupFlowTest : GroupFlowTest() {
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
        acquaintanceLevel = ContactModel.AcquaintanceLevel.DIRECT,
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
        otherMembers = emptySet(),
        userState = UserState.MEMBER,
        notificationTriggerPolicyOverride = null,
    )

    private val initialGroupModelData = myInitialGroupModelData.copy(
        groupIdentity = GroupIdentity(initialContactData.identity, 43),
        name = "ExistingGroup",
    )

    private val initialLeftGroupModelData = myInitialGroupModelData.copy(
        groupIdentity = GroupIdentity(initialContactData.identity, 44),
        name = "LeftGroup",
        userState = UserState.LEFT,
    )

    private val initialKickedGroupModelData = myInitialGroupModelData.copy(
        groupIdentity = GroupIdentity(initialContactData.identity, 45),
        name = "KickedGroup",
        userState = UserState.KICKED,
    )

    @Before
    fun setup() {
        clearDatabaseAndCaches(serviceManager)

        assert(myContact.identity == TestHelpers.ensureIdentity(serviceManager))

        // Note that we use from sync to prevent any reflection. This is only acceptable in tests.
        contactModelRepository.createFromSync(initialContactData)
        groupModelRepository.apply {
            createFromSync(myInitialGroupModelData)
            createFromSync(initialGroupModelData)
            createFromSync(initialLeftGroupModelData)
            createFromSync(initialKickedGroupModelData)
        }
    }

    @Test
    fun testGroupLeaveMd() = runTest {
        val groupModel =
            groupModelRepository.getByGroupIdentity(initialGroupModelData.groupIdentity)
        assertNotNull(groupModel)
        assertSuccessfulLeave(
            groupModel,
            GroupLeaveIntent.LEAVE,
            ReflectionExpectation.REFLECTION_SUCCESS,
        )
    }

    @Test
    fun testGroupLeaveNonMd() = runTest {
        val groupModel =
            groupModelRepository.getByGroupIdentity(initialGroupModelData.groupIdentity)
        assertNotNull(groupModel)
        assertSuccessfulLeave(
            groupModel,
            GroupLeaveIntent.LEAVE,
            ReflectionExpectation.REFLECTION_SKIPPED,
        )
    }

    @Test
    fun testGroupLeaveAndRemoveMd() = runTest {
        val groupModel =
            groupModelRepository.getByGroupIdentity(initialGroupModelData.groupIdentity)
        assertNotNull(groupModel)
        assertSuccessfulLeave(
            groupModel,
            GroupLeaveIntent.LEAVE_AND_REMOVE,
            ReflectionExpectation.REFLECTION_SUCCESS,
        )
    }

    @Test
    fun testGroupLeaveAndRemoveNonMd() = runTest {
        val groupModel =
            groupModelRepository.getByGroupIdentity(initialGroupModelData.groupIdentity)
        assertNotNull(groupModel)
        assertSuccessfulLeave(
            groupModel,
            GroupLeaveIntent.LEAVE_AND_REMOVE,
            ReflectionExpectation.REFLECTION_SKIPPED,
        )
    }

    @Test
    fun testGroupLeaveMyGroupMd() = runTest {
        val groupModel =
            groupModelRepository.getByGroupIdentity(myInitialGroupModelData.groupIdentity)
        assertNotNull(groupModel)
        assertUnsuccessfulLeave(
            groupModel,
            GroupLeaveIntent.LEAVE,
            ReflectionExpectation.REFLECTION_SUCCESS,
        )
    }

    @Test
    fun testLeaveMyGroupNonMd() = runTest {
        val groupModel =
            groupModelRepository.getByGroupIdentity(myInitialGroupModelData.groupIdentity)
        assertNotNull(groupModel)
        assertUnsuccessfulLeave(
            groupModel,
            GroupLeaveIntent.LEAVE,
            ReflectionExpectation.REFLECTION_SKIPPED,
        )
    }

    @Test
    fun testGroupLeaveAndRemoveMyGroupMd() = runTest {
        val groupModel =
            groupModelRepository.getByGroupIdentity(myInitialGroupModelData.groupIdentity)
        assertNotNull(groupModel)
        assertUnsuccessfulLeave(
            groupModel,
            GroupLeaveIntent.LEAVE_AND_REMOVE,
            ReflectionExpectation.REFLECTION_SUCCESS,
        )
    }

    @Test
    fun testLeaveAndRemoveMyGroupNonMd() = runTest {
        val groupModel =
            groupModelRepository.getByGroupIdentity(myInitialGroupModelData.groupIdentity)
        assertNotNull(groupModel)
        assertUnsuccessfulLeave(
            groupModel,
            GroupLeaveIntent.LEAVE_AND_REMOVE,
            ReflectionExpectation.REFLECTION_SKIPPED,
        )
    }

    @Test
    fun testLeaveLeftGroupMd() = runTest {
        val groupModel =
            groupModelRepository.getByGroupIdentity(initialLeftGroupModelData.groupIdentity)
        assertNotNull(groupModel)
        assertUnsuccessfulLeave(
            groupModel,
            GroupLeaveIntent.LEAVE,
            ReflectionExpectation.REFLECTION_SUCCESS,
        )
    }

    @Test
    fun testLeaveLeftGroupNonMd() = runTest {
        val groupModel =
            groupModelRepository.getByGroupIdentity(initialLeftGroupModelData.groupIdentity)
        assertNotNull(groupModel)
        assertUnsuccessfulLeave(
            groupModel,
            GroupLeaveIntent.LEAVE,
            ReflectionExpectation.REFLECTION_SKIPPED,
        )
    }

    @Test
    fun testLeaveAndRemoveLeftGroupMd() = runTest {
        val groupModel =
            groupModelRepository.getByGroupIdentity(initialLeftGroupModelData.groupIdentity)
        assertNotNull(groupModel)
        assertUnsuccessfulLeave(
            groupModel,
            GroupLeaveIntent.LEAVE_AND_REMOVE,
            ReflectionExpectation.REFLECTION_SUCCESS,
        )
    }

    @Test
    fun testLeaveAndRemoveLeftGroupNonMd() = runTest {
        val groupModel =
            groupModelRepository.getByGroupIdentity(initialLeftGroupModelData.groupIdentity)
        assertNotNull(groupModel)
        assertUnsuccessfulLeave(
            groupModel,
            GroupLeaveIntent.LEAVE_AND_REMOVE,
            ReflectionExpectation.REFLECTION_SKIPPED,
        )
    }

    @Test
    fun testLeaveKickedGroupMd() = runTest {
        val groupModel =
            groupModelRepository.getByGroupIdentity(initialKickedGroupModelData.groupIdentity)
        assertNotNull(groupModel)
        assertUnsuccessfulLeave(
            groupModel,
            GroupLeaveIntent.LEAVE,
            ReflectionExpectation.REFLECTION_SUCCESS,
        )
    }

    @Test
    fun testLeaveKickedGroupNonMd() = runTest {
        val groupModel =
            groupModelRepository.getByGroupIdentity(initialKickedGroupModelData.groupIdentity)
        assertNotNull(groupModel)
        assertUnsuccessfulLeave(
            groupModel,
            GroupLeaveIntent.LEAVE,
            ReflectionExpectation.REFLECTION_SKIPPED,
        )
    }

    @Test
    fun testLeaveAndRemoveKickedGroupMd() = runTest {
        val groupModel =
            groupModelRepository.getByGroupIdentity(initialKickedGroupModelData.groupIdentity)
        assertNotNull(groupModel)
        assertUnsuccessfulLeave(
            groupModel,
            GroupLeaveIntent.LEAVE_AND_REMOVE,
            ReflectionExpectation.REFLECTION_SUCCESS,
        )
    }

    @Test
    fun testLeaveAndRemoveKickedGroupNonMd() = runTest {
        val groupModel =
            groupModelRepository.getByGroupIdentity(initialKickedGroupModelData.groupIdentity)
        assertNotNull(groupModel)
        assertUnsuccessfulLeave(
            groupModel,
            GroupLeaveIntent.LEAVE_AND_REMOVE,
            ReflectionExpectation.REFLECTION_SKIPPED,
        )
    }

    @Test
    fun testLeaveRemovedGroupMd() = runTest {
        val groupModel =
            groupModelRepository.getByGroupIdentity(initialKickedGroupModelData.groupIdentity)
        assertNotNull(groupModel)

        groupModelRepository.persistRemovedGroup(groupModel.groupIdentity)
        assertNull(groupModel.data.value)

        assertUnsuccessfulLeave(
            groupModel,
            GroupLeaveIntent.LEAVE,
            ReflectionExpectation.REFLECTION_SUCCESS,
        )
    }

    @Test
    fun testLeaveRemovedGroupNonMd() = runTest {
        val groupModel =
            groupModelRepository.getByGroupIdentity(initialKickedGroupModelData.groupIdentity)
        assertNotNull(groupModel)

        groupModelRepository.persistRemovedGroup(groupModel.groupIdentity)
        assertNull(groupModel.data.value)

        assertUnsuccessfulLeave(
            groupModel,
            GroupLeaveIntent.LEAVE,
            ReflectionExpectation.REFLECTION_SKIPPED,
        )
    }

    @Test
    fun testLeaveAndRemoveRemovedGroupMd() = runTest {
        val groupModel =
            groupModelRepository.getByGroupIdentity(initialKickedGroupModelData.groupIdentity)
        assertNotNull(groupModel)

        groupModelRepository.persistRemovedGroup(groupModel.groupIdentity)
        assertNull(groupModel.data.value)

        assertUnsuccessfulLeave(
            groupModel,
            GroupLeaveIntent.LEAVE_AND_REMOVE,
            ReflectionExpectation.REFLECTION_SUCCESS,
        )
    }

    @Test
    fun testLeaveAndRemoveRemovedGroupNonMd() = runTest {
        val groupModel =
            groupModelRepository.getByGroupIdentity(initialKickedGroupModelData.groupIdentity)
        assertNotNull(groupModel)

        groupModelRepository.persistRemovedGroup(groupModel.groupIdentity)
        assertNull(groupModel.data.value)

        assertUnsuccessfulLeave(
            groupModel,
            GroupLeaveIntent.LEAVE_AND_REMOVE,
            ReflectionExpectation.REFLECTION_SKIPPED,
        )
    }

    private suspend fun assertSuccessfulLeave(
        groupModel: GroupModel,
        intent: GroupLeaveIntent,
        reflectionExpectation: ReflectionExpectation,
    ) {
        assertTrue {
            runGroupLeave(groupModel, intent, reflectionExpectation)
        }

        when (intent) {
            GroupLeaveIntent.LEAVE -> assertEquals(UserState.LEFT, groupModel.data.value?.userState)
            GroupLeaveIntent.LEAVE_AND_REMOVE -> assertNull(groupModel.data.value)
        }
    }

    private suspend fun assertUnsuccessfulLeave(
        groupModel: GroupModel,
        intent: GroupLeaveIntent,
        reflectionExpectation: ReflectionExpectation,
    ) {
        val groupModelDataBefore = groupModel.data.value
        assertFalse {
            runGroupLeave(groupModel, intent, reflectionExpectation)
        }
        val groupModelDataAfter = groupModel.data.value
        // Assert that the group model has not changed
        assertEquals(groupModelDataBefore, groupModelDataAfter)
    }

    private suspend fun runGroupLeave(
        groupModel: GroupModel,
        intent: GroupLeaveIntent,
        reflectionExpectation: ReflectionExpectation,
    ): Boolean {
        val groupModelData = groupModel.data.value

        // Prepare task manager and group flow dispatcher
        val taskManager = ControlledTaskManager(
            getExpectedTaskAssertions(groupModelData, intent, reflectionExpectation),
        )
        val groupFlowDispatcher = getGroupFlowDispatcher(
            reflectionExpectation.setupConfig,
            taskManager,
        )

        // Run leave group flow
        val result = groupFlowDispatcher.runLeaveGroupFlow(
            null,
            intent,
            groupModel,
        ).await()

        taskManager.pendingTaskAssertions.size.let { size ->
            if (size > 0) {
                fail("There are $size pending task assertions left")
            }
        }

        return result
    }

    private fun getExpectedTaskAssertions(
        groupModelData: GroupModelData?,
        intent: GroupLeaveIntent,
        reflectionExpectation: ReflectionExpectation,
    ): MutableList<(Task<*, TaskCodec>) -> Unit> {
        if (groupModelData == null ||
            groupModelData.groupIdentity.creatorIdentity == myContact.identity ||
            groupModelData.userState != UserState.MEMBER
        ) {
            return mutableListOf()
        }

        val scheduledTaskAssertions: MutableList<(Task<*, TaskCodec>) -> Unit> = mutableListOf()
        // If multi device is enabled, then we expect a reflection
        if (reflectionExpectation.setupConfig == SetupConfig.MULTI_DEVICE_ENABLED) {
            scheduledTaskAssertions.add { task ->
                when (intent) {
                    GroupLeaveIntent.LEAVE -> assertIs<ReflectLocalGroupLeaveOrDisband>(task)
                    GroupLeaveIntent.LEAVE_AND_REMOVE -> assertIs<ReflectGroupSyncDeleteTask>(task)
                }
            }
        }

        // If the reflection fails, we do not expect a task that sends out csp messages
        if (reflectionExpectation != ReflectionExpectation.REFLECTION_FAIL) {
            scheduledTaskAssertions.add { task ->
                assertIs<OutgoingGroupLeaveTask>(task)
            }
        }

        return scheduledTaskAssertions
    }
}

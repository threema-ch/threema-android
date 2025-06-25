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
import ch.threema.app.groupflows.GroupFlowResult
import ch.threema.app.tasks.ReflectGroupSyncDeleteTask
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
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.fail
import kotlinx.coroutines.test.runTest

@DangerousTest
class RemoveGroupFlowTest : GroupFlowTest() {
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

    private val myInitialLeftGroupModelData = myInitialGroupModelData.copy(
        groupIdentity = GroupIdentity(myContact.identity, 43),
        name = "MyLeftGroup",
        userState = UserState.LEFT,
    )

    private val initialGroupModelData = myInitialGroupModelData.copy(
        groupIdentity = GroupIdentity(initialContactData.identity, 44),
        name = "MemberGroup",
        userState = UserState.MEMBER,
    )

    private val initialLeftGroupModelData = myInitialGroupModelData.copy(
        groupIdentity = GroupIdentity(initialContactData.identity, 45),
        name = "LeftGroup",
        userState = UserState.LEFT,
    )

    @BeforeTest
    fun setup() {
        clearDatabaseAndCaches(serviceManager)

        assert(myContact.identity == TestHelpers.ensureIdentity(serviceManager))

        // Note that we use from sync to prevent any reflection. This is only acceptable in tests.
        contactModelRepository.createFromSync(initialContactData)
        groupModelRepository.apply {
            createFromSync(myInitialGroupModelData)
            createFromSync(myInitialLeftGroupModelData)
            createFromSync(initialGroupModelData)
            createFromSync(initialLeftGroupModelData)
        }
    }

    /* Tests where the group can be removed. */

    /**
     * Test that a left group where the user is the creator (a disbanded group) can be removed.
     */
    @Test
    fun testRemoveMyLeftGroupMd() = runTest {
        val groupModel =
            groupModelRepository.getByGroupIdentity(myInitialLeftGroupModelData.groupIdentity)
        assertNotNull(groupModel)
        assertSuccessfulRemove(
            groupModel,
            ReflectionExpectation.REFLECTION_SUCCESS,
        )
    }

    /**
     * Test that a left group where the user is the creator (a disbanded group) can be removed.
     */
    @Test
    fun testRemoveMyLeftGroupNonMd() = runTest {
        val groupModel =
            groupModelRepository.getByGroupIdentity(myInitialLeftGroupModelData.groupIdentity)
        assertNotNull(groupModel)
        assertSuccessfulRemove(
            groupModel,
            ReflectionExpectation.REFLECTION_SKIPPED,
        )
    }

    /**
     * Test that a left group where the user is not the creator can be removed.
     */
    @Test
    fun testRemoveLeftGroupMd() = runTest {
        val groupModel =
            groupModelRepository.getByGroupIdentity(initialLeftGroupModelData.groupIdentity)
        assertNotNull(groupModel)
        assertSuccessfulRemove(
            groupModel,
            ReflectionExpectation.REFLECTION_SUCCESS,
        )
    }

    /**
     * Test that a left group where the user is not the creator can be removed.
     */
    @Test
    fun testRemoveLeftGroupNonMd() = runTest {
        val groupModel =
            groupModelRepository.getByGroupIdentity(initialLeftGroupModelData.groupIdentity)
        assertNotNull(groupModel)
        assertSuccessfulRemove(
            groupModel,
            ReflectionExpectation.REFLECTION_SKIPPED,
        )
    }

    /* Tests where the group cannot be removed because the user is still a member */

    /**
     * Test that a group where the user is member (and creator) cannot be removed.
     */
    @Test
    fun testRemoveMyActiveGroupMd() = runTest {
        val groupModel =
            groupModelRepository.getByGroupIdentity(myInitialGroupModelData.groupIdentity)
        assertNotNull(groupModel)
        assertUnsuccessfulRemove(
            groupModel,
            ReflectionExpectation.REFLECTION_SUCCESS,
        )
    }

    /**
     * Test that a group where the user is member (and creator) cannot be removed.
     */
    @Test
    fun testRemoveMyActiveGroupNonMd() = runTest {
        val groupModel =
            groupModelRepository.getByGroupIdentity(myInitialGroupModelData.groupIdentity)
        assertNotNull(groupModel)
        assertUnsuccessfulRemove(
            groupModel,
            ReflectionExpectation.REFLECTION_SKIPPED,
        )
    }

    /**
     * Test that a group where the user is member (and not the creator) cannot be removed.
     */
    @Test
    fun testRemoveActiveGroupMd() = runTest {
        val groupModel =
            groupModelRepository.getByGroupIdentity(initialGroupModelData.groupIdentity)
        assertNotNull(groupModel)
        assertUnsuccessfulRemove(
            groupModel,
            ReflectionExpectation.REFLECTION_SUCCESS,
        )
    }

    /**
     * Test that a group where the user is member (and not the creator) cannot be removed.
     */
    @Test
    fun testRemoveActiveGroupNonMd() = runTest {
        val groupModel =
            groupModelRepository.getByGroupIdentity(initialGroupModelData.groupIdentity)
        assertNotNull(groupModel)
        assertUnsuccessfulRemove(
            groupModel,
            ReflectionExpectation.REFLECTION_SKIPPED,
        )
    }

    @Test
    fun shouldNotRemoveGroupWhenMdActiveButConnectionIsLost() = runTest {
        // arrange
        val groupModel = groupModelRepository.getByGroupIdentity(myInitialLeftGroupModelData.groupIdentity)
        val taskManager = ControlledTaskManager(emptyList())
        val groupFlowDispatcher = getGroupFlowDispatcher(
            setupConfig = SetupConfig.MULTI_DEVICE_ENABLED,
            taskManager = taskManager,
            connection = ConnectionDisconnected,
        )

        // act
        val groupFlowResult = groupFlowDispatcher
            .runRemoveGroupFlow(groupModel!!)
            .await()

        // assert
        assertIs<GroupFlowResult.Failure.Network>(groupFlowResult)
    }

    private suspend fun assertSuccessfulRemove(
        groupModel: GroupModel,
        reflectionExpectation: ReflectionExpectation,
    ) {
        assertIs<GroupFlowResult.Success>(
            runGroupRemove(groupModel, reflectionExpectation),
        )
        assertNull(groupModel.data.value)
    }

    private suspend fun assertUnsuccessfulRemove(
        groupModel: GroupModel,
        reflectionExpectation: ReflectionExpectation,
    ) {
        assertIs<GroupFlowResult.Failure>(
            runGroupRemove(groupModel, reflectionExpectation),
        )
        assertNotNull(groupModel.data.value)
    }

    private suspend fun runGroupRemove(
        groupModel: GroupModel,
        reflectionExpectation: ReflectionExpectation,
    ): GroupFlowResult {
        val groupModelData = groupModel.data.value

        // Prepare task manager and group flow dispatcher
        val taskManager = ControlledTaskManager(
            getExpectedTaskAssertions(groupModelData, reflectionExpectation),
        )
        val groupFlowDispatcher = getGroupFlowDispatcher(
            reflectionExpectation.setupConfig,
            taskManager,
        )

        // Run remove group flow
        val groupFlowResult = groupFlowDispatcher.runRemoveGroupFlow(groupModel).await()

        taskManager.pendingTaskAssertions.size.let { size ->
            if (size > 0) {
                fail("There are $size pending task assertions left")
            }
        }

        return groupFlowResult
    }

    private fun getExpectedTaskAssertions(
        groupModelData: GroupModelData?,
        reflectionExpectation: ReflectionExpectation,
    ): MutableList<(Task<*, TaskCodec>) -> Unit> {
        if (groupModelData == null || groupModelData.userState == UserState.MEMBER) {
            return mutableListOf()
        }

        val scheduledTaskAssertions: MutableList<(Task<*, TaskCodec>) -> Unit> = mutableListOf()
        // If multi device is enabled, then we expect a reflection task
        if (reflectionExpectation.setupConfig == SetupConfig.MULTI_DEVICE_ENABLED) {
            scheduledTaskAssertions.add { task ->
                assertIs<ReflectGroupSyncDeleteTask>(task)
            }
        }

        return scheduledTaskAssertions
    }
}

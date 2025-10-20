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

import android.text.format.DateUtils
import ch.threema.app.DangerousTest
import ch.threema.app.ThreemaApplication
import ch.threema.app.groupflows.GroupCreateProperties
import ch.threema.app.groupflows.GroupFlowResult
import ch.threema.app.tasks.GroupCreateTask
import ch.threema.app.tasks.ReflectGroupSyncCreateTask
import ch.threema.app.testutils.TestHelpers
import ch.threema.app.testutils.TestHelpers.TestContact
import ch.threema.app.testutils.clearDatabaseAndCaches
import ch.threema.data.models.ContactModelData
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
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * This test asserts that the corresponding tasks have been scheduled when running the create group
 * flow.
 */
@DangerousTest
class CreateGroupFlowTest : GroupFlowTest() {
    private val myContact: TestContact = TestHelpers.TEST_CONTACT

    private val initialContactModelData = ContactModelData(
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

    @BeforeTest
    fun setup() {
        clearDatabaseAndCaches(serviceManager)

        assert(myContact.identity == TestHelpers.ensureIdentity(serviceManager))

        // Note that we use from sync to prevent any reflection. This is only acceptable in tests.
        serviceManager.modelRepositories.contacts.createFromSync(initialContactModelData)
    }

    @Test
    fun testKnownMemberMD() = runTest {
        val memberIdentity = initialContactModelData.identity

        // Assert that the member exists as a contact
        assertNotNull(contactModelRepository.getByIdentity(memberIdentity))

        testAndAssertSuccessfulGroupCreation(
            GroupCreateProperties(
                name = "Test",
                profilePicture = null,
                members = setOf(memberIdentity),
            ),
            ReflectionExpectation.REFLECTION_SUCCESS,
        )
    }

    @Test
    fun testKnownMemberNonMD() = runTest {
        val memberIdentity = initialContactModelData.identity

        // Assert that the member exists as a contact
        assertNotNull(contactModelRepository.getByIdentity(memberIdentity))

        testAndAssertSuccessfulGroupCreation(
            GroupCreateProperties(
                name = "Test",
                profilePicture = null,
                members = setOf(memberIdentity),
            ),
            ReflectionExpectation.REFLECTION_SKIPPED,
        )
    }

    @Test
    fun testNotesGroupMD() = runTest {
        testAndAssertSuccessfulGroupCreation(
            GroupCreateProperties(
                name = "Test",
                profilePicture = null,
                members = emptySet(),
            ),
            ReflectionExpectation.REFLECTION_SUCCESS,
        )
    }

    @Test
    fun testNotesGroupNonMD() = runTest {
        testAndAssertSuccessfulGroupCreation(
            GroupCreateProperties(
                name = "Test",
                profilePicture = null,
                members = emptySet(),
            ),
            ReflectionExpectation.REFLECTION_SKIPPED,
        )
    }

    @Test
    fun testUnknownMemberMD() = runTest {
        val unknownIdentity = "0UNKNOWN"

        // Assert that the identity is really unknown
        assertNull(contactModelRepository.getByIdentity(unknownIdentity))

        val groupFlowResult: GroupFlowResult = testGroupCreation(
            GroupCreateProperties(
                name = "Test",
                profilePicture = null,
                members = setOf(initialContactModelData.identity, unknownIdentity),
            ),
            ReflectionExpectation.REFLECTION_FAIL,
        )

        assertIs<GroupFlowResult.Failure.Other>(groupFlowResult)
    }

    @Test
    fun shouldNotCreateGroupWhenMdActiveButConnectionIsLost() = runTest {
        // arrange
        val taskManager = ControlledTaskManager(emptyList())
        val groupFlowDispatcher = getGroupFlowDispatcher(
            setupConfig = SetupConfig.MULTI_DEVICE_ENABLED,
            taskManager = taskManager,
            connection = ConnectionDisconnected,
        )

        // act
        val groupFlowResult: GroupFlowResult = groupFlowDispatcher.runCreateGroupFlow(
            ThreemaApplication.getAppContext(),
            GroupCreateProperties(
                name = "Test",
                profilePicture = null,
                members = setOf(initialContactModelData.identity),
            ),
        ).await()

        // assert
        assertTrue(groupFlowResult is GroupFlowResult.Failure.Network)
    }

    private suspend fun testAndAssertSuccessfulGroupCreation(
        groupCreateProperties: GroupCreateProperties,
        reflectionExpectation: ReflectionExpectation,
    ): GroupModel {
        val createGroupFlowResult = testGroupCreation(groupCreateProperties, reflectionExpectation)
        assertTrue(createGroupFlowResult is GroupFlowResult.Success)
        val groupModel = createGroupFlowResult.groupModel
        groupModel.assertCreatedFrom(groupCreateProperties)
        groupModel.assertNewGroup()
        return groupModel
    }

    private suspend fun testGroupCreation(
        groupCreateProperties: GroupCreateProperties,
        reflectionExpectation: ReflectionExpectation,
    ): GroupFlowResult {
        val scheduledTaskAssertions: MutableList<(Task<*, TaskCodec>) -> Unit> = mutableListOf()

        // If multi device is enabled, then we expect the ReflectGroupSyncCreateTask to be scheduled
        if (reflectionExpectation.setupConfig == SetupConfig.MULTI_DEVICE_ENABLED) {
            scheduledTaskAssertions.add { task ->
                assertIs<ReflectGroupSyncCreateTask>(task)
            }
        }

        // If the group is not a notes group, we expect that a task is scheduled that sends out csp
        // messages.
        val isNotesGroup = groupCreateProperties.members.isEmpty()
        val reflectionFails = reflectionExpectation == ReflectionExpectation.REFLECTION_FAIL
        if (!isNotesGroup && !reflectionFails) {
            scheduledTaskAssertions.add { task ->
                assertIs<GroupCreateTask>(task)
            }
        }

        // Prepare task manager and group flow dispatcher
        val taskManager = ControlledTaskManager(scheduledTaskAssertions)
        val groupFlowDispatcher = getGroupFlowDispatcher(
            reflectionExpectation.setupConfig,
            taskManager,
            ConnectionLoggedIn,
        )

        // Run create group flow
        val groupFlowResult: GroupFlowResult = groupFlowDispatcher.runCreateGroupFlow(
            ThreemaApplication.getAppContext(),
            groupCreateProperties,
        ).await()

        // Assert that all expected tasks have been scheduled
        assert(taskManager.pendingTaskAssertions.isEmpty()) {
            "There are ${taskManager.pendingTaskAssertions} pending task assertions left"
        }

        return groupFlowResult
    }

    private fun GroupModel?.assertCreatedFrom(groupCreateProperties: GroupCreateProperties) {
        assertNotNull(this)
        data.assertCreatedFrom(groupCreateProperties)
    }

    private fun GroupModelData?.assertCreatedFrom(groupCreateProperties: GroupCreateProperties) {
        assertNotNull(this)
        assertEquals(groupCreateProperties.name, name)
        assertEquals(groupCreateProperties.members, otherMembers)
    }

    private fun GroupModel?.assertNewGroup() {
        assertNotNull(this)
        data.assertNewGroup()
    }

    private fun GroupModelData?.assertNewGroup() {
        assertNotNull(this)
        assertEquals(UserState.MEMBER, userState)
        assertEquals(lastUpdate, createdAt)
        assertTrue {
            val current = Date().time
            val aWhileAgo = current - DateUtils.SECOND_IN_MILLIS * 30
            createdAt.time in aWhileAgo..<current
        }
        assertFalse(isArchived)
        assertNull(groupDescription)
        assertNull(groupDescriptionChangedAt)
    }
}

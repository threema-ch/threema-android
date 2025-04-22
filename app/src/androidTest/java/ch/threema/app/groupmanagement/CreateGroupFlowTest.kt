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
import ch.threema.app.groupflows.ProfilePicture
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

    @Before
    fun setup() {
        clearDatabaseAndCaches(serviceManager)

        assert(myContact.identity == TestHelpers.ensureIdentity(serviceManager))

        // Note that we use from sync to prevent any reflection. This is only acceptable in tests.
        serviceManager.modelRepositories.contacts.createFromSync(initialContactModelData)
    }

    @Test
    fun testKnownMember() = runTest {
        val memberIdentity = initialContactModelData.identity

        // Assert that the member exists as a contact
        assertNotNull(contactModelRepository.getByIdentity(memberIdentity))

        testAndAssertSuccessfulGroupCreation(
            GroupCreateProperties(
                name = "Test",
                profilePicture = ProfilePicture(null as ByteArray?),
                members = setOf(memberIdentity),
            ),
            ReflectionExpectation.REFLECTION_SUCCESS,
        )
    }

    @Test
    fun testKnownMemberNonMd() = runTest {
        val memberIdentity = initialContactModelData.identity

        // Assert that the member exists as a contact
        assertNotNull(contactModelRepository.getByIdentity(memberIdentity))

        testAndAssertSuccessfulGroupCreation(
            GroupCreateProperties(
                name = "Test",
                profilePicture = ProfilePicture(null as ByteArray?),
                members = setOf(memberIdentity),
            ),
            ReflectionExpectation.REFLECTION_SKIPPED,
        )
    }

    @Test
    fun testNotesGroupMd() = runTest {
        testAndAssertSuccessfulGroupCreation(
            GroupCreateProperties(
                name = "Test",
                profilePicture = ProfilePicture(null as ByteArray?),
                members = emptySet(),
            ),
            ReflectionExpectation.REFLECTION_SUCCESS,
        )
    }

    @Test
    fun testNotesGroupNonMd() = runTest {
        testAndAssertSuccessfulGroupCreation(
            GroupCreateProperties(
                name = "Test",
                profilePicture = ProfilePicture(null as ByteArray?),
                members = emptySet(),
            ),
            ReflectionExpectation.REFLECTION_SKIPPED,
        )
    }

    @Test
    fun testUnknownMemberMd() = runTest {
        val unknownIdentity = "0UNKNOWN"

        // Assert that the identity is really unknown
        assertNull(contactModelRepository.getByIdentity(unknownIdentity))

        val groupModel = testGroupCreation(
            GroupCreateProperties(
                name = "Test",
                profilePicture = ProfilePicture(null as ByteArray?),
                members = setOf(initialContactModelData.identity, unknownIdentity),
            ),
            ReflectionExpectation.REFLECTION_FAIL,
        )

        assertNull(groupModel)
    }

    private suspend fun testAndAssertSuccessfulGroupCreation(
        groupCreateProperties: GroupCreateProperties,
        reflectionExpectation: ReflectionExpectation,
    ): GroupModel? {
        val groupModel = testGroupCreation(groupCreateProperties, reflectionExpectation)
        groupModel.assertCreatedFrom(groupCreateProperties)
        groupModel.assertNewGroup()
        return groupModel
    }

    private suspend fun testGroupCreation(
        groupCreateProperties: GroupCreateProperties,
        reflectionExpectation: ReflectionExpectation,
    ): GroupModel? {
        val scheduledTaskAssertions: MutableList<(Task<*, TaskCodec>) -> Unit> = mutableListOf()
        // If multi device is enabled, then we expect a reflection
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
        )

        // Run create group flow
        val groupModel = groupFlowDispatcher.runCreateGroupFlow(
            null,
            ThreemaApplication.getAppContext(),
            groupCreateProperties,
        ).await()

        // Assert that all expected tasks have been scheduled
        taskManager.pendingTaskAssertions.size.let { size ->
            if (size > 0) {
                fail("There are $size pending task assertions left")
            }
        }

        return groupModel
    }

    private fun GroupModel?.assertCreatedFrom(groupCreateProperties: GroupCreateProperties) {
        assertNotNull(this)
        data.value.assertCreatedFrom(groupCreateProperties)
    }

    private fun GroupModelData?.assertCreatedFrom(groupCreateProperties: GroupCreateProperties) {
        assertNotNull(this)
        assertEquals(groupCreateProperties.name, name)
        assertEquals(groupCreateProperties.members, otherMembers)
    }

    private fun GroupModel?.assertNewGroup() {
        assertNotNull(this)
        data.value.assertNewGroup()
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

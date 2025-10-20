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
import ch.threema.app.groupflows.GroupChanges
import ch.threema.app.groupflows.GroupFlowResult
import ch.threema.app.tasks.GroupUpdateTask
import ch.threema.app.tasks.ReflectLocalGroupUpdate
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
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlinx.coroutines.test.runTest

@DangerousTest
class UpdateGroupFlowTest : GroupFlowTest() {
    private val myContact: TestHelpers.TestContact = TestHelpers.TEST_CONTACT

    /**
     * A contact that is stored in the database. It is the creator of [initialGroupModelData].
     */
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

    /**
     * A contact that is stored in the database. An initial group member of
     * [myInitialGroupModelData].
     */
    private val initialGroupMemberData = ContactModelData(
        identity = "TESTTEST",
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
        otherMembers = setOf(initialGroupMemberData.identity),
        userState = UserState.MEMBER,
        notificationTriggerPolicyOverride = null,
    )

    private val initialGroupModelData = GroupModelData(
        groupIdentity = GroupIdentity(initialContactData.identity, 42),
        name = "ExistingGroup",
        createdAt = Date(),
        synchronizedAt = null,
        lastUpdate = Date(),
        isArchived = false,
        groupDescription = null,
        groupDescriptionChangedAt = null,
        // User is the only member besides from the creator
        otherMembers = emptySet(),
        userState = UserState.MEMBER,
        notificationTriggerPolicyOverride = null,
    )

    @BeforeTest
    fun setup() {
        clearDatabaseAndCaches(serviceManager)

        assert(myContact.identity == TestHelpers.ensureIdentity(serviceManager))

        // Note that we use from sync to prevent any reflection. This is only acceptable in tests.
        contactModelRepository.apply {
            createFromSync(initialContactData)
            createFromSync(initialGroupMemberData)
        }
        groupModelRepository.apply {
            createFromSync(myInitialGroupModelData)
            createFromSync(initialGroupModelData)
        }
    }

    @Test
    fun testGroupNameModificationMd() = runTest {
        val groupModel = groupModelRepository.getByGroupIdentity(myInitialGroupModelData.groupIdentity)
        assertNotNull(groupModel)
        val groupChanges = GroupChanges(
            name = "NewGroupName",
            profilePictureChange = null,
            updatedMembers = myInitialGroupModelData.otherMembers,
            groupModelData = myInitialGroupModelData,
        )

        assertSuccessfulGroupUpdate(
            groupModel,
            groupChanges,
            ReflectionExpectation.REFLECTION_SUCCESS,
        )
    }

    @Test
    fun testGroupNameModificationNonMd() = runTest {
        val groupModel = groupModelRepository.getByGroupIdentity(myInitialGroupModelData.groupIdentity)
        assertNotNull(groupModel)
        val groupChanges = GroupChanges(
            name = "NewGroupName",
            profilePictureChange = null,
            updatedMembers = myInitialGroupModelData.otherMembers,
            groupModelData = myInitialGroupModelData,
        )

        assertSuccessfulGroupUpdate(
            groupModel,
            groupChanges,
            ReflectionExpectation.REFLECTION_SKIPPED,
        )
    }

    @Test
    fun testGroupNameAndAddedMembersModificationMd() = runTest {
        val groupModel = groupModelRepository.getByGroupIdentity(myInitialGroupModelData.groupIdentity)
        assertNotNull(groupModel)

        // Assert that the new member is not yet a member of the group
        assertTrue {
            groupModel.data?.otherMembers?.contains(initialContactData.identity) == false
        }

        val groupChanges = GroupChanges(
            name = "NewGroupName",
            profilePictureChange = null,
            updatedMembers = myInitialGroupModelData.otherMembers + initialContactData.identity,
            groupModelData = myInitialGroupModelData,
        )

        assertSuccessfulGroupUpdate(
            groupModel,
            groupChanges,
            ReflectionExpectation.REFLECTION_SUCCESS,
        )

        assertTrue {
            groupModel.data?.otherMembers?.contains(initialContactData.identity) == true
        }
    }

    @Test
    fun testGroupNameAndAddedMembersModificationNonMd() = runTest {
        val groupModel = groupModelRepository.getByGroupIdentity(myInitialGroupModelData.groupIdentity)
        assertNotNull(groupModel)
        val groupChanges = GroupChanges(
            name = "NewGroupName",
            profilePictureChange = null,
            updatedMembers = myInitialGroupModelData.otherMembers + initialContactData.identity,
            groupModelData = myInitialGroupModelData,
        )

        assertSuccessfulGroupUpdate(
            groupModel,
            groupChanges,
            ReflectionExpectation.REFLECTION_SKIPPED,
        )

        assertTrue {
            groupModel.data?.otherMembers?.contains(initialContactData.identity) == true
        }
    }

    @Test
    fun testGroupNameAndRemovedMembersModificationMd() = runTest {
        val groupModel = groupModelRepository.getByGroupIdentity(myInitialGroupModelData.groupIdentity)
        assertNotNull(groupModel)
        val groupChanges = GroupChanges(
            name = "NewGroupName",
            profilePictureChange = null,
            updatedMembers = emptySet(),
            groupModelData = myInitialGroupModelData,
        )

        assertSuccessfulGroupUpdate(
            groupModel,
            groupChanges,
            ReflectionExpectation.REFLECTION_SUCCESS,
        )

        assertTrue { groupModel.data?.otherMembers?.isEmpty() == true }
    }

    @Test
    fun testGroupNameAndRemovedMembersModificationNonMd() = runTest {
        val groupModel = groupModelRepository.getByGroupIdentity(myInitialGroupModelData.groupIdentity)
        assertNotNull(groupModel)
        val groupChanges = GroupChanges(
            name = "NewGroupName",
            profilePictureChange = null,
            updatedMembers = emptySet(),
            groupModelData = myInitialGroupModelData,
        )

        assertSuccessfulGroupUpdate(
            groupModel,
            groupChanges,
            ReflectionExpectation.REFLECTION_SKIPPED,
        )

        assertTrue { groupModel.data?.otherMembers?.isEmpty() == true }
    }

    @Test
    fun testModificationOfDeletedGroupMd() = runTest {
        val groupModel = groupModelRepository.getByGroupIdentity(myInitialGroupModelData.groupIdentity)
        assertNotNull(groupModel)
        val groupChanges = GroupChanges(
            name = "NewGroupName",
            profilePictureChange = null,
            updatedMembers = emptySet(),
            groupModelData = myInitialGroupModelData,
        )

        // Delete the group before updating it
        groupModelRepository.persistRemovedGroup(groupModel.groupIdentity)
        assertNull(groupModel.data)

        assertUnsuccessfulGroupUpdate(
            groupModel,
            groupChanges,
            ReflectionExpectation.REFLECTION_SUCCESS,
        )

        assertNull(groupModel.data)
    }

    @Test
    fun testModificationOfDeletedGroupNonMd() = runTest {
        val groupModel = groupModelRepository.getByGroupIdentity(myInitialGroupModelData.groupIdentity)
        assertNotNull(groupModel)
        val groupChanges = GroupChanges(
            name = "NewGroupName",
            profilePictureChange = null,
            updatedMembers = emptySet(),
            groupModelData = myInitialGroupModelData,
        )

        // Delete the group before updating it
        groupModelRepository.persistRemovedGroup(groupModel.groupIdentity)
        assertNull(groupModel.data)

        assertUnsuccessfulGroupUpdate(
            groupModel,
            groupChanges,
            ReflectionExpectation.REFLECTION_SKIPPED,
        )

        assertNull(groupModel.data)
    }

    @Test
    fun testGroupNameAndAddedMembersModificationOfForeignGroupMd() = runTest {
        val groupModel = groupModelRepository.getByGroupIdentity(initialGroupModelData.groupIdentity)
        assertNotNull(groupModel)
        val groupChanges = GroupChanges(
            name = "NewGroupName",
            profilePictureChange = null,
            updatedMembers = setOf(initialGroupMemberData.identity),
            groupModelData = initialGroupModelData,
        )

        assertUnsuccessfulGroupUpdate(
            groupModel,
            groupChanges,
            ReflectionExpectation.REFLECTION_SKIPPED,
        )

        assertTrue {
            groupModel.data?.otherMembers?.contains(initialGroupMemberData.identity) == false
        }
    }

    @Test
    fun testGroupNameAndAddedMembersModificationOfForeignGroupNonMd() = runTest {
        val groupModel = groupModelRepository.getByGroupIdentity(initialGroupModelData.groupIdentity)
        assertNotNull(groupModel)
        val groupChanges = GroupChanges(
            name = "NewGroupName",
            profilePictureChange = null,
            updatedMembers = setOf(initialGroupMemberData.identity),
            groupModelData = initialGroupModelData,
        )

        assertUnsuccessfulGroupUpdate(
            groupModel,
            groupChanges,
            ReflectionExpectation.REFLECTION_SKIPPED,
        )

        assertTrue {
            groupModel.data?.otherMembers?.contains(initialGroupMemberData.identity) == false
        }
    }

    @Test
    fun shouldNotUpdateGroupWhenMdActiveButConnectionIsLost() = runTest {
        // arrange
        val groupModel = groupModelRepository.getByGroupIdentity(myInitialGroupModelData.groupIdentity)
        val taskManager = ControlledTaskManager(emptyList())
        val groupFlowDispatcher = getGroupFlowDispatcher(
            setupConfig = SetupConfig.MULTI_DEVICE_ENABLED,
            taskManager = taskManager,
            connection = ConnectionDisconnected,
        )
        val groupChangesNewName = GroupChanges(
            name = "NewGroupName",
            profilePictureChange = null,
            updatedMembers = myInitialGroupModelData.otherMembers,
            groupModelData = myInitialGroupModelData,
        )

        // act
        val groupFlowResult = groupFlowDispatcher
            .runUpdateGroupFlow(groupChangesNewName, groupModel!!)
            .await()

        // assert
        assertIs<GroupFlowResult.Failure.Network>(groupFlowResult)
    }

    private suspend fun assertSuccessfulGroupUpdate(
        groupModel: GroupModel,
        groupChanges: GroupChanges,
        reflectionExpectation: ReflectionExpectation,
    ) {
        val groupFlowResult = runGroupUpdate(
            groupModel = groupModel,
            groupChanges = groupChanges,
            reflectionExpectation = reflectionExpectation,
            successExpected = true,
        )
        assertIs<GroupFlowResult.Success>(groupFlowResult)

        val groupModelData = groupModel.data
        assertNotNull(groupModelData)
        groupModelData.assertChangesApplied(groupChanges)
    }

    private suspend fun assertUnsuccessfulGroupUpdate(
        groupModel: GroupModel,
        groupChanges: GroupChanges,
        reflectionExpectation: ReflectionExpectation,
    ) {
        val groupFlowResult = runGroupUpdate(
            groupModel = groupModel,
            groupChanges = groupChanges,
            reflectionExpectation = reflectionExpectation,
            successExpected = false,
        )
        assertIs<GroupFlowResult.Failure>(groupFlowResult)
    }

    private suspend fun runGroupUpdate(
        groupModel: GroupModel,
        groupChanges: GroupChanges,
        reflectionExpectation: ReflectionExpectation,
        successExpected: Boolean,
    ): GroupFlowResult {
        val groupModelData = groupModel.data

        // Prepare task manager and group flow dispatcher
        val taskManager = ControlledTaskManager(
            getExpectedTaskAssertions(groupModelData, reflectionExpectation, successExpected),
        )
        val groupFlowDispatcher = getGroupFlowDispatcher(
            reflectionExpectation.setupConfig,
            taskManager,
        )

        // Run update group flow
        val groupFlowResult = groupFlowDispatcher
            .runUpdateGroupFlow(groupChanges, groupModel)
            .await()

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
        successExpected: Boolean,
    ): MutableList<(Task<*, TaskCodec>) -> Unit> {
        if (groupModelData == null) {
            return mutableListOf()
        }

        val scheduledTaskAssertions: MutableList<(Task<*, TaskCodec>) -> Unit> = mutableListOf()
        // If multi device is enabled, then we expect a reflection
        if (reflectionExpectation.setupConfig == SetupConfig.MULTI_DEVICE_ENABLED) {
            scheduledTaskAssertions.add { task ->
                assertIs<ReflectLocalGroupUpdate>(task)
            }
        }

        // If the group is not a notes group, we expect that a task is scheduled that sends out csp
        // messages.
        val isNotesGroup =
            groupModelData.otherMembers.isEmpty() && groupModelData.groupIdentity.creatorIdentity == myContact.identity
        val reflectionFails = reflectionExpectation == ReflectionExpectation.REFLECTION_FAIL
        if (successExpected && !isNotesGroup && !reflectionFails) {
            scheduledTaskAssertions.add { task ->
                assertIs<GroupUpdateTask>(task)
            }
        }

        return scheduledTaskAssertions
    }

    /**
     * Assert that the [groupChanges] have been applied to the group model data.
     */
    private fun GroupModelData.assertChangesApplied(groupChanges: GroupChanges) {
        groupChanges.name?.let { newName ->
            assertEquals(newName, this.name)
        }
        assertContainsAll(this.otherMembers, groupChanges.addMembers)
        assertContainsNone(this.otherMembers, groupChanges.removeMembers)
    }

    private fun <T> assertContainsAll(container: Iterable<T>, elements: Iterable<T>) {
        elements.forEach {
            assertContains(container, it)
        }
    }

    private fun <T> assertContainsNone(container: Iterable<T>, elements: Iterable<T>) {
        val intersection = container.intersect(elements.toSet())
        assertTrue(message = "Elements contained unexpectedly") {
            intersection.isEmpty()
        }
    }
}

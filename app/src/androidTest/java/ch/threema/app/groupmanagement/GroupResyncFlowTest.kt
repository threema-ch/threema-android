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
import ch.threema.app.tasks.ActiveGroupStateResyncTask
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
import kotlin.test.fail
import kotlinx.coroutines.test.runTest

@DangerousTest
class GroupResyncFlowTest : GroupFlowTest() {
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

    @BeforeTest
    fun setup() {
        clearDatabaseAndCaches(serviceManager)

        assert(myContact.identity == TestHelpers.ensureIdentity(serviceManager))

        // Note that we use from sync to prevent any reflection. This is only acceptable in tests.
        contactModelRepository.createFromSync(initialContactData)
        groupModelRepository.apply {
            createFromSync(myInitialGroupModelData)
            createFromSync(initialGroupModelData)
        }
    }

    @Test
    fun testGroupResyncMd() = runTest {
        val groupModel =
            groupModelRepository.getByGroupIdentity(myInitialGroupModelData.groupIdentity)
        assertNotNull(groupModel)
        assertSuccessfulGroupResync(groupModel, SetupConfig.MULTI_DEVICE_ENABLED)
    }

    @Test
    fun testGroupResyncNonMd() = runTest {
        val groupModel =
            groupModelRepository.getByGroupIdentity(myInitialGroupModelData.groupIdentity)
        assertNotNull(groupModel)
        assertSuccessfulGroupResync(groupModel, SetupConfig.MULTI_DEVICE_DISABLED)
    }

    @Test
    fun testForeignGroupResyncMd() = runTest {
        val groupModel =
            groupModelRepository.getByGroupIdentity(initialGroupModelData.groupIdentity)
        assertNotNull(groupModel)
        assertUnsuccessfulGroupResync(groupModel, SetupConfig.MULTI_DEVICE_ENABLED)
    }

    @Test
    fun testForeignGroupResyncNonMd() = runTest {
        val groupModel =
            groupModelRepository.getByGroupIdentity(initialGroupModelData.groupIdentity)
        assertNotNull(groupModel)
        assertUnsuccessfulGroupResync(groupModel, SetupConfig.MULTI_DEVICE_DISABLED)
    }

    private suspend fun assertSuccessfulGroupResync(
        groupModel: GroupModel,
        setupConfig: SetupConfig,
    ) {
        assertIs<GroupFlowResult.Success>(
            runGroupResync(groupModel, setupConfig),
        )
    }

    private suspend fun assertUnsuccessfulGroupResync(
        groupModel: GroupModel,
        setupConfig: SetupConfig,
    ) {
        assertIs<GroupFlowResult.Failure>(
            runGroupResync(groupModel, setupConfig),
        )
    }

    private suspend fun runGroupResync(
        groupModel: GroupModel,
        setupConfig: SetupConfig,
    ): GroupFlowResult {
        val groupModelData = groupModel.data.value

        // Prepare task manager and group flow dispatcher
        val taskManager = ControlledTaskManager(
            getExpectedTaskAssertions(groupModelData),
        )
        val groupFlowDispatcher = getGroupFlowDispatcher(
            setupConfig,
            taskManager,
        )

        // Run group resync flow
        val groupFowResult = groupFlowDispatcher.runGroupResyncFlow(groupModel).await()

        taskManager.pendingTaskAssertions.size.let { size ->
            if (size > 0) {
                fail("There are $size pending task assertions left")
            }
        }

        return groupFowResult
    }

    private fun getExpectedTaskAssertions(groupModelData: GroupModelData?): MutableList<(Task<*, TaskCodec>) -> Unit> {
        if (groupModelData == null ||
            groupModelData.groupIdentity.creatorIdentity != myContact.identity ||
            groupModelData.userState != UserState.MEMBER
        ) {
            return mutableListOf()
        }

        return mutableListOf({ task -> assertIs<ActiveGroupStateResyncTask>(task) })
    }
}

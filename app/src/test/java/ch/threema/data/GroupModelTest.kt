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

package ch.threema.data

import ch.threema.app.managers.CoreServiceManager
import ch.threema.app.multidevice.MultiDeviceManager
import ch.threema.base.crypto.NonceFactory
import ch.threema.base.crypto.NonceStore
import ch.threema.base.utils.now
import ch.threema.data.models.GroupIdentity
import ch.threema.data.models.GroupModel
import ch.threema.data.models.GroupModelData
import ch.threema.data.storage.DatabaseBackend
import ch.threema.domain.taskmanager.QueueSendCompleteListener
import ch.threema.domain.taskmanager.Task
import ch.threema.domain.taskmanager.TaskCodec
import ch.threema.domain.taskmanager.TaskManager
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred

class GroupModelTest {
    private val databaseBackendMock = mockk<DatabaseBackend>()
    private val multiDeviceManagerMock = mockk<MultiDeviceManager>().also {
        every { it.isMultiDeviceActive } returns true
    }
    private val nonceStoreMock = mockk<NonceStore>()
    private val nonceFactory = NonceFactory(nonceStoreMock)
    private val taskManager = object : TaskManager {
        val scheduledTasks = mutableListOf<Task<*, TaskCodec>>()

        override fun <R> schedule(task: Task<R, TaskCodec>): Deferred<R> {
            scheduledTasks.add(task)
            return CompletableDeferred()
        }

        override fun hasPendingTasks(): Boolean = scheduledTasks.isNotEmpty()

        override fun addQueueSendCompleteListener(listener: QueueSendCompleteListener) {
            // Nothing to do
        }

        override fun removeQueueSendCompleteListener(listener: QueueSendCompleteListener) {
            // Nothing to do
        }
    }
    private val coreServiceManagerMock = mockk<CoreServiceManager>().also {
        every { it.taskManager } returns taskManager
        every { it.multiDeviceManager } returns multiDeviceManagerMock
        every { it.nonceFactory } returns nonceFactory
    }

    private fun createTestGroup(): GroupModel {
        val groupIdentity = GroupIdentity("TESTTEST", 42)
        val members = setOf("AAAAAAAA", "BBBBBBBB")
        val now = now()
        return GroupModel(
            groupIdentity,
            GroupModelData(
                groupIdentity = groupIdentity,
                name = "Group",
                createdAt = now,
                synchronizedAt = now,
                lastUpdate = null,
                isArchived = false,
                precomputedColorIndex = 0.toUByte(),
                groupDescription = "Description",
                groupDescriptionChangedAt = now,
                otherMembers = members,
                userState = ch.threema.storage.models.GroupModel.UserState.MEMBER,
                notificationTriggerPolicyOverride = null,
            ),
            databaseBackendMock,
            coreServiceManagerMock,
        )
    }

    @Test
    fun testGroupIdentityToHexString() {
        val identity = "TESTTEST"
        assertEquals("d6ffffffffffffff", GroupIdentity(identity, -42).groupIdHexString)
        assertEquals("ffffffffffffffff", GroupIdentity(identity, -1).groupIdHexString)
        assertEquals("0000000000000000", GroupIdentity(identity, 0).groupIdHexString)
        assertEquals("0100000000000000", GroupIdentity(identity, 1).groupIdHexString)
        assertEquals("2a00000000000000", GroupIdentity(identity, 42).groupIdHexString)
        assertEquals("0000000000000080", GroupIdentity(identity, Long.MIN_VALUE).groupIdHexString)
        assertEquals("ffffffffffffff7f", GroupIdentity(identity, Long.MAX_VALUE).groupIdHexString)
        assertEquals("4ea878fdffffffff", GroupIdentity(identity, -42424242).groupIdHexString)
        assertEquals("b257870200000000", GroupIdentity(identity, 42424242).groupIdHexString)
    }

    @Test
    fun testGroupIdentityToByteArray() {
        val identity = "TESTTEST"
        assertContentEquals(
            byteArrayOf(-42, -1, -1, -1, -1, -1, -1, -1),
            GroupIdentity(identity, -42).groupIdByteArray,
        )
        assertContentEquals(
            byteArrayOf(-1, -1, -1, -1, -1, -1, -1, -1),
            GroupIdentity(identity, -1).groupIdByteArray,
        )
        assertContentEquals(
            byteArrayOf(0, 0, 0, 0, 0, 0, 0, 0),
            GroupIdentity(identity, 0).groupIdByteArray,
        )
        assertContentEquals(
            byteArrayOf(1, 0, 0, 0, 0, 0, 0, 0),
            GroupIdentity(identity, 1).groupIdByteArray,
        )
        assertContentEquals(
            byteArrayOf(42, 0, 0, 0, 0, 0, 0, 0),
            GroupIdentity(identity, 42).groupIdByteArray,
        )
        assertContentEquals(
            byteArrayOf(0, 0, 0, 0, 0, 0, 0, -128),
            GroupIdentity(identity, Long.MIN_VALUE).groupIdByteArray,
        )
        assertContentEquals(
            byteArrayOf(-1, -1, -1, -1, -1, -1, -1, 127),
            GroupIdentity(identity, Long.MAX_VALUE).groupIdByteArray,
        )
        assertContentEquals(
            byteArrayOf(78, -88, 120, -3, -1, -1, -1, -1),
            GroupIdentity(identity, -42424242).groupIdByteArray,
        )
        assertContentEquals(
            byteArrayOf(-78, 87, -121, 2, 0, 0, 0, 0),
            GroupIdentity(identity, 42424242).groupIdByteArray,
        )
    }

    /**
     * Test the construction using the primary constructor.
     *
     * Data is accessed through the `data` state flow.
     */
    @Test
    fun testConstruction() {
        val now = now()
        val groupIdentity = GroupIdentity("TESTTEST", 42)
        val name = "Group"
        val createdAt = now
        val synchronizedAt = now
        val lastUpdate = null
        val deleted = false
        val isArchived = false
        val colorIndex = 0.toUByte()
        val groupDesc = "Description"
        val groupDescChangedAt = now
        val members = setOf("AAAAAAAA", "BBBBBBBB")
        val group = GroupModel(
            groupIdentity,
            GroupModelData(
                groupIdentity = groupIdentity,
                name = name,
                createdAt = createdAt,
                synchronizedAt = synchronizedAt,
                lastUpdate = lastUpdate,
                isArchived = isArchived,
                precomputedColorIndex = colorIndex,
                groupDescription = groupDesc,
                groupDescriptionChangedAt = groupDescChangedAt,
                otherMembers = members,
                userState = ch.threema.storage.models.GroupModel.UserState.MEMBER,
                notificationTriggerPolicyOverride = null,
            ),
            databaseBackendMock,
            coreServiceManagerMock,
        )

        val value = group.data.value!!
        assertEquals(groupIdentity, value.groupIdentity)
        assertEquals(name, value.name)
        assertEquals(createdAt, value.createdAt)
        assertEquals(synchronizedAt, value.synchronizedAt)
        assertEquals(lastUpdate, value.lastUpdate)
        assertEquals(isArchived, value.isArchived)
        assertEquals(colorIndex, value.colorIndex)
        assertEquals(groupDesc, value.groupDescription)
        assertEquals(groupDescChangedAt, value.groupDescriptionChangedAt)
        assertEquals(members, value.otherMembers)
    }

    @Test
    fun testConstructorValidGroupIdentity() {
        val data = createTestGroup().data.value!!.copy(
            groupIdentity = GroupIdentity("AAAAAAAA", 42),
        )
        val model = GroupModel(
            // The same identity but different object is provided
            GroupIdentity("AAAAAAAA", 42),
            data,
            databaseBackendMock,
            coreServiceManagerMock,
        )

        assertEquals("AAAAAAAA", model.groupIdentity.creatorIdentity)
        assertEquals(42, model.groupIdentity.groupId)
    }

    @Test
    fun testConstructorValidateCreatorIdentity() {
        val testData = createTestGroup().data.value!!
        val groupIdentity = GroupIdentity("AAAAAAAA", 42)
        val data = testData.copy(groupIdentity = groupIdentity)
        assertFailsWith<AssertionError> {
            GroupModel(
                data.groupIdentity.copy(creatorIdentity = "BBBBBBBB"),
                data,
                databaseBackendMock,
                coreServiceManagerMock,
            )
        }
    }

    @Test
    fun testConstructorValidateGroupId() {
        val testData = createTestGroup().data.value!!
        val groupIdentity = GroupIdentity("AAAAAAAA", 42)
        val data = testData.copy(groupIdentity = groupIdentity)
        assertFailsWith<AssertionError> {
            GroupModel(
                data.groupIdentity.copy(groupId = 0),
                data,
                databaseBackendMock,
                coreServiceManagerMock,
            )
        }
    }
}

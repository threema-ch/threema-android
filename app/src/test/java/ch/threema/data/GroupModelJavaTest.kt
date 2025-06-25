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
import ch.threema.common.now
import ch.threema.data.models.GroupIdentity
import ch.threema.data.models.GroupModel
import ch.threema.data.models.GroupModelData.Companion.javaCreate
import ch.threema.data.storage.DatabaseBackend
import ch.threema.domain.models.GroupId
import ch.threema.domain.taskmanager.TaskManager
import ch.threema.storage.models.GroupModel.UserState
import io.mockk.every
import io.mockk.mockk
import java.util.Date
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class GroupModelJavaTest {
    private val databaseBackendMock = mockk<DatabaseBackend>()
    private val coreServiceManagerMock = mockk<CoreServiceManager>()
    private val multiDeviceManagerMock = mockk<MultiDeviceManager>()
    private val taskManagerMock = mockk<TaskManager>()

    @BeforeTest
    fun setUp() {
        every { coreServiceManagerMock.multiDeviceManager } returns multiDeviceManagerMock
        every { coreServiceManagerMock.taskManager } returns taskManagerMock
    }

    @Test
    fun testConstruction() {
        val creatorIdentity = "TESTTEST"
        val groupId = GroupId(42)
        val name = "Group"
        val createdAt = now()
        val synchronizedAt = now()
        val lastUpdate: Date? = null
        val isArchived = false
        val colorIndex = 0
        val groupDesc = "Description"
        val groupDescChangedAt = now()
        val members = setOf("AAAAAAAA", "BBBBBBBB")
        val userState = UserState.MEMBER
        val notificationTriggerPolicyOverride: Long? = null

        val groupModel = GroupModel(
            groupIdentity = GroupIdentity(creatorIdentity, groupId.toLong()),
            data = javaCreate(
                creatorIdentity = creatorIdentity,
                groupId = groupId.toLong(),
                name = name,
                createdAt = createdAt,
                synchronizedAt = synchronizedAt,
                lastUpdate = lastUpdate,
                isArchived = isArchived,
                colorIndex = colorIndex,
                groupDescription = groupDesc,
                groupDescriptionChangedAt = groupDescChangedAt,
                members = members,
                userState = userState,
                notificationTriggerPolicyOverride = notificationTriggerPolicyOverride,
            ),
            databaseBackend = databaseBackendMock,
            coreServiceManager = coreServiceManagerMock,
        )

        val value = groupModel.data.value!!
        assertEquals("TESTTEST", value.groupIdentity.creatorIdentity)
        assertEquals(groupId, GroupId(value.groupIdentity.groupId))
        assertEquals(name, value.name)
        assertEquals(createdAt, value.createdAt)
        assertEquals(synchronizedAt, value.synchronizedAt)
        assertEquals(lastUpdate, value.lastUpdate)
        assertEquals(isArchived, value.isArchived)
        assertEquals(colorIndex.toLong(), value.colorIndexInt().toLong())
        assertEquals(groupDesc, value.groupDescription)
        assertEquals(groupDescChangedAt, value.groupDescriptionChangedAt)
        assertEquals(members, value.otherMembers)
    }

    @Test
    fun testGroupIdentityByteArray() {
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

    @Test
    fun testColorIndexRange() {
        assertValidColorIndex(0)
        assertValidColorIndex(42)
        assertValidColorIndex(254)
        assertValidColorIndex(255)

        assertInvalidColorIndex(Int.MIN_VALUE)
        assertInvalidColorIndex(-1)
        assertInvalidColorIndex(256)
        assertInvalidColorIndex(Int.MAX_VALUE)
    }

    private fun assertValidColorIndex(colorIndex: Int) {
        javaCreate(
            creatorIdentity = "",
            groupId = 42,
            name = "Group",
            createdAt = now(),
            synchronizedAt = now(),
            lastUpdate = null,
            isArchived = false,
            colorIndex = colorIndex,
            groupDescription = "Description",
            groupDescriptionChangedAt = now(),
            members = emptySet(),
            userState = UserState.MEMBER,
            notificationTriggerPolicyOverride = null,
        )
    }

    private fun assertInvalidColorIndex(colorIndex: Int) {
        assertFailsWith<IllegalArgumentException> {
            javaCreate(
                creatorIdentity = "",
                groupId = 42,
                name = "Group",
                createdAt = now(),
                synchronizedAt = now(),
                lastUpdate = null,
                isArchived = false,
                colorIndex = colorIndex,
                groupDescription = "Description",
                groupDescriptionChangedAt = now(),
                members = emptySet(),
                userState = UserState.MEMBER,
                notificationTriggerPolicyOverride = null,
            )
        }
    }
}

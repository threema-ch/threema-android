/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2024 Threema GmbH
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

import ch.threema.data.models.GroupIdentity
import ch.threema.data.models.GroupModel
import ch.threema.data.models.GroupModelData
import ch.threema.data.storage.DatabaseBackend
import org.junit.Assert
import org.junit.Assert.assertArrayEquals
import org.powermock.api.mockito.PowerMockito
import java.util.Date
import kotlin.test.Test
import kotlin.test.assertEquals

class GroupModelTest {
    private val databaseBackendMock = PowerMockito.mock(DatabaseBackend::class.java)

    private fun createTestGroup(): GroupModel {
        val groupIdentity = GroupIdentity("TESTTEST", 42)
        val members = setOf("AAAAAAAA", "BBBBBBBB")
        return GroupModel(
            groupIdentity,
            GroupModelData(
                groupIdentity,
                "Group",
                Date(),
                Date(),
                null,
                deleted = false,
                isArchived = false,
                0.toUByte(),
                "Description",
                Date(),
                members,
            ),
            databaseBackendMock
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
        assertArrayEquals(byteArrayOf(-42, -1, -1, -1, -1, -1, -1, -1), GroupIdentity(identity, -42).groupIdByteArray)
        assertArrayEquals(byteArrayOf(-1, -1, -1, -1, -1, -1, -1, -1), GroupIdentity(identity, -1).groupIdByteArray)
        assertArrayEquals(byteArrayOf(0, 0, 0, 0, 0, 0, 0, 0), GroupIdentity(identity, 0).groupIdByteArray)
        assertArrayEquals(byteArrayOf(1, 0, 0, 0, 0, 0, 0, 0), GroupIdentity(identity, 1).groupIdByteArray)
        assertArrayEquals(byteArrayOf(42, 0, 0, 0, 0, 0, 0, 0), GroupIdentity(identity, 42).groupIdByteArray)
        assertArrayEquals(byteArrayOf(0, 0, 0, 0, 0, 0, 0, -128), GroupIdentity(identity, Long.MIN_VALUE).groupIdByteArray)
        assertArrayEquals(byteArrayOf(-1, -1, -1, -1, -1, -1, -1, 127), GroupIdentity(identity, Long.MAX_VALUE).groupIdByteArray)
        assertArrayEquals(byteArrayOf(78, -88, 120, -3, -1, -1, -1, -1), GroupIdentity(identity, -42424242).groupIdByteArray)
        assertArrayEquals(byteArrayOf(-78, 87, -121, 2, 0, 0, 0, 0), GroupIdentity(identity, 42424242).groupIdByteArray)
    }

    /**
     * Test the construction using the primary constructor.
     *
     * Data is accessed through the `data` state flow.
     */
    @Test
    fun testConstruction() {
        val groupIdentity = GroupIdentity("TESTTEST", 42)
        val name = "Group"
        val createdAt = Date()
        val synchronizedAt = Date()
        val lastUpdate = null
        val deleted = false
        val isArchived = false
        val colorIndex = 0.toUByte()
        val groupDesc = "Description"
        val groupDescChangedAt = Date()
        val members = setOf("AAAAAAAA", "BBBBBBBB")
        val group = GroupModel(
            groupIdentity,
            GroupModelData(
                groupIdentity,
                name,
                createdAt,
                synchronizedAt,
                lastUpdate,
                deleted,
                isArchived,
                colorIndex,
                groupDesc,
                groupDescChangedAt,
                members,
            ), databaseBackendMock
        )

        val value = group.data.value!!
        assertEquals(groupIdentity, value.groupIdentity)
        assertEquals(name, value.name)
        assertEquals(createdAt, value.createdAt)
        assertEquals(synchronizedAt, value.synchronizedAt)
        assertEquals(lastUpdate, value.lastUpdate)
        assertEquals(deleted, value.deleted)
        assertEquals(isArchived, value.isArchived)
        assertEquals(colorIndex, value.colorIndex)
        assertEquals(groupDesc, value.groupDescription)
        assertEquals(groupDescChangedAt, value.groupDescriptionChangedAt)
        assertEquals(members, value.members)
    }

    @Test
    fun testConstructorValidGroupIdentity() {
        val data = createTestGroup().data.value!!.copy(
            groupIdentity = GroupIdentity("AAAAAAAA", 42)
        )
        val model = GroupModel(
            // The same identity but different object is provided
            GroupIdentity("AAAAAAAA", 42),
            data,
            databaseBackendMock
        )

        assertEquals("AAAAAAAA", model.groupIdentity.creatorIdentity)
        assertEquals(42, model.groupIdentity.groupId)
    }

    @Test
    fun testConstructorValidateCreatorIdentity() {
        val testData = createTestGroup().data.value!!
        val groupIdentity = GroupIdentity("AAAAAAAA", 42)
        val data = testData.copy(groupIdentity = groupIdentity)
        Assert.assertThrows(AssertionError::class.java) {
            GroupModel(
                data.groupIdentity.copy(creatorIdentity = "BBBBBBBB"),
                data,
                databaseBackendMock
            )
        }
    }

    @Test
    fun testConstructorValidateGroupId() {
        val testData = createTestGroup().data.value!!
        val groupIdentity = GroupIdentity("AAAAAAAA", 42)
        val data = testData.copy(groupIdentity = groupIdentity)
        Assert.assertThrows(AssertionError::class.java) {
            GroupModel(
                data.groupIdentity.copy(groupId = 0),
                data,
                databaseBackendMock
            )
        }
    }
}

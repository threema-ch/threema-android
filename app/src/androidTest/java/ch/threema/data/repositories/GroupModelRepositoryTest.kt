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

package ch.threema.data.repositories

import ch.threema.data.TestDatabaseService
import ch.threema.data.models.GroupIdentity
import ch.threema.data.models.GroupModelDataFactory
import ch.threema.data.storage.DatabaseBackend
import ch.threema.data.storage.DbGroup
import ch.threema.data.storage.SqliteDatabaseBackend
import ch.threema.domain.models.GroupId
import ch.threema.storage.models.GroupModel
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GroupModelRepositoryTest {
    private lateinit var databaseService: TestDatabaseService
    private lateinit var databaseBackend: DatabaseBackend
    private lateinit var groupModelRepository: GroupModelRepository

    private fun createTestDbGroup(groupIdentity: GroupIdentity): DbGroup {
        return DbGroup(
            groupIdentity.creatorIdentity,
            groupIdentity.groupIdHexString,
            "Group",
            Date(),
            Date(),
            null,
            deleted = false,
            isArchived = false,
            0.toUByte(),
            "Description",
            Date(),
            setOf("AAAAAAAA", "BBBBBBBB"),
        )
    }

    @Before
    fun before() {
        this.databaseService = TestDatabaseService()
        this.databaseBackend = SqliteDatabaseBackend(databaseService)
        this.groupModelRepository = ModelRepositories(databaseService).groups
    }

    @Test
    fun getByGroupIdentityNotFound() {
        val groupIdentity = GroupIdentity("AAAAAAAA", 42)
        val model = groupModelRepository.getByGroupIdentity(groupIdentity)
        assertNull(model)
    }

    @Test
    fun getByCreatorIdentityAndIdNotFound() {
        val model = groupModelRepository.getByCreatorIdentityAndId("AAAAAAAA", GroupId(42))
        assertNull(model)
    }

    @Test
    fun getByGroupIdentityExisting() {
        val groupIdentity = GroupIdentity("TESTTEST", 42)

        // Create group using the "old" model
        databaseService.groupModelFactory.create(
            GroupModel()
                .setCreatorIdentity(groupIdentity.creatorIdentity)
                .setApiGroupId(GroupId(groupIdentity.groupId))
                .setCreatedAt(Date())
        )

        // Fetch group using the "new" model
        val model = groupModelRepository.getByGroupIdentity(groupIdentity)!!
        assertTrue { model.groupIdentity == groupIdentity }
    }

    @Test
    fun getByCreatorIdentityAndIdExisting() {
        val creatorIdentity = "TESTTEST"
        val groupId = GroupId(-42)

        // Create group using the "old" model
        databaseService.groupModelFactory.create(
            GroupModel()
                .setCreatorIdentity(creatorIdentity)
                .setApiGroupId(groupId)
                .setCreatedAt(Date())
        )

        // Fetch group using the "new" model
        val model = groupModelRepository.getByCreatorIdentityAndId(creatorIdentity, groupId)!!
        val groupIdentity = GroupIdentity(creatorIdentity, groupId.toLong())
        assertTrue { model.groupIdentity == groupIdentity }
    }

    @Test
    fun testGetByLocalId() {
        val groupIdentity = GroupIdentity("TESTTEST", 42)
        val testGroup = createTestDbGroup(groupIdentity)
        databaseBackend.createGroup(testGroup)

        // This should work because the database is initially empty and the local group id starts
        // with 1.
        val fetchedGroup = groupModelRepository.getByLocalGroupDbId(1)
        assertEquals(GroupModelDataFactory.toDataType(testGroup), fetchedGroup?.data?.value)
    }

    @Test
    fun testGetByCreatorIdentityAndGroupId() {
        val groupIdentity = GroupIdentity("TESTTEST", 42)
        val testGroup = createTestDbGroup(groupIdentity)
        databaseBackend.createGroup(testGroup)

        val fetchedGroup = groupModelRepository.getByCreatorIdentityAndId(
            groupIdentity.creatorIdentity,
            GroupId(groupIdentity.groupId),
        )
        assertEquals(GroupModelDataFactory.toDataType(testGroup), fetchedGroup?.data?.value)
    }

    @Test
    fun testGetByGroupIdentity() {
        val groupIdentityDefault = GroupIdentity("TESTTEST", 42)
        val defaultGroup = createTestDbGroup(groupIdentityDefault)
        testInsertAndGet(groupIdentityDefault, defaultGroup)

        val groupIdentityEmpty = GroupIdentity("TESTTEST", 43)
        val emptyGroup = createTestDbGroup(groupIdentityEmpty).copy(members = emptySet())
        testInsertAndGet(groupIdentityEmpty, emptyGroup)

        val groupIdentityDatesNull = GroupIdentity("TESTTEST", 44)
        val datesNullGroup = createTestDbGroup(groupIdentityDatesNull).copy(
            synchronizedAt = null,
            lastUpdate = null,
            groupDescriptionChangedAt = null
        )
        testInsertAndGet(groupIdentityDatesNull, datesNullGroup)
    }

    @Test
    fun testMemberSetModification() {
        val groupIdentity = GroupIdentity("TESTTEST", 42)
        val defaultGroup = createTestDbGroup(groupIdentity)
        testInsertAndGet(groupIdentity, defaultGroup)

        val testData = groupModelRepository.getByGroupIdentity(groupIdentity)!!.data.value!!
        Assert.assertThrows(UnsupportedOperationException::class.java) {
            // Casting the set to a mutable set will work, but adding a new member to the set should
            // result in a runtime exception. Note that this is mainly in java code a problem, as
            // there is no cast needed to add a new member. Of course, it will result in a runtime
            // exception as well.
            (testData.members as MutableSet).add("01234567")
        }
    }

    private fun testInsertAndGet(groupIdentity: GroupIdentity, testGroup: DbGroup) {
        databaseBackend.createGroup(testGroup)

        val fetchedGroup = groupModelRepository.getByGroupIdentity(groupIdentity)
        assertEquals(GroupModelDataFactory.toDataType(testGroup), fetchedGroup?.data?.value)
    }
}

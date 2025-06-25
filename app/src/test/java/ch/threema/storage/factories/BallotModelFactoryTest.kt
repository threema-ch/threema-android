/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2020-2025 Threema GmbH
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

package ch.threema.storage.factories

import android.content.ContentValues
import android.database.Cursor
import ch.threema.app.messagereceiver.ContactMessageReceiver
import ch.threema.app.messagereceiver.DistributionListMessageReceiver
import ch.threema.app.messagereceiver.GroupMessageReceiver
import ch.threema.app.messagereceiver.MessageReceiver
import ch.threema.app.services.ballot.BallotService.BallotFilter
import ch.threema.storage.ColumnIndexCache
import ch.threema.storage.DatabaseUtil
import ch.threema.storage.models.ContactModel
import ch.threema.storage.models.GroupModel
import ch.threema.storage.models.ballot.BallotModel
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.unmockkConstructor
import io.mockk.unmockkStatic
import io.mockk.verify
import java.util.Date
import kotlin.random.Random
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import net.zetetic.database.sqlcipher.SQLiteDatabase

class BallotModelFactoryTest {

    private lateinit var readableDatabaseMock: SQLiteDatabase
    private lateinit var writeableDatabaseMock: SQLiteDatabase
    private lateinit var columnIndexCacheMock: ColumnIndexCache
    private lateinit var factory: BallotModelFactory

    @BeforeTest
    fun setUp() {
        readableDatabaseMock = mockk(relaxed = true)
        writeableDatabaseMock = mockk(relaxed = true)
        columnIndexCacheMock = mockk(relaxed = true)
        factory = mockk(relaxed = true)

        every { factory.readableDatabase } returns readableDatabaseMock
        every { factory.writableDatabase } returns writeableDatabaseMock
        every { factory.columnIndexCache } returns columnIndexCacheMock
        every { factory.tableName } returns "ballot"
    }

    @Test
    fun testGetAll() {
        every { factory.all } answers { callOriginal() }
        val allCursorResult = mockk<Cursor>()
        val res = mockk<List<BallotModel>>()
        every {
            readableDatabaseMock.query("ballot", null, null, null, null, null, null)
        } returns allCursorResult
        every { factory.convertList(allCursorResult) } returns res

        assertEquals(res, factory.all)

        verify(exactly = 1) { factory.convertList(allCursorResult) }
    }

    @Test
    fun testGetById() {
        every { factory.getById(any()) } answers { callOriginal() }

        factory.getById(1323123)

        verify(exactly = 1) { factory.getFirst("id=?", arrayOf("1323123")) }
    }

    @Test
    fun testConvertWithCursor() {
        every { factory.convert(any()) } answers { callOriginal() }

        val cursorMock = mockk<Cursor>(relaxed = true)
        val d1 = randomDate()
        val d2 = randomDate()
        val d3 = randomDate()

        // Mock all indices
        every { columnIndexCacheMock.getColumnIndex(cursorMock, "id") } returns 10
        every { cursorMock.getInt(10) } returns 9982
        every { columnIndexCacheMock.getColumnIndex(cursorMock, "apiBallotId") } returns 20
        every { cursorMock.getString(20) } returns "apiBallotId.value"
        every { columnIndexCacheMock.getColumnIndex(cursorMock, "creatorIdentity") } returns 30
        every { cursorMock.getString(30) } returns "creatorIdentity.value"
        every { columnIndexCacheMock.getColumnIndex(cursorMock, "name") } returns 40
        every { cursorMock.getString(40) } returns "name.value"
        every { columnIndexCacheMock.getColumnIndex(cursorMock, "state") } returns 50
        every { cursorMock.getString(50) } returns "OPEN"
        every { columnIndexCacheMock.getColumnIndex(cursorMock, "assessment") } returns 60
        every { cursorMock.getString(60) } returns "MULTIPLE_CHOICE"
        every { columnIndexCacheMock.getColumnIndex(cursorMock, "type") } returns 70
        every { cursorMock.getString(70) } returns "INTERMEDIATE"
        every { columnIndexCacheMock.getColumnIndex(cursorMock, "choiceType") } returns 80
        every { cursorMock.getString(80) } returns "TEXT"
        every { columnIndexCacheMock.getColumnIndex(cursorMock, "createdAt") } returns 90
        every { cursorMock.getLong(90) } returns d1.time
        every { columnIndexCacheMock.getColumnIndex(cursorMock, "modifiedAt") } returns 100
        every { cursorMock.getLong(100) } returns d2.time
        every { columnIndexCacheMock.getColumnIndex(cursorMock, "lastViewedAt") } returns 110
        every { cursorMock.getLong(110) } returns d3.time

        val ballotModel = factory.convert(cursorMock)

        assertEquals(9982, ballotModel.id.toLong())
        assertEquals("apiBallotId.value", ballotModel.apiBallotId)
        assertEquals("creatorIdentity.value", ballotModel.creatorIdentity)
        assertEquals("name.value", ballotModel.name)
        assertEquals(BallotModel.State.OPEN, ballotModel.state)
        assertEquals(BallotModel.Assessment.MULTIPLE_CHOICE, ballotModel.assessment)
        assertEquals(BallotModel.Type.INTERMEDIATE, ballotModel.type)
        assertEquals(BallotModel.ChoiceType.TEXT, ballotModel.choiceType)
        assertEquals(d1.time, ballotModel.createdAt.time)
        assertEquals(d2.time, ballotModel.modifiedAt.time)
        assertEquals(d3.time, ballotModel.lastViewedAt.time)
    }

    @Test
    fun testConvertList() {
        every { factory.convertList(any()) } answers { callOriginal() }

        val cursorMock = mockk<Cursor>(relaxed = true)
        val ballotModelMock1 = mockk<BallotModel>()
        val ballotModelMock2 = mockk<BallotModel>()
        val ballotModelMock3 = mockk<BallotModel>()

        every { cursorMock.moveToNext() } returns true andThen true andThen true andThen false
        every { factory.convert(cursorMock) } returns ballotModelMock1 andThen ballotModelMock2 andThen ballotModelMock3
        val result = factory.convertList(cursorMock)

        verify(exactly = 3) { factory.convert(cursorMock) }

        assertContentEquals(
            listOf(
                ballotModelMock1,
                ballotModelMock2,
                ballotModelMock3,
            ),
            result,
        )
    }

    @Test
    fun testCreateOrUpdateInsert() {
        every { factory.createOrUpdate(any()) } answers { callOriginal() }
        val ballotModelMock = mockk<BallotModel> {
            every { id } returns 0
        }

        factory.createOrUpdate(ballotModelMock)

        verify(exactly = 0) { factory.update(any()) }
        verify(exactly = 1) { factory.create(ballotModelMock) }
    }

    @Test
    fun testCreateOrUpdateUpdate_NotExists_Insert() {
        every { factory.createOrUpdate(any()) } answers { callOriginal() }
        val ballotModelMock = mockk<BallotModel> {
            every { id } returns 123
        }
        val cursorMock = mockk<Cursor>(relaxed = true)
        every {
            readableDatabaseMock.query("ballot", null, "id=?", arrayOf("123"), null, null, null)
        } returns cursorMock
        every { cursorMock.moveToNext() } returns false

        factory.createOrUpdate(ballotModelMock)

        verify(exactly = 1) { cursorMock.moveToNext() }
        verify(exactly = 0) { factory.update(any()) }
        verify(exactly = 1) { factory.create(ballotModelMock) }
    }

    @Test
    fun testCreateOrUpdateUpdate() {
        every { factory.createOrUpdate(any()) } answers { callOriginal() }
        val ballotModelMock = mockk<BallotModel> {
            every { id } returns 123
        }
        val cursorMock = mockk<Cursor>(relaxed = true)
        every {
            readableDatabaseMock.query("ballot", null, "id=?", arrayOf("123"), null, null, null)
        } returns cursorMock
        every { cursorMock.moveToNext() } returns true

        factory.createOrUpdate(ballotModelMock)

        verify(exactly = 1) { cursorMock.moveToNext() }
        verify(exactly = 1) { factory.update(any()) }
        verify(exactly = 0) { factory.create(ballotModelMock) }
    }

    @Test
    fun testBuildContentValues() {
        every { factory.buildContentValues(any()) } answers { callOriginal() }
        mockkConstructor(ContentValues::class)
        val d1 = randomDate()
        val d2 = randomDate()
        val d3 = randomDate()
        val ballotModel = BallotModel().apply {
            setId(123)
            setApiBallotId("apiBallotId.value")
            setCreatorIdentity("creatorIdentity.value")
            setName("name.value")
            setState(BallotModel.State.CLOSED)
            setAssessment(BallotModel.Assessment.SINGLE_CHOICE)
            setType(BallotModel.Type.INTERMEDIATE)
            setChoiceType(BallotModel.ChoiceType.TEXT)
            setCreatedAt(d1)
            setModifiedAt(d2)
            setLastViewedAt(d3)
        }

        factory.buildContentValues(ballotModel)

        verify(exactly = 1) { constructedWith<ContentValues>().put("apiBallotId", "apiBallotId.value") }
        verify(exactly = 1) { constructedWith<ContentValues>().put("creatorIdentity", "creatorIdentity.value") }
        verify(exactly = 1) { constructedWith<ContentValues>().put("name", "name.value") }
        verify(exactly = 1) { constructedWith<ContentValues>().put("state", "CLOSED") }
        verify(exactly = 1) { constructedWith<ContentValues>().put("assessment", "SINGLE_CHOICE") }
        verify(exactly = 1) { constructedWith<ContentValues>().put("type", "INTERMEDIATE") }
        verify(exactly = 1) { constructedWith<ContentValues>().put("choiceType", "TEXT") }
        verify(exactly = 1) { constructedWith<ContentValues>().put("createdAt", d1.time) }
        verify(exactly = 1) { constructedWith<ContentValues>().put("modifiedAt", d2.time) }
        verify(exactly = 1) { constructedWith<ContentValues>().put("lastViewedAt", d3.time) }

        unmockkConstructor(ContentValues::class)
    }

    @Test
    fun testCreate() {
        every { factory.create(any()) } answers { callOriginal() }
        val ballotModelMock = mockk<BallotModel>(relaxed = true)
        val contentValuesMock = mockk<ContentValues>()
        every { factory.buildContentValues(ballotModelMock) } returns contentValuesMock
        every { writeableDatabaseMock.insertOrThrow("ballot", null, contentValuesMock) } returns 123L

        val result = factory.create(ballotModelMock)

        assertTrue(result)
        verify(exactly = 1) { factory.buildContentValues(ballotModelMock) }
        verify(exactly = 1) { factory.writableDatabase }
        verify(exactly = 0) { factory.readableDatabase }
        verify(exactly = 1) { writeableDatabaseMock.insertOrThrow("ballot", null, contentValuesMock) }
        verify(exactly = 1) { ballotModelMock.setId(123) }
    }

    @Test
    fun testUpdate() {
        every { factory.update(any()) } answers { callOriginal() }

        val ballotModelMock = mockk<BallotModel> {
            every { id } returns 12093
        }
        val contentValuesMock = mockk<ContentValues>()
        every { factory.buildContentValues(ballotModelMock) } returns contentValuesMock

        val result = factory.update(ballotModelMock)

        assertTrue(result)
        verify(exactly = 1) { factory.buildContentValues(ballotModelMock) }
        verify(exactly = 1) { factory.writableDatabase }
        verify(exactly = 0) { factory.readableDatabase }
        verify(exactly = 1) { writeableDatabaseMock.update("ballot", contentValuesMock, BallotModel.COLUMN_ID + "=?", arrayOf("12093")) }
    }

    @Test
    fun testDelete() {
        every { factory.delete(any()) } answers { callOriginal() }
        val ballotModelMock = mockk<BallotModel> {
            every { id } returns 523
        }
        every {
            writeableDatabaseMock.delete("ballot", BallotModel.COLUMN_ID + "=?", arrayOf("523"))
        } returns 12893

        val result = factory.delete(ballotModelMock)

        assertEquals(12893, result)
        verify(exactly = 1) { factory.writableDatabase }
        verify(exactly = 0) { factory.readableDatabase }
        verify(exactly = 1) { writeableDatabaseMock.delete("ballot", BallotModel.COLUMN_ID + "=?", arrayOf("523")) }
    }

    @Test
    fun testGetFirst_NoResult() {
        every { factory.getFirst(any(), any()) } answers { callOriginal() }
        val cursorMock = mockk<Cursor>(relaxed = true)
        every { cursorMock.moveToFirst() } returns false
        every {
            readableDatabaseMock.query(
                "ballot",
                null,
                "selection",
                arrayOf("a", "b", "c"),
                null,
                null,
                null,
            )
        } returns cursorMock

        val result = factory.getFirst("selection", arrayOf("a", "b", "c"))

        assertNull(result)
        verify(exactly = 0) { factory.convert(any()) }
        verify(exactly = 0) { factory.writableDatabase }
        verify(exactly = 1) {
            readableDatabaseMock.query(
                "ballot",
                null,
                "selection",
                arrayOf("a", "b", "c"),
                null,
                null,
                null,
            )
        }
    }

    @Test
    fun testGetFirst_Result() {
        every { factory.getFirst(any(), any()) } answers { callOriginal() }
        val cursorMock = mockk<Cursor>(relaxed = true) {
            every { moveToFirst() } returns true
        }
        val ballotModelMock = mockk<BallotModel>()
        every {
            readableDatabaseMock.query(
                "ballot",
                null,
                "selection",
                arrayOf("a", "b", "c"),
                null,
                null,
                null,
            )
        } returns cursorMock
        every { factory.convert(cursorMock) } returns ballotModelMock

        val result = factory.getFirst("selection", arrayOf("a", "b", "c"))

        assertEquals(ballotModelMock, result)
        verify(exactly = 0) { factory.writableDatabase }
        verify(exactly = 1) { factory.convert(cursorMock) }
        verify(exactly = 1) {
            readableDatabaseMock.query(
                "ballot",
                null,
                "selection",
                arrayOf("a", "b", "c"),
                null,
                null,
                null,
            )
        }
    }

    @Test
    fun testCount() {
        val filterMock = mockk<BallotFilter>()
        val cursorMock = mockk<Cursor>()
        mockkStatic(DatabaseUtil::class)
        every { factory.count(any()) } answers { callOriginal() }
        every { factory.runBallotFilterQuery(filterMock, "SELECT COUNT(*)") } returns cursorMock
        every { DatabaseUtil.count(cursorMock) } returns 18923890L

        val count = factory.count(filterMock)

        assertEquals(18923890L, count)
        verify(exactly = 1) { factory.runBallotFilterQuery(filterMock, "SELECT COUNT(*)") }

        unmockkStatic(DatabaseUtil::class)
    }

    @Test
    fun testRunBallotFilterQuery_GroupDefault() {
        every { factory.runBallotFilterQuery(any(), any()) } answers { callOriginal() }
        mockkStatic(DatabaseUtil::class)

        val expectedQuery = "SELECT STERN FROM ballot b" +
            " INNER JOIN group_ballot l ON l.ballotId = b.id AND l.groupId = ?" +
            " WHERE (b.state IN (two-placeholder-argument))" +
            " ORDER BY b.createdAt DESC"

        val expectedArguments = listOf(
            "12903",
            "OPEN",
            "TEMPORARY",
        )

        val filterMock = mockk<BallotFilter>()
        val receiverMock = mockk<GroupMessageReceiver>()
        val groupMock = mockk<GroupModel>()
        val convertedArgumentsMock = arrayOf("a", "b", "c")
        val cursorMock = mockk<Cursor>()

        every { filterMock.receiver } returns receiverMock
        every { filterMock.createdOrNotVotedByIdentity() } returns null
        every { filterMock.states } returns arrayOf(BallotModel.State.OPEN, BallotModel.State.TEMPORARY)
        every { receiverMock.type } returns MessageReceiver.Type_GROUP
        every { receiverMock.group } returns groupMock
        every { groupMock.id } returns 12903
        every { DatabaseUtil.convertArguments(expectedArguments) } returns convertedArgumentsMock
        every { DatabaseUtil.makePlaceholders(2) } returns "two-placeholder-argument"
        every { readableDatabaseMock.rawQuery(expectedQuery, convertedArgumentsMock) } returns cursorMock

        assertEquals(
            cursorMock,
            factory.runBallotFilterQuery(filterMock, "SELECT STERN"),
            "Cursor mock was not returned. Maybe the `expectedQuery` is wrong?",
        )

        verify(exactly = 0) { factory.writableDatabase }
        verify(exactly = 1) { readableDatabaseMock.rawQuery(expectedQuery, convertedArgumentsMock) }
        verify(atLeast = 1) { filterMock.receiver }

        unmockkStatic(DatabaseUtil::class)
    }

    @Test
    fun testRunBallotFilterQuery_UserDefault() {
        every { factory.runBallotFilterQuery(any(), any()) } answers { callOriginal() }
        mockkStatic(DatabaseUtil::class)

        val expectedQuery = "SELECT STERN FROM ballot b" +
            " INNER JOIN identity_ballot l ON l.ballotId = b.id AND l.identity = ?" +
            " WHERE (b.state IN (two-placeholder-argument))" +
            " ORDER BY b.createdAt DESC"

        val expectedArguments = listOf(
            "12345678",
            "OPEN",
            "TEMPORARY",
        )

        val filterMock = mockk<BallotFilter>()
        val receiverMock = mockk<ContactMessageReceiver>()
        val contactModel = ContactModel.create("12345678", ByteArray(32))
        val convertedArgumentsMock = arrayOf("a", "b", "c")
        val cursorMock = mockk<Cursor>()

        every { filterMock.receiver } returns receiverMock
        every { filterMock.createdOrNotVotedByIdentity() } returns null
        every { filterMock.states } returns arrayOf(BallotModel.State.OPEN, BallotModel.State.TEMPORARY)
        every { receiverMock.type } returns MessageReceiver.Type_CONTACT
        every { receiverMock.contact } returns contactModel
        every { DatabaseUtil.convertArguments(expectedArguments) } returns convertedArgumentsMock
        every { DatabaseUtil.makePlaceholders(2) } returns "two-placeholder-argument"
        every { readableDatabaseMock.rawQuery(expectedQuery, convertedArgumentsMock) } returns cursorMock

        assertEquals(
            cursorMock,
            factory.runBallotFilterQuery(filterMock, "SELECT STERN"),
            "Cursor mock was not returned. Maybe the `expectedQuery` is wrong?",
        )
        verify(exactly = 0) { factory.writableDatabase }
        verify(exactly = 1) { readableDatabaseMock.rawQuery(expectedQuery, convertedArgumentsMock) }
        verify(atLeast = 1) { filterMock.receiver }

        unmockkStatic(DatabaseUtil::class)
    }

    @Test
    fun testRunBallotFilterQuery_GroupCreatedOrNotVotedByIdentity() {
        every { factory.runBallotFilterQuery(any(), any()) } answers { callOriginal() }
        mockkStatic(DatabaseUtil::class)
        val expectedQuery = "SELECT STERN FROM ballot b" +
            " INNER JOIN group_ballot l ON l.ballotId = b.id AND l.groupId = ?" +
            " WHERE (b.state IN (two-placeholder-argument))" +
            " AND (b.creatorIdentity = ? OR NOT EXISTS (SELECT sv.ballotId FROM ballot_vote sv WHERE sv.votingIdentity = ? AND sv.ballotId = b.id))" +
            " ORDER BY b.createdAt DESC"
        val expectedArguments = listOf(
            // Group ID
            "12903",
            "OPEN",
            "TEMPORARY",
            // Be sure the FILTER-ID is set two times (see query)
            "FILTER-ID",
            "FILTER-ID",
        )
        val filterMock = mockk<BallotFilter>()
        val receiverMock = mockk<GroupMessageReceiver>()
        val groupMock = mockk<GroupModel>()
        val convertedArgumentsMock = arrayOf("a", "b", "c")
        val cursorMock = mockk<Cursor>()
        every { filterMock.receiver } returns receiverMock
        every { filterMock.createdOrNotVotedByIdentity() } returns "FILTER-ID"
        every { filterMock.states } returns arrayOf(BallotModel.State.OPEN, BallotModel.State.TEMPORARY)
        every { receiverMock.type } returns MessageReceiver.Type_GROUP
        every { receiverMock.group } returns groupMock
        every { groupMock.id } returns 12903
        every { DatabaseUtil.convertArguments(expectedArguments) } returns convertedArgumentsMock
        every { DatabaseUtil.makePlaceholders(2) } returns "two-placeholder-argument"
        every { readableDatabaseMock.rawQuery(expectedQuery, convertedArgumentsMock) } returns cursorMock

        val result = factory.runBallotFilterQuery(filterMock, "SELECT STERN")

        assertEquals(cursorMock, result, "Cursor mock was not returned. Maybe the `expectedQuery` is wrong?")
        verify(exactly = 0) { factory.writableDatabase }
        verify(exactly = 1) { readableDatabaseMock.rawQuery(expectedQuery, convertedArgumentsMock) }
        verify(atLeast = 1) { filterMock.receiver }

        unmockkStatic(DatabaseUtil::class)
    }

    @Test
    fun testRunBallotFilterQuery_NoFilter() {
        every { factory.runBallotFilterQuery(any(), any()) } answers { callOriginal() }
        mockkStatic(DatabaseUtil::class)
        val expectedQuery = "SELECT STERN FROM ballot b" +
            " INNER JOIN identity_ballot l ON l.ballotId = b.id AND l.identity = ?" +
            " ORDER BY b.createdAt DESC"
        val expectedArguments = listOf(
            // Threema ID
            "12345678",
        )
        val filterMock = mockk<BallotFilter>()
        val receiverMock = mockk<ContactMessageReceiver>()
        val contactModel = ContactModel.create("12345678", ByteArray(32))
        val convertedArgumentsMock = arrayOf("a", "b", "c")
        val cursorMock = mockk<Cursor>()
        every { filterMock.receiver } returns receiverMock
        every { filterMock.createdOrNotVotedByIdentity() } returns null
        every { filterMock.states } returns null
        every { receiverMock.type } returns MessageReceiver.Type_CONTACT
        every { receiverMock.contact } returns contactModel
        every { DatabaseUtil.convertArguments(expectedArguments) } returns convertedArgumentsMock
        every { readableDatabaseMock.rawQuery(expectedQuery, convertedArgumentsMock) } returns cursorMock

        val result = factory.runBallotFilterQuery(filterMock, "SELECT STERN")

        assertEquals(result, cursorMock, "Cursor mock was not returned. Maybe the `expectedQuery` is wrong?")

        verify(exactly = 0) { factory.writableDatabase }
        verify(exactly = 1) { readableDatabaseMock.rawQuery(expectedQuery, convertedArgumentsMock) }
        verify(atLeast = 1) { filterMock.receiver }
        unmockkStatic(DatabaseUtil::class)
    }

    @Test
    fun testRunBallotFilterQuery_DistributionList() {
        every { factory.runBallotFilterQuery(any(), any()) } answers { callOriginal() }
        mockkStatic(DatabaseUtil::class)
        val filterMock = mockk<BallotFilter>()
        val receiverMock = mockk<DistributionListMessageReceiver>()
        every { filterMock.receiver } returns receiverMock as MessageReceiver<*>
        every { filterMock.createdOrNotVotedByIdentity() } returns null
        every { filterMock.states } returns null
        every { receiverMock.type } returns MessageReceiver.Type_DISTRIBUTION_LIST

        val result = factory.runBallotFilterQuery(filterMock, "SELECT STERN")

        assertNull(result)
        verify(exactly = 0) { factory.writableDatabase }
        verify(exactly = 0) { factory.readableDatabase }
        unmockkStatic(DatabaseUtil::class)
    }

    private fun randomDate(): Date =
        Date(-946771200000L + (Random.nextLong() % (70L * 365 * 24 * 60 * 60 * 1000)))
}

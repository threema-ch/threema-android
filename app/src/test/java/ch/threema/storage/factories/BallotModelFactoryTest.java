/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2020-2023 Threema GmbH
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

package ch.threema.storage.factories;


import android.content.ContentValues;

import android.database.Cursor;
import net.zetetic.database.sqlcipher.SQLiteDatabase;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Random;

import ch.threema.app.messagereceiver.ContactMessageReceiver;
import ch.threema.app.messagereceiver.DistributionListMessageReceiver;
import ch.threema.app.messagereceiver.GroupMessageReceiver;
import ch.threema.app.messagereceiver.MessageReceiver;
import ch.threema.app.services.ballot.BallotService;
import ch.threema.storage.ColumnIndexCache;
import ch.threema.storage.DatabaseUtil;
import ch.threema.storage.models.ContactModel;
import ch.threema.storage.models.GroupModel;
import ch.threema.storage.models.ballot.BallotModel;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.powermock.api.mockito.PowerMockito.doCallRealMethod;
import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.mockStatic;
import static org.powermock.api.mockito.PowerMockito.when;
import static org.powermock.api.mockito.PowerMockito.whenNew;

@RunWith(PowerMockRunner.class)
@PrepareForTest({BallotModelFactory.class, ContentValues.class, DatabaseUtil.class, SQLiteDatabase.class})
public class BallotModelFactoryTest {
	final SQLiteDatabase readableDatabaseMock = PowerMockito.mock(SQLiteDatabase.class);
	final SQLiteDatabase writeableDatabaseMock = PowerMockito.mock(SQLiteDatabase.class);
	final ColumnIndexCache columnIndexCacheMock = PowerMockito.mock(ColumnIndexCache.class);

	BallotModelFactory factory = mock(BallotModelFactory.class);

	@Before
	public void setUp() {
		when(factory.getReadableDatabase()).thenReturn(this.readableDatabaseMock);
		when(factory.getWritableDatabase()).thenReturn(this.writeableDatabaseMock);
		when(factory.getColumnIndexCache()).thenReturn(this.columnIndexCacheMock);

		when(factory.getTableName()).thenReturn("ballot");
	}

	@Test
	public void testGetAll() {
		doCallRealMethod().when(factory).getAll();

		Cursor allCursorResult = mock(Cursor.class);
		List<BallotModel> res = mock(List.class);
		when(readableDatabaseMock.query("ballot", null, null, null, null, null, null)).thenReturn(allCursorResult);
		when(factory.convertList(allCursorResult)).thenReturn(res);
		Assert.assertEquals(res, factory.getAll());

		verify(factory, times(1)).convertList(allCursorResult);
	}

	@Test
	public void testGetById() throws Exception {
		doCallRealMethod().when(factory).getById(any(Integer.class));

		factory.getById(1323123);

		// Be sure the getFirst is called
		verify(factory, times(1)).getFirst(
			"id=?", new String[]{"1323123"}
		);
	}

	@Test
	public void testConvertWithCursor() {
		doCallRealMethod().when(factory).convert(any(Cursor.class));

		Cursor cursorMock = mock(Cursor.class);
		Date d1 = this.randomDate();
		Date d2 = this.randomDate();
		Date d3 = this.randomDate();

		// Mock all index
		when(this.columnIndexCacheMock.getColumnIndex(cursorMock, "id")).thenReturn(10);
		when(cursorMock.getInt(10)).thenReturn(9982);

		when(this.columnIndexCacheMock.getColumnIndex(cursorMock, "apiBallotId")).thenReturn(20);
		when(cursorMock.getString(20)).thenReturn("apiBallotId.value");

		when(this.columnIndexCacheMock.getColumnIndex(cursorMock, "creatorIdentity")).thenReturn(30);
		when(cursorMock.getString(30)).thenReturn("creatorIdentity.value");

		when(this.columnIndexCacheMock.getColumnIndex(cursorMock, "name")).thenReturn(40);
		when(cursorMock.getString(40)).thenReturn("name.value");

		when(this.columnIndexCacheMock.getColumnIndex(cursorMock, "state")).thenReturn(50);
		when(cursorMock.getString(50)).thenReturn("OPEN");

		when(this.columnIndexCacheMock.getColumnIndex(cursorMock, "assessment")).thenReturn(60);
		when(cursorMock.getString(60)).thenReturn("MULTIPLE_CHOICE");

		when(this.columnIndexCacheMock.getColumnIndex(cursorMock, "type")).thenReturn(70);
		when(cursorMock.getString(70)).thenReturn("INTERMEDIATE");

		when(this.columnIndexCacheMock.getColumnIndex(cursorMock, "choiceType")).thenReturn(80);
		when(cursorMock.getString(80)).thenReturn("TEXT");

		when(this.columnIndexCacheMock.getColumnIndex(cursorMock, "createdAt")).thenReturn(90);
		when(cursorMock.getLong(90)).thenReturn(d1.getTime());

		when(this.columnIndexCacheMock.getColumnIndex(cursorMock, "modifiedAt")).thenReturn(100);
		when(cursorMock.getLong(100)).thenReturn(d2.getTime());

		when(this.columnIndexCacheMock.getColumnIndex(cursorMock, "lastViewedAt")).thenReturn(110);
		when(cursorMock.getLong(110)).thenReturn(d3.getTime());

		BallotModel ballotModel = factory.convert(cursorMock);

		Assert.assertEquals(9982, ballotModel.getId());
		Assert.assertEquals("apiBallotId.value", ballotModel.getApiBallotId());
		Assert.assertEquals("creatorIdentity.value", ballotModel.getCreatorIdentity());
		Assert.assertEquals("name.value", ballotModel.getName());
		Assert.assertEquals(BallotModel.State.OPEN, ballotModel.getState());
		Assert.assertEquals(BallotModel.Assessment.MULTIPLE_CHOICE, ballotModel.getAssessment());
		Assert.assertEquals(BallotModel.Type.INTERMEDIATE, ballotModel.getType());
		Assert.assertEquals(BallotModel.ChoiceType.TEXT, ballotModel.getChoiceType());
		Assert.assertEquals(d1.getTime(), ballotModel.getCreatedAt().getTime());
		Assert.assertEquals(d2.getTime(), ballotModel.getModifiedAt().getTime());
		Assert.assertEquals(d3.getTime(), ballotModel.getLastViewedAt().getTime());
	}

	@Test
	public void testConvertList() {
		doCallRealMethod().when(factory).convertList(any(Cursor.class));

		Cursor cursorMock = mock(Cursor.class);
		BallotModel ballotModelMock1 = mock(BallotModel.class);
		BallotModel ballotModelMock2 = mock(BallotModel.class);
		BallotModel ballotModelMock3 = mock(BallotModel.class);


		// Loop three times
		when(cursorMock.moveToNext()).thenReturn(true).thenReturn(true).thenReturn(true).thenReturn(false);
		when(factory.convert(cursorMock)).thenReturn(ballotModelMock1).thenReturn(ballotModelMock2).thenReturn(ballotModelMock3);
		List<BallotModel> result = factory.convertList(cursorMock);

		verify(factory, times(3)).convert(cursorMock);

		Assert.assertEquals(3, result.size());
		Assert.assertEquals(ballotModelMock1, result.get(0));
		Assert.assertEquals(ballotModelMock2, result.get(1));
		Assert.assertEquals(ballotModelMock3, result.get(2));
	}

	@Test
	public void testCreateOrUpdateInsert() {
		doCallRealMethod().when(factory).createOrUpdate(any(BallotModel.class));

		BallotModel ballotModelMock = mock(BallotModel.class);
		when(ballotModelMock.getId()).thenReturn(0);


		factory.createOrUpdate(ballotModelMock);


		verify(factory, times(0)).update(any(BallotModel.class));
		verify(factory, times(1)).create(ballotModelMock);
	}

	@Test
	public void testCreateOrUpdateUpdate_NotExists_Insert() {
		doCallRealMethod().when(factory).createOrUpdate(any(BallotModel.class));

		BallotModel ballotModelMock = mock(BallotModel.class);
		Cursor cursorMock = mock(Cursor.class);

		when(ballotModelMock.getId()).thenReturn(123);


		when(readableDatabaseMock.query("ballot", null, "id=?", new String[]{"123"}, null, null, null))
			.thenReturn(cursorMock);

		when(cursorMock.moveToNext()).thenReturn(false);
		factory.createOrUpdate(ballotModelMock);

		verify(cursorMock, times(1)).moveToNext();
		verify(factory, times(0)).update(any(BallotModel.class));
		verify(factory, times(1)).create(ballotModelMock);
	}

	@Test
	public void testCreateOrUpdateUpdate() {
		doCallRealMethod().when(factory).createOrUpdate(any(BallotModel.class));

		BallotModel ballotModelMock = mock(BallotModel.class);
		Cursor cursorMock = mock(Cursor.class);

		when(ballotModelMock.getId()).thenReturn(123);


		when(readableDatabaseMock.query("ballot", null, "id=?", new String[]{"123"}, null, null, null))
			.thenReturn(cursorMock);

		when(cursorMock.moveToNext()).thenReturn(true);
		factory.createOrUpdate(ballotModelMock);

		verify(cursorMock, times(1)).moveToNext();
		verify(factory, times(1)).update(any(BallotModel.class));
		verify(factory, times(0)).create(ballotModelMock);
	}

	@Test
	public void testBuildContentValues() throws Exception {
		doCallRealMethod().when(factory).buildContentValues(any(BallotModel.class));

		ContentValues contentValuesMock = mock(ContentValues.class);

		whenNew(ContentValues.class).withNoArguments().thenReturn(contentValuesMock);

		Date d1 = this.randomDate();
		Date d2 = this.randomDate();
		Date d3 = this.randomDate();
		BallotModel ballotModel = new BallotModel();
		ballotModel
			.setId(123)
			.setApiBallotId("apiBallotId.value")
			.setCreatorIdentity("creatorIdentity.value")
			.setName("name.value")
			.setState(BallotModel.State.CLOSED)
			.setAssessment(BallotModel.Assessment.SINGLE_CHOICE)
			.setType(BallotModel.Type.INTERMEDIATE)
			.setChoiceType(BallotModel.ChoiceType.TEXT)
			.setCreatedAt(d1)
			.setModifiedAt(d2)
			.setLastViewedAt(d3);
		ContentValues contentValuesResult = factory.buildContentValues(ballotModel);

		Assert.assertNotNull(contentValuesMock);
		Assert.assertEquals(contentValuesResult, contentValuesMock);
		verify(contentValuesMock, times(1)).put("apiBallotId", "apiBallotId.value");
		verify(contentValuesMock, times(1)).put("creatorIdentity", "creatorIdentity.value");
		verify(contentValuesMock, times(1)).put("name", "name.value");
		verify(contentValuesMock, times(1)).put("state", "CLOSED");
		verify(contentValuesMock, times(1)).put("assessment", "SINGLE_CHOICE");
		verify(contentValuesMock, times(1)).put("type", "INTERMEDIATE");
		verify(contentValuesMock, times(1)).put("choiceType", "TEXT");
		verify(contentValuesMock, times(1)).put("createdAt", d1.getTime());
		verify(contentValuesMock, times(1)).put("modifiedAt", d2.getTime());
		verify(contentValuesMock, times(1)).put("lastViewedAt", d3.getTime());
	}

	@Test
	public void testCreate() {
		doCallRealMethod().when(factory).create(any(BallotModel.class));

		BallotModel ballotModelMock = mock(BallotModel.class);
		ContentValues contentValuesMock = mock(ContentValues.class);

		when(factory.buildContentValues(ballotModelMock)).thenReturn(contentValuesMock);
		when(writeableDatabaseMock.insertOrThrow("ballot", null, contentValuesMock)).thenReturn(123L);

		Assert.assertEquals(true, factory.create(ballotModelMock));
		verify(factory, times(1)).buildContentValues(ballotModelMock);
		verify(factory, times(1)).getWritableDatabase();
		verify(factory, times(0)).getReadableDatabase();
		verify(writeableDatabaseMock, times(1)).insertOrThrow("ballot", null, contentValuesMock);
		verify(ballotModelMock, times(1)).setId(123);
	}

	@Test
	public void testUpdate() {
		doCallRealMethod().when(factory).update(any(BallotModel.class));

		BallotModel ballotModelMock = mock(BallotModel.class);
		ContentValues contentValuesMock = mock(ContentValues.class);

		when(ballotModelMock.getId()).thenReturn(12093);
		when(factory.buildContentValues(ballotModelMock)).thenReturn(contentValuesMock);
		Assert.assertEquals(true, factory.update(ballotModelMock));
		verify(factory, times(1)).buildContentValues(ballotModelMock);
		verify(factory, times(1)).getWritableDatabase();
		verify(factory, times(0)).getReadableDatabase();
		verify(writeableDatabaseMock, times(1)).update
			("ballot", contentValuesMock, BallotModel.COLUMN_ID + "=?", new String[]{"12093"});
	}

	@Test
	public void testDelete() {
		doCallRealMethod().when(factory).delete(any(BallotModel.class));

		BallotModel ballotModelMock = mock(BallotModel.class);

		when(writeableDatabaseMock.delete("ballot", BallotModel.COLUMN_ID + "=?", new String[]{"523"})).thenReturn(12893);
		when(ballotModelMock.getId()).thenReturn(523);
		Assert.assertEquals(12893, factory.delete(ballotModelMock));
		verify(factory, times(1)).getWritableDatabase();
		verify(factory, times(0)).getReadableDatabase();
		verify(writeableDatabaseMock, times(1)).delete("ballot", BallotModel.COLUMN_ID + "=?", new String[]{"523"});
	}

	@Test
	public void testGetFirst_NoResult() {
		doCallRealMethod().when(factory).getFirst(any(String.class), any(String[].class));

		Cursor cursorMock = mock(Cursor.class);
		when(cursorMock.moveToFirst()).thenReturn(false);

		when(readableDatabaseMock.query(
			"ballot",
			null,
			"selection",
			new String[]{"a", "b", "c"},
			null,
			null,
			null
		)).thenReturn(cursorMock);

		Assert.assertNull(factory.getFirst("selection", new String[]{"a", "b", "c"}));
		verify(factory, times(0)).convert(any(Cursor.class));
		verify(factory, times(0)).getWritableDatabase();
		verify(readableDatabaseMock, times(1)).query(
			"ballot",
			null,
			"selection",
			new String[]{"a", "b", "c"},
			null,
			null,
			null
		);
	}

	@Test
	public void testGetFirst_Result() {
		doCallRealMethod().when(factory).getFirst(any(String.class), any(String[].class));

		Cursor cursorMock = mock(Cursor.class);
		BallotModel ballotModelMock = mock(BallotModel.class);

		when(cursorMock.moveToFirst()).thenReturn(true);

		when(readableDatabaseMock.query(
			"ballot",
			null,
			"selection",
			new String[]{"a", "b", "c"},
			null,
			null,
			null
		)).thenReturn(cursorMock);

		when(factory.convert(cursorMock)).thenReturn(ballotModelMock);

		Assert.assertEquals(ballotModelMock, factory.getFirst("selection", new String[]{"a", "b", "c"}));
		verify(factory, times(0)).getWritableDatabase();
		verify(factory, times(1)).convert(cursorMock);
		verify(readableDatabaseMock, times(1)).query(
			"ballot",
			null,
			"selection",
			new String[]{"a", "b", "c"},
			null,
			null,
			null
		);
	}

	@Test
	public void testCount() {
		// Set up mocks
		BallotService.BallotFilter filterMock = mock(BallotService.BallotFilter.class);
		Cursor cursorMock = mock(Cursor.class);
		mockStatic(DatabaseUtil.class);

		// Mock rules
		doCallRealMethod().when(factory).count(any());
		when(factory.runBallotFilterQuery(filterMock, "SELECT COUNT(*)")).thenReturn(cursorMock);
		when(DatabaseUtil.count(cursorMock)).thenReturn(18923890L);

		// Get count
		long count = factory.count(filterMock);
		Assert.assertEquals(18923890L, count);
		verify(factory, times(1)).runBallotFilterQuery(filterMock, "SELECT COUNT(*)");
	}

	@Test
	public void testRunBallotFilterQuery_GroupDefault() {
		doCallRealMethod().when(factory).runBallotFilterQuery(any(BallotService.BallotFilter.class), any(String.class));
		mockStatic(DatabaseUtil.class);

		String expectedQuery = "SELECT STERN FROM ballot b";
		expectedQuery += " INNER JOIN group_ballot l ON l.ballotId = b.id AND l.groupId = ?";
		expectedQuery += " WHERE (b.state IN (two-placeholder-argument))";
		expectedQuery += " ORDER BY b.createdAt DESC";

		List<String> expectedArguments = new ArrayList<>();
		// Group ID
		expectedArguments.add("12903");
		expectedArguments.add("OPEN");
		expectedArguments.add("TEMPORARY");

		BallotService.BallotFilter filterMock = mock(BallotService.BallotFilter.class);
		GroupMessageReceiver receiverMock = mock(GroupMessageReceiver.class);
		GroupModel groupMock = mock(GroupModel.class);
		String[] convertedArgumentsMock = new String[]{"a", "b", "c"};
		Cursor cursorMock = mock(Cursor.class);

		//noinspection unchecked,rawtypes
		when(filterMock.getReceiver()).thenReturn((MessageReceiver) receiverMock);
		when(filterMock.createdOrNotVotedByIdentity()).thenReturn(null);
		when(filterMock.getStates()).thenReturn(new BallotModel.State[]{BallotModel.State.OPEN, BallotModel.State.TEMPORARY});
		when(receiverMock.getType()).thenReturn(MessageReceiver.Type_GROUP);
		when(receiverMock.getGroup()).thenReturn(groupMock);
		when(groupMock.getId()).thenReturn(12903);
		when(DatabaseUtil.convertArguments(expectedArguments)).thenReturn(convertedArgumentsMock);
		when(DatabaseUtil.makePlaceholders(2)).thenReturn("two-placeholder-argument");
		when(readableDatabaseMock.rawQuery(expectedQuery, convertedArgumentsMock)).thenReturn(cursorMock);

		Assert.assertEquals(
			"Cursor mock was not returned. Maybe the `expectedQuery` is wrong?",
			cursorMock,
			factory.runBallotFilterQuery(filterMock, "SELECT STERN")
		);

		verify(factory, never()).getWritableDatabase();
		verify(readableDatabaseMock, times(1)).rawQuery(expectedQuery, convertedArgumentsMock);
		verify(filterMock, atLeast(1)).getReceiver();
	}

	@Test
	public void testRunBallotFilterQuery_UserDefault() {
		doCallRealMethod().when(factory).runBallotFilterQuery(any(BallotService.BallotFilter.class), any(String.class));
		mockStatic(DatabaseUtil.class);

		String expectedQuery = "SELECT STERN FROM ballot b";
		expectedQuery += " INNER JOIN identity_ballot l ON l.ballotId = b.id AND l.identity = ?";
		expectedQuery += " WHERE (b.state IN (two-placeholder-argument))";
		expectedQuery += " ORDER BY b.createdAt DESC";

		List<String> expectedArguments = new ArrayList<>();
		// Group ID
		expectedArguments.add("C-I-D");
		expectedArguments.add("OPEN");
		expectedArguments.add("TEMPORARY");

		BallotService.BallotFilter filterMock = mock(BallotService.BallotFilter.class);
		ContactMessageReceiver receiverMock = mock(ContactMessageReceiver.class);
		ContactModel contactModelMock = mock(ContactModel.class);
		String[] convertedArgumentsMock = new String[]{"a", "b", "c"};
		Cursor cursorMock = mock(Cursor.class);

		//noinspection unchecked,rawtypes
		when(filterMock.getReceiver()).thenReturn((MessageReceiver) receiverMock);
		when(filterMock.createdOrNotVotedByIdentity()).thenReturn(null);
		when(filterMock.getStates()).thenReturn(new BallotModel.State[]{BallotModel.State.OPEN, BallotModel.State.TEMPORARY});
		when(receiverMock.getType()).thenReturn(MessageReceiver.Type_CONTACT);
		when(receiverMock.getContact()).thenReturn(contactModelMock);
		when(contactModelMock.getIdentity()).thenReturn("C-I-D");
		when(DatabaseUtil.convertArguments(expectedArguments)).thenReturn(convertedArgumentsMock);
		when(DatabaseUtil.makePlaceholders(2)).thenReturn("two-placeholder-argument");
		when(readableDatabaseMock.rawQuery(expectedQuery, convertedArgumentsMock)).thenReturn(cursorMock);

		Assert.assertEquals(
			"Cursor mock was not returned. Maybe the `expectedQuery` is wrong?",
			cursorMock,
			factory.runBallotFilterQuery(filterMock, "SELECT STERN")
		);

		verify(factory, never()).getWritableDatabase();
		verify(readableDatabaseMock, times(1)).rawQuery(expectedQuery, convertedArgumentsMock);
		verify(filterMock, atLeast(1)).getReceiver();
	}

	@Test
	public void testRunBallotFilterQuery_GroupCreatedOrNotVotedByIdentity() {
		doCallRealMethod().when(factory).runBallotFilterQuery(any(BallotService.BallotFilter.class), any(String.class));
		mockStatic(DatabaseUtil.class);

		String expectedQuery = "SELECT STERN FROM ballot b";
		expectedQuery += " INNER JOIN group_ballot l ON l.ballotId = b.id AND l.groupId = ?";
		expectedQuery += " WHERE (b.state IN (two-placeholder-argument))";
		expectedQuery += " AND (b.creatorIdentity = ? OR NOT EXISTS (SELECT sv.ballotId FROM ballot_vote sv WHERE sv.votingIdentity = ? AND sv.ballotId = b.id))";
		expectedQuery += " ORDER BY b.createdAt DESC";

		List<String> expectedArguments = new ArrayList<>();
		// Group ID
		expectedArguments.add("12903");
		// Be sure the FILTER-ID is set two times (see query)
		expectedArguments.add("OPEN");
		expectedArguments.add("TEMPORARY");
		expectedArguments.add("FILTER-ID");
		expectedArguments.add("FILTER-ID");

		BallotService.BallotFilter filterMock = mock(BallotService.BallotFilter.class);
		GroupMessageReceiver receiverMock = mock(GroupMessageReceiver.class);
		GroupModel groupMock = mock(GroupModel.class);
		String[] convertedArgumentsMock = new String[]{"a", "b", "c"};
		Cursor cursorMock = mock(Cursor.class);

		//noinspection unchecked,rawtypes
		when(filterMock.getReceiver()).thenReturn((MessageReceiver) receiverMock);
		when(filterMock.createdOrNotVotedByIdentity()).thenReturn("FILTER-ID");
		when(filterMock.getStates()).thenReturn(new BallotModel.State[]{BallotModel.State.OPEN, BallotModel.State.TEMPORARY});
		when(receiverMock.getType()).thenReturn(MessageReceiver.Type_GROUP);
		when(receiverMock.getGroup()).thenReturn(groupMock);
		when(groupMock.getId()).thenReturn(12903);
		when(DatabaseUtil.convertArguments(expectedArguments)).thenReturn(convertedArgumentsMock);
		when(DatabaseUtil.makePlaceholders(2)).thenReturn("two-placeholder-argument");
		when(readableDatabaseMock.rawQuery(expectedQuery, convertedArgumentsMock)).thenReturn(cursorMock);

		Assert.assertEquals(
			"Cursor mock was not returned. Maybe the `expectedQuery` is wrong?",
			cursorMock,
			factory.runBallotFilterQuery(filterMock, "SELECT STERN")
		);

		verify(factory, never()).getWritableDatabase();
		verify(readableDatabaseMock, times(1)).rawQuery(expectedQuery, convertedArgumentsMock);
		verify(filterMock, atLeast(1)).getReceiver();
	}

	@Test
	public void testRunBallotFilterQuery_NoFilter() {
		doCallRealMethod().when(factory).runBallotFilterQuery(any(BallotService.BallotFilter.class), any(String.class));
		mockStatic(DatabaseUtil.class);

		String expectedQuery = "SELECT STERN FROM ballot b";
		expectedQuery += " INNER JOIN identity_ballot l ON l.ballotId = b.id AND l.identity = ?";
		expectedQuery += " ORDER BY b.createdAt DESC";

		List<String> expectedArguments = new ArrayList<>();
		// Group ID
		expectedArguments.add("C-I-D");

		BallotService.BallotFilter filterMock = mock(BallotService.BallotFilter.class);
		ContactMessageReceiver receiverMock = mock(ContactMessageReceiver.class);
		ContactModel contactModelMock = mock(ContactModel.class);
		String[] convertedArgumentsMock = new String[]{"a", "b", "c"};
		Cursor cursorMock = mock(Cursor.class);

		//noinspection unchecked,rawtypes
		when(filterMock.getReceiver()).thenReturn((MessageReceiver) receiverMock);
		when(filterMock.createdOrNotVotedByIdentity()).thenReturn(null);
		when(filterMock.getStates()).thenReturn(null);
		when(receiverMock.getType()).thenReturn(MessageReceiver.Type_CONTACT);
		when(receiverMock.getContact()).thenReturn(contactModelMock);
		when(contactModelMock.getIdentity()).thenReturn("C-I-D");
		when(DatabaseUtil.convertArguments(expectedArguments)).thenReturn(convertedArgumentsMock);
		when(readableDatabaseMock.rawQuery(expectedQuery, convertedArgumentsMock)).thenReturn(cursorMock);

		Assert.assertEquals(
			"Cursor mock was not returned. Maybe the `expectedQuery` is wrong?",
			cursorMock,
			factory.runBallotFilterQuery(filterMock, "SELECT STERN")
		);

		verify(factory, never()).getWritableDatabase();
		verify(readableDatabaseMock, times(1)).rawQuery(expectedQuery, convertedArgumentsMock);
		verify(filterMock, atLeast(1)).getReceiver();
	}

	@Test
	public void testRunBallotFilterQuery_DistributionList() {
		doCallRealMethod().when(factory).runBallotFilterQuery(any(BallotService.BallotFilter.class), any(String.class));
		mockStatic(DatabaseUtil.class);


		List<String> expectedArguments = new ArrayList<>();
		// Group ID
		expectedArguments.add("C-I-D");

		BallotService.BallotFilter filterMock = mock(BallotService.BallotFilter.class);
		DistributionListMessageReceiver receiverMock = mock(DistributionListMessageReceiver.class);

		//noinspection unchecked,rawtypes
		when(filterMock.getReceiver()).thenReturn((MessageReceiver) receiverMock);
		when(filterMock.createdOrNotVotedByIdentity()).thenReturn(null);
		when(filterMock.getStates()).thenReturn(null);
		when(receiverMock.getType()).thenReturn(MessageReceiver.Type_DISTRIBUTION_LIST);

		Assert.assertNull(factory.runBallotFilterQuery(filterMock, "SELECT STERN"));

		verify(factory, never()).getWritableDatabase();
		verify(factory, never()).getReadableDatabase();
	}
	private Date randomDate() {
		// Get a new random instance, seeded from the clock
		Random rnd = new Random();
		return new Date(-946771200000L + (Math.abs(rnd.nextLong()) % (70L * 365 * 24 * 60 * 60 * 1000)));
	}

}

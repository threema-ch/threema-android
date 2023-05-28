/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2021-2023 Threema GmbH
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

package ch.threema.app.services.systemupdate;

import net.sqlcipher.database.SQLiteDatabase;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.Arrays;

import ch.threema.storage.DatabaseServiceNew;
import ch.threema.storage.factories.GroupInviteModelFactory;
import ch.threema.storage.factories.IncomingGroupJoinRequestModelFactory;
import ch.threema.storage.factories.OutgoingGroupJoinRequestModelFactory;

public class SystemUpdateToVersion65Test {

	private DatabaseServiceNew databaseServiceMock;
	private SQLiteDatabase sqLiteDatabaseMock;

	@Before
	public void createMocks() {
		this.databaseServiceMock = Mockito.mock(DatabaseServiceNew.class);
		this.sqLiteDatabaseMock = Mockito.mock(SQLiteDatabase.class);
	}

	@Test
	public void runAsyncTest() {
		SystemUpdateToVersion65 update = new SystemUpdateToVersion65(this.databaseServiceMock, this.sqLiteDatabaseMock);

		Assert.assertTrue(update.runASync());
	}

	@Test
	public void getTextTest() {
		SystemUpdateToVersion65 update = new SystemUpdateToVersion65(this.databaseServiceMock, this.sqLiteDatabaseMock);

		Assert.assertEquals("version 65", update.getText());
	}


	@Test
	public void runDirectly() {
		SystemUpdateToVersion65 update = new SystemUpdateToVersion65(this.databaseServiceMock, this.sqLiteDatabaseMock);

		Mockito.when(this.databaseServiceMock.getGroupInviteModelFactory())
			.thenReturn(Mockito.mock(GroupInviteModelFactory.class));
		Mockito.when(this.databaseServiceMock.getOutgoingGroupJoinRequestModelFactory())
			.thenReturn(Mockito.mock(OutgoingGroupJoinRequestModelFactory.class));
		Mockito.when(this.databaseServiceMock.getIncomingGroupJoinRequestModelFactory())
			.thenReturn(Mockito.mock(IncomingGroupJoinRequestModelFactory.class));

		Mockito.when(this.databaseServiceMock.getGroupInviteModelFactory().getStatements())
			.thenReturn(new String[]{"invite1", "invite2"});
		Mockito.when(this.databaseServiceMock.getOutgoingGroupJoinRequestModelFactory().getStatements())
			.thenReturn(new String[]{"request1"});
		Mockito.when(this.databaseServiceMock.getIncomingGroupJoinRequestModelFactory().getStatements())
			.thenReturn(new String[]{"incomming1"});

		update.runDirectly();

		for (String statement : Arrays.asList("invite1", "invite2", "request1", "incomming1")) {
			Mockito.verify(this.sqLiteDatabaseMock).execSQL(statement);
		}
	}
}

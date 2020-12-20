/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2014-2020 Threema GmbH
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

import java.sql.SQLException;

import ch.threema.app.services.UpdateSystemService;
import ch.threema.storage.DatabaseServiceNew;
import ch.threema.storage.factories.ModelFactory;

public class SystemUpdateToVersion25 implements UpdateSystemService.SystemUpdate {

	private final DatabaseServiceNew databaseService;
	private final SQLiteDatabase sqLiteDatabase;


	public SystemUpdateToVersion25(DatabaseServiceNew databaseService, SQLiteDatabase sqLiteDatabase) {

		this.databaseService = databaseService;
		this.sqLiteDatabase = sqLiteDatabase;
	}
//
//	private void drop(String t) {
//		try {
//			if(
//					this.connectionSource.getReadWriteConnection()
//							.isTableExists(t)) {
//				this.connectionSource.getReadWriteConnection().executeStatement("DROP TABLE " + t, DatabaseConnection.DEFAULT_RESULT_FLAGS);
//			}
//		} catch (SQLException e) {
//			e.printStackTrace();
//		}
//	}
	@Override
	public boolean runDirectly() throws SQLException {

//		this.drop(BallotModel.TABLE);
//		this.drop(BallotChoiceModel.TABLE);
//		this.drop(BallotVoteModel.TABLE);
//		this.drop(IdentityBallotModel.TABLE);
//		this.drop(GroupBallotModel.TABLE);

		for(ModelFactory f: new ModelFactory[]{
				this.databaseService.getBallotModelFactory(),
				this.databaseService.getBallotChoiceModelFactory(),
				this.databaseService.getBallotVoteModelFactory(),
				this.databaseService.getIdentityBallotModelFactory(),
				this.databaseService.getGroupBallotModelFactory()
		}){
			for(String s: f.getStatements()) {
				this.sqLiteDatabase.execSQL(s);
			}
		}

		return true;
	}

	@Override
	public boolean runASync() {
		return true;
	}

	@Override
	public String getText() {
		return "version 25";
	}
}

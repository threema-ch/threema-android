/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2014-2021 Threema GmbH
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

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.Context;
import android.database.Cursor;

import net.sqlcipher.database.SQLiteDatabase;

import java.util.Arrays;
import java.util.HashSet;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;
import ch.threema.app.R;
import ch.threema.app.collections.Functional;
import ch.threema.app.collections.IPredicateNonNull;
import ch.threema.app.services.UpdateSystemService;
import ch.threema.app.stores.PreferenceStore;

public class SystemUpdateToVersion6 implements UpdateSystemService.SystemUpdate {

	private final Context context;
	private final SQLiteDatabase sqLiteDatabase;

	public SystemUpdateToVersion6(Context context, SQLiteDatabase sqLiteDatabase) {
		this.context = context;
		this.sqLiteDatabase = sqLiteDatabase;
	}

	@Override
	public boolean runDirectly() {

		String[] messageTableColumnNames = sqLiteDatabase.rawQuery("SELECT * FROM contacts LIMIT 0", null).getColumnNames();

		boolean threemaAndroidContactIdExists = Functional.select(Arrays.asList(messageTableColumnNames), new IPredicateNonNull<String>() {
			@Override
			public boolean apply(@NonNull String type) {
				return type.equals("threemaAndroidContactId");
			}
		}) != null;

		if(!threemaAndroidContactIdExists) {
			sqLiteDatabase.rawExecSQL("ALTER TABLE contacts ADD COLUMN threemaAndroidContactId VARCHAR(255) DEFAULT NULL");
		}

		//get all contacts to save the threema android contact id

		AccountManager accountManager = AccountManager.get(this.context);
		final String myIdentity = PreferenceManager.getDefaultSharedPreferences(this.context).getString(PreferenceStore.PREFS_IDENTITY, null);

		if(myIdentity != null) {
			Account account = Functional.select(new HashSet<Account>(Arrays.asList(accountManager.getAccountsByType(context.getString(R.string.package_name)))), new IPredicateNonNull<Account>() {
				@Override
				public boolean apply(@NonNull Account type) {
					return type.name.equals(myIdentity);
				}
			});

			if(account != null) {
				Cursor contacts = sqLiteDatabase.rawQuery("SELECT identity, androidContactId, firstName, lastName FROM contacts", null);
				while(contacts.moveToNext()) {
					String identity = contacts.getString(0);
					String androidContactId = contacts.getString(1);
					String f = contacts.getString(2);
					String l = contacts.getString(3);

					String name = new StringBuilder()
							.append(f!=null?f:"")
							.append(f!=null?" ":"")
							.append(l!=null?l:"")
							.toString()
							.trim();

					if ((name.length() == 0) || (f == null && l == null)) {
						name = identity;
					}

					if(identity == null || identity.length() == 0) {
						continue;
					}
				}
				contacts.close();
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
		return "version 6";
	}
}

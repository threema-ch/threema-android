/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2018-2024 Threema GmbH
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

import android.Manifest;
import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.ContentResolver;
import android.content.Context;
import android.provider.ContactsContract;

import androidx.preference.PreferenceManager;

import org.slf4j.Logger;

import java.sql.SQLException;

import ch.threema.app.BuildFlavor;
import ch.threema.app.R;
import ch.threema.app.services.UpdateSystemService;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.SynchronizeContactsUtil;
import ch.threema.base.utils.LoggingUtil;

/**
 *  Fix Contact sync account type for Threema Libre
 */
public class SystemUpdateToVersion91 implements UpdateSystemService.SystemUpdate {
	public static final int VERSION = 91;
	private static final Logger logger = LoggingUtil.getThreemaLogger("SystemUpdateToVersion91");

	private final Context context;

	public SystemUpdateToVersion91(Context context) {
		this.context = context;
	}

	@Override
	public boolean runDirectly() throws SQLException {
		if (BuildFlavor.isLibre()) {
			final boolean isSyncContacts = PreferenceManager.getDefaultSharedPreferences(this.context).getBoolean(context.getString(R.string.preferences__sync_contacts), false);

			if (!SynchronizeContactsUtil.isRestrictedProfile(context) && isSyncContacts) {
				if (ConfigUtils.isPermissionGranted(context, Manifest.permission.WRITE_CONTACTS)) {
					final AccountManager accountManager = AccountManager.get(this.context);

					if (accountManager != null) {
						try {
							for (Account account : accountManager.getAccountsByTypeForPackage("ch.threema.app", context.getPackageName())) {
								if (account.name.equals(context.getString(R.string.app_name))) {
									accountManager.removeAccount(account, null, null);
								}
							}

							// we don't need to wait until removal is complete to create a new account that differs from the existing one(s)
							Account newAccount = new Account(context.getString(R.string.app_name), context.getString(R.string.package_name));
							accountManager.addAccountExplicitly(newAccount, "", null);
							ContentResolver.setIsSyncable(newAccount, ContactsContract.AUTHORITY, 1);
							if (!ContentResolver.getSyncAutomatically(newAccount, ContactsContract.AUTHORITY)) {
								ContentResolver.setSyncAutomatically(newAccount, ContactsContract.AUTHORITY, true);
							}
						} catch (Exception e) {
							logger.error("Exception", e);
						}
					}
				}
			}
		}
		return true;
	}

	@Override
	public boolean runAsync() {
		return true;
	}

	@Override
	public String getText() {
		return "version " + VERSION + " (fix libre account type)";
	}
}

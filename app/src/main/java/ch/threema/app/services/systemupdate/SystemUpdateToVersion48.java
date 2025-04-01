/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2018-2025 Threema GmbH
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

import org.slf4j.Logger;

import java.sql.SQLException;
import java.util.Arrays;

import androidx.preference.PreferenceManager;
import ch.threema.app.R;
import ch.threema.app.services.UpdateSystemService;
import ch.threema.app.stores.PreferenceStore;
import ch.threema.base.utils.LoggingUtil;

/**
 * rename account manager accounts
 */
public class SystemUpdateToVersion48 implements UpdateSystemService.SystemUpdate {
    private static final Logger logger = LoggingUtil.getThreemaLogger("SystemUpdateToVersion48");

    private final Context context;

    public SystemUpdateToVersion48(Context context) {
        this.context = context;
    }

    @Override
    public boolean runDirectly() throws SQLException {
        final AccountManager accountManager = AccountManager.get(this.context);
        final String myIdentity = PreferenceManager.getDefaultSharedPreferences(this.context).getString(PreferenceStore.PREFS_IDENTITY, null);

        if (accountManager != null && myIdentity != null) {
            try {
                Account accountToRename = null;

                for (Account account : Arrays.asList(accountManager.getAccountsByType(context.getPackageName()))) {
                    if (account.name.equals(myIdentity)) {
                        accountToRename = account;
                    } else {
                        if (!account.name.equals(context.getString(R.string.title_mythreemaid))) {
                            accountManager.removeAccount(account, null, null);
                        }
                    }
                }

                // rename old-style ID-based account to generic name
                if (accountToRename != null) {
                    accountManager.renameAccount(accountToRename, context.getString(R.string.title_mythreemaid), null, null);
                }
                return true;
            } catch (Exception e) {
                logger.error("Exception", e);
            }
        }
        return false;
    }

    @Override
    public boolean runAsync() {
        return true;
    }

    @Override
    public String getText() {
        return "version 48";
    }
}

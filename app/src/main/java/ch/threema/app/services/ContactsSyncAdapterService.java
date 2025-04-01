/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2013-2025 Threema GmbH
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

package ch.threema.app.services;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

import ch.threema.app.adapters.ContactsSyncAdapter;

public class ContactsSyncAdapterService extends Service {

    private static ContactsSyncAdapter contactsSyncAdapter = null;
    private static final Object syncAdapterLock = new Object();

    private static boolean isSyncEnabled = true;

    public static void enableSync() {
        synchronized (syncAdapterLock) {
            isSyncEnabled = true;
            setAdapterSyncEnabled();
        }
    }

    public static void disableSync() {
        synchronized (syncAdapterLock) {
            isSyncEnabled = false;
            setAdapterSyncEnabled();
        }
    }

    private static void setAdapterSyncEnabled() {
        synchronized (syncAdapterLock) {
            if (contactsSyncAdapter != null) {
                contactsSyncAdapter.setSyncEnabled(isSyncEnabled);
            }
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();

        synchronized (syncAdapterLock) {
            if (contactsSyncAdapter == null) {
                contactsSyncAdapter = new ContactsSyncAdapter(getApplicationContext(), true);
                contactsSyncAdapter.setSyncEnabled(isSyncEnabled);
            }
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return contactsSyncAdapter.getSyncAdapterBinder();
    }
}

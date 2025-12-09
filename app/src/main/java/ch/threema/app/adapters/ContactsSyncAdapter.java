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

package ch.threema.app.adapters;

import android.accounts.Account;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.Context;
import android.content.SyncResult;
import android.os.Bundle;

import org.slf4j.Logger;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.managers.ListenerManager;
import ch.threema.app.managers.ServiceManager;
import ch.threema.app.routines.SynchronizeContactsRoutine;
import ch.threema.app.services.SynchronizeContactsService;
import ch.threema.app.utils.IntentDataUtil;
import static ch.threema.base.utils.LoggingKt.getThreemaLogger;
import ch.threema.localcrypto.exceptions.MasterKeyLockedException;

public class ContactsSyncAdapter extends AbstractThreadedSyncAdapter {
    private static final Logger logger = getThreemaLogger("ContactsSyncAdapter");

    private boolean isSyncEnabled = true;

    public ContactsSyncAdapter(Context context, boolean autoInitialize) {
        super(context, autoInitialize);
    }

    public void setSyncEnabled(boolean enabled) {
        isSyncEnabled = enabled;
    }

    @Override
    public void onPerformSync(Account account, Bundle extras, String authority, ContentProviderClient provider, SyncResult syncResult) {
        logger.info("onPerformSync");

        if (!isSyncEnabled) {
            logger.info("Contact sync is disabled; retry later.");
            // Workaround to trigger a soft error to retry the sync at a later moment
            // See
            //  - https://developer.android.com/reference/android/content/SyncResult#hasSoftError()
            //  - https://developer.android.com/reference/android/content/SyncResult#SyncResult()
            syncResult.stats.numIoExceptions++;
            return;
        }

        try {
            ServiceManager serviceManager = ThreemaApplication.getServiceManager();

            if (serviceManager == null) {
                return;
            }

            if (serviceManager.getPreferenceService().isSyncContacts()) {
                logger.info("Start sync adapter run");
                SynchronizeContactsService synchronizeContactsService = serviceManager.getSynchronizeContactsService();

                if (synchronizeContactsService.isFullSyncInProgress()) {
                    logger.info("A full sync is already running");
                    syncResult.stats.numUpdates = 0;
                    syncResult.stats.numInserts = 0;
                    syncResult.stats.numDeletes = 0;
                    syncResult.stats.numEntries = 0;
                    return;
                }

                SynchronizeContactsRoutine routine = synchronizeContactsService.instantiateSynchronization();
                //update stats on finished to resolve the "every minute sync" bug

                routine.addOnFinished((success, modifiedAccounts, createdContacts, deletedAccounts) -> {
                    // let user know that contact was added
                    ListenerManager.newSyncedContactListener.handle(listener -> listener.onNew(createdContacts));

                    //hack to not schedule the next sync!
                    syncResult.stats.numUpdates = 0;//modifiedAccounts;
                    syncResult.stats.numInserts = 0;//createdAccounts;
                    syncResult.stats.numDeletes = 0;//deletedAccounts;
                    syncResult.stats.numEntries = 0;//createdAccounts;

                    //send a broadcast to let others know that the list has changed
                    LocalBroadcastManager.getInstance(ThreemaApplication.getAppContext()).sendBroadcast(IntentDataUtil.createActionIntentContactsChanged());
                });

                // not in a thread: `onPerformSync` is already called in a background thread
                routine.run();
            }
        } catch (MasterKeyLockedException e) {
            logger.debug("MasterKeyLockedException [" + e.getMessage() + "]");

        } finally {
            logger.debug(
                "sync finished Sync [numEntries={}, updates={}, inserts={}, deletes={}",
                syncResult.stats.numEntries,
                syncResult.stats.numUpdates,
                syncResult.stats.numInserts,
                syncResult.stats.numDeletes
            );
        }
    }

    @Override
    public void onSyncCanceled() {
        logger.info("onSyncCanceled");
        super.onSyncCanceled();
    }

    @Override
    public void onSyncCanceled(Thread thread) {
        logger.info("onSyncCanceled");
        super.onSyncCanceled(thread);
    }
}

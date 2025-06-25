/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2014-2025 Threema GmbH
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

package ch.threema.app.systemupdates.updates;

import org.slf4j.Logger;

import androidx.annotation.NonNull;
import ch.threema.app.managers.ServiceManager;
import ch.threema.app.preference.service.PreferenceService;
import ch.threema.app.services.SynchronizeContactsService;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.localcrypto.MasterKeyLockedException;

public class SystemUpdateToVersion14 implements SystemUpdate {
    private static final Logger logger = LoggingUtil.getThreemaLogger("SystemUpdateToVersion14");

    private @NonNull final ServiceManager serviceManager;

    public SystemUpdateToVersion14(@NonNull ServiceManager serviceManager) {
        this.serviceManager = serviceManager;
    }

    @Override
    public void run() {
        //check if auto sync is enabled
        PreferenceService preferenceService = serviceManager.getPreferenceService();
        if (preferenceService.isSyncContacts()) {
            //disable sync
            final SynchronizeContactsService synchronizeContactService;
            try {
                synchronizeContactService = serviceManager.getSynchronizeContactsService();
            } catch (MasterKeyLockedException e) {
                throw new RuntimeException("Failed to get synchronize contacts service", e);
            }
            synchronizeContactService.disableSyncFromLocal(new Runnable() {
                @Override
                public void run() {
                    synchronizeContactService.enableSyncFromLocal();
                }
            });
        }
    }

    @Override
    public int getVersion() {
        return 13;
    }
}

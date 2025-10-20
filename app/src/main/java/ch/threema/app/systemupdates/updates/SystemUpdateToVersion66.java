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

package ch.threema.app.systemupdates.updates;

import android.Manifest;
import android.content.Context;

import org.slf4j.Logger;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresPermission;
import ch.threema.app.managers.ServiceManager;
import ch.threema.app.preference.service.PreferenceService;
import ch.threema.app.utils.AndroidContactUtil;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.localcrypto.exceptions.MasterKeyLockedException;

public class SystemUpdateToVersion66 implements SystemUpdate {
    public static final int VERSION = 66;
    private static final Logger logger = LoggingUtil.getThreemaLogger("SystemUpdateToVersion66");
    private @NonNull final Context context;
    private @NonNull final ServiceManager serviceManager;

    public SystemUpdateToVersion66(@NonNull Context context, @NonNull ServiceManager serviceManager) {
        this.context = context;
        this.serviceManager = serviceManager;
    }

    @Override
    public void run() {
        if (!ConfigUtils.isPermissionGranted(context, Manifest.permission.WRITE_CONTACTS)) {
            return; // best effort
        }

        forceContactResync();
    }

    @RequiresPermission(Manifest.permission.WRITE_CONTACTS)
    private void forceContactResync() {
        logger.info("Force a contacts resync");

        AndroidContactUtil androidContactUtil = AndroidContactUtil.getInstance();
        androidContactUtil.deleteAllThreemaRawContacts();

        PreferenceService preferenceService = serviceManager.getPreferenceService();
        if (preferenceService.isSyncContacts()) {
            try {
                serviceManager.getSynchronizeContactsService()
                    .instantiateSynchronizationAndRun();
            } catch (MasterKeyLockedException e) {
                logger.error("Failed to run contact sync", e);
            }
        }
    }

    @Override
    public String getDescription() {
        return "force a contacts resync";
    }

    @Override
    public int getVersion() {
        return VERSION;
    }
}

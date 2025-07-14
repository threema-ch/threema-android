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

package ch.threema.app.utils;

import android.Manifest;
import android.content.Context;
import android.os.Bundle;
import android.os.UserManager;

import org.slf4j.Logger;

import java.util.Set;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresPermission;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.managers.ServiceManager;
import ch.threema.app.routines.SynchronizeContactsRoutine;
import ch.threema.app.services.SynchronizeContactsService;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.localcrypto.MasterKeyLockedException;

public class SynchronizeContactsUtil {
    private static final Logger logger = LoggingUtil.getThreemaLogger("SynchronizeContactsUtil");

    @RequiresPermission(allOf = {Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS})
    public static void startDirectly() {
        logger.info("Starting contact sync");
        SynchronizeContactsRoutine routine = getSynchronizeContactsRoutine();
        if (routine == null) {
            logger.error("Could not start synchronize contacts routine directly");
            return;
        }
        routine.run();
    }

    @RequiresPermission(allOf = {Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS})
    public static void startDirectly(@NonNull String identity) {
        logger.info("Starting single contact sync for identity {}", identity);
        SynchronizeContactsRoutine routine = getSynchronizeContactsRoutine(Set.of(identity));
        if (routine == null) {
            logger.error("Could not start synchronize contacts routine directly for identity {}", identity);
            return;
        }
        routine.run();
    }

    @Nullable
    private static SynchronizeContactsService getSynchronizeContactsService() {
        ServiceManager serviceManager = ThreemaApplication.getServiceManager();
        if (serviceManager == null) {
            logger.error("Cannot get synchronize contacts service as service manager is null");
            return null;
        }
        try {
            return serviceManager.getSynchronizeContactsService();
        } catch (MasterKeyLockedException e) {
            //do nothing
            logger.error("Exception", e);
        }

        return null;
    }

    @Nullable
    private static SynchronizeContactsRoutine getSynchronizeContactsRoutine() {
        return getSynchronizeContactsRoutine(Set.of());
    }

    @Nullable
    private static SynchronizeContactsRoutine getSynchronizeContactsRoutine(Set<String> identities) {
        ServiceManager serviceManager = ThreemaApplication.getServiceManager();
        if (serviceManager == null) {
            logger.error("Cannot get synchronize contacts routine as service manager is unavailable");
            return null;
        }

        if (serviceManager.getPreferenceService().isSyncContacts()) {
            SynchronizeContactsService synchronizeContactsService = getSynchronizeContactsService();

            if (synchronizeContactsService != null) {
                return synchronizeContactsService.instantiateSynchronization(identities);
            }
        }

        return null;
    }

    public static boolean isRestrictedProfile(Context context) {
        UserManager um = (UserManager) context.getSystemService(Context.USER_SERVICE);
        Bundle restrictions = um.getUserRestrictions();
        // cannot add accounts or modify sync profiles
        return restrictions.getBoolean(UserManager.DISALLOW_MODIFY_ACCOUNTS, false);
    }
}

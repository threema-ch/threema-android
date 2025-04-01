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

import android.content.Context;
import android.os.Bundle;
import android.os.UserManager;

import org.slf4j.Logger;

import ch.threema.app.ThreemaApplication;
import ch.threema.app.exceptions.FileSystemNotPresentException;
import ch.threema.app.managers.ServiceManager;
import ch.threema.app.routines.SynchronizeContactsRoutine;
import ch.threema.app.services.PreferenceService;
import ch.threema.app.services.SynchronizeContactsService;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.localcrypto.MasterKeyLockedException;

public class SynchronizeContactsUtil {
    private static final Logger logger = LoggingUtil.getThreemaLogger("SynchronizeContactsUtil");

    public static void startDirectly() {
        SynchronizeContactsRoutine routine = getSynchronizeContactsRoutine();
        if (routine != null) {
            routine.run();
        }
    }

    public static void startDirectly(String forIdentity) {
        logger.info("Starting single contact sync for identity {}", forIdentity);
        SynchronizeContactsRoutine routine = getSynchronizeContactsRoutine();
        if (routine != null) {
            routine.addProcessIdentity(forIdentity);
            routine.run();
        }
    }

    private static SynchronizeContactsService getSynchronizeContactsService() {
        ServiceManager serviceManager = ThreemaApplication.getServiceManager();
        if (serviceManager == null) {
            return null;
        }
        try {
            SynchronizeContactsService synchronizeContactsService = serviceManager.getSynchronizeContactsService();
            return synchronizeContactsService;
        } catch (MasterKeyLockedException | FileSystemNotPresentException e) {
            //do nothing
            logger.error("Exception", e);
        }

        return null;
    }

    private static SynchronizeContactsRoutine getSynchronizeContactsRoutine() {
        ServiceManager serviceManager = ThreemaApplication.getServiceManager();
        if (serviceManager == null) {
            return null;
        }

        PreferenceService preferenceService = serviceManager.getPreferenceService();
        if (preferenceService == null) {
            return null;
        }


        if (preferenceService.isSyncContacts()) {
            SynchronizeContactsService synchronizeContactsService = getSynchronizeContactsService();

            if (synchronizeContactsService != null) {
                return synchronizeContactsService.instantiateSynchronization();
            }
        }

        return null;
    }

    public static boolean isRestrictedProfile(Context context) {
        UserManager um = (UserManager) context.getSystemService(Context.USER_SERVICE);
        Bundle restrictions = um.getUserRestrictions();
        if (restrictions.getBoolean(UserManager.DISALLOW_MODIFY_ACCOUNTS, false)) {
            // cannot add accounts or modify sync profiles
            return true;
        }
        return false;
    }
}

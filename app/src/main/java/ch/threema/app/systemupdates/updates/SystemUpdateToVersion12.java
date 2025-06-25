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

import androidx.annotation.NonNull;
import ch.threema.app.managers.ServiceManager;
import ch.threema.app.services.ContactService;
import ch.threema.app.preference.service.PreferenceService;
import ch.threema.app.services.UserService;
import ch.threema.app.utils.SynchronizeContactsUtil;
import ch.threema.localcrypto.MasterKeyLockedException;

public class SystemUpdateToVersion12 implements SystemUpdate {
    private final @NonNull ServiceManager serviceManager;

    public SystemUpdateToVersion12(@NonNull ServiceManager serviceManager) {
        this.serviceManager = serviceManager;
    }

    @Override
    public void run() {
        SynchronizeContactsUtil.startDirectly();

        ContactService contactService;
        try {
            contactService = serviceManager.getContactService();
        } catch (MasterKeyLockedException e) {
            throw new RuntimeException("Failed to get contact service", e);
        }

        PreferenceService preferenceService = serviceManager.getPreferenceService();
        if (preferenceService.isSyncContacts()) {
            UserService userService = serviceManager.getUserService();
            if (userService != null) {
                userService.enableAccountAutoSync(true);
            }
        }
    }

    @Override
    public int getVersion() {
        return 12;
    }
}

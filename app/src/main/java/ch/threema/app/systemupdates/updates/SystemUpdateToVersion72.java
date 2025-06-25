/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2022-2025 Threema GmbH
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
import ch.threema.localcrypto.MasterKeyLockedException;
import ch.threema.storage.models.ContactModel;

/**
 * For ID colors we store the first byte of the SHA-256 hash of the contact identity.
 */
public class SystemUpdateToVersion72 implements SystemUpdate {
    public static final int VERSION = 72;

    private @NonNull final ServiceManager serviceManager;

    public SystemUpdateToVersion72(@NonNull ServiceManager serviceManager) {
        this.serviceManager = serviceManager;
    }

    @Override
    public void run() {
        final ContactService contactService;
        try {
            contactService = serviceManager.getContactService();
        } catch (MasterKeyLockedException e) {
            throw new RuntimeException(e);
        }
        for (ContactModel contact : contactService.getAll()) {
            contact.initializeIdColor();
            contactService.save(contact);
        }
    }

    @Override
    public int getVersion() {
        return VERSION;
    }
}

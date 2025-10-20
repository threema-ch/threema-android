/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2017-2025 Threema GmbH
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
import ch.threema.domain.models.IdentityState;
import ch.threema.domain.protocol.ThreemaFeature;
import ch.threema.localcrypto.exceptions.MasterKeyLockedException;

/**
 * Update all Contacts with Feature Level < current Feature Level
 */
public class SystemUpdateToVersion39 implements SystemUpdate {

    private final @NonNull ServiceManager serviceManager;

    public SystemUpdateToVersion39(@NonNull ServiceManager serviceManager) {
        this.serviceManager = serviceManager;
    }

    @Override
    public void run() {
        ContactService contactService;
        try {
            contactService = serviceManager.getContactService();
        } catch (MasterKeyLockedException e) {
            throw new RuntimeException("Failed to get contact service", e);
        }

        // call find with fetchMissingFeatureLevel = true to fetch all contacts without current feature level
        contactService.find(new ContactService.Filter() {
            @Override
            public IdentityState[] states() {
                return null;
            }

            @Override
            public Long requiredFeature() {
                return ThreemaFeature.VOIP;
            }

            @Override
            public Boolean fetchMissingFeatureLevel() {
                return true;
            }

            @Override
            public Boolean includeMyself() {
                return true;
            }

            @Override
            public Boolean includeHidden() {
                return true;
            }

            @Override
            public Boolean onlyWithReceiptSettings() {
                return false;
            }
        });
    }

    @Override
    public String getDescription() {
        return "sync feature levels";
    }

    @Override
    public int getVersion() {
        return 39;
    }
}

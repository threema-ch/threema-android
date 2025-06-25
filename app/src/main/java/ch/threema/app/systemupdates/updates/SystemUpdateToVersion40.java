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
import ch.threema.app.preference.service.PreferenceService;
import ch.threema.app.utils.TestUtil;
import ch.threema.storage.models.WebClientSessionModel;

public class SystemUpdateToVersion40 implements SystemUpdate {
    private @NonNull final ServiceManager serviceManager;

    public SystemUpdateToVersion40(ServiceManager serviceManager) {
        this.serviceManager = serviceManager;
    }

    @Override
    public void run() {
        PreferenceService preferenceService = serviceManager.getPreferenceService();
        String currentPushToken = preferenceService.getPushToken();

        if (!TestUtil.isEmptyOrNull(currentPushToken)) {
            // update all
            serviceManager.getDatabaseService().getWritableDatabase()
                .execSQL(
                    "UPDATE " + WebClientSessionModel.TABLE + " " + "SET " + WebClientSessionModel.COLUMN_PUSH_TOKEN + "=?",
                    new String[]{currentPushToken}
                );
        }
    }

    @Override
    public int getVersion() {
        return 40;
    }
}

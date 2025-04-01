/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2015-2024 Threema GmbH
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

package ch.threema.app.activities;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.UiThread;
import ch.threema.app.ThreemaApplication;
import ch.threema.storage.models.GroupModel;

public class GroupNotificationsActivity extends NotificationsActivity {
    private GroupModel groupModel;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        int groupId = getIntent().getIntExtra(ThreemaApplication.INTENT_DATA_GROUP, 0);
        if (groupId == 0) {
            finish();
            return;
        }

        groupModel = groupService.getById(groupId);
        uid = groupService.getUniqueIdString(groupModel);

        refreshSettings();
    }

    public void refreshSettings() {
        defaultRingtone = ringtoneService.getDefaultGroupRingtone();
        selectedRingtone = ringtoneService.getGroupRingtone(uid);

        super.refreshSettings();
    }

    @Override
    void notifySettingsChanged() {
        this.conversationService.refresh(this.groupModel);
    }

    @Override
    @UiThread
    protected void updateUI() {
        super.updateUI();
    }

    @Override
    protected void setupButtons() {
        super.setupButtons();

        radioSilentExceptMentions.setVisibility(View.VISIBLE);
    }
}

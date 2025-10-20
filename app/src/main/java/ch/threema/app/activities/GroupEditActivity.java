/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2015-2025 Threema GmbH
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

import android.content.Intent;
import android.text.InputType;

import java.io.File;

import androidx.fragment.app.Fragment;
import ch.threema.app.R;
import ch.threema.app.dialogs.ContactEditDialog;
import ch.threema.storage.models.GroupModel;

public abstract class GroupEditActivity extends ThreemaToolbarActivity {
    protected static final String DIALOG_TAG_GROUPNAME = "groupName";

    @Override
    protected void handleDeviceInsets() {
        // Prevent super method behaviour
    }

    protected void launchGroupSetNameAndAvatarDialog() {
        final int inputType = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PERSON_NAME;
        ContactEditDialog.newInstance(
                R.string.edit_name,
                R.string.group_name,
                -1,
                inputType,
                null,
                false,
                GroupModel.GROUP_NAME_MAX_LENGTH_BYTES)
            .show(getSupportFragmentManager(), DIALOG_TAG_GROUPNAME);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        try {
            Fragment fragment = getSupportFragmentManager().findFragmentByTag(DIALOG_TAG_GROUPNAME);
            if (fragment != null && fragment.isAdded()) {
                fragment.onActivityResult(requestCode, resultCode, data);
            }
        } catch (Exception e) {
            //
        }
    }
}

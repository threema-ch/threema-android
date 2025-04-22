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
import android.os.Bundle;
import android.widget.Toast;

import org.slf4j.Logger;

import java.io.File;
import java.util.Set;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.groupflows.GroupCreateProperties;
import ch.threema.app.groupflows.ProfilePicture;
import ch.threema.app.dialogs.ContactEditDialog;
import ch.threema.app.services.GroupFlowDispatcher;
import ch.threema.app.utils.IntentDataUtil;
import ch.threema.app.utils.RuntimeUtil;
import ch.threema.base.ThreemaException;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.data.models.GroupModel;
import kotlinx.coroutines.Deferred;

public class GroupAdd2Activity extends GroupEditActivity implements ContactEditDialog.ContactEditDialogClickListener {
    private static final Logger logger = LoggingUtil.getThreemaLogger("GroupAdd2Activity");

    private static final String BUNDLE_GROUP_IDENTITIES = "grId";

    private String[] groupIdentities;

    @Override
    public int getLayoutResource() {
        return R.layout.activity_group_add2;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        logger.debug("onCreate");
        super.onCreate(savedInstanceState);

        final ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setTitle("");
        }

        if (getIntent() != null) {
            this.groupIdentities = IntentDataUtil.getContactIdentities(getIntent());
        }

        if (savedInstanceState == null) {
            launchGroupSetNameAndAvatarDialog();
        } else {
            groupIdentities = savedInstanceState.getStringArray(BUNDLE_GROUP_IDENTITIES);
        }
    }

    private void createGroup(
        @NonNull final String groupName,
        @NonNull final Set<String> groupIdentities,
        @Nullable final File avatarFile
    ) {
        GroupFlowDispatcher groupFlowDispatcher;
        try {
            groupFlowDispatcher = serviceManager.getGroupFlowDispatcher();
        } catch (ThreemaException e) {
            logger.error("Could not get group flow dispatcher", e);
            return;
        }

        Deferred<ch.threema.data.models.GroupModel> groupAddResult =
            groupFlowDispatcher.runCreateGroupFlow(
                getSupportFragmentManager(),
                this,
                new GroupCreateProperties(
                    groupName,
                    new ProfilePicture(avatarFile),
                    groupIdentities
                )
            );

        groupAddResult.invokeOnCompletion(throwable -> {
            ch.threema.data.models.GroupModel groupModel = groupAddResult.getCompleted();
            RuntimeUtil.runOnUiThread(() -> {
                if (groupModel != null) {
                    creatingGroupDone(groupModel);
                } else {
                    Toast.makeText(GroupAdd2Activity.this,
                        getString(R.string.error_creating_group) + ": " + getString(R.string.internet_connection_required), Toast.LENGTH_LONG).show();
                    setResult(RESULT_CANCELED);
                    finish();
                }
            });
            return null;
        });
    }

    private void creatingGroupDone(GroupModel newModel) {
        Toast.makeText(ThreemaApplication.getAppContext(),
            getString(R.string.group_created_confirm), Toast.LENGTH_LONG).show();

        Intent intent = new Intent(this, ComposeMessageActivity.class);
        intent.putExtra(ThreemaApplication.INTENT_DATA_GROUP_DATABASE_ID, (int) newModel.getDatabaseId());
        setResult(RESULT_OK);
        startActivity(intent);
        finish();
    }

    @Override
    public void onYes(String tag, String text1, String text2, @Nullable File avatarFile) {
        String groupName = text1 != null ? text1 : "";
        createGroup(groupName, Set.of(this.groupIdentities), avatarFile);
    }

    @Override
    public void onNo(String tag) {
        finish();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putSerializable(BUNDLE_GROUP_IDENTITIES, groupIdentities);

        super.onSaveInstanceState(outState);
    }
}

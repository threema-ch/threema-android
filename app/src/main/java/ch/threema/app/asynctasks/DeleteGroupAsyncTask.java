/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2018-2025 Threema GmbH
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

package ch.threema.app.asynctasks;

import android.os.AsyncTask;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.appcompat.app.AppCompatActivity;

import androidx.fragment.app.FragmentManager;
import ch.threema.app.R;
import ch.threema.app.dialogs.GenericProgressDialog;
import ch.threema.app.listeners.ConversationListener;
import ch.threema.app.managers.ListenerManager;
import ch.threema.app.services.GroupService;
import ch.threema.app.utils.DialogUtil;
import ch.threema.storage.models.GroupModel;

/**
 * Delete a group.
 * <p>
 * This will show a dialog while the process is ongoing.
 */
public class DeleteGroupAsyncTask extends AsyncTask<Void, Void, Void> {
    private static final String DIALOG_TAG = "lg";

    private final @NonNull GroupModel groupModel;
    private final @NonNull GroupService groupService;
    private final AppCompatActivity activity;
    private final Fragment fragment;
    private final @Nullable Runnable runOnCompletion;

    public DeleteGroupAsyncTask(
        @NonNull GroupModel groupModel,
        @NonNull GroupService groupService,
        AppCompatActivity activity,
        Fragment fragment,
        @Nullable Runnable runOnCompletion
    ) {

        this.groupModel = groupModel;
        this.groupService = groupService;
        this.activity = activity;
        this.fragment = fragment;
        this.runOnCompletion = runOnCompletion;
    }

    @Override
    protected void onPreExecute() {
        final FragmentManager fragmentManager = activity != null
            ? activity.getSupportFragmentManager()
            : fragment.getFragmentManager();
        GenericProgressDialog.newInstance(
            R.string.action_delete_group,
            R.string.please_wait
        ).show(fragmentManager, DIALOG_TAG);
    }

    @Override
    protected Void doInBackground(Void... params) {
        groupService.leaveGroupFromLocal(groupModel);
        groupService.remove(groupModel);
        return null;
    }

    @Override
    protected void onPostExecute(Void aVoid) {
        DialogUtil.dismissDialog(activity != null ? activity.getSupportFragmentManager() : fragment.getFragmentManager(), DIALOG_TAG, true);

        ListenerManager.conversationListeners.handle(ConversationListener::onModifiedAll);

        if (runOnCompletion != null) {
            runOnCompletion.run();
        }
    }
}

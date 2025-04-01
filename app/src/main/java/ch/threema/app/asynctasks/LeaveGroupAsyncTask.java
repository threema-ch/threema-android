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
import androidx.fragment.app.Fragment;
import androidx.appcompat.app.AppCompatActivity;

import ch.threema.app.R;
import ch.threema.app.dialogs.GenericProgressDialog;
import ch.threema.app.listeners.ConversationListener;
import ch.threema.app.listeners.GroupListener;
import ch.threema.app.managers.ListenerManager;
import ch.threema.app.services.GroupService;
import ch.threema.app.utils.DialogUtil;
import ch.threema.storage.models.GroupModel;

public class LeaveGroupAsyncTask extends AsyncTask<Void, Void, Void> {
    private static final String DIALOG_TAG = "lg";

    private final @NonNull GroupModel groupModel;
    private final GroupService groupService;
    private final Fragment fragment;
    private final AppCompatActivity activity;
    private final Runnable runOnCompletion;

    public LeaveGroupAsyncTask(
        @NonNull GroupModel groupModel,
        GroupService groupService,
        AppCompatActivity activity,
        Fragment fragment,
        Runnable runOnCompletion
    ) {

        this.groupModel = groupModel;
        this.groupService = groupService;
        this.activity = activity;
        this.fragment = fragment;
        this.runOnCompletion = runOnCompletion;
    }

    @Override
    protected void onPreExecute() {
        GenericProgressDialog.newInstance(R.string.action_leave_group, R.string.please_wait).show(activity != null ? activity.getSupportFragmentManager() : fragment.getFragmentManager(), DIALOG_TAG);
    }

    @Override
    protected Void doInBackground(Void... params) {
        groupService.leaveGroupFromLocal(groupModel);
        return null;
    }

    @Override
    protected void onPostExecute(Void aVoid) {
        DialogUtil.dismissDialog(activity != null ? activity.getSupportFragmentManager() : fragment.getFragmentManager(), DIALOG_TAG, true);
        ListenerManager.conversationListeners.handle(new ListenerManager.HandleListener<ConversationListener>() {
            @Override
            public void handle(ConversationListener listener) {
                listener.onModifiedAll();
            }
        });

        ListenerManager.groupListeners.handle(new ListenerManager.HandleListener<GroupListener>() {
            @Override
            public void handle(GroupListener listener) {
                listener.onLeave(groupModel);
            }
        });

        if (runOnCompletion != null) {
            runOnCompletion.run();
        }
    }
}

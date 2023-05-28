/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2018-2023 Threema GmbH
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
import androidx.fragment.app.Fragment;

import ch.threema.app.R;
import ch.threema.app.dialogs.GenericProgressDialog;
import ch.threema.app.listeners.ConversationListener;
import ch.threema.app.listeners.DistributionListListener;
import ch.threema.app.managers.ListenerManager;
import ch.threema.app.services.DistributionListService;
import ch.threema.app.utils.DialogUtil;
import ch.threema.storage.models.DistributionListModel;

public class DeleteDistributionListAsyncTask extends AsyncTask<Void, Void, Void> {
	private static final String DIALOG_TAG = "lg";

	private final DistributionListModel distributionListModel;
	private final DistributionListService distributionListService;
	private final Fragment fragment;
	private final Runnable runOnCompletion;

	public DeleteDistributionListAsyncTask(DistributionListModel distributionListModel,
	                                       DistributionListService distributionListService,
	                                       Fragment fragment,
	                                       Runnable runOnCompletion) {

		this.distributionListModel = distributionListModel;
		this.distributionListService = distributionListService;
		this.fragment = fragment;
		this.runOnCompletion = runOnCompletion;
	}

	@Override
	protected void onPreExecute() {
		GenericProgressDialog.newInstance(R.string.really_delete_distribution_list, R.string.please_wait).show(fragment.getFragmentManager(), DIALOG_TAG);
	}

	@Override
	protected Void doInBackground(Void... params) {
		distributionListService.remove(distributionListModel);
		return null;
	}

	@Override
	protected void onPostExecute(Void aVoid) {
		DialogUtil.dismissDialog(fragment.getFragmentManager(), DIALOG_TAG, true);
		ListenerManager.conversationListeners.handle(new ListenerManager.HandleListener<ConversationListener>() {
			@Override
			public void handle(ConversationListener listener) {
				listener.onModifiedAll();
			}
		});

		ListenerManager.distributionListListeners.handle(new ListenerManager.HandleListener<DistributionListListener>() {
			@Override
			public void handle(DistributionListListener listener) {
				listener.onRemove(distributionListModel);
			}
		});

		if (runOnCompletion != null) {
			runOnCompletion.run();
		}
	}
}

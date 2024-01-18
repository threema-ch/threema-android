/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2019-2024 Threema GmbH
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

import android.content.DialogInterface;
import android.os.AsyncTask;
import android.view.View;

import com.google.android.material.snackbar.Snackbar;

import org.slf4j.Logger;

import java.util.Iterator;
import java.util.List;

import androidx.fragment.app.FragmentManager;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.dialogs.CancelableHorizontalProgressDialog;
import ch.threema.app.listeners.ConversationListener;
import ch.threema.app.managers.ListenerManager;
import ch.threema.app.managers.ServiceManager;
import ch.threema.app.services.ConversationService;
import ch.threema.app.services.DistributionListService;
import ch.threema.app.services.GroupService;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.DialogUtil;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.storage.models.ConversationModel;

public class DeleteConversationsAsyncTask extends AsyncTask<Void, Integer, Integer> {
	private static final Logger logger = LoggingUtil.getThreemaLogger("DeleteConversationsAsyncTask");

	private static final String DIALOG_TAG = "dcon";

	private final FragmentManager fragmentManager;
	private final Runnable runOnCompletion;
	private final List<ConversationModel> conversationModels;
	private GroupService groupService;
	private DistributionListService distributionListService;
	private ConversationService conversationService;
	private final View feedbackView;

	private boolean cancelled = false;

	public DeleteConversationsAsyncTask(FragmentManager fragmentManager,
	                                    List<ConversationModel> conversationModels,
	                                    View feedbackView,
	                                    Runnable runOnCompletion) {

		this.fragmentManager = fragmentManager;
		this.runOnCompletion = runOnCompletion;
		this.conversationModels = conversationModels;
		this.feedbackView = feedbackView;

		ServiceManager serviceManager = ThreemaApplication.getServiceManager();

		try {
			this.groupService = serviceManager.getGroupService();
			this.distributionListService = serviceManager.getDistributionListService();
			this.conversationService = serviceManager.getConversationService();
		} catch (Exception e) {
			logger.error("Exception", e);
		}
	}

	@Override
	protected void onPreExecute() {
		CancelableHorizontalProgressDialog dialog = CancelableHorizontalProgressDialog.newInstance(R.string.deleting_thread, R.string.cancel, conversationModels.size());
		dialog.setOnCancelListener(new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				cancelled = true;
			}
		});
		dialog.show(fragmentManager, DIALOG_TAG);
	}

	@Override
	protected void onProgressUpdate(Integer... index) {
		DialogUtil.updateProgress(fragmentManager, DIALOG_TAG, index[0]);
	}

	@Override
	protected Integer doInBackground(Void... params) {
		int i = 0;
		Iterator<ConversationModel> conversationModelIterator = conversationModels.iterator();
		while (conversationModelIterator.hasNext() && !cancelled) {
			publishProgress(++i);

			ConversationModel conversationModel = conversationModelIterator.next();
			// remove all messages
			conversationService.clear(conversationModel);

			if (conversationModel.isGroupConversation()) {
				groupService.leaveGroupFromLocal(conversationModel.getGroup());
				groupService.remove(conversationModel.getGroup());
			} else if (conversationModel.isDistributionListConversation()) {
				distributionListService.remove(conversationModel.getDistributionList());
			}
			// do not remove contact...
		}
		return i;
	}

	@Override
	protected void onPostExecute(Integer count) {
		if (count > 0) {
			DialogUtil.dismissDialog(fragmentManager, DIALOG_TAG, true);
			ListenerManager.conversationListeners.handle(new ListenerManager.HandleListener<ConversationListener>() {
				@Override
				public void handle(ConversationListener listener) {
					listener.onModifiedAll();
				}
			});

			// API 19 min
			if (feedbackView != null && feedbackView.isAttachedToWindow()) {
				Snackbar.make(feedbackView, (ConfigUtils.getSafeQuantityString(ThreemaApplication.getAppContext(), R.plurals.chat_deleted, count, count)), Snackbar.LENGTH_SHORT).show();
			}

			if (runOnCompletion != null) {
				runOnCompletion.run();
			}
		}
	}
}

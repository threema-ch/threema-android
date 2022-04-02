/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2018-2022 Threema GmbH
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

import net.sqlcipher.database.SQLiteException;

import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

import androidx.fragment.app.FragmentManager;
import ch.threema.app.R;
import ch.threema.app.dialogs.CancelableHorizontalProgressDialog;
import ch.threema.app.managers.ListenerManager;
import ch.threema.app.messagereceiver.MessageReceiver;
import ch.threema.app.services.ConversationService;
import ch.threema.app.services.MessageService;
import ch.threema.app.utils.DialogUtil;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.storage.models.AbstractMessageModel;

public class EmptyChatAsyncTask extends AsyncTask<Void, Integer, Integer> {
	private static final Logger logger = LoggingUtil.getThreemaLogger("EmptyChatAsyncTask");

	private static final String DIALOG_TAG_EMPTYING_CHAT = "ec";

	private final MessageReceiver messageReceiver;
	private final MessageService messageService;
	private final ConversationService conversationService;
	private final FragmentManager fragmentManager;
	private final boolean quiet;
	private final Runnable runOnCompletion;
	private int progress;
	private boolean isCancelled;

	public EmptyChatAsyncTask(MessageReceiver messageReceiver,
	                          MessageService messageService,
	                          ConversationService conversationService,
	                          FragmentManager fragmentManager,
	                          boolean quiet,
	                          Runnable runOnCompletion) {

		this.messageReceiver = messageReceiver;
		this.messageService = messageService;
		this.conversationService = conversationService;
		this.fragmentManager = fragmentManager;
		this.quiet = quiet;
		this.runOnCompletion = runOnCompletion;
	}

	@Override
	protected void onPreExecute() {
		if (!quiet) {
			isCancelled = false;
			CancelableHorizontalProgressDialog dialog = CancelableHorizontalProgressDialog.newInstance(R.string.emptying_chat, 0, R.string.cancel, 100);
			dialog.setOnCancelListener((dialog1, which) -> isCancelled = true);
			dialog.show(fragmentManager, DIALOG_TAG_EMPTYING_CHAT);
		}
	}

	@Override
	protected void onProgressUpdate(Integer... values) {
		if (quiet) {
			return;
		}
		if (values[0] > progress) {
			DialogUtil.updateProgress(fragmentManager, DIALOG_TAG_EMPTYING_CHAT, values[0]);
			progress = values[0];
		}
	}

	@Override
	protected Integer doInBackground(Void... voids) {
		List<AbstractMessageModel> messageModelsToDelete = new ArrayList<>(messageService.getMessagesForReceiver(messageReceiver));
		int i = 0, size = messageModelsToDelete.size();
		if (size > 0) {
			for (AbstractMessageModel abstractMessageModel : messageModelsToDelete) {
				if (isCancelled) {
					break;
				}

				publishProgress(i++ * 100 / size);
				try {
					messageService.remove(abstractMessageModel, true);
				} catch (SQLiteException e) {
					logger.error("Unable to remove message", e);
				}
			}

			ListenerManager.messageListeners.handle(listener -> listener.onRemoved(messageModelsToDelete));

			conversationService.refresh(messageReceiver);
		}
		return size;
	}

	@Override
	protected void onPostExecute(Integer count) {
		if (!quiet) {
			DialogUtil.dismissDialog(fragmentManager, DIALOG_TAG_EMPTYING_CHAT, true);
		}
		if (count > 0) {
			if (runOnCompletion != null) {
				runOnCompletion.run();
			}
		}
	}
}

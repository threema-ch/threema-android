/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2018-2020 Threema GmbH
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

import androidx.fragment.app.FragmentManager;
import ch.threema.app.R;
import ch.threema.app.dialogs.GenericProgressDialog;
import ch.threema.app.messagereceiver.MessageReceiver;
import ch.threema.app.services.MessageService;
import ch.threema.app.utils.DialogUtil;
import ch.threema.storage.models.AbstractMessageModel;

public class EmptyChatAsyncTask extends AsyncTask<Void, Void, Integer> {
	private static final String DIALOG_TAG_EMPTYING_CHAT = "ec";

	private final MessageReceiver[] messageReceivers;
	private final MessageService messageService;
	private final FragmentManager fragmentManager;
	private final boolean quiet;
	private final Runnable runOnCompletion;

	public EmptyChatAsyncTask(MessageReceiver[] messageReceivers,
	                          MessageService messageService,
	                          FragmentManager fragmentManager,
	                          boolean quiet,
	                          Runnable runOnCompletion) {

		this.messageReceivers = messageReceivers;
		this.messageService = messageService;
		this.fragmentManager = fragmentManager;
		this.quiet = quiet;
		this.runOnCompletion = runOnCompletion;
	}

	@Override
	protected void onPreExecute() {
		if (!quiet) {
			GenericProgressDialog.newInstance(R.string.emptying_chat, R.string.please_wait).show(fragmentManager, DIALOG_TAG_EMPTYING_CHAT);
		}
	}

	@Override
	protected Integer doInBackground(Void... voids) {
		int i = 0;
		for (MessageReceiver messageReceiver: messageReceivers) {
			for (AbstractMessageModel m : messageService.getMessagesForReceiver(messageReceiver)) {
				messageService.remove(m, true);
				i++;
			}
		}
		return i;
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

/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2015-2021 Threema GmbH
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

package ch.threema.app.receivers;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.widget.Toast;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import androidx.core.app.RemoteInput;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.messagereceiver.MessageReceiver;
import ch.threema.app.utils.IntentDataUtil;
import ch.threema.storage.models.AbstractMessageModel;

public class ReplyActionBroadcastReceiver extends ActionBroadcastReceiver {
	private static final Logger logger = LoggerFactory.getLogger(ReplyActionBroadcastReceiver.class);

	@SuppressLint("StaticFieldLeak")
	@Override
	public void onReceive(final Context context, final Intent intent) {
		if (!requiredInstances()) {
			Toast.makeText(context, R.string.verify_failed, Toast.LENGTH_LONG).show();
			return;
		}

		final PendingResult pendingResult = goAsync();

		new AsyncTask<Void, Void, Boolean>() {
			MessageReceiver messageReceiver = null;
			AbstractMessageModel messageModel = null;
			CharSequence message = null;

			@Override
			protected void onPreExecute() {
				super.onPreExecute();

				messageReceiver = IntentDataUtil.getMessageReceiverFromIntent(context, intent);

				if (messageReceiver != null) {
					messageModel = IntentDataUtil.getMessageModelFromReceiver(intent, messageReceiver);
					message = getMessageText(intent);
				}
			}

			@Override
			protected Boolean doInBackground(Void... params) {
				if (messageModel != null && message != null) {
					// we need to make sure there's a connection during delivery
					lifetimeService.acquireConnection(TAG);

					try {
						messageService.sendText(message.toString(), messageReceiver);
						messageService.markConversationAsRead(messageReceiver, notificationService);
						lifetimeService.releaseConnectionLinger(TAG, WEARABLE_CONNECTION_LINGER);
						logger.debug("Message replied: " + messageModel.getUid());
						return true;
					} catch (Exception e) {
						logger.error("Exception", e);
					}
				}
				lifetimeService.releaseConnectionLinger(TAG, WEARABLE_CONNECTION_LINGER);
				return false;
			}

			@Override
			protected void onPostExecute(Boolean success) {
				if (success != null) {
					Toast.makeText(context, success ? R.string.message_sent : R.string.verify_failed, Toast.LENGTH_LONG).show();
				}
				pendingResult.finish();
			}
		}.execute();
	}

	private CharSequence getMessageText(Intent intent) {
		Bundle remoteInput =RemoteInput.getResultsFromIntent(intent);
		if (remoteInput != null) {
			return remoteInput.getCharSequence(ThreemaApplication.EXTRA_VOICE_REPLY);
		}
		return null;
	}
}

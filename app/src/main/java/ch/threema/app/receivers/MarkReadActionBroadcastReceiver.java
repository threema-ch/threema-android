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

import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.threema.app.messagereceiver.MessageReceiver;
import ch.threema.app.utils.IntentDataUtil;

public class MarkReadActionBroadcastReceiver extends ActionBroadcastReceiver {
	private static final Logger logger = LoggerFactory.getLogger(ActionBroadcastReceiver.class);

	@Override
	public void onReceive(final Context context, final Intent intent) {
		final PendingResult pendingResult = goAsync();

		new AsyncTask<Void, Void, Boolean>() {
			MessageReceiver messageReceiver = null;

			@Override
			protected void onPreExecute() {
				super.onPreExecute();

				messageReceiver = IntentDataUtil.getMessageReceiverFromIntent(context, intent);
			}

			@Override
			protected Boolean doInBackground(Void... params) {

				if (messageReceiver != null) {
					lifetimeService.acquireConnection(TAG);
					messageService.markConversationAsRead(messageReceiver, notificationService);
					lifetimeService.releaseConnectionLinger(TAG, WEARABLE_CONNECTION_LINGER);

					return true;
				}
				return false;
			}

			@Override
			protected void onPostExecute(Boolean success) {
				if (success) {
					logger.debug("Conversation read: " + messageReceiver.getUniqueIdString());
				}

				notificationService.cancel(messageReceiver);

				pendingResult.finish();
			}
		}.execute();
	}
}


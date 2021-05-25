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
import android.widget.Toast;

import ch.threema.app.R;
import ch.threema.app.messagereceiver.MessageReceiver;
import ch.threema.app.utils.IntentDataUtil;
import ch.threema.storage.models.AbstractMessageModel;

public class AcknowledgeActionBroadcastReceiver extends ActionBroadcastReceiver {
	@Override
	public void onReceive(final Context context, final Intent intent) {
		final PendingResult pendingResult = goAsync();

		new AsyncTask<Void, Void, Boolean>() {

			MessageReceiver messageReceiver = null;
			AbstractMessageModel messageModel = null;

			@Override
			protected void onPreExecute() {
				super.onPreExecute();

				messageReceiver = IntentDataUtil.getMessageReceiverFromIntent(context, intent);

				if (messageReceiver != null) {
					messageModel = IntentDataUtil.getMessageModelFromReceiver(intent, messageReceiver);
				}
			}

			@Override
			protected Boolean doInBackground(Void... params) {
				if (messageModel != null) {
					// we need to make sure there's a connection during delivery
					lifetimeService.acquireConnection(TAG);

					messageService.sendUserAcknowledgement(messageModel);
					messageService.markMessageAsRead(messageModel, notificationService);

					lifetimeService.releaseConnectionLinger(TAG, WEARABLE_CONNECTION_LINGER);

					return true;
				}
				return false;
			}

			@Override
			protected void onPostExecute(Boolean success) {
				if (success != null) {
					Toast.makeText(context, success ? R.string.message_acknowledged : R.string.an_error_occurred, Toast.LENGTH_LONG).show();
				}

				notificationService.cancel(messageReceiver);

				pendingResult.finish();
			}
		}.execute();
	}
}

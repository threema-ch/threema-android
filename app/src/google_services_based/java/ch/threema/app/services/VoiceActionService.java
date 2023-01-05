/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2016-2023 Threema GmbH
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

package ch.threema.app.services;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.widget.Toast;

import com.google.android.search.verification.client.SearchActionVerificationClientService;

import org.slf4j.Logger;

import java.util.Collections;

import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.managers.ServiceManager;
import ch.threema.app.messagereceiver.MessageReceiver;
import ch.threema.app.ui.MediaItem;
import ch.threema.app.utils.RuntimeUtil;
import ch.threema.app.utils.TestUtil;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.storage.models.ContactModel;

public class VoiceActionService extends SearchActionVerificationClientService {
	private static final Logger logger = LoggingUtil.getThreemaLogger("VoiceActionService");
	private static final String TAG = "VoiceActionService";

	private MessageService messageService;
	private LifetimeService lifetimeService;
	private NotificationService notificationService;
	private ContactService contactService;
	private LockAppService lockAppService;

	private static final String CHANNEL_ID_GOOGLE_ASSISTANT = "Voice_Actions";
	private static final int NOTIFICATION_ID = 10000;

	@Override
	public void performAction(Intent intent, boolean isVerified, Bundle options) {
		logger.debug(String.format("performAction: intent - %s, isVerified - %s", intent, isVerified));

		this.instantiate();

		if (!lockAppService.isLocked()) {
			doPerformAction(intent, isVerified);
		} else {
			RuntimeUtil.runOnUiThread(() -> Toast.makeText(VoiceActionService.this, R.string.pin_locked_cannot_send, Toast.LENGTH_LONG).show());
		}
	}

	@RequiresApi(Build.VERSION_CODES.O)
	@Override
	protected void postForegroundNotification() {
		this.createChannel();
		NotificationCompat.Builder notificationBuilder =
				new NotificationCompat.Builder(this.getApplicationContext(), CHANNEL_ID_GOOGLE_ASSISTANT)
						.setGroup(CHANNEL_ID_GOOGLE_ASSISTANT)
						.setContentTitle(this.getApplicationContext().getResources().getString(R.string.voice_action_title))
						.setContentText(this.getApplicationContext().getResources().getString(R.string.voice_action_body))
						.setSmallIcon(R.drawable.ic_notification_small)
						.setPriority(NotificationCompat.PRIORITY_MIN)
						.setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
						.setLocalOnly(true);
		this.startForeground(NOTIFICATION_ID, notificationBuilder.build());
	}

	@RequiresApi(Build.VERSION_CODES.O)
	private void createChannel() {
		NotificationChannel channel = new NotificationChannel(CHANNEL_ID_GOOGLE_ASSISTANT, this.getApplicationContext().getResources().getString(R.string.voice_action_title), NotificationManager.IMPORTANCE_LOW);
		channel.enableVibration(false);
		channel.enableLights(false);
		channel.setShowBadge(false);

		NotificationManager notificationManager = this.getApplicationContext().getSystemService(NotificationManager.class);
		if (notificationManager != null) {
			notificationManager.createNotificationChannel(channel);
		}
	}

	private boolean sendAudioMessage(final MessageReceiver messageReceiver, Intent intent, String caption) {
		ClipData clipData;
		clipData = intent.getClipData();
		if (clipData == null) {
			return false;
		}

		ClipData.Item item = clipData.getItemAt(0);
		if (item == null) {
			return false;
		}

		Uri uri = item.getUri();
		if (uri == null) {
			return false;
		}

		logger.debug("Audio uri: " + uri);

		MediaItem mediaItem = new MediaItem(uri, MediaItem.TYPE_VOICEMESSAGE);
		mediaItem.setCaption(caption);

		messageService.sendMediaAsync(Collections.singletonList(mediaItem), Collections.singletonList(messageReceiver), new MessageServiceImpl.SendResultListener() {
			@Override
			public void onError(String errorMessage) {
				logger.debug("Error sending audio message: " + errorMessage);
				lifetimeService.releaseConnectionLinger(TAG, PollingHelper.CONNECTION_LINGER);
			}

			@Override
			public void onCompleted() {
				logger.debug("Audio message sent");
				messageService.markConversationAsRead(messageReceiver, notificationService);
				lifetimeService.releaseConnectionLinger(TAG, PollingHelper.CONNECTION_LINGER);
			}
		});
		return true;
	}

	public void doPerformAction(Intent intent, boolean isVerified) {

		if (isVerified) {
			Bundle bundle = intent.getExtras();

			if (bundle != null) {
				String identity = bundle.getString("com.google.android.voicesearch.extra.RECIPIENT_CONTACT_CHAT_ID");
				String message = bundle.getString("android.intent.extra.TEXT");

				if (!TestUtil.empty(identity, message)) {
					ContactModel contactModel = contactService.getByIdentity(identity);

					if (contactModel != null) {
						final MessageReceiver messageReceiver = contactService.createReceiver(contactModel);

						if (messageReceiver != null) {
							lifetimeService.acquireConnection(TAG);

							if (!sendAudioMessage(messageReceiver, intent, message)) {
								try {
									messageService.sendText(message, messageReceiver);
									messageService.markConversationAsRead(messageReceiver, notificationService);

									logger.debug("Message sent to: " + identity);
								} catch (Exception e) {
									logger.error("Exception", e);
								}

								lifetimeService.releaseConnectionLinger(TAG, PollingHelper.CONNECTION_LINGER);
							}
						}
					}
				}
			}
		}
	}

/*	@Override
	public boolean isTestingMode() {
		return true;
	}
*/
	final protected boolean requiredInstances() {
		if (!this.checkInstances()) {
			this.instantiate();
		}
		return this.checkInstances();
	}

	protected boolean checkInstances() {
		return TestUtil.required(
				this.messageService,
				this.lifetimeService,
				this.notificationService,
				this.contactService,
				this.lockAppService
		);
	}

	protected void instantiate() {
		ServiceManager serviceManager = ThreemaApplication.getServiceManager();
		if (serviceManager != null) {
			try {
				this.messageService = serviceManager.getMessageService();
				this.lifetimeService = serviceManager.getLifetimeService();
				this.notificationService = serviceManager.getNotificationService();
				this.contactService = serviceManager.getContactService();
				this.lockAppService = serviceManager.getLockAppService();
			} catch (Exception e) {
				logger.error("Exception", e);
			}
		}
	}
}

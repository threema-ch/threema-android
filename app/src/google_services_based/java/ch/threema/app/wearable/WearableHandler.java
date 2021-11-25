/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2021 Threema GmbH
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

package ch.threema.app.wearable;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;

import com.google.android.gms.common.data.FreezableUtils;
import com.google.android.gms.tasks.Tasks;
import com.google.android.gms.wearable.DataClient;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.concurrent.ExecutionException;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.utils.NameUtil;
import ch.threema.app.utils.RuntimeUtil;
import ch.threema.app.voip.services.CallRejectService;
import ch.threema.app.voip.services.VoipCallService;
import ch.threema.app.voip.services.VoipStateService;
import ch.threema.app.voip.util.VoipUtil;
import ch.threema.domain.protocol.csp.messages.voip.VoipCallAnswerData;
import ch.threema.storage.models.ContactModel;

import static ch.threema.app.voip.services.VoipCallService.EXTRA_CALL_ID;
import static ch.threema.app.voip.services.VoipCallService.EXTRA_CONTACT_IDENTITY;
import static ch.threema.app.voip.services.VoipStateService.TYPE_ACTIVITY;
import static ch.threema.app.voip.services.VoipStateService.TYPE_NOTIFICATION;

public class WearableHandler {
	private static final Logger logger = LoggerFactory.getLogger(VoipStateService.class);

	private final Context appContext;
	private final DataClient.OnDataChangedListener wearableListener;

	public WearableHandler(Context context) {
		this.appContext = context;
		this.wearableListener = new DataClient.OnDataChangedListener() {
			@Override
			public void onDataChanged(@NonNull DataEventBuffer eventsBuffer) {
				final List<DataEvent> events = FreezableUtils.freezeIterable(eventsBuffer);
				for (DataEvent event : events) {
					if (event.getType() == DataEvent.TYPE_CHANGED) {
						String path = event.getDataItem().getUri().getPath();
						logger.info("onDataChanged Listener data event path {}", path);
						if ("/accept-call".equals(path)) {
							DataMapItem dataMapItem = DataMapItem.fromDataItem(event.getDataItem());
							long callId = dataMapItem.getDataMap().getLong(EXTRA_CALL_ID, 0L);
							String identity = dataMapItem.getDataMap().getString(EXTRA_CONTACT_IDENTITY);
							final Intent intent = VoipStateService.createAcceptIntent(callId, identity);
							appContext.startService(intent);
							//Listen again for hang up
							Wearable.getDataClient(appContext).addListener(wearableListener);

						} if("/reject-call".equals(path)) {
							DataMapItem dataMapItem = DataMapItem.fromDataItem(event.getDataItem());
							long callId = dataMapItem.getDataMap().getLong(EXTRA_CALL_ID, 0L);
							String identity = dataMapItem.getDataMap().getString(EXTRA_CONTACT_IDENTITY);
							final Intent rejectIntent = VoipStateService.createRejectIntent(
								callId,
								identity,
								VoipCallAnswerData.RejectReason.REJECTED
							);
							CallRejectService.enqueueWork(appContext, rejectIntent);
						} if ("/disconnect-call".equals(path)){
							VoipUtil.sendVoipCommand(appContext, VoipCallService.class, VoipCallService.ACTION_HANGUP);
						}
					}
				}
			}
		};
	}

	/*
	 *  Cancel notification or activity on wearable
	 */
	public static void cancelOnWearable(@VoipStateService.Component int component){
		RuntimeUtil.runInAsyncTask(() -> {
			try {
				final List<Node> nodes = Tasks.await(
					Wearable.getNodeClient(ThreemaApplication.getAppContext()).getConnectedNodes()
				);
				if (nodes != null) {
					for (Node node : nodes) {
						if (node.getId() != null) {
							switch (component) {
								case TYPE_NOTIFICATION:
									Wearable.getMessageClient(ThreemaApplication.getAppContext())
										.sendMessage(node.getId(), "/cancel-notification", null);
									break;
								case TYPE_ACTIVITY:
									Wearable.getMessageClient(ThreemaApplication.getAppContext())
										.sendMessage(node.getId(), "/cancel-activity", null);
									break;
								default:
									break;
							}
						}
					}
				}
			} catch (ExecutionException e) {
				final String message = e.getMessage();
				if (message != null && message.contains("Wearable.API is not available on this device")) {
					logger.debug("cancelOnWearable: ExecutionException while trying to connect to wearable: {}", message);
				} else {
					logger.info("cancelOnWearable: ExecutionException while trying to connect to wearable: {}", message);
				}
			} catch (InterruptedException e) {
				logger.info("cancelOnWearable: Interrupted while waiting for wearable client");
				// Restore interrupted state...
				Thread.currentThread().interrupt();
			}
		});
	}

	/*
	 *  Send information to the companion app on the wearable device
	 */
	@WorkerThread
	public void showWearableNotification(
		@NonNull ContactModel contact,
		long callId,
		@Nullable Bitmap avatar) {
		final DataClient dataClient = Wearable.getDataClient(appContext);

		// Add data to the request
		final PutDataMapRequest putDataMapRequest = PutDataMapRequest.create("/incoming-call");
		putDataMapRequest.getDataMap().putLong(EXTRA_CALL_ID, callId);
		putDataMapRequest.getDataMap().putString(EXTRA_CONTACT_IDENTITY, contact.getIdentity());
		logger.debug("sending the following contactIdentity from VoipState to wearable " + contact.getIdentity());
		putDataMapRequest.getDataMap().putString("CONTACT_NAME", NameUtil.getDisplayNameOrNickname(contact, true));
		putDataMapRequest.getDataMap().putLong("CALL_TIME", System.currentTimeMillis());
		if (avatar != null) {
			final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
			avatar.compress(Bitmap.CompressFormat.PNG, 100, buffer);
			putDataMapRequest.getDataMap().putByteArray("CONTACT_AVATAR", buffer.toByteArray());
		}

		final PutDataRequest request = putDataMapRequest.asPutDataRequest();
		request.setUrgent();

		dataClient.addListener(this.wearableListener);
		dataClient.putDataItem(request);
	}
}

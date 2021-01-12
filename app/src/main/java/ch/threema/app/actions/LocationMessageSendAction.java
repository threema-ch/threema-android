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

package ch.threema.app.actions;

import android.location.Location;

import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.messagereceiver.MessageReceiver;
import ch.threema.app.services.MessageService;
import ch.threema.app.utils.MessageUtil;
import ch.threema.base.ThreemaException;
import ch.threema.storage.models.AbstractMessageModel;

public class LocationMessageSendAction extends SendAction {
	protected static volatile LocationMessageSendAction instance;
	private static final Object instanceLock = new Object();

	private MessageService messageService;

	private LocationMessageSendAction() {
		// Singleton
	}

	public static LocationMessageSendAction getInstance() {
		if (instance == null) {
			synchronized (instanceLock) {
				if (instance == null) {
					instance = new LocationMessageSendAction();
				}
			}
		}
		return instance;
	}

	public boolean sendLocationMessage(final MessageReceiver[] allReceivers,
									   final Location location,
									   final String poiName,
									   final ActionHandler actionHandler) {

		if (actionHandler == null) {
			return false;
		}

		try {
			messageService = this.getServiceManager().getMessageService();
		} catch (ThreemaException e) {
			actionHandler.onError(e.getMessage());
			return false;
		}

		if (messageService == null || location == null) {
			actionHandler.onError("Nothing to send");
			return false;
		}

		if (allReceivers.length < 1) {
			actionHandler.onError("no message receiver");
			return false;
		}

		// loop all receivers (required for distribution lists)
		// add distribution list members to list of receivers
		final MessageReceiver[] resolvedReceivers = MessageUtil.addDistributionListReceivers(allReceivers);
		final int numReceivers = resolvedReceivers.length;

		sendSingleMessage(resolvedReceivers[0], location, poiName, new ActionHandler() {
			int receiverIndex = 0;

			@Override
			public void onError(String errorMessage) {
				actionHandler.onError(errorMessage);
			}

			@Override
			public void onWarning(String warning, boolean continueAction) {
			}

			@Override
			public void onProgress(int progress, int total) {
				actionHandler.onProgress(progress + receiverIndex, numReceivers);
			}

			@Override
			public void onCompleted() {
				if (receiverIndex < numReceivers - 1) {
					receiverIndex++;
					sendSingleMessage(resolvedReceivers[receiverIndex], location, poiName, this);
				} else {
					actionHandler.onCompleted();
					messageService.sendProfilePicture(resolvedReceivers);
				}
			}
		});
		return true;
	}

	private void sendSingleMessage(final MessageReceiver messageReceiver, final Location location, final String poiName, final ActionHandler actionHandler) {
		if (messageReceiver == null) {
			actionHandler.onError("No receiver");
			return;
		}

		try {
			messageService.sendLocation(
					location,
					poiName,
					messageReceiver,
					new MessageService.CompletionHandler() {
						@Override
						public void sendComplete(AbstractMessageModel messageModel) {}

						@Override
						public void sendQueued(AbstractMessageModel messageModel) {
							actionHandler.onCompleted();
						}

						@Override
						public void sendError(int reason) {
							actionHandler.onError(String.format(ThreemaApplication.getAppContext().getString(R.string.an_error_occurred_more), Integer.toString(reason)));
						}
					});
		} catch (final Exception e) {
			actionHandler.onError(e.getMessage());
		}
	}
}

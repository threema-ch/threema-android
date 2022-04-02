/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2014-2022 Threema GmbH
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

import org.slf4j.Logger;

import ch.threema.app.utils.ExponentialBackOffUtil;
import ch.threema.base.utils.LoggingUtil;

public class MessageSendingServiceExponentialBackOff implements MessageSendingService {
	private static final Logger logger = LoggingUtil.getThreemaLogger("MessageSendingServiceExponentialBackOff");

	private final MessageSendingServiceState messageSendingServiceState;

	public MessageSendingServiceExponentialBackOff(MessageSendingServiceState messageSendingServiceState) {
		this.messageSendingServiceState = messageSendingServiceState;
	}

	@Override
	public void addToQueue(final MessageSendingProcess process) {
		ExponentialBackOffUtil.getInstance().run(new ExponentialBackOffUtil.BackOffRunnable() {
			@Override
			public void run(int currentRetry) throws Exception {
				try {
					process.send();
				} catch (Exception x) {
					logger.error("Sending message failed", x);
					messageSendingServiceState.exception(x, 0);
					throw x;
				}
			}

			@Override
			public void finished(int currentRetry) {
				messageSendingServiceState.processingFinished(process.getMessageModel(), process.getReceiver());
			}

			@Override
			public void exception(Exception e, int currentRetry) {
				messageSendingServiceState.processingFailed(process.getMessageModel(), process.getReceiver());
			}
		}, 5);
	}
}

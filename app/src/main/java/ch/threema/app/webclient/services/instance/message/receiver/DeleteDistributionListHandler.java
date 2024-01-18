/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2017-2024 Threema GmbH
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

package ch.threema.app.webclient.services.instance.message.receiver;

import org.msgpack.core.MessagePackException;
import org.msgpack.value.Value;
import org.slf4j.Logger;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Map;

import androidx.annotation.AnyThread;
import androidx.annotation.StringDef;
import androidx.annotation.WorkerThread;

import ch.threema.app.services.DistributionListService;
import ch.threema.app.webclient.Protocol;
import ch.threema.app.webclient.converter.Receiver;
import ch.threema.app.webclient.services.instance.MessageDispatcher;
import ch.threema.app.webclient.services.instance.MessageReceiver;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.storage.models.DistributionListModel;

@WorkerThread
public class DeleteDistributionListHandler extends MessageReceiver {
	private static final Logger logger = LoggingUtil.getThreemaLogger("DeleteDistributionListHandler");

	private final MessageDispatcher responseDispatcher;
	private final DistributionListService distributionListService;

	@Retention(RetentionPolicy.SOURCE)
	@StringDef({
		Protocol.ERROR_INVALID_DISTRIBUTION_LIST,
		Protocol.ERROR_BAD_REQUEST,
		Protocol.ERROR_INTERNAL,
	})
	private @interface ErrorCode {}

	@AnyThread
	public DeleteDistributionListHandler(MessageDispatcher responseDispatcher,
	                                     DistributionListService distributionListService) {
		super(Protocol.SUB_TYPE_DISTRIBUTION_LIST);
		this.responseDispatcher = responseDispatcher;
		this.distributionListService = distributionListService;
	}

	@Override
	protected void receive(Map<String, Value> message) throws MessagePackException {
		logger.debug("Received delete distribution list request");
		final Map<String, Value> args = this.getArguments(message, false, new String[] {
			Protocol.ARGUMENT_TEMPORARY_ID,
		});
		final String temporaryId = args.get(Protocol.ARGUMENT_TEMPORARY_ID).asStringValue().toString();

		// Get args
		if (!args.containsKey(Protocol.ARGUMENT_RECEIVER_ID)) {
			logger.error("Invalid distribution list delete request, id not set");
			this.failed(temporaryId, Protocol.ERROR_BAD_REQUEST);
			return;
		}
		final long distributionListId = Long.parseLong(args.get(Receiver.ID).asStringValue().toString());

		// Get distribution list
		final DistributionListModel distributionListModel = this.distributionListService.getById(distributionListId);
		if (distributionListModel == null) {
			this.failed(temporaryId, Protocol.ERROR_INVALID_DISTRIBUTION_LIST);
			return;
		}

		// Save changes
		final boolean removed = this.distributionListService.remove(distributionListModel);
		if (!removed) {
			this.failed(temporaryId, Protocol.ERROR_INTERNAL);
		} else {
			this.success(temporaryId);
		}
	}

	private void success(String temporaryId) {
		logger.debug("Respond with leave group success");
		this.sendConfirmActionSuccess(this.responseDispatcher, temporaryId);
	}

	private void failed(String temporaryId, @ErrorCode String errorCode) {
		logger.warn("Respond with modify group failed ({})", errorCode);
		this.sendConfirmActionFailure(this.responseDispatcher, temporaryId, errorCode);
	}

	@Override
	protected boolean maybeNeedsConnection() {
		return false;
	}
}

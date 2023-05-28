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

package ch.threema.app.webclient.services.instance.message.receiver;

import androidx.annotation.AnyThread;
import androidx.annotation.StringDef;
import androidx.annotation.WorkerThread;

import org.msgpack.core.MessagePackException;
import org.msgpack.value.Value;
import org.slf4j.Logger;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.List;
import java.util.Map;

import ch.threema.app.services.DistributionListService;
import ch.threema.app.webclient.Protocol;
import ch.threema.app.webclient.converter.DistributionList;
import ch.threema.app.webclient.converter.MsgpackObjectBuilder;
import ch.threema.app.webclient.converter.Receiver;
import ch.threema.app.webclient.exceptions.ConversionException;
import ch.threema.app.webclient.services.instance.MessageDispatcher;
import ch.threema.app.webclient.services.instance.MessageReceiver;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.storage.models.DistributionListModel;

import static java.nio.charset.StandardCharsets.UTF_8;

@WorkerThread
public class ModifyDistributionListHandler extends MessageReceiver {
	private static final Logger logger = LoggingUtil.getThreemaLogger("ModifyDistributionListHandler");

	private final MessageDispatcher dispatcher;
	private final DistributionListService distributionListService;

	@Retention(RetentionPolicy.SOURCE)
	@StringDef({
		Protocol.ERROR_INVALID_DISTRIBUTION_LIST,
		Protocol.ERROR_NO_MEMBERS,
		Protocol.ERROR_BAD_REQUEST,
		Protocol.ERROR_VALUE_TOO_LONG,
		Protocol.ERROR_INTERNAL,
	})
	private @interface ErrorCode {}

	@AnyThread
	public ModifyDistributionListHandler(MessageDispatcher dispatcher,
	                                     DistributionListService distributionListService) {
		super(Protocol.SUB_TYPE_DISTRIBUTION_LIST);
		this.dispatcher = dispatcher;
		this.distributionListService = distributionListService;
	}

	@Override
	protected void receive(Map<String, Value> message) throws MessagePackException {
		logger.debug("Received modify distribution list");
		final Map<String, Value> args = this.getArguments(message, false, new String[] {
			Protocol.ARGUMENT_TEMPORARY_ID,
		});
		final String temporaryId = args.get(Protocol.ARGUMENT_TEMPORARY_ID).asStringValue().toString();

		// Get args
		if (!args.containsKey(Receiver.ID)) {
			logger.error("Invalid distribution list update request, id not set");
			this.failed(temporaryId, Protocol.ERROR_BAD_REQUEST);
			return;
		}
		final int distributionListId = Integer.parseInt(args.get(Receiver.ID).asStringValue().toString());

		// Get distribution list
		final DistributionListModel distributionListModel = this.distributionListService.getById(distributionListId);
		if (distributionListModel == null) {
			this.failed(temporaryId, Protocol.ERROR_INVALID_DISTRIBUTION_LIST);
			return;
		}

		// Process data
		final Map<String, Value> data = this.getData(message, false);
		if (!data.containsKey(Protocol.ARGUMENT_MEMBERS)) {
			this.failed(temporaryId, Protocol.ERROR_NO_MEMBERS);
			return;
		}

		// Update members
		final List<Value> members = data.get(Protocol.ARGUMENT_MEMBERS).asArrayValue().list();
		final String[] identities = new String[members.size()];
		for (int n = 0; n < members.size(); n++) {
			identities[n] = members.get(n).asStringValue().toString();
		}

		// Update name
		String name = distributionListModel.getName();
		if (data.containsKey(Protocol.ARGUMENT_NAME)) {
			name = this.getValueString(data.get(Protocol.ARGUMENT_NAME));
			if (name.getBytes(UTF_8).length > Protocol.LIMIT_BYTES_DISTRIBUTION_LIST_NAME) {
				this.failed(temporaryId, Protocol.ERROR_VALUE_TOO_LONG);
				return;
			}
		}

		// Save changes
		try {
			this.distributionListService.updateDistributionList(distributionListModel, name, identities);
			this.success(temporaryId, distributionListModel);
		} catch (Exception e1) {
			this.failed(temporaryId, Protocol.ERROR_INTERNAL);
		}
	}

	private void success(String temporaryId, DistributionListModel distributionListModel) {
		logger.debug("Respond modify distribution list success");
		try {
			this.send(this.dispatcher,
					new MsgpackObjectBuilder()
						.put(Protocol.SUB_TYPE_RECEIVER, DistributionList.convert(distributionListModel)),
					new MsgpackObjectBuilder()
						.put(Protocol.ARGUMENT_SUCCESS, true)
						.put(Protocol.ARGUMENT_TEMPORARY_ID, temporaryId)
			);
		} catch (ConversionException e) {
			logger.error("Exception", e);
		}
	}

	private void failed(String temporaryId, @ErrorCode String errorCode) {
		logger.warn("Respond modify distribution list failed ({})", errorCode);
		this.send(this.dispatcher,
				new MsgpackObjectBuilder()
						.putNull(Protocol.SUB_TYPE_RECEIVER),
				new MsgpackObjectBuilder()
						.put(Protocol.ARGUMENT_SUCCESS, false)
						.put(Protocol.ARGUMENT_ERROR, errorCode)
						.put(Protocol.ARGUMENT_TEMPORARY_ID, temporaryId)
		);
	}

	@Override
	protected boolean maybeNeedsConnection() {
		return false;
	}
}

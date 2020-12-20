/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2016-2020 Threema GmbH
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
import org.slf4j.LoggerFactory;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import ch.threema.app.services.DistributionListService;
import ch.threema.app.webclient.Protocol;
import ch.threema.app.webclient.converter.DistributionList;
import ch.threema.app.webclient.converter.MsgpackObjectBuilder;
import ch.threema.app.webclient.exceptions.ConversionException;
import ch.threema.app.webclient.services.instance.MessageDispatcher;
import ch.threema.app.webclient.services.instance.MessageReceiver;
import ch.threema.storage.models.DistributionListModel;

@WorkerThread
public class CreateDistributionListHandler extends MessageReceiver {
	private static final Logger logger = LoggerFactory.getLogger(CreateDistributionListHandler.class);

	private final MessageDispatcher dispatcher;
	private final DistributionListService distributionListService;

	@Retention(RetentionPolicy.SOURCE)
	@StringDef({
		Protocol.ERROR_BAD_REQUEST,
		Protocol.ERROR_VALUE_TOO_LONG,
		Protocol.ERROR_INTERNAL,
	})
	private @interface ErrorCode {}

	@AnyThread
	public CreateDistributionListHandler(MessageDispatcher dispatcher,
	                                     DistributionListService distributionListService) {
		super(Protocol.SUB_TYPE_DISTRIBUTION_LIST);
		this.dispatcher = dispatcher;
		this.distributionListService = distributionListService;
	}

	@Override
	protected void receive(Map<String, Value> message) throws MessagePackException {
		logger.debug("Received create distribution list create");
		final Map<String, Value> args = this.getArguments(message, false, new String[]{
			Protocol.ARGUMENT_TEMPORARY_ID,
		});
		final Map<String, Value> data = this.getData(message, false);

		final String temporaryId = args.get(Protocol.ARGUMENT_TEMPORARY_ID).asStringValue().toString();

		// Parse members
		if (!data.containsKey(Protocol.ARGUMENT_MEMBERS)) {
			logger.error("Invalid request, members not set");
			this.failed(temporaryId, Protocol.ERROR_BAD_REQUEST);
			return;
		}
		final List<Value> members = data.get(Protocol.ARGUMENT_MEMBERS).asArrayValue().list();
		final String[] identities = new String[members.size()];
		for (int n = 0; n < members.size(); n++) {
			identities[n] = members.get(n).asStringValue().toString();
		}

		// Parse distribution list name
		String name = null;
		if (data.containsKey(Protocol.ARGUMENT_NAME)
				&& !data.get(Protocol.ARGUMENT_NAME).isNilValue()) {
			name = data.get(Protocol.ARGUMENT_NAME).asStringValue().toString();
			if (name.getBytes(StandardCharsets.UTF_8).length > Protocol.LIMIT_BYTES_DISTRIBUTION_LIST_NAME) {
				this.failed(temporaryId, Protocol.ERROR_VALUE_TOO_LONG);
				return;
			}
		}

		try {
			final DistributionListModel distributionListModel =
				this.distributionListService.createDistributionList(name, identities);
			this.success(temporaryId, distributionListModel);
		} catch (Exception e1) {
			this.failed(temporaryId, Protocol.ERROR_INTERNAL);
		}
	}

	private void success(String temporaryId, DistributionListModel distributionListModel) {
		logger.debug("Respond create distribution list success");
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
		logger.warn("Respond create distribution list failed ({})", errorCode);
		this.send(this.dispatcher,
				(MsgpackObjectBuilder) null,
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

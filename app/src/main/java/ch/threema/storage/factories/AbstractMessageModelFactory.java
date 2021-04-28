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

package ch.threema.storage.factories;

import android.content.ContentValues;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import ch.threema.app.services.MessageService;
import ch.threema.app.utils.MessageUtil;
import ch.threema.app.utils.TestUtil;
import ch.threema.storage.CursorHelper;
import ch.threema.storage.DatabaseServiceNew;
import ch.threema.storage.DatabaseUtil;
import ch.threema.storage.QueryBuilder;
import ch.threema.storage.models.AbstractMessageModel;
import ch.threema.storage.models.MessageState;
import ch.threema.storage.models.MessageType;
import ch.threema.storage.models.data.MessageContentsType;
import ch.threema.storage.models.data.media.AudioDataModel;
import ch.threema.storage.models.data.media.FileDataModel;
import ch.threema.storage.models.data.media.VideoDataModel;

abstract class AbstractMessageModelFactory extends ModelFactory {
	private static final Logger logger = LoggerFactory.getLogger(AbstractMessageModelFactory.class);

	AbstractMessageModelFactory(DatabaseServiceNew databaseServiceNew, String tableName) {
		super(databaseServiceNew, tableName);
	}

	void convert(final AbstractMessageModel messageModel, CursorHelper cursorFactory) {
		cursorFactory.current(new CursorHelper.Callback() {
			@Override
			public boolean next(CursorHelper cursorFactory) {
				messageModel
						.setId(cursorFactory.getInt(AbstractMessageModel.COLUMN_ID))
						.setUid(cursorFactory.getString(AbstractMessageModel.COLUMN_UID))
						.setApiMessageId(cursorFactory.getString(AbstractMessageModel.COLUMN_API_MESSAGE_ID))
						.setIdentity(cursorFactory.getString(AbstractMessageModel.COLUMN_IDENTITY))
						.setOutbox(cursorFactory.getBoolean(AbstractMessageModel.COLUMN_OUTBOX))
						.setCorrelationId(cursorFactory.getString(AbstractMessageModel.COLUMN_CORRELATION_ID))
						.setBody(cursorFactory.getString(AbstractMessageModel.COLUMN_BODY))
						.setRead(cursorFactory.getBoolean(AbstractMessageModel.COLUMN_IS_READ))
						.setSaved(cursorFactory.getBoolean(AbstractMessageModel.COLUMN_IS_SAVED))
						.setPostedAt(cursorFactory.getDate(AbstractMessageModel.COLUMN_POSTED_AT))
						.setCreatedAt(cursorFactory.getDate(AbstractMessageModel.COLUMN_CREATED_AT))
						.setModifiedAt(cursorFactory.getDate(AbstractMessageModel.COLUMN_MODIFIED_AT))
						.setIsStatusMessage(cursorFactory.getBoolean(AbstractMessageModel.COLUMN_IS_STATUS_MESSAGE))
						.setIsQueued(cursorFactory.getBoolean(AbstractMessageModel.COLUMN_IS_QUEUED))
						.setCaption(cursorFactory.getString(AbstractMessageModel.COLUMN_CAPTION))
						.setQuotedMessageId(cursorFactory.getString(AbstractMessageModel.COLUMN_QUOTED_MESSAGE_API_MESSAGE_ID))
						.setMessageContentsType(cursorFactory.getInt(AbstractMessageModel.COLUMN_MESSAGE_CONTENTS_TYPE))
						.setMessageFlags(cursorFactory.getInt(AbstractMessageModel.COLUMN_MESSAGE_FLAGS))
				;
				String stateString = cursorFactory.getString(AbstractMessageModel.COLUMN_STATE);
				if (!TestUtil.empty(stateString)) {
					try {
						messageModel.setState(MessageState.valueOf(stateString));
					}
					catch (IllegalArgumentException x) {
						logger.error("Invalid message state " + stateString + " - ignore", x);
					}
				}

				int type = cursorFactory.getInt(AbstractMessageModel.COLUMN_TYPE);
				MessageType[] types = MessageType.values();
				if (type >= 0 && type < types.length) {
					messageModel.setType(types[type]);
				}
				return false;
			}
		});
	}

	ContentValues buildContentValues(AbstractMessageModel messageModel) {
		ContentValues contentValues = new ContentValues();
		contentValues.put(AbstractMessageModel.COLUMN_UID, messageModel.getUid());
		contentValues.put(AbstractMessageModel.COLUMN_API_MESSAGE_ID, messageModel.getApiMessageId());
		contentValues.put(AbstractMessageModel.COLUMN_IDENTITY, messageModel.getIdentity());
		contentValues.put(AbstractMessageModel.COLUMN_OUTBOX, messageModel.isOutbox());
		contentValues.put(AbstractMessageModel.COLUMN_TYPE,  messageModel.getType() != null ? messageModel.getType().ordinal() : null);
		contentValues.put(AbstractMessageModel.COLUMN_CORRELATION_ID, messageModel.getCorrelationId());
		contentValues.put(AbstractMessageModel.COLUMN_BODY, messageModel.getBody());
		contentValues.put(AbstractMessageModel.COLUMN_IS_READ, messageModel.isRead());
		contentValues.put(AbstractMessageModel.COLUMN_IS_SAVED, messageModel.isSaved());
		contentValues.put(AbstractMessageModel.COLUMN_STATE, messageModel.getState() != null ? messageModel.getState().toString() : null);
		contentValues.put(AbstractMessageModel.COLUMN_POSTED_AT, DatabaseUtil.getDateTimeContentValue(messageModel.getPostedAt()));
		contentValues.put(AbstractMessageModel.COLUMN_CREATED_AT, DatabaseUtil.getDateTimeContentValue(messageModel.getCreatedAt()));
		contentValues.put(AbstractMessageModel.COLUMN_MODIFIED_AT, DatabaseUtil.getDateTimeContentValue(messageModel.getModifiedAt()));
		contentValues.put(AbstractMessageModel.COLUMN_IS_STATUS_MESSAGE, messageModel.isStatusMessage());
		contentValues.put(AbstractMessageModel.COLUMN_IS_QUEUED, messageModel.isQueued());
		contentValues.put(AbstractMessageModel.COLUMN_CAPTION, messageModel.getCaption());
		contentValues.put(AbstractMessageModel.COLUMN_QUOTED_MESSAGE_API_MESSAGE_ID, messageModel.getQuotedMessageId());
		contentValues.put(AbstractMessageModel.COLUMN_MESSAGE_CONTENTS_TYPE, messageModel.getMessageContentsType());
		contentValues.put(AbstractMessageModel.COLUMN_MESSAGE_FLAGS, messageModel.getMessageFlags());

		return contentValues;
	}

	void appendFilter(QueryBuilder queryBuilder, MessageService.MessageFilter filter, List<String> placeholders) {
		if(filter != null) {
			if(!filter.withStatusMessages()) {
				queryBuilder.appendWhere(AbstractMessageModel.COLUMN_IS_STATUS_MESSAGE + "=0");
			}
			if(filter.onlyUnread()) {
				queryBuilder.appendWhere(AbstractMessageModel.COLUMN_OUTBOX + "=0");
				queryBuilder.appendWhere(AbstractMessageModel.COLUMN_IS_READ + "=0");
				queryBuilder.appendWhere(AbstractMessageModel.COLUMN_IS_STATUS_MESSAGE + "=0");
			}

			if(!filter.withUnsaved()) {
				queryBuilder.appendWhere(AbstractMessageModel.COLUMN_IS_SAVED + "=1");
			}

			if(filter.types() != null && filter.types().length > 0) {
				queryBuilder.appendWhere(AbstractMessageModel.COLUMN_TYPE + " IN (" + DatabaseUtil.makePlaceholders(filter.types().length) + ")");
				for(MessageType f: filter.types()) {
					placeholders.add(String.valueOf(f.ordinal()));
				}
			}

			if(filter.contentTypes() != null && filter.contentTypes().length > 0) {
				queryBuilder.appendWhere(AbstractMessageModel.COLUMN_MESSAGE_CONTENTS_TYPE + " IN (" + DatabaseUtil.makePlaceholders(filter.contentTypes().length) + ")");
				for(@MessageContentsType int f: filter.contentTypes()) {
					placeholders.add(String.valueOf(f));
				}
			}

			if(filter.getPageReferenceId() != null && filter.getPageReferenceId() > 0) {
				queryBuilder.appendWhere(AbstractMessageModel.COLUMN_ID + "<?");
				placeholders.add(String.valueOf(filter.getPageReferenceId()));
			}
		}
	}

	<T> void postFilter(List<T> input, MessageService.MessageFilter filter) {
		if(filter != null && filter.onlyDownloaded()) {
			Iterator<T> i = input.iterator();
			while (i.hasNext()) {
				AbstractMessageModel m = (AbstractMessageModel)i.next();
				boolean remove = false;
				if (m.getType() == MessageType.VIDEO) {
					VideoDataModel d = m.getVideoData();
					remove = (d == null || !d.isDownloaded());
				} else if (m.getType() == MessageType.VOICEMESSAGE) {
					AudioDataModel d = m.getAudioData();
					remove = (d == null || !d.isDownloaded());
				} else if (m.getType() == MessageType.FILE) {
					FileDataModel d = m.getFileData();
					remove = (d == null || !d.isDownloaded());
				}

				if (remove) {
					i.remove();
				}
			}
		}
	}

	String limitFilter(MessageService.MessageFilter filter) {
		if (filter != null && filter.getPageSize() > 0) {
			return "" + filter.getPageSize();
		}
		return null;
	}

	public void markUnqueuedMessagesAsFailed() {
		List<String> params = new ArrayList<>();
		int messageTypeSize = 0;
		for(MessageType t: MessageUtil.getFileTypes()) {
			messageTypeSize++;
			params.add(String.valueOf(t.ordinal()));
		}
		params.add(MessageState.SENDFAILED.toString());

		//select all unqueued!
		ContentValues values = new ContentValues();
		values.put(AbstractMessageModel.COLUMN_STATE, MessageState.SENDFAILED.toString());

		try {
			int updated = this.databaseService.getWritableDatabase().update(
				this.getTableName(),
				values,
				AbstractMessageModel.COLUMN_TYPE + " IN (" + DatabaseUtil.makePlaceholders(messageTypeSize) + ") "
					+ "AND " + AbstractMessageModel.COLUMN_IS_QUEUED + " = 0 "
					+ "AND " + AbstractMessageModel.COLUMN_STATE + " != ? "
					+ "AND " + AbstractMessageModel.COLUMN_OUTBOX + " = 1",
				DatabaseUtil.convertArguments(params)
			);

			if (updated > 0) {
				logger.info(updated + " messages in sending status were updated to sendfailed.");
			}
		}
		catch (Exception e) {
			logger.error("Exception", e);
		}
	}
}

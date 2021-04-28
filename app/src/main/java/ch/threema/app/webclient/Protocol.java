/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2016-2021 Threema GmbH
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

package ch.threema.app.webclient;

import android.graphics.Bitmap;

import androidx.annotation.AnyThread;

@AnyThread
public class Protocol {
	public final static int PROTOCOL_VERSION = 2;

	public final static String FIELD_TYPE = "type";
	public final static String FIELD_SUB_TYPE = "subType";
	public final static String FIELD_DATA = "data";
	public final static String FIELD_ARGUMENTS = "args";
	public final static String TYPE_REQUEST = "request";
	public final static String TYPE_RESPONSE = "response";
	public final static String TYPE_UPDATE = "update";
	public final static String TYPE_CREATE = "create";
	public final static String TYPE_DELETE = "delete";
	public final static String SUB_TYPE_RECEIVER = "receiver";
	public final static String SUB_TYPE_RECEIVERS = "receivers";
	public final static String SUB_TYPE_CONVERSATION = "conversation";
	public final static String SUB_TYPE_CONVERSATIONS = "conversations";
	public final static String SUB_TYPE_MESSAGE = "message";
	public final static String SUB_TYPE_MESSAGES = "messages";
	public final static String SUB_TYPE_TEXT_MESSAGE = "textMessage";
	public final static String SUB_TYPE_FILE_MESSAGE = "fileMessage";
	public final static String SUB_TYPE_AVATAR = "avatar";
	public final static String SUB_TYPE_THUMBNAIL = "thumbnail";
	public final static String SUB_TYPE_TYPING = "typing";
	public final static String SUB_TYPE_READ = "read";
	public final static String SUB_TYPE_CLIENT_INFO = "clientInfo";
	public final static String SUB_TYPE_KEY_PERSISTED = "keyPersisted";
	public final static String SUB_TYPE_ACK = "ack";
	public final static String SUB_TYPE_BLOB = "blob";
	public final static String SUB_TYPE_CLEAN_RECEIVER_CONVERSATION = "cleanReceiverConversation";
	public final static String SUB_TYPE_CONTACT_DETAIL = "contactDetail";
	public final static String SUB_TYPE_CONTACT = "contact";
	public final static String SUB_TYPE_GROUP = "group";
	public final static String SUB_TYPE_DISTRIBUTION_LIST = "distributionList";
	public final static String SUB_TYPE_ALERT = "alert";
	public final static String SUB_TYPE_GROUP_SYNC = "groupSync";
	public final static String SUB_TYPE_BATTERY_STATUS = "batteryStatus";
	public final static String SUB_TYPE_VOIP_STATUS = "voipStatus";
	public final static String SUB_TYPE_CONFIRM_ACTION = "confirmAction";
	public final static String SUB_TYPE_PROFILE = "profile";
	public final static String SUB_TYPE_CONNECTION_INFO = "connectionInfo";
	public final static String SUB_TYPE_CONNECTION_DISCONNECT = "connectionDisconnect";
	public final static String SUB_TYPE_ACTIVE_CONVERSATION = "activeConversation";
	public final static String ARGUMENT_MODE = "mode";
	public final static String ARGUMENT_MODE_NEW = "new";
	public final static String ARGUMENT_MODE_MODIFIED = "modified";
	public final static String ARGUMENT_MODE_REMOVED = "removed";
	public final static String ARGUMENT_RECEIVER_TYPE = "type";
	public final static String ARGUMENT_RECEIVER_ID = "id";
	public final static String ARGUMENT_TEMPORARY_ID = "temporaryId";
	public final static String ARGUMENT_AVATAR = "avatar";
	public final static String ARGUMENT_AVATAR_HIGH_RESOLUTION = "highResolution";
	public final static String ARGUMENT_IS_TYPING = "isTyping";
	public final static String ARGUMENT_MESSAGE_ID = "messageId";
	public final static String ARGUMENT_REFERENCE_MSG_ID = "refMsgId";
	public final static String ARGUMENT_USER_AGENT = "userAgent";
	public final static String ARGUMENT_BROWSER_NAME = "browserName";
	public final static String ARGUMENT_BROWSER_VERSION = "browserVersion";
	public final static String ARGUMENT_IDENTITY = "identity";
	public final static String ARGUMENT_MESSAGE_ACKNOWLEDGED = "acknowledged";
	public final static String ARGUMENT_BLOB_BLOB = "blob";
	public final static String ARGUMENT_BLOB_TYPE = "type";
	public final static String ARGUMENT_BLOB_NAME = "name";
	public final static String ARGUMENT_SUCCESS = "success";
	public final static String ARGUMENT_ERROR = "error";
	public final static String ARGUMENT_NAME = "name";
	public final static String ARGUMENT_MEMBERS = "members";
	public final static String ARGUMENT_ALERT_SOURCE= "source";
	public final static String ARGUMENT_ALERT_TYPE = "type";
	public final static String ARGUMENT_ALERT_MESSAGE = "message";
	public final static String ARGUMENT_DELETE_TYPE = "deleteType";
	public final static String ARGUMENT_MAX_SIZE = "maxSize";
	public final static String ERROR_INTERNAL = "internalError";
	public final static String ERROR_BAD_REQUEST = "badRequest";
	public final static String ERROR_DISABLED_BY_POLICY = "disabledByPolicy";
	public final static String ERROR_INVALID_IDENTITY = "invalidIdentity";
	public final static String ERROR_INVALID_CONTACT = "invalidContact";
	public final static String ERROR_INVALID_GROUP = "invalidGroup";
	public final static String ERROR_INVALID_DISTRIBUTION_LIST = "invalidDistributionList";
	public final static String ERROR_INVALID_CONVERSATION = "invalidConversation";
	public final static String ERROR_NOT_ALLOWED_LINKED = "notAllowedLinked";
	public final static String ERROR_NOT_ALLOWED_BUSINESS = "notAllowedBusiness";
	public final static String ERROR_GROUP_SYNC_FAILED = "syncFailed";
	public final static String ERROR_NOT_ALLOWED = "notAllowed";
	public final static String ERROR_NO_MEMBERS = "noMembers";
	public final static String ERROR_ALREADY_LEFT = "alreadyLeft";
	public final static String ERROR_INVALID_AVATAR = "invalidAvatar";
	public final static String ERROR_BLOB_DOWNLOAD_FAILED = "blobDownloadFailed";
	public final static String ERROR_BLOB_DECRYPT_FAILED = "blobDecryptFailed";
	public final static String ERROR_INVALID_MESSAGE = "invalidMessage";
	public final static String ERROR_VALUE_TOO_LONG = "valueTooLong";

	public final static Bitmap.CompressFormat FORMAT_AVATAR = Bitmap.CompressFormat.PNG;
	public final static Bitmap.CompressFormat FORMAT_THUMBNAIL = Bitmap.CompressFormat.JPEG;

	public final static int SIZE_PREVIEW_MAX_PX = 50;
	public final static int SIZE_THUMBNAIL_MAX_PX = 350;
	public final static int SIZE_AVATAR_HIRES_MAX_PX = 512;
	public final static int SIZE_AVATAR_LORES_MAX_PX = 48;

	public final static int QUALITY_AVATAR_HIRES = 75;
	public final static int QUALITY_AVATAR_LORES = 100;
	public final static int QUALITY_PREVIEW = 50;
	public final static int QUALITY_THUMBNAIL = 75;

	public final static int LIMIT_BYTES_PUBLIC_NICKNAME = 32;
	public final static int LIMIT_BYTES_FIRST_NAME = 256;
	public final static int LIMIT_BYTES_LAST_NAME = 256;
	public final static int LIMIT_BYTES_GROUP_NAME = 256;
	public final static int LIMIT_BYTES_DISTRIBUTION_LIST_NAME = 256;

	private Protocol() {
		// This class only contains static fields and should not be instantiated
	}
}

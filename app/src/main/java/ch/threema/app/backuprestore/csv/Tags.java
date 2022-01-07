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

package ch.threema.app.backuprestore.csv;

public abstract class Tags {
	public static final String SETTINGS_FILE_NAME = "settings";
	public static final String APP_SETTINGS_FILE_NAME = "app_settings";
	public static final String IDENTITY_FILE_NAME = "identity";
	public static final String CONTACTS_FILE_NAME = "contacts";
	public static final String GROUPS_FILE_NAME = "groups";
	public static final String MESSAGE_FILE_PREFIX = "message_";
	public static final String GROUP_MESSAGE_FILE_PREFIX = "group_message_";
	public static final String DISTRIBUTION_LIST_MESSAGE_FILE_PREFIX = "distribution_list_message_";
	public static final String MESSAGE_MEDIA_FILE_PREFIX = "message_media_";
	public static final String GROUP_MESSAGE_MEDIA_FILE_PREFIX = "group_message_media_";
	public static final String MESSAGE_MEDIA_THUMBNAIL_FILE_PREFIX = "message_thumbnail_";
	public static final String GROUP_MESSAGE_MEDIA_THUMBNAIL_FILE_PREFIX = "group_message_thumbnail_";
	public static final String CONTACT_AVATAR_FILE_PREFIX = "contact_avatar_";
	public static final String CONTACT_PROFILE_PIC_FILE_PREFIX = "contact_profile_pic_";

	public static final String DISTRIBUTION_LIST_MESSAGE_MEDIA_FILE_PREFIX = "distribution_list_message_media_";
	public static final String DISTRIBUTION_LIST_MESSAGE_MEDIA_THUMBNAIL_FILE_PREFIX = "distribution_list_thumbnail_";

	public static final String GROUP_AVATAR_PREFIX = "group_avatar_";
	public static final String DISTRIBUTION_LISTS_FILE_NAME = "distribution_list";
	public static final String BALLOT_FILE_NAME = "ballot";
	public static final String BALLOT_CHOICE_FILE_NAME = "ballot_choice";
	public static final String BALLOT_VOTE_FILE_NAME = "ballot_vote";
	public static final String CSV_FILE_POSTFIX = ".csv";

	public static final String TAG_INFO_VERSION = "version";

	public static final String TAG_CONTACT_IDENTITY = "identity";
	public static final String TAG_CONTACT_FIRST_NAME = "firstname";
	public static final String TAG_CONTACT_LAST_NAME = "lastname";
	public static final String TAG_CONTACT_PUBLIC_KEY = "publickey";
	public static final String TAG_CONTACT_NICK_NAME = "nick_name";
	public static final String TAG_CONTACT_COLOR = "color";
	public static final String TAG_CONTACT_VERIFICATION_LEVEL = "verification";
	public static final String TAG_CONTACT_ANDROID_CONTACT_ID = "acid";
	public static final String TAG_CONTACT_THREEMA_ANDROID_CONTACT_ID = "tacid";
	public static final String TAG_CONTACT_HIDDEN = "hidden";
	public static final String TAG_CONTACT_ARCHIVED = "archived";

	public static final String TAG_GROUP_ID = "id";
	public static final String TAG_GROUP_CREATOR = "creator";
	public static final String TAG_GROUP_NAME = "groupname";
	public static final String TAG_GROUP_CREATED_AT = "created_at";
	public static final String TAG_GROUP_MEMBERS = "members";
	public static final String TAG_GROUP_DELETED = "deleted";
	public static final String TAG_GROUP_ARCHIVED = "archived";

	public static final String TAG_MESSAGE_UID = "uid";
	public static final String TAG_MESSAGE_IDENTITY = "identity";
	public static final String TAG_MESSAGE_BODY = "body";
	public static final String TAG_MESSAGE_TYPE = "type";
	public static final String TAG_MESSAGE_POSTED_AT = "posted_at";
	public static final String TAG_MESSAGE_API_MESSAGE_ID = "apiid";
	public static final String TAG_MESSAGE_IS_OUTBOX = "isoutbox";
	public static final String TAG_MESSAGE_IS_READ = "isread";
	public static final String TAG_MESSAGE_IS_SAVED = "issaved";
	public static final String TAG_MESSAGE_CREATED_AT = "created_at";
	public static final String TAG_MESSAGE_MODIFIED_AT = "modified_at";
	public static final String TAG_MESSAGE_DELIVERED_AT = "delivered_at";
	public static final String TAG_MESSAGE_READ_AT = "read_at";

	public static final String TAG_MESSAGE_MESSAGE_STATE = "messagestae";
	public static final String TAG_MESSAGE_IS_STATUS_MESSAGE = "isstatusmessage";
	public static final String TAG_MESSAGE_IS_QUEUED = "isqueued";
	public static final String TAG_MESSAGE_CAPTION = "caption";
	public static final String TAG_MESSAGE_QUOTED_MESSAGE_ID = "quoted_message_apiid";

	public static final String TAG_DISTRIBUTION_LIST_ID = "id";
	public static final String TAG_DISTRIBUTION_LIST_NAME = "distribution_list_name";
	public static final String TAG_DISTRIBUTION_CREATED_AT = "created_at";
	public static final String TAG_DISTRIBUTION_MEMBERS = "distribution_members";
	public static final String TAG_DISTRIBUTION_LIST_ARCHIVED = "archived";

	public static final String TAG_BALLOT_ID = "id";
	public static final String TAG_BALLOT_API_ID = "aid";
	public static final String TAG_BALLOT_API_CREATOR = "creator";
	public static final String TAG_BALLOT_REF = "ref";
	public static final String TAG_BALLOT_REF_ID = "ref_id";
	public static final String TAG_BALLOT_NAME = "name";
	public static final String TAG_BALLOT_STATE = "state";
	public static final String TAG_BALLOT_ASSESSMENT = "assessment";
	public static final String TAG_BALLOT_TYPE = "type";
	public static final String TAG_BALLOT_C_TYPE = "choice_type";
	public static final String TAG_BALLOT_LAST_VIEWED_AT = "last_viewed_at";
	public static final String TAG_BALLOT_CREATED_AT = "created_at";
	public static final String TAG_BALLOT_MODIFIED_AT = "modified_at";

	public static final String TAG_BALLOT_CHOICE_ID = "id";
	public static final String TAG_BALLOT_CHOICE_BALLOT_UID = "ballot";
	public static final String TAG_BALLOT_CHOICE_API_ID = "aid";
	public static final String TAG_BALLOT_CHOICE_TYPE = "type";
	public static final String TAG_BALLOT_CHOICE_NAME = "name";
	public static final String TAG_BALLOT_CHOICE_VOTE_COUNT= "vote_count";
	public static final String TAG_BALLOT_CHOICE_ORDER = "order";
	public static final String TAG_BALLOT_CHOICE_CREATED_AT = "created_at";
	public static final String TAG_BALLOT_CHOICE_MODIFIED_AT = "modified_at";

	public static final String TAG_BALLOT_VOTE_ID = "id";
	public static final String TAG_BALLOT_VOTE_BALLOT_UID = "ballot_uid";
	public static final String TAG_BALLOT_VOTE_CHOICE_UID = "choice_uid";
	public static final String TAG_BALLOT_VOTE_IDENTITY = "identity";
	public static final String TAG_BALLOT_VOTE_CHOICE = "choice";
	public static final String TAG_BALLOT_VOTE_CREATED_AT = "created_at";
	public static final String TAG_BALLOT_VOTE_MODIFIED_AT = "modified_at";
}

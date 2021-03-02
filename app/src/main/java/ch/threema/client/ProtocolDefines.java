/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema Java Client
 * Copyright (c) 2013-2021 Threema GmbH
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

package ch.threema.client;

public class ProtocolDefines {

	/* Timers and timeouts (in seconds) */
	public static final int CONNECT_TIMEOUT = 15;
	public static final int CONNECT_TIMEOUT_IPV6 = 3;

	public static final int READ_TIMEOUT = 20;
	public static final int WRITE_TIMEOUT = 20;
	public static final int KEEPALIVE_INTERVAL = 180;
	public static final int BLOB_CONNECT_TIMEOUT = 30;
	public static final int BLOB_LOAD_TIMEOUT = 100;
	public static final int API_REQUEST_TIMEOUT = 20;

	public static final int CLIENT_TEMPKEY_MAXAGE = 7*86400;

	public static final int RECONNECT_BASE_INTERVAL = 2;
	public static final int RECONNECT_MAX_INTERVAL = 10;

	/* object lengths */
	public static final int COOKIE_LEN = 16;
	public static final int PUSH_FROM_LEN = 32;
	public static final int IDENTITY_LEN = 8;
	public static final int MESSAGE_ID_LEN = 8;
	public static final int BLOB_ID_LEN = 16;
	public static final int BLOB_KEY_LEN = 32;
	public static final int GROUP_ID_LEN = 8;
	public static final int BALLOT_ID_LEN = 8;

	public static final int BALLOT_STATE_LEN = 1;
	public static final int BALLOT_ASSESSMENT_TYPE_LEN = 1;
	public static final int BALLOT_VISIBILITY_LEN = 1;

	/* max message size */
	public static final int MAX_PKT_LEN = 8192;
	public static final int OVERHEAD_NACL_BOX = 16; // Excluding nonce
	public static final int OVERHEAD_PKT_HDR = 4;
	public static final int OVERHEAD_MSG_HDR = 88;
	public static final int OVERHEAD_BOX_HDR = 1;
	public static final int OVERHEAD_MAXPADDING = 255;
	public static final int MAX_MESSAGE_LEN = MAX_PKT_LEN
		- OVERHEAD_NACL_BOX * 2 // Both app-to-server and end-to-end
		- OVERHEAD_PKT_HDR
		- OVERHEAD_MSG_HDR
		- OVERHEAD_BOX_HDR
		- OVERHEAD_MAXPADDING;
	public static final int MAX_TEXT_MESSAGE_LEN = 3500;  // Until ANDR-998 and IOS-865 are resolved
	public static final int MIN_MESSAGE_PADDED_LEN = 32;

	/* message type */
	public static final int MSGTYPE_TEXT = 0x01;
	public static final int MSGTYPE_IMAGE = 0x02;
	public static final int MSGTYPE_LOCATION = 0x10;
	public static final int MSGTYPE_VIDEO = 0x13;
	public static final int MSGTYPE_AUDIO = 0x14;
	public static final int MSGTYPE_BALLOT_CREATE = 0x15;
	public static final int MSGTYPE_BALLOT_VOTE = 0x16;
	public static final int MSGTYPE_FILE = 0x17;
	public static final int MSGTYPE_CONTACT_SET_PHOTO = 0x18;
	public static final int MSGTYPE_CONTACT_DELETE_PHOTO = 0x19;
	public static final int MSGTYPE_CONTACT_REQUEST_PHOTO = 0x1a;
	public static final int MSGTYPE_GROUP_TEXT = 0x41;
	public static final int MSGTYPE_GROUP_LOCATION = 0x42;
	public static final int MSGTYPE_GROUP_IMAGE = 0x43;
	public static final int MSGTYPE_GROUP_VIDEO = 0x44;
	public static final int MSGTYPE_GROUP_AUDIO = 0x45;
	public static final int MSGTYPE_GROUP_FILE = 0x46;
	public static final int MSGTYPE_GROUP_CREATE = 0x4a;
	public static final int MSGTYPE_GROUP_RENAME = 0x4b;
	public static final int MSGTYPE_GROUP_LEAVE = 0x4c;
	public static final int MSGTYPE_GROUP_ADD_MEMBER = 0x4d;
	public static final int MSGTYPE_GROUP_REMOVE_MEMBER = 0x4e;
	public static final int MSGTYPE_GROUP_DESTROY = 0x4f;
	public static final int MSGTYPE_GROUP_SET_PHOTO = 0x50;
	public static final int MSGTYPE_GROUP_REQUEST_SYNC = 0x51;
	public static final int MSGTYPE_GROUP_BALLOT_CREATE = 0x52;
	public static final int MSGTYPE_GROUP_BALLOT_VOTE = 0x53;
	public static final int MSGTYPE_GROUP_DELETE_PHOTO = 0x54;
	public static final int MSGTYPE_VOIP_CALL_OFFER = 0x60;
	public static final int MSGTYPE_VOIP_CALL_ANSWER = 0x61;
	public static final int MSGTYPE_VOIP_ICE_CANDIDATES = 0x62;
	public static final int MSGTYPE_VOIP_CALL_HANGUP = 0x63;
	public static final int MSGTYPE_VOIP_CALL_RINGING = 0x64;
	public static final int MSGTYPE_DELIVERY_RECEIPT = 0x80;
	public static final int MSGTYPE_TYPING_INDICATOR = 0x90;

	/* message flags */
	public static final int MESSAGE_FLAG_PUSH = 0x01;
	public static final int MESSAGE_FLAG_IMMEDIATE = 0x02;
	public static final int MESSAGE_FLAG_NOACK = 0x04;
	public static final int MESSAGE_FLAG_GROUP = 0x10;
	public static final int MESSAGE_FLAG_VOIP = 0x20;
	public static final int MESSAGE_FLAG_NO_DELIVERY_RECEIPTS = 0x80;

	/* delivery receipt statuses */
	public static final int DELIVERYRECEIPT_MSGRECEIVED = 0x01;
	public static final int DELIVERYRECEIPT_MSGREAD = 0x02;
	public static final int DELIVERYRECEIPT_MSGUSERACK = 0x03;
	public static final int DELIVERYRECEIPT_MSGUSERDEC = 0x04;

	/* payload types */
	public static final int PLTYPE_ECHO_REQUEST = 0x00;
	public static final int PLTYPE_ECHO_REPLY = 0x80;
	public static final int PLTYPE_OUTGOING_MESSAGE = 0x01;
	public static final int PLTYPE_OUTGOING_MESSAGE_ACK = 0x81;
	public static final int PLTYPE_INCOMING_MESSAGE = 0x02;
	public static final int PLTYPE_INCOMING_MESSAGE_ACK = 0x82;
	public static final int PLTYPE_PUSH_NOTIFICATION_TOKEN = 0x20;
	public static final int PLTYPE_PUSH_ALLOWED_IDENTITIES = 0x21;
	public static final int PLTYPE_VOIP_PUSH_NOTIFICATION_TOKEN = 0x24;
	public static final int PLTYPE_QUEUE_SEND_COMPLETE = 0xd0;
	public static final int PLTYPE_ERROR = 0xe0;
	public static final int PLTYPE_ALERT = 0xe1;

	/* push token types */
	public static final int PUSHTOKEN_TYPE_NONE = 0x00;
	public static final int PUSHTOKEN_TYPE_GCM = 0x11;

	/* nonces */
	public static final byte[] IMAGE_NONCE = new byte[]{0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x01};
	public static final byte[] VIDEO_NONCE = new byte[]{0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x01};
	public static final byte[] THUMBNAIL_NONCE = new byte[]{0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x02};
	public static final byte[] AUDIO_NONCE = new byte[]{0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x01};
	public static final byte[] GROUP_PHOTO_NONCE = new byte[]{0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x01};
	public static final byte[] CONTACT_PHOTO_NONCE = new byte[]{0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x01};
	public static final byte[] FILE_NONCE = new byte[]{0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x01};
	public static final byte[] FILE_THUMBNAIL_NONCE = new byte[]{0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x02};
}

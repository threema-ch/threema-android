/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2021-2022 Threema GmbH
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

package ch.threema.domain.protocol.csp.coders;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.neilalexander.jnacl.NaCl;

import org.apache.commons.io.EndianUtils;
import org.slf4j.Logger;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Date;

import androidx.annotation.NonNull;
import ch.threema.base.ThreemaException;
import ch.threema.base.crypto.NonceFactory;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.domain.models.Contact;
import ch.threema.domain.models.GroupId;
import ch.threema.domain.models.MessageId;
import ch.threema.domain.protocol.csp.ProtocolDefines;
import ch.threema.domain.protocol.csp.messages.AbstractMessage;
import ch.threema.domain.protocol.csp.messages.BadMessageException;
import ch.threema.domain.protocol.csp.messages.BoxAudioMessage;
import ch.threema.domain.protocol.csp.messages.BoxImageMessage;
import ch.threema.domain.protocol.csp.messages.BoxLocationMessage;
import ch.threema.domain.protocol.csp.messages.BoxTextMessage;
import ch.threema.domain.protocol.csp.messages.BoxVideoMessage;
import ch.threema.domain.protocol.csp.messages.ContactDeletePhotoMessage;
import ch.threema.domain.protocol.csp.messages.ContactRequestPhotoMessage;
import ch.threema.domain.protocol.csp.messages.ContactSetPhotoMessage;
import ch.threema.domain.protocol.csp.messages.DeliveryReceiptMessage;
import ch.threema.domain.protocol.csp.messages.GroupAudioMessage;
import ch.threema.domain.protocol.csp.messages.GroupCreateMessage;
import ch.threema.domain.protocol.csp.messages.GroupDeletePhotoMessage;
import ch.threema.domain.protocol.csp.messages.GroupImageMessage;
import ch.threema.domain.protocol.csp.messages.GroupLeaveMessage;
import ch.threema.domain.protocol.csp.messages.GroupLocationMessage;
import ch.threema.domain.protocol.csp.messages.GroupRenameMessage;
import ch.threema.domain.protocol.csp.messages.GroupRequestSyncMessage;
import ch.threema.domain.protocol.csp.messages.GroupSetPhotoMessage;
import ch.threema.domain.protocol.csp.messages.GroupTextMessage;
import ch.threema.domain.protocol.csp.messages.GroupVideoMessage;
import ch.threema.domain.protocol.csp.messages.MissingPublicKeyException;
import ch.threema.domain.protocol.csp.messages.TypingIndicatorMessage;
import ch.threema.domain.protocol.csp.messages.ballot.BallotCreateMessage;
import ch.threema.domain.protocol.csp.messages.ballot.BallotData;
import ch.threema.domain.protocol.csp.messages.ballot.BallotId;
import ch.threema.domain.protocol.csp.messages.ballot.BallotVoteMessage;
import ch.threema.domain.protocol.csp.messages.ballot.GroupBallotCreateMessage;
import ch.threema.domain.protocol.csp.messages.ballot.GroupBallotVoteMessage;
import ch.threema.domain.protocol.csp.messages.file.FileData;
import ch.threema.domain.protocol.csp.messages.file.FileMessage;
import ch.threema.domain.protocol.csp.messages.file.GroupFileMessage;
import ch.threema.domain.protocol.csp.messages.group.GroupJoinRequestData;
import ch.threema.domain.protocol.csp.messages.group.GroupJoinRequestMessage;
import ch.threema.domain.protocol.csp.messages.group.GroupJoinResponseData;
import ch.threema.domain.protocol.csp.messages.group.GroupJoinResponseMessage;
import ch.threema.domain.protocol.csp.messages.voip.VoipCallAnswerData;
import ch.threema.domain.protocol.csp.messages.voip.VoipCallAnswerMessage;
import ch.threema.domain.protocol.csp.messages.voip.VoipCallHangupData;
import ch.threema.domain.protocol.csp.messages.voip.VoipCallHangupMessage;
import ch.threema.domain.protocol.csp.messages.voip.VoipCallOfferData;
import ch.threema.domain.protocol.csp.messages.voip.VoipCallOfferMessage;
import ch.threema.domain.protocol.csp.messages.voip.VoipCallRingingData;
import ch.threema.domain.protocol.csp.messages.voip.VoipCallRingingMessage;
import ch.threema.domain.protocol.csp.messages.voip.VoipICECandidatesData;
import ch.threema.domain.protocol.csp.messages.voip.VoipICECandidatesMessage;
import ch.threema.domain.stores.ContactStore;
import ch.threema.domain.stores.IdentityStoreInterface;
import ch.threema.protobuf.csp.e2e.MessageMetadata;

import static java.nio.charset.StandardCharsets.UTF_8;

public class MessageCoder {
	private static final Logger logger = LoggingUtil.getThreemaLogger("MessageCoder");

	private final @NonNull ContactStore contactStore;
	private final @NonNull IdentityStoreInterface identityStore;

	/**
	 *
	 * @param contactStore contact store to use for retrieving keys
	 * @param identityStore identity store to use for encryption
	 */
	public MessageCoder(@NonNull ContactStore contactStore, @NonNull IdentityStoreInterface identityStore) {
		this.contactStore = contactStore;
		this.identityStore = identityStore;
	}


	/**
	 * Attempt to decrypt a boxed (encrypted) message using the given contact and identity store
	 * and return the decrypted result. The result will be a subclass of AbstractMessage according
	 * to the message type. Note that the contact is not added, if it does not exist.
	 *
	 * @param boxmsg boxed message to be decrypted
	 * @param fetch if true, attempt to fetch missing public keys from network (may cause delays)
	 * @return decrypted message, or null if the message type is not supported
	 * @throws BadMessageException if the message cannot be decrypted successfully
	 * @throws MissingPublicKeyException if the sender's public key cannot be obtained
	 */
	public AbstractMessage decode(@NonNull MessageBox boxmsg, boolean fetch) throws BadMessageException, MissingPublicKeyException {

		if (!boxmsg.getToIdentity().equals(identityStore.getIdentity())) {
			throw new BadMessageException("Message is not for own identity, cannot decode");
		}

		// check if contact already exists
		Contact contact = contactStore.getContactForIdentity(boxmsg.getFromIdentity());
		boolean addContact = contact == null;

		/* obtain public key of sender */
		Contact fetchedContact = contactStore.getContactForIdentity(boxmsg.getFromIdentity(), fetch, false);

		if (fetchedContact == null) {
			throw new MissingPublicKeyException("Missing public key for ID " + boxmsg.getFromIdentity());
		}

		/* decrypt with our secret key */
		byte[] data = identityStore.decryptData(boxmsg.getBox(), boxmsg.getNonce(), fetchedContact.getPublicKey());
		if (data == null) {
			throw new BadMessageException("Decryption of message from " + boxmsg.getFromIdentity() + " failed");
		}

		if (data.length == 1) {
			throw new BadMessageException("Empty message received");
		}

		/* remove padding */
		int padbytes = data[data.length - 1] & 0xFF;
		int realDataLength = data.length - padbytes;
		if (realDataLength < 1) {
			throw new BadMessageException("Bad message padding");
		}

		// Check

		MessageCoder.logger.debug("Effective data length is {}", realDataLength);

		/* first byte of data is type */
		int type = data[0] & 0xFF;
		AbstractMessage msg = null;

		// Set this flag to true for message types that should add the contact as hidden contact
		boolean addHidden = false;

		switch (type) {
			case ProtocolDefines.MSGTYPE_TEXT: {
				if (realDataLength < 2) {
					throw new BadMessageException("Bad length (" + realDataLength + ") for text message");
				}

				BoxTextMessage textmsg = new BoxTextMessage();
				textmsg.setText(new String(data, 1, realDataLength - 1, UTF_8));
				msg = textmsg;
				break;
			}

			case ProtocolDefines.MSGTYPE_IMAGE: {
				if (realDataLength != (1 + ProtocolDefines.BLOB_ID_LEN + 4 + NaCl.NONCEBYTES)) {
					throw new BadMessageException("Bad length (" + realDataLength + ") for image message");
				}

				BoxImageMessage imagemsg = new BoxImageMessage();

				byte[] blobId = new byte[ProtocolDefines.BLOB_ID_LEN];
				System.arraycopy(data, 1, blobId, 0, ProtocolDefines.BLOB_ID_LEN);
				imagemsg.setBlobId(blobId);

				int size = EndianUtils.readSwappedInteger(data, 1 + ProtocolDefines.BLOB_ID_LEN);
				imagemsg.setSize(size);

				byte[] nonce = new byte[NaCl.NONCEBYTES];
				System.arraycopy(data, 1 + 4 + ProtocolDefines.BLOB_ID_LEN, nonce, 0, nonce.length);
				imagemsg.setNonce(nonce);

				msg = imagemsg;

				break;
			}

			case ProtocolDefines.MSGTYPE_VIDEO: {
				if (realDataLength != (1 + 2 + ProtocolDefines.BLOB_ID_LEN + 4 + ProtocolDefines.BLOB_ID_LEN + 4 + ProtocolDefines.BLOB_KEY_LEN)) {
					throw new BadMessageException("Bad length (" + realDataLength + ") for video message");
				}

				BoxVideoMessage videomsg = new BoxVideoMessage();

				ByteArrayInputStream bis = new ByteArrayInputStream(data, 1, data.length - 1);

				try {
					videomsg.setDuration(EndianUtils.readSwappedShort(bis));

					byte[] videoBlobId = new byte[ProtocolDefines.BLOB_ID_LEN];
					bis.read(videoBlobId);
					videomsg.setVideoBlobId(videoBlobId);

					videomsg.setVideoSize(EndianUtils.readSwappedInteger(bis));

					byte[] thumbnailBlobId = new byte[ProtocolDefines.BLOB_ID_LEN];
					bis.read(thumbnailBlobId);
					videomsg.setThumbnailBlobId(thumbnailBlobId);

					videomsg.setThumbnailSize(EndianUtils.readSwappedInteger(bis));

					byte[] encryptionKey = new byte[ProtocolDefines.BLOB_KEY_LEN];
					bis.read(encryptionKey);
					videomsg.setEncryptionKey(encryptionKey);
				} catch (IOException e) {
					throw new RuntimeException(e);
				}

				msg = videomsg;

				break;
			}

			case ProtocolDefines.MSGTYPE_LOCATION: {
				if (realDataLength < 4) {
					throw new BadMessageException("Bad length (" + realDataLength + ") for location message");
				}

				String locStr = new String(data, 1, realDataLength - 1, UTF_8);
				String[] lines = locStr.split("\n");
				String[] locArr = lines[0].split(",");

				MessageCoder.logger.info("Raw location message: {}", locStr);

				if (locArr.length < 2 || locArr.length > 3) {
					throw new BadMessageException("Bad coordinate format in location message");
				}

				BoxLocationMessage locationmsg = new BoxLocationMessage();
				locationmsg.setLatitude(Double.parseDouble(locArr[0]));
				locationmsg.setLongitude(Double.parseDouble(locArr[1]));

				if (locArr.length == 3) {
					locationmsg.setAccuracy(Double.parseDouble(locArr[2]));
				}

				String address = null;
				if (lines.length == 2) {
					address = lines[1];
				} else if (lines.length >= 3) {
					locationmsg.setPoiName(lines[1]);
					address = lines[2];
				}

				if (address != null) {
					locationmsg.setPoiAddress(address.replace("\\n", "\n"));
				}

				if (locationmsg.getLatitude() < -90.0 || locationmsg.getLatitude() > 90.0 ||
					locationmsg.getLongitude() < -180.0 || locationmsg.getLongitude() > 180.0) {
					throw new BadMessageException("Invalid coordinate values in location message");
				}

				msg = locationmsg;

				break;
			}

			case ProtocolDefines.MSGTYPE_AUDIO: {
				if (realDataLength != (1 + 2 + ProtocolDefines.BLOB_ID_LEN + 4 + ProtocolDefines.BLOB_KEY_LEN)) {
					throw new BadMessageException("Bad length (" + realDataLength + ") for audio message");
				}

				BoxAudioMessage audiomsg = new BoxAudioMessage();

				ByteArrayInputStream bis = new ByteArrayInputStream(data, 1, data.length - 1);

				try {
					audiomsg.setDuration(EndianUtils.readSwappedShort(bis));

					byte[] audioBlobId = new byte[ProtocolDefines.BLOB_ID_LEN];
					bis.read(audioBlobId);
					audiomsg.setAudioBlobId(audioBlobId);

					audiomsg.setAudioSize(EndianUtils.readSwappedInteger(bis));

					byte[] encryptionKey = new byte[ProtocolDefines.BLOB_KEY_LEN];
					bis.read(encryptionKey);
					audiomsg.setEncryptionKey(encryptionKey);
				} catch (IOException e) {
					throw new RuntimeException(e);
				}

				msg = audiomsg;

				break;
			}

			case ProtocolDefines.MSGTYPE_GROUP_CREATE: {
				if (realDataLength < (1 + ProtocolDefines.GROUP_ID_LEN + ProtocolDefines.IDENTITY_LEN) ||
						((realDataLength - 1 - ProtocolDefines.GROUP_ID_LEN) % ProtocolDefines.IDENTITY_LEN) != 0) {
					throw new BadMessageException("Bad length (" + realDataLength + ") for group create message");
				}

				GroupCreateMessage groupcreatemsg = new GroupCreateMessage();
				groupcreatemsg.setGroupCreator(boxmsg.getFromIdentity());
				groupcreatemsg.setGroupId(new GroupId(data, 1));
				int numMembers = ((realDataLength - ProtocolDefines.GROUP_ID_LEN - 1) / ProtocolDefines.IDENTITY_LEN);
				String[] members = new String[numMembers];
				for (int i = 0; i < numMembers; i++) {
					members[i] = new String(data, 1 + ProtocolDefines.GROUP_ID_LEN + i * ProtocolDefines.IDENTITY_LEN, ProtocolDefines.IDENTITY_LEN, StandardCharsets.US_ASCII);
				}
				groupcreatemsg.setMembers(members);
				msg = groupcreatemsg;
				break;
			}

			case ProtocolDefines.MSGTYPE_GROUP_REQUEST_SYNC: {
				if (realDataLength != (1 + ProtocolDefines.GROUP_ID_LEN)) {
					throw new BadMessageException("Bad length (" + realDataLength + ") for group request sync message");
				}

				GroupRequestSyncMessage groupRequestSyncMessage = new GroupRequestSyncMessage();
				groupRequestSyncMessage.setGroupCreator(boxmsg.getToIdentity());
				groupRequestSyncMessage.setGroupId(new GroupId(data, 1));

				msg = groupRequestSyncMessage;

				break;
			}

			case ProtocolDefines.MSGTYPE_GROUP_RENAME: {
				if (realDataLength < (1 + ProtocolDefines.GROUP_ID_LEN)) {
					throw new BadMessageException("Bad length (" + realDataLength + ") for group rename message");
				}

				GroupRenameMessage grouprenamemsg = new GroupRenameMessage();
				grouprenamemsg.setGroupCreator(boxmsg.getFromIdentity());
				grouprenamemsg.setGroupId(new GroupId(data, 1));
				grouprenamemsg.setGroupName(new String(data, 1 + ProtocolDefines.GROUP_ID_LEN, realDataLength - 1 - ProtocolDefines.GROUP_ID_LEN, UTF_8));
				msg = grouprenamemsg;

				break;
			}

			case ProtocolDefines.MSGTYPE_GROUP_LEAVE: {
				if (realDataLength != (1 + ProtocolDefines.IDENTITY_LEN + ProtocolDefines.GROUP_ID_LEN)) {
					throw new BadMessageException("Bad length (" + realDataLength + ") for group leave message");
				}

				GroupLeaveMessage groupleavemsg = new GroupLeaveMessage();
				groupleavemsg.setGroupCreator(new String(data, 1, ProtocolDefines.IDENTITY_LEN, StandardCharsets.US_ASCII));
				groupleavemsg.setGroupId(new GroupId(data, 1 + ProtocolDefines.IDENTITY_LEN));
				msg = groupleavemsg;
				break;
			}

			case ProtocolDefines.MSGTYPE_GROUP_TEXT: {
				if (realDataLength < (1 + ProtocolDefines.IDENTITY_LEN + ProtocolDefines.GROUP_ID_LEN)) {
					throw new BadMessageException("Bad length (" + realDataLength + ") for group text message");
				}

				GroupTextMessage grouptextmsg = new GroupTextMessage();
				grouptextmsg.setGroupCreator(new String(data, 1, ProtocolDefines.IDENTITY_LEN, StandardCharsets.US_ASCII));
				grouptextmsg.setGroupId(new GroupId(data, 1 + ProtocolDefines.IDENTITY_LEN));
				grouptextmsg.setText(new String(data, 1 + ProtocolDefines.IDENTITY_LEN + ProtocolDefines.GROUP_ID_LEN, realDataLength - 1 - ProtocolDefines.IDENTITY_LEN - ProtocolDefines.GROUP_ID_LEN, UTF_8));
				msg = grouptextmsg;
				break;
			}

			case ProtocolDefines.MSGTYPE_GROUP_SET_PHOTO: {
				if (realDataLength != (1 + ProtocolDefines.GROUP_ID_LEN + ProtocolDefines.BLOB_ID_LEN + 4 + ProtocolDefines.BLOB_KEY_LEN)) {
					throw new BadMessageException("Bad length (" + realDataLength + ") for group set photo message");
				}

				GroupSetPhotoMessage groupsetphotomsg = new GroupSetPhotoMessage();
				groupsetphotomsg.setGroupCreator(boxmsg.getFromIdentity());

				int i = 1;
				groupsetphotomsg.setGroupId(new GroupId(data, i));
				i += ProtocolDefines.GROUP_ID_LEN;
				byte[] blobId = new byte[ProtocolDefines.BLOB_ID_LEN];
				System.arraycopy(data, i, blobId, 0, ProtocolDefines.BLOB_ID_LEN);
				i += ProtocolDefines.BLOB_ID_LEN;
				groupsetphotomsg.setBlobId(blobId);
				groupsetphotomsg.setSize(EndianUtils.readSwappedInteger(data, i));
				i += 4;
				byte[] blobKey = new byte[ProtocolDefines.BLOB_KEY_LEN];
				System.arraycopy(data, i, blobKey, 0, ProtocolDefines.BLOB_KEY_LEN);
				groupsetphotomsg.setEncryptionKey(blobKey);
				msg = groupsetphotomsg;

				break;
			}

			case ProtocolDefines.MSGTYPE_GROUP_DELETE_PHOTO: {
				if (realDataLength != (1 + ProtocolDefines.GROUP_ID_LEN)) {
					throw new BadMessageException("Bad length (" + realDataLength + ") for group delete photo message");
				}

				GroupDeletePhotoMessage groupDeletePhotoMessage = new GroupDeletePhotoMessage();
				groupDeletePhotoMessage.setGroupCreator(boxmsg.getFromIdentity());
				groupDeletePhotoMessage.setGroupId(new GroupId(data, 1));

				msg = groupDeletePhotoMessage;

				break;
			}

			case ProtocolDefines.MSGTYPE_GROUP_IMAGE: {
				if (realDataLength != (1 + ProtocolDefines.IDENTITY_LEN + ProtocolDefines.GROUP_ID_LEN + ProtocolDefines.BLOB_ID_LEN + 4 + ProtocolDefines.BLOB_KEY_LEN)) {
					throw new BadMessageException("Bad length (" + realDataLength + ") for group image message");
				}

				int i = 1;

				GroupImageMessage groupimagemsg = new GroupImageMessage();
				groupimagemsg.setGroupCreator(new String(data, i, ProtocolDefines.IDENTITY_LEN, StandardCharsets.US_ASCII));
				i += ProtocolDefines.IDENTITY_LEN;
				groupimagemsg.setGroupId(new GroupId(data, i));
				i += ProtocolDefines.GROUP_ID_LEN;
				byte[] blobId = new byte[ProtocolDefines.BLOB_ID_LEN];
				System.arraycopy(data, i, blobId, 0, ProtocolDefines.BLOB_ID_LEN);
				i += ProtocolDefines.BLOB_ID_LEN;
				groupimagemsg.setBlobId(blobId);
				groupimagemsg.setSize(EndianUtils.readSwappedInteger(data, i));
				i += 4;
				byte[] blobKey = new byte[ProtocolDefines.BLOB_KEY_LEN];
				System.arraycopy(data, i, blobKey, 0, ProtocolDefines.BLOB_KEY_LEN);
				groupimagemsg.setEncryptionKey(blobKey);
				msg = groupimagemsg;

				break;
			}

			case ProtocolDefines.MSGTYPE_GROUP_VIDEO: {
				if (realDataLength != (1 + ProtocolDefines.IDENTITY_LEN + ProtocolDefines.GROUP_ID_LEN + 2 + 2 * ProtocolDefines.BLOB_ID_LEN + 2 * 4 + ProtocolDefines.BLOB_KEY_LEN)) {
					throw new BadMessageException("Bad length (" + realDataLength + ") for group video message");
				}

				int i = 1;

				GroupVideoMessage groupvideomsg = new GroupVideoMessage();
				groupvideomsg.setGroupCreator(new String(data, i, ProtocolDefines.IDENTITY_LEN, StandardCharsets.US_ASCII));
				i += ProtocolDefines.IDENTITY_LEN;
				groupvideomsg.setGroupId(new GroupId(data, i));
				i += ProtocolDefines.GROUP_ID_LEN;
				groupvideomsg.setDuration(EndianUtils.readSwappedShort(data, i));
				i += 2;
				byte[] videoBlobId = new byte[ProtocolDefines.BLOB_ID_LEN];
				System.arraycopy(data, i, videoBlobId, 0, ProtocolDefines.BLOB_ID_LEN);
				i += ProtocolDefines.BLOB_ID_LEN;
				groupvideomsg.setVideoBlobId(videoBlobId);
				groupvideomsg.setVideoSize(EndianUtils.readSwappedInteger(data, i));
				i += 4;
				byte[] thumbnailBlobId = new byte[ProtocolDefines.BLOB_ID_LEN];
				System.arraycopy(data, i, thumbnailBlobId, 0, ProtocolDefines.BLOB_ID_LEN);
				i += ProtocolDefines.BLOB_ID_LEN;
				groupvideomsg.setThumbnailBlobId(thumbnailBlobId);
				groupvideomsg.setThumbnailSize(EndianUtils.readSwappedInteger(data, i));
				i += 4;
				byte[] blobKey = new byte[ProtocolDefines.BLOB_KEY_LEN];
				System.arraycopy(data, i, blobKey, 0, ProtocolDefines.BLOB_KEY_LEN);
				groupvideomsg.setEncryptionKey(blobKey);
				msg = groupvideomsg;

				break;
			}

			case ProtocolDefines.MSGTYPE_GROUP_LOCATION: {
				if (realDataLength < (1 + ProtocolDefines.IDENTITY_LEN + ProtocolDefines.GROUP_ID_LEN + 3)) {
					throw new BadMessageException("Bad length (" + realDataLength + ") for group location message");
				}

				String locStr = new String(data, 1 + ProtocolDefines.IDENTITY_LEN + ProtocolDefines.GROUP_ID_LEN,
					realDataLength - ProtocolDefines.IDENTITY_LEN - ProtocolDefines.GROUP_ID_LEN - 1, UTF_8);
				String[] lines = locStr.split("\n");
				String[] locArr = lines[0].split(",");

				MessageCoder.logger.info("Raw location message: {}", locStr);

				if (locArr.length < 2 || locArr.length > 3) {
					throw new BadMessageException("Bad coordinate format in group location message");
				}

				GroupLocationMessage grouplocationmsg = new GroupLocationMessage();
				grouplocationmsg.setGroupCreator(new String(data, 1, ProtocolDefines.IDENTITY_LEN, StandardCharsets.US_ASCII));
				grouplocationmsg.setGroupId(new GroupId(data, 1 + ProtocolDefines.IDENTITY_LEN));
				grouplocationmsg.setLatitude(Double.parseDouble(locArr[0]));
				grouplocationmsg.setLongitude(Double.parseDouble(locArr[1]));

				if (locArr.length == 3)
					grouplocationmsg.setAccuracy(Double.parseDouble(locArr[2]));

				if (lines.length >= 2) {
					grouplocationmsg.setPoiName(lines[1]);
					if (lines.length >= 3)
						grouplocationmsg.setPoiAddress(lines[2].replace("\\n", "\n"));
				}

				if (grouplocationmsg.getLatitude() < -90.0 || grouplocationmsg.getLatitude() > 90.0 ||
					grouplocationmsg.getLongitude() < -180.0 || grouplocationmsg.getLongitude() > 180.0) {
					throw new BadMessageException("Invalid coordinate values in group location message");
				}

				msg = grouplocationmsg;

				break;
			}

			case ProtocolDefines.MSGTYPE_GROUP_AUDIO: {
				if (realDataLength != (1 + ProtocolDefines.IDENTITY_LEN + ProtocolDefines.GROUP_ID_LEN + 2 + ProtocolDefines.BLOB_ID_LEN + 4 + ProtocolDefines.BLOB_KEY_LEN)) {
					throw new BadMessageException("Bad length (" + realDataLength + ") for group audio message");
				}

				int i = 1;

				GroupAudioMessage groupaudiomsg = new GroupAudioMessage();
				groupaudiomsg.setGroupCreator(new String(data, i, ProtocolDefines.IDENTITY_LEN, StandardCharsets.US_ASCII));
				i += ProtocolDefines.IDENTITY_LEN;
				groupaudiomsg.setGroupId(new GroupId(data, i));
				i += ProtocolDefines.GROUP_ID_LEN;
				groupaudiomsg.setDuration(EndianUtils.readSwappedShort(data, i));
				i += 2;
				byte[] audioBlobId = new byte[ProtocolDefines.BLOB_ID_LEN];
				System.arraycopy(data, i, audioBlobId, 0, ProtocolDefines.BLOB_ID_LEN);
				i += ProtocolDefines.BLOB_ID_LEN;
				groupaudiomsg.setAudioBlobId(audioBlobId);
				groupaudiomsg.setAudioSize(EndianUtils.readSwappedInteger(data, i));
				i += 4;
				byte[] blobKey = new byte[ProtocolDefines.BLOB_KEY_LEN];
				System.arraycopy(data, i, blobKey, 0, ProtocolDefines.BLOB_KEY_LEN);
				groupaudiomsg.setEncryptionKey(blobKey);
				msg = groupaudiomsg;

				break;
			}

			case ProtocolDefines.MSGTYPE_BALLOT_CREATE: {
				BallotCreateMessage groupBallotCreateMessage = new BallotCreateMessage();
				int pos = 1;
				groupBallotCreateMessage.setBallotCreator(boxmsg.getFromIdentity());
				groupBallotCreateMessage.setBallotId(new BallotId(data, pos));
				pos += ProtocolDefines.BALLOT_ID_LEN;
				groupBallotCreateMessage.setData(BallotData.parse(new String(data, pos, realDataLength - pos, UTF_8)));
				msg = groupBallotCreateMessage;
				break;
			}

			case ProtocolDefines.MSGTYPE_FILE: {
				FileMessage fileMessage = new FileMessage();
				int pos = 1;
				fileMessage.setData(FileData.parse(new String(data, pos, realDataLength - pos, UTF_8)));
				msg = fileMessage;
				break;
			}

			case ProtocolDefines.MSGTYPE_BALLOT_VOTE: {
				BallotVoteMessage groupBallotVoteMessage = new BallotVoteMessage();
				int pos = 1;

				groupBallotVoteMessage.setBallotCreator(new String(data, pos, ProtocolDefines.IDENTITY_LEN, StandardCharsets.US_ASCII));
				pos += ProtocolDefines.IDENTITY_LEN;

				groupBallotVoteMessage.setBallotId(new BallotId(data, pos));
				pos += ProtocolDefines.BALLOT_ID_LEN;

				groupBallotVoteMessage.parseVotes(new String(data, pos, realDataLength - pos, UTF_8));
				msg = groupBallotVoteMessage;
				break;
			}

			case ProtocolDefines.MSGTYPE_GROUP_BALLOT_CREATE: {
				GroupBallotCreateMessage groupBallotCreateMessage = new GroupBallotCreateMessage();
				int pos = 1;
				groupBallotCreateMessage.setGroupCreator(new String(data, 1, ProtocolDefines.IDENTITY_LEN, StandardCharsets.US_ASCII));
				pos += ProtocolDefines.IDENTITY_LEN;

				groupBallotCreateMessage.setGroupId(new GroupId(data, pos));
				pos += ProtocolDefines.GROUP_ID_LEN;

				groupBallotCreateMessage.setBallotCreator(boxmsg.getFromIdentity());
				groupBallotCreateMessage.setBallotId(new BallotId(data, pos));
				pos += ProtocolDefines.BALLOT_ID_LEN;

				groupBallotCreateMessage.setData(BallotData.parse(new String(data, pos, realDataLength - pos, UTF_8)));
				msg = groupBallotCreateMessage;
				break;
			}

			case ProtocolDefines.MSGTYPE_GROUP_FILE: {
				GroupFileMessage groupFileMessage = new GroupFileMessage();
				int pos = 1;
				groupFileMessage.setGroupCreator(new String(data, 1, ProtocolDefines.IDENTITY_LEN, StandardCharsets.US_ASCII));
				pos += ProtocolDefines.IDENTITY_LEN;

				groupFileMessage.setGroupId(new GroupId(data, pos));
				pos += ProtocolDefines.GROUP_ID_LEN;

				final String jsonObjectString = new String(data, pos, realDataLength - pos, UTF_8);
				groupFileMessage.setData(FileData.parse(jsonObjectString));
				msg = groupFileMessage;
				break;
			}

			case ProtocolDefines.MSGTYPE_GROUP_BALLOT_VOTE: {
				GroupBallotVoteMessage groupBallotVoteMessage = new GroupBallotVoteMessage();
				int pos = 1;
				groupBallotVoteMessage.setGroupCreator(new String(data, 1, ProtocolDefines.IDENTITY_LEN, StandardCharsets.US_ASCII));
				pos += ProtocolDefines.IDENTITY_LEN;

				groupBallotVoteMessage.setGroupId(new GroupId(data, pos));
				pos += ProtocolDefines.GROUP_ID_LEN;

				groupBallotVoteMessage.setBallotCreator(new String(data, pos, ProtocolDefines.IDENTITY_LEN, StandardCharsets.US_ASCII));
				pos += ProtocolDefines.IDENTITY_LEN;

				groupBallotVoteMessage.setBallotId(new BallotId(data, pos));
				pos += ProtocolDefines.BALLOT_ID_LEN;

				groupBallotVoteMessage.parseVotes(new String(data, pos, realDataLength - pos, UTF_8));
				msg = groupBallotVoteMessage;
				break;
			}

			case ProtocolDefines.MSGTYPE_GROUP_JOIN_REQUEST: {
				final byte[] protobufPayload = Arrays.copyOfRange(data, 1, realDataLength);
				final GroupJoinRequestData groupJoinRequestData = GroupJoinRequestData.fromProtobuf(protobufPayload);
				msg = new GroupJoinRequestMessage(groupJoinRequestData);
				break;
			}

			case ProtocolDefines.MSGTYPE_GROUP_JOIN_RESPONSE: {
				final byte[] protobufPayload = Arrays.copyOfRange(data, 1, realDataLength);
				final GroupJoinResponseData groupJoinResponseData = GroupJoinResponseData.fromProtobuf(protobufPayload);
				msg = new GroupJoinResponseMessage(groupJoinResponseData);
				break;
			}

			case ProtocolDefines.MSGTYPE_DELIVERY_RECEIPT: {
				addContact = false;
				if (realDataLength < ProtocolDefines.MESSAGE_ID_LEN + 2 || ((realDataLength - 2) % ProtocolDefines.MESSAGE_ID_LEN) != 0) {
					throw new BadMessageException("Bad length (" + realDataLength + ") for delivery receipt");
				}

				DeliveryReceiptMessage receiptmsg = new DeliveryReceiptMessage();
				receiptmsg.setReceiptType(data[1] & 0xFF);

				int numMsgIds = ((realDataLength - 2) / ProtocolDefines.MESSAGE_ID_LEN);
				MessageId[] receiptMessageIds = new MessageId[numMsgIds];
				for (int i = 0; i < numMsgIds; i++) {
					receiptMessageIds[i] = new MessageId(data, 2 + i * ProtocolDefines.MESSAGE_ID_LEN);
				}

				receiptmsg.setReceiptMessageIds(receiptMessageIds);
				msg = receiptmsg;

				break;
			}

			case ProtocolDefines.MSGTYPE_TYPING_INDICATOR: {
				addHidden = true;
				if (realDataLength != 2) {
					throw new BadMessageException("Bad length (" + realDataLength + ") for typing indicator");
				}

				TypingIndicatorMessage typingmsg = new TypingIndicatorMessage();
				typingmsg.setTyping((data[1] & 0xFF) > 0);
				msg = typingmsg;
				break;
			}

			case ProtocolDefines.MSGTYPE_CONTACT_SET_PHOTO: {
				if (realDataLength != (1 + ProtocolDefines.BLOB_ID_LEN + 4 + ProtocolDefines.BLOB_KEY_LEN)) {
					throw new BadMessageException("Bad length (" + realDataLength + ") for contact set photo message");
				}

				ContactSetPhotoMessage contactSetPhotoMessage = new ContactSetPhotoMessage();
				contactSetPhotoMessage.setFromIdentity(boxmsg.getFromIdentity());

				int i = 1;
				byte[] blobId = new byte[ProtocolDefines.BLOB_ID_LEN];
				System.arraycopy(data, i, blobId, 0, ProtocolDefines.BLOB_ID_LEN);
				i += ProtocolDefines.BLOB_ID_LEN;
				contactSetPhotoMessage.setBlobId(blobId);
				contactSetPhotoMessage.setSize(EndianUtils.readSwappedInteger(data, i));
				i += 4;
				byte[] blobKey = new byte[ProtocolDefines.BLOB_KEY_LEN];
				System.arraycopy(data, i, blobKey, 0, ProtocolDefines.BLOB_KEY_LEN);
				contactSetPhotoMessage.setEncryptionKey(blobKey);
				msg = contactSetPhotoMessage;

				break;
			}

			case ProtocolDefines.MSGTYPE_CONTACT_DELETE_PHOTO: {
				if (realDataLength != 1) {
					throw new BadMessageException("Bad length (" + realDataLength + ") for contact delete photo message");
				}
				msg = new ContactDeletePhotoMessage();

				break;
			}

			case ProtocolDefines.MSGTYPE_CONTACT_REQUEST_PHOTO: {
				if (realDataLength != 1) {
					throw new BadMessageException("Bad length (" + realDataLength + ") for contact request photo message");
				}
				msg = new ContactRequestPhotoMessage();

				break;
			}

			case ProtocolDefines.MSGTYPE_VOIP_CALL_OFFER: {
				final VoipCallOfferData offerData = VoipCallOfferData.parse(
					new String(data, 1, realDataLength - 1, UTF_8)
				);
				msg = new VoipCallOfferMessage().setData(offerData);
				break;
			}

			case ProtocolDefines.MSGTYPE_VOIP_CALL_ANSWER: {
				final VoipCallAnswerData answerData = VoipCallAnswerData.parse(
					new String(data, 1, realDataLength - 1, UTF_8)
				);
				msg = new VoipCallAnswerMessage().setData(answerData);
				break;
			}

			case ProtocolDefines.MSGTYPE_VOIP_ICE_CANDIDATES: {
				final VoipICECandidatesData candidatesData = VoipICECandidatesData.parse(
					new String(data, 1, realDataLength - 1, UTF_8)
				);
				msg = new VoipICECandidatesMessage().setData(candidatesData);
				break;
			}

			case ProtocolDefines.MSGTYPE_VOIP_CALL_HANGUP: {
				final VoipCallHangupData hangupData = VoipCallHangupData.parse(
					new String(data, 1, realDataLength - 1, UTF_8)
				);
				msg = new VoipCallHangupMessage().setData(hangupData);
				break;
			}

			case ProtocolDefines.MSGTYPE_VOIP_CALL_RINGING: {
				final VoipCallRingingData ringingData = VoipCallRingingData.parse(
					new String(data, 1, realDataLength - 1, UTF_8)
				);
				msg = new VoipCallRingingMessage().setData(ringingData);
				break;
			}

			default:
				MessageCoder.logger.info("Unsupported message type {}", type);
				break;
		}

		if (addContact) {
			contactStore.addContact(fetchedContact, addHidden);
		}

		if (msg != null) {
			/* copy header attributes from boxed message */
			msg.setFromIdentity(boxmsg.getFromIdentity());
			msg.setToIdentity(boxmsg.getToIdentity());
			msg.setMessageId(boxmsg.getMessageId());
			msg.setDate(boxmsg.getDate());
			msg.setMessageFlags(boxmsg.getFlags());
			msg.setPushFromName(boxmsg.getPushFromName());

			// Decrypt metadata, if present
			if (boxmsg.getMetadataBox() != null) {
				MetadataCoder coder = new MetadataCoder(identityStore);
				try {
					MessageMetadata metadata = coder.decode(boxmsg.getNonce(), boxmsg.getMetadataBox(), fetchedContact.getPublicKey());

					// Ensure message ID matches envelope message ID (so the server cannot swap it and
					// cause messages to be misquoted or delivery receipts to be swapped)
					if (metadata.getMessageId() != 0) {
						MessageId metadataMessageId = new MessageId(metadata.getMessageId());
						if (!metadataMessageId.equals(boxmsg.getMessageId())) {
							throw new BadMessageException("Metadata message ID does not match envelope message ID");
						}
					}

					// Take date from encrypted metadata
					if (metadata.getCreatedAt() != 0) {
						msg.setDate(new Date(metadata.getCreatedAt()));
					}

					// Take nickname from encrypted metadata
					msg.setPushFromName(metadata.getNickname());
				} catch (InvalidProtocolBufferException | ThreemaException e) {
					throw new BadMessageException("Metadata decode failed", e);
				}
			}
		}

		return msg;
	}

	/**
	 * Encrypt this message using the given contact and identity store and return the boxed result.
	 *
	 * @return boxed message
	 * @throws ThreemaException
	 */
	public @NonNull
	MessageBox encode(@NonNull AbstractMessage message, @NonNull NonceFactory nonceFactory) throws ThreemaException {
		try {
			/* prepare data for box */
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			bos.write(message.getType());
			bos.write(message.getBody());

			/* PKCS7 padding */
			SecureRandom rnd = new SecureRandom();
			int padbytes = rnd.nextInt(254) + 1;
			if ((bos.size() + padbytes) < ProtocolDefines.MIN_MESSAGE_PADDED_LEN) {
				padbytes = ProtocolDefines.MIN_MESSAGE_PADDED_LEN - bos.size();
			}
			MessageCoder.logger.debug("Adding {} padding bytes", padbytes);

			byte[] paddata = new byte[padbytes];
			for (int i = 0; i < padbytes; i++)
				paddata[i] = (byte) padbytes;

			bos.write(paddata);
			byte[] boxData = bos.toByteArray();

			/* obtain receiver's public key */
			Contact receiver = contactStore.getContactForIdentity(message.getToIdentity(), false, true);
			byte[] receiverPublicKey = receiver != null ? receiver.getPublicKey() : null;

			if (receiverPublicKey == null) {
				throw new ThreemaException("Missing public key for ID " + message.getToIdentity());
			}

			/* make random nonce; only save if the message is not a non-queued message */
			byte[] nonce = nonceFactory.next(!message.flagNoServerQueuing());

			/* sign/encrypt with our private key */
			byte[] boxedData = identityStore.encryptData(boxData, nonce, receiverPublicKey);
			if (boxedData == null) {
				throw new ThreemaException("Data encryption failed");
			}

			/* Encrypt metadata */
			MessageMetadata.Builder metadataBuilder = MessageMetadata.newBuilder()
				.setMessageId(message.getMessageId().getMessageIdLong())
				.setCreatedAt(message.getDate().getTime());

			String nickname = message.getPushFromName() == null ? identityStore.getPublicNickname() : message.getPushFromName();

			/* Only include if a nickname is present and the current message type allows sending profile information */
			if (nickname != null && nickname.length() > 0 && message.allowSendingProfile()) {
				byte[] padding = new byte[Math.max(0, 16 - nickname.getBytes().length)];
				metadataBuilder.setPadding(ByteString.copyFrom(padding))
					.setNickname(nickname);
			}

			MetadataBox metadataBox = new MetadataCoder(identityStore).encode(metadataBuilder.build(), nonce, receiverPublicKey);

			/* make BoxedMessage */
			MessageBox boxmsg = new MessageBox();
			boxmsg.setFromIdentity(message.getFromIdentity());
			boxmsg.setToIdentity(message.getToIdentity());
			boxmsg.setMessageId(message.getMessageId());
			boxmsg.setDate(message.getDate());

			int flags = 0;
			if (message.flagSendPush())
				flags |= ProtocolDefines.MESSAGE_FLAG_SEND_PUSH;
			if (message.flagNoServerQueuing())
				flags |= ProtocolDefines.MESSAGE_FLAG_NO_SERVER_QUEUING;
			if (message.flagNoServerAck())
				flags |= ProtocolDefines.MESSAGE_FLAG_NO_SERVER_ACK;
			if (message.flagGroupMessage())
				flags |= ProtocolDefines.MESSAGE_FLAG_GROUP;
			if (message.flagShortLivedServerQueuing())
				flags |= ProtocolDefines.MESSAGE_FLAG_SHORT_LIVED;
			boxmsg.setFlags(flags);

			if (message.allowSendingProfile()) {
				/* Include in header as well for the time being until most clients support the metadata box */
				boxmsg.setPushFromName(nickname);
			}
			boxmsg.setMetadataBox(metadataBox);
			boxmsg.setNonce(nonce);
			boxmsg.setBox(boxedData);

			return boxmsg;
		} catch (IOException e) {
			/* should never happen as we only write to a byte array */
			logger.error(e.getMessage(), e);
			throw new ThreemaException("Failed to create MessageBox");
		}
	}
}

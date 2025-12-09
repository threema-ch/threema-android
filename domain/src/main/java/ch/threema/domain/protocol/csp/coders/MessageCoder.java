/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2021-2025 Threema GmbH
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

import androidx.annotation.NonNull;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import ch.threema.base.ThreemaException;
import static ch.threema.base.utils.LoggingKt.getThreemaLogger;
import ch.threema.domain.models.Contact;
import ch.threema.domain.models.GroupId;
import ch.threema.domain.models.MessageId;
import ch.threema.domain.protocol.csp.ProtocolDefines;
import ch.threema.domain.protocol.csp.messages.AbstractGroupMessage;
import ch.threema.domain.protocol.csp.messages.AbstractMessage;
import ch.threema.domain.protocol.csp.messages.BadMessageException;
import ch.threema.domain.protocol.csp.messages.AudioMessage;
import ch.threema.domain.protocol.csp.messages.DeleteMessage;
import ch.threema.domain.protocol.csp.messages.EmptyMessage;
import ch.threema.domain.protocol.csp.messages.EditMessage;
import ch.threema.domain.protocol.csp.messages.GroupDeleteMessage;
import ch.threema.domain.protocol.csp.messages.GroupEditMessage;
import ch.threema.domain.protocol.csp.messages.GroupReactionMessage;
import ch.threema.domain.protocol.csp.messages.ImageMessage;
import ch.threema.domain.protocol.csp.messages.location.LocationMessage;
import ch.threema.domain.protocol.csp.messages.ReactionMessage;
import ch.threema.domain.protocol.csp.messages.TextMessage;
import ch.threema.domain.protocol.csp.messages.VideoMessage;
import ch.threema.domain.protocol.csp.messages.DeleteProfilePictureMessage;
import ch.threema.domain.protocol.csp.messages.ContactRequestProfilePictureMessage;
import ch.threema.domain.protocol.csp.messages.SetProfilePictureMessage;
import ch.threema.domain.protocol.csp.messages.DeliveryReceiptMessage;
import ch.threema.domain.protocol.csp.messages.GroupAudioMessage;
import ch.threema.domain.protocol.csp.messages.GroupSetupMessage;
import ch.threema.domain.protocol.csp.messages.GroupDeleteProfilePictureMessage;
import ch.threema.domain.protocol.csp.messages.GroupDeliveryReceiptMessage;
import ch.threema.domain.protocol.csp.messages.GroupImageMessage;
import ch.threema.domain.protocol.csp.messages.GroupLeaveMessage;
import ch.threema.domain.protocol.csp.messages.location.GroupLocationMessage;
import ch.threema.domain.protocol.csp.messages.GroupNameMessage;
import ch.threema.domain.protocol.csp.messages.GroupSyncRequestMessage;
import ch.threema.domain.protocol.csp.messages.GroupSetProfilePictureMessage;
import ch.threema.domain.protocol.csp.messages.GroupTextMessage;
import ch.threema.domain.protocol.csp.messages.GroupVideoMessage;
import ch.threema.domain.protocol.csp.messages.MissingPublicKeyException;
import ch.threema.domain.protocol.csp.messages.TypingIndicatorMessage;
import ch.threema.domain.protocol.csp.messages.WebSessionResumeMessage;
import ch.threema.domain.protocol.csp.messages.ballot.PollSetupMessage;
import ch.threema.domain.protocol.csp.messages.ballot.PollVoteMessage;
import ch.threema.domain.protocol.csp.messages.ballot.GroupPollSetupMessage;
import ch.threema.domain.protocol.csp.messages.ballot.GroupPollVoteMessage;
import ch.threema.domain.protocol.csp.messages.file.FileMessage;
import ch.threema.domain.protocol.csp.messages.file.GroupFileMessage;
import ch.threema.domain.protocol.csp.messages.fs.ForwardSecurityData;
import ch.threema.domain.protocol.csp.messages.fs.ForwardSecurityEnvelopeMessage;
import ch.threema.domain.protocol.csp.messages.groupcall.GroupCallStartMessage;
import ch.threema.domain.protocol.csp.messages.voip.VoipCallAnswerMessage;
import ch.threema.domain.protocol.csp.messages.voip.VoipCallHangupMessage;
import ch.threema.domain.protocol.csp.messages.voip.VoipCallOfferMessage;
import ch.threema.domain.protocol.csp.messages.voip.VoipCallRingingMessage;
import ch.threema.domain.protocol.csp.messages.voip.VoipICECandidatesMessage;
import ch.threema.domain.stores.ContactStore;
import ch.threema.domain.stores.IdentityStore;
import ch.threema.protobuf.csp.e2e.MessageMetadata;
import ch.threema.protobuf.csp.e2e.fs.Version;

import static ch.threema.common.ByteArrayExtensionsKt.readLittleEndianInt;
import static ch.threema.common.ByteArrayExtensionsKt.readLittleEndianShort;
import static java.nio.charset.StandardCharsets.UTF_8;

public class MessageCoder {
    private static final Logger logger = getThreemaLogger("MessageCoder");

    private final @NonNull ContactStore contactStore;
    private final @NonNull IdentityStore identityStore;

    /**
     * @param contactStore  contact store to use for retrieving keys
     * @param identityStore identity store to use for encryption
     */
    public MessageCoder(@NonNull ContactStore contactStore, @NonNull IdentityStore identityStore) {
        this.contactStore = contactStore;
        this.identityStore = identityStore;
    }


    /**
     * Attempt to decrypt a boxed (encrypted) message using the given contact and identity store
     * and return the decrypted result. The result will be a subclass of AbstractMessage according
     * to the message type. Note that the contact is not added, if it does not exist.
     *
     * @param boxmsg boxed message to be decrypted
     * @return decrypted message, or null if the message type is not supported
     * @throws BadMessageException       if the message cannot be decrypted successfully
     * @throws MissingPublicKeyException if the sender's public key cannot be obtained
     */
    public @NonNull AbstractMessage decode(@NonNull MessageBox boxmsg) throws BadMessageException, MissingPublicKeyException {

        if (!boxmsg.getToIdentity().equals(identityStore.getIdentity())) {
            throw new BadMessageException("Message is not for own identity, cannot decode");
        }

        if (boxmsg.getFromIdentity().equals(identityStore.getIdentity())) {
            throw new BadMessageException("Message is from own identity, cannot decode");
        }

        /* obtain public key of sender */
        Contact contact = contactStore.getContactForIdentityIncludingCache(boxmsg.getFromIdentity());

        if (contact == null) {
            throw new MissingPublicKeyException("Missing public key for ID " + boxmsg.getFromIdentity());
        }

        /* decrypt with our secret key */
        byte[] data = identityStore.decryptData(boxmsg.getBox(), boxmsg.getNonce(), contact.getPublicKey());
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
        MessageCoder.logger.debug("Effective data length is {}", realDataLength);

        AbstractMessage msg = deserializeData(data, realDataLength, boxmsg.getFromIdentity(), boxmsg.getToIdentity());

        /* copy header attributes from boxed message */
        msg.setFromIdentity(boxmsg.getFromIdentity());
        msg.setToIdentity(boxmsg.getToIdentity());
        msg.setMessageId(boxmsg.getMessageId());
        msg.setDate(boxmsg.getDate());
        msg.setMessageFlags(boxmsg.getFlags());

        // Decrypt metadata, if present
        if (boxmsg.getMetadataBox() != null) {
            MetadataCoder coder = new MetadataCoder(identityStore);
            try {
                MessageMetadata metadata = coder.decode(boxmsg.getNonce(), boxmsg.getMetadataBox(), contact.getPublicKey());

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

                // Take nickname from encrypted metadata. Note that the nickname in the metadata box
                // should be used even if the message would not allow user profile distribution
                // Note that it must be explicitly checked that the nickname is present, otherwise
                // the default value would be the empty string (which would reset the nickname)
                if (metadata.hasNickname()) {
                    msg.setNickname(metadata.getNickname());
                }
            } catch (InvalidProtocolBufferException | ThreemaException e) {
                throw new BadMessageException("Metadata decode failed", e);
            }
        } else if (msg.allowUserProfileDistribution()) {
            // If there is no metadata box but the message allows user profile distribution, take
            // the nickname from the box message
            msg.setNickname(boxmsg.getPushFromName());
        }

        return msg;
    }

    /**
     * Decode a FS encapsulated message that has already been decrypted, and return it with the
     * same attributes as the outer (envelope) message.
     *
     * @param data  decrypted body
     * @param outer outer message
     * @throws BadMessageException if the message cannot be decoded or if the encapsulated message
     *                             is not allowed to be encapsulated
     */
    public @NonNull AbstractMessage decodeEncapsulated(
        @NonNull byte[] data,
        @NonNull AbstractMessage outer,
        @NonNull Version appliedVersion
    ) throws BadMessageException {
        AbstractMessage msg = deserializeData(data, data.length, outer.getFromIdentity(), outer.getToIdentity());

        // Filter messages not allowed by any FS version.
        if (msg instanceof ForwardSecurityEnvelopeMessage) {
            throw new BadMessageException("Unexpected FS envelope encapsulated by an FS message");
        }

        // Filter messages based on the applied version.
        switch (appliedVersion) {
            case V1_0:
            case V1_1:
                // Technically, typing-indicator and delivery-receipts are not allowed for V1.0 but
                // they don't do any harm, so we'll let this slide through for simplicity.
                //
                // Disallow encapsulation of group messages for V1.X
                if (msg instanceof AbstractGroupMessage) {
                    throw new BadMessageException("Unexpected group message encapsulated by an FS message");
                }
                break;
            case V1_2:
                // Every message type is ok with V1.2
                break;
            default:
                throw new BadMessageException("Unhandled FS version when decapsulating: " + appliedVersion);
        }

        /* copy header attributes from outer message */
        msg.setFromIdentity(outer.getFromIdentity());
        msg.setToIdentity(outer.getToIdentity());
        msg.setMessageId(outer.getMessageId());
        msg.setDate(outer.getDate());
        msg.setMessageFlags(outer.getMessageFlags());
        msg.setNickname(outer.getNickname());

        return msg;
    }

    /**
     * Encrypt this message using the given contact and identity store and return the boxed result.
     *
     * @return boxed message
     */
    public @NonNull
    MessageBox encode(@NonNull AbstractMessage message, @NonNull byte[] nonce) throws ThreemaException {
        try {
            /* prepare data for box */
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            bos.write(message.getType());
            byte[] body = message.getBody();
            if (body == null) {
                throw new ThreemaException("Message body is null");
            }
            bos.write(body);

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
            Contact receiver = contactStore.getContactForIdentityIncludingCache(message.getToIdentity());
            byte[] receiverPublicKey = receiver != null ? receiver.getPublicKey() : null;

            if (receiverPublicKey == null) {
                throw new ThreemaException("Missing public key for ID " + message.getToIdentity());
            }

            /* sign/encrypt with our private key */
            byte[] boxedData = identityStore.encryptData(boxData, nonce, receiverPublicKey);
            if (boxedData == null) {
                throw new ThreemaException("Data encryption failed");
            }

            /* Encrypt metadata */
            MessageMetadata.Builder metadataBuilder = MessageMetadata.newBuilder()
                .setMessageId(message.getMessageId().getMessageIdLong())
                .setCreatedAt(message.getDate().getTime());

            // Get the nickname from the identity store
            String nickname = identityStore.getPublicNickname();

            // Include the nickname if the message allows user profile distribution
            if (message.allowUserProfileDistribution()) {
                // Use padding to get a length of at least 16 bytes with nickname + padding
                byte[] padding = new byte[Math.max(0, 16 - nickname.getBytes().length)];
                metadataBuilder.setPadding(ByteString.copyFrom(padding));
                metadataBuilder.setNickname(nickname);
            } else {
                // Set 16 bytes padding to get a length of at least 16 bytes with nickname + padding
                metadataBuilder.setPadding(ByteString.copyFrom(new byte[16]));
                // Note that this call is required to clear the nickname. If the messages should not
                // distribute the user profile, the nickname must be cleared. Otherwise an empty
                // string is sent, which will delete the nickname on the receiver's device.
                metadataBuilder.clearNickname();
            }

            MetadataBox metadataBox = new MetadataCoder(identityStore).encode(metadataBuilder.build(), nonce, receiverPublicKey);

            /* make BoxedMessage */
            MessageBox boxmsg = new MessageBox();
            boxmsg.setFromIdentity(message.getFromIdentity());
            boxmsg.setToIdentity(message.getToIdentity());
            boxmsg.setMessageId(message.getMessageId());
            boxmsg.setDate(message.getDate());
            boxmsg.setFlags(message.getMessageFlags());

            if (message.allowUserProfileDistribution() && boxmsg.getToIdentity() != null && boxmsg.getToIdentity().startsWith("*")) {
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

    private @NonNull AbstractMessage deserializeData(byte[] data, int realDataLength, String fromIdentity, String toIdentity) throws BadMessageException {
        /* first byte of data is type */
        int type = data[0] & 0xFF;
        AbstractMessage message;

        switch (type) {
            case ProtocolDefines.MSGTYPE_TEXT: {
                message = TextMessage.fromByteArray(data, 1, realDataLength - 1);
                break;
            }

            case ProtocolDefines.MSGTYPE_IMAGE: {
                message = ImageMessage.fromByteArray(data, 1, realDataLength - 1);
                break;
            }

            case ProtocolDefines.MSGTYPE_VIDEO: {
                message = VideoMessage.fromByteArray(data, 1, realDataLength - 1);
                break;
            }

            case ProtocolDefines.MSGTYPE_AUDIO: {
                message = AudioMessage.fromByteArray(data, 1, realDataLength - 1);
                break;
            }

            case ProtocolDefines.MSGTYPE_LOCATION: {
                message = LocationMessage.fromByteArray(data, 1, realDataLength - 1);
                break;
            }

            case ProtocolDefines.MSGTYPE_GROUP_CREATE: {
                if (realDataLength < (1 + ProtocolDefines.GROUP_ID_LEN) ||
                    ((realDataLength - 1 - ProtocolDefines.GROUP_ID_LEN) % ProtocolDefines.IDENTITY_LEN) != 0) {
                    throw new BadMessageException("Bad length (" + realDataLength + ") for group create message");
                }

                GroupSetupMessage groupcreatemsg = new GroupSetupMessage();
                groupcreatemsg.setGroupCreator(fromIdentity);
                groupcreatemsg.setApiGroupId(new GroupId(data, 1));
                int numMembers = ((realDataLength - ProtocolDefines.GROUP_ID_LEN - 1) / ProtocolDefines.IDENTITY_LEN);
                String[] members = new String[numMembers];
                for (int i = 0; i < numMembers; i++) {
                    members[i] = new String(data, 1 + ProtocolDefines.GROUP_ID_LEN + i * ProtocolDefines.IDENTITY_LEN, ProtocolDefines.IDENTITY_LEN, StandardCharsets.US_ASCII);
                }
                groupcreatemsg.setMembers(members);
                message = groupcreatemsg;
                break;
            }

            case ProtocolDefines.MSGTYPE_GROUP_REQUEST_SYNC: {
                message = GroupSyncRequestMessage.fromByteArray(data, 1, realDataLength - 1, toIdentity);
                break;
            }

            case ProtocolDefines.MSGTYPE_GROUP_RENAME: {
                if (realDataLength < (1 + ProtocolDefines.GROUP_ID_LEN)) {
                    throw new BadMessageException("Bad length (" + realDataLength + ") for group rename message");
                }

                GroupNameMessage grouprenamemsg = new GroupNameMessage();
                grouprenamemsg.setGroupCreator(fromIdentity);
                grouprenamemsg.setApiGroupId(new GroupId(data, 1));
                grouprenamemsg.setGroupName(new String(data, 1 + ProtocolDefines.GROUP_ID_LEN, realDataLength - 1 - ProtocolDefines.GROUP_ID_LEN, UTF_8));
                message = grouprenamemsg;

                break;
            }

            case ProtocolDefines.MSGTYPE_GROUP_LEAVE: {
                if (realDataLength != (1 + ProtocolDefines.IDENTITY_LEN + ProtocolDefines.GROUP_ID_LEN)) {
                    throw new BadMessageException("Bad length (" + realDataLength + ") for group leave message");
                }

                GroupLeaveMessage groupleavemsg = new GroupLeaveMessage();
                groupleavemsg.setGroupCreator(new String(data, 1, ProtocolDefines.IDENTITY_LEN, StandardCharsets.US_ASCII));
                groupleavemsg.setApiGroupId(new GroupId(data, 1 + ProtocolDefines.IDENTITY_LEN));
                message = groupleavemsg;
                break;
            }

            case ProtocolDefines.MSGTYPE_GROUP_TEXT: {
                message = GroupTextMessage.fromByteArray(data, 1, realDataLength - 1);
                break;
            }

            case ProtocolDefines.MSGTYPE_GROUP_SET_PHOTO: {
                if (realDataLength != (1 + ProtocolDefines.GROUP_ID_LEN + ProtocolDefines.BLOB_ID_LEN + 4 + ProtocolDefines.BLOB_KEY_LEN)) {
                    throw new BadMessageException("Bad length (" + realDataLength + ") for group set photo message");
                }

                GroupSetProfilePictureMessage groupsetphotomsg = new GroupSetProfilePictureMessage();
                groupsetphotomsg.setGroupCreator(fromIdentity);

                int i = 1;
                groupsetphotomsg.setApiGroupId(new GroupId(data, i));
                i += ProtocolDefines.GROUP_ID_LEN;
                byte[] blobId = new byte[ProtocolDefines.BLOB_ID_LEN];
                System.arraycopy(data, i, blobId, 0, ProtocolDefines.BLOB_ID_LEN);
                i += ProtocolDefines.BLOB_ID_LEN;
                groupsetphotomsg.setBlobId(blobId);
                groupsetphotomsg.setSize(readLittleEndianInt(data, i));
                i += 4;
                byte[] blobKey = new byte[ProtocolDefines.BLOB_KEY_LEN];
                System.arraycopy(data, i, blobKey, 0, ProtocolDefines.BLOB_KEY_LEN);
                groupsetphotomsg.setEncryptionKey(blobKey);
                message = groupsetphotomsg;

                break;
            }

            case ProtocolDefines.MSGTYPE_GROUP_DELETE_PHOTO: {
                if (realDataLength != (1 + ProtocolDefines.GROUP_ID_LEN)) {
                    throw new BadMessageException("Bad length (" + realDataLength + ") for group delete photo message");
                }

                GroupDeleteProfilePictureMessage groupDeleteProfilePictureMessage = new GroupDeleteProfilePictureMessage();
                groupDeleteProfilePictureMessage.setGroupCreator(fromIdentity);
                groupDeleteProfilePictureMessage.setApiGroupId(new GroupId(data, 1));

                message = groupDeleteProfilePictureMessage;

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
                groupimagemsg.setApiGroupId(new GroupId(data, i));
                i += ProtocolDefines.GROUP_ID_LEN;
                byte[] blobId = new byte[ProtocolDefines.BLOB_ID_LEN];
                System.arraycopy(data, i, blobId, 0, ProtocolDefines.BLOB_ID_LEN);
                i += ProtocolDefines.BLOB_ID_LEN;
                groupimagemsg.setBlobId(blobId);
                groupimagemsg.setSize(readLittleEndianInt(data, i));
                i += 4;
                byte[] blobKey = new byte[ProtocolDefines.BLOB_KEY_LEN];
                System.arraycopy(data, i, blobKey, 0, ProtocolDefines.BLOB_KEY_LEN);
                groupimagemsg.setEncryptionKey(blobKey);
                message = groupimagemsg;

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
                groupvideomsg.setApiGroupId(new GroupId(data, i));
                i += ProtocolDefines.GROUP_ID_LEN;
                groupvideomsg.setDuration(readLittleEndianShort(data, i));
                i += 2;
                byte[] videoBlobId = new byte[ProtocolDefines.BLOB_ID_LEN];
                System.arraycopy(data, i, videoBlobId, 0, ProtocolDefines.BLOB_ID_LEN);
                i += ProtocolDefines.BLOB_ID_LEN;
                groupvideomsg.setVideoBlobId(videoBlobId);
                groupvideomsg.setVideoSize(readLittleEndianInt(data, i));
                i += 4;
                byte[] thumbnailBlobId = new byte[ProtocolDefines.BLOB_ID_LEN];
                System.arraycopy(data, i, thumbnailBlobId, 0, ProtocolDefines.BLOB_ID_LEN);
                i += ProtocolDefines.BLOB_ID_LEN;
                groupvideomsg.setThumbnailBlobId(thumbnailBlobId);
                groupvideomsg.setThumbnailSize(readLittleEndianInt(data, i));
                i += 4;
                byte[] blobKey = new byte[ProtocolDefines.BLOB_KEY_LEN];
                System.arraycopy(data, i, blobKey, 0, ProtocolDefines.BLOB_KEY_LEN);
                groupvideomsg.setEncryptionKey(blobKey);
                message = groupvideomsg;

                break;
            }

            case ProtocolDefines.MSGTYPE_GROUP_LOCATION: {
                message = GroupLocationMessage.fromByteArray(data, 1, realDataLength - 1);
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
                groupaudiomsg.setApiGroupId(new GroupId(data, i));
                i += ProtocolDefines.GROUP_ID_LEN;
                groupaudiomsg.setDuration(readLittleEndianShort(data, i));
                i += 2;
                byte[] audioBlobId = new byte[ProtocolDefines.BLOB_ID_LEN];
                System.arraycopy(data, i, audioBlobId, 0, ProtocolDefines.BLOB_ID_LEN);
                i += ProtocolDefines.BLOB_ID_LEN;
                groupaudiomsg.setAudioBlobId(audioBlobId);
                groupaudiomsg.setAudioSize(readLittleEndianInt(data, i));
                i += 4;
                byte[] blobKey = new byte[ProtocolDefines.BLOB_KEY_LEN];
                System.arraycopy(data, i, blobKey, 0, ProtocolDefines.BLOB_KEY_LEN);
                groupaudiomsg.setEncryptionKey(blobKey);
                message = groupaudiomsg;

                break;
            }

            case ProtocolDefines.MSGTYPE_BALLOT_CREATE: {
                message = PollSetupMessage.fromByteArray(data, 1, realDataLength - 1, fromIdentity);
                break;
            }

            case ProtocolDefines.MSGTYPE_FILE: {
                message = FileMessage.fromByteArray(data, 1, realDataLength - 1);
                break;
            }

            case ProtocolDefines.MSGTYPE_BALLOT_VOTE: {
                message = PollVoteMessage.fromByteArray(data, 1, realDataLength - 1);
                break;
            }

            case ProtocolDefines.MSGTYPE_GROUP_BALLOT_CREATE: {
                message = GroupPollSetupMessage.fromByteArray(data, 1, realDataLength - 1, fromIdentity);
                break;
            }

            case ProtocolDefines.MSGTYPE_GROUP_FILE: {
                message = GroupFileMessage.fromByteArray(data, 1, realDataLength - 1);
                break;
            }

            case ProtocolDefines.MSGTYPE_GROUP_BALLOT_VOTE: {
                message = GroupPollVoteMessage.fromByteArray(data, 1, realDataLength - 1);
                break;
            }

            case ProtocolDefines.MSGTYPE_GROUP_CALL_START: {
                message = GroupCallStartMessage.fromByteArray(data, 1, realDataLength - 1);
                break;
            }

            case ProtocolDefines.MSGTYPE_DELIVERY_RECEIPT: {
                message = DeliveryReceiptMessage.fromByteArray(data, 1, realDataLength - 1);
                break;
            }

            case ProtocolDefines.MSGTYPE_GROUP_DELIVERY_RECEIPT: {
                message = GroupDeliveryReceiptMessage.fromByteArray(data, 1, realDataLength - 1);
                break;
            }

            case ProtocolDefines.MSGTYPE_TYPING_INDICATOR: {
                message = TypingIndicatorMessage.fromByteArray(data, 1, realDataLength - 1);
                break;
            }

            case ProtocolDefines.MSGTYPE_CONTACT_SET_PHOTO: {
                message = SetProfilePictureMessage.fromByteArray(data, 1, realDataLength - 1);
                break;
            }

            case ProtocolDefines.MSGTYPE_CONTACT_DELETE_PHOTO: {
                message = DeleteProfilePictureMessage.fromByteArray(data, 1, realDataLength - 1);
                break;
            }

            case ProtocolDefines.MSGTYPE_CONTACT_REQUEST_PHOTO: {
                message = ContactRequestProfilePictureMessage.fromByteArray(data, 1, realDataLength - 1);
                break;
            }

            case ProtocolDefines.MSGTYPE_VOIP_CALL_OFFER: {
                message = VoipCallOfferMessage.fromByteArray(data, 1, realDataLength - 1);
                break;
            }

            case ProtocolDefines.MSGTYPE_VOIP_CALL_ANSWER: {
                message = VoipCallAnswerMessage.fromByteArray(data, 1, realDataLength - 1);
                break;
            }

            case ProtocolDefines.MSGTYPE_VOIP_ICE_CANDIDATES: {
                message = VoipICECandidatesMessage.fromByteArray(data, 1, realDataLength - 1);
                break;
            }

            case ProtocolDefines.MSGTYPE_VOIP_CALL_HANGUP: {
                message = VoipCallHangupMessage.fromByteArray(data, 1, realDataLength - 1);
                break;
            }

            case ProtocolDefines.MSGTYPE_VOIP_CALL_RINGING: {
                message = VoipCallRingingMessage.fromByteArray(data, 1, realDataLength - 1);
                break;
            }

            case ProtocolDefines.MSGTYPE_FS_ENVELOPE: {
                final byte[] protobufPayload = Arrays.copyOfRange(data, 1, realDataLength);
                final ForwardSecurityData forwardSecurityData = ForwardSecurityData.fromProtobuf(protobufPayload);
                message = new ForwardSecurityEnvelopeMessage(forwardSecurityData);
                break;
            }

            case ProtocolDefines.MSGTYPE_EMPTY: {
                message = new EmptyMessage();
                break;
            }

            case ProtocolDefines.MSGTYPE_WEB_SESSION_RESUME: {
                final Map<String, String> webSessionResumeData = new HashMap<>();
                try {
                    JSONObject object = new JSONObject(new String(data, 1, realDataLength - 1, UTF_8));
                    webSessionResumeData.put("wcs", object.getString("wcs"));
                    webSessionResumeData.put("wct", String.valueOf(object.getLong("wct")));
                    webSessionResumeData.put("wcv", String.valueOf(object.getInt("wcv")));
                    if (object.has("wca")) {
                        webSessionResumeData.put("wca", object.getString("wca"));
                    }
                } catch (JSONException e) {
                    throw new BadMessageException(e.getMessage());
                }
                message = new WebSessionResumeMessage(webSessionResumeData);
                break;
            }

            case ProtocolDefines.MSGTYPE_EDIT_MESSAGE: {
                message = EditMessage.fromByteArray(data, 1, realDataLength - 1);
                break;
            }

            case ProtocolDefines.MSGTYPE_DELETE_MESSAGE: {
                message = DeleteMessage.fromByteArray(data, 1, realDataLength - 1);
                break;
            }

            case ProtocolDefines.MSGTYPE_GROUP_EDIT_MESSAGE: {
                message = GroupEditMessage.fromByteArray(data, 1, realDataLength - 1);
                break;
            }

            case ProtocolDefines.MSGTYPE_GROUP_DELETE_MESSAGE: {
                message = GroupDeleteMessage.fromByteArray(data, 1, realDataLength - 1);
                break;
            }

            case ProtocolDefines.MSGTYPE_REACTION: {
                message = ReactionMessage.fromByteArray(data, 1, realDataLength - 1);
                break;
            }

            case ProtocolDefines.MSGTYPE_GROUP_REACTION: {
                message = GroupReactionMessage.fromByteArray(data, 1, realDataLength - 1);
                break;
            }

            default:
                throw new BadMessageException("Unsupported message type " + type);
        }

        return message;
    }
}

/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2013-2022 Threema GmbH
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

import com.neilalexander.jnacl.NaCl;
import org.apache.commons.io.EndianUtils;
import org.slf4j.Logger;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import ch.threema.base.utils.LoggingUtil;
import ch.threema.base.utils.Utils;
import ch.threema.domain.protocol.csp.connection.Payload;
import ch.threema.domain.protocol.csp.ProtocolDefines;
import ch.threema.domain.models.MessageId;

/**
 * A boxed (= encrypted) message, either received from the server or prepared for sending to the server.
 */
public class MessageBox implements Serializable {

	private static final Logger logger = LoggingUtil.getThreemaLogger("MessageBox");

	private String fromIdentity;
	private String toIdentity;
	private MessageId messageId;
	private Date date;
	private int flags;
	private String pushFromName;
	private MetadataBox metadataBox;
	private byte[] nonce;
	private byte[] box;

	/**
	 * Serialize this boxed message into a binary representation.
	 *
	 * @return binary representation of boxed message
	 */
	public byte[] makeBinary() {
		ByteArrayOutputStream bos = new ByteArrayOutputStream();

		try {
			bos.write(fromIdentity.getBytes());
			bos.write(toIdentity.getBytes());
			bos.write(messageId.getMessageId());
			EndianUtils.writeSwappedInteger(bos, (int) (date.getTime() / 1000));
			bos.write(flags);
			bos.write(0);	/* reserved */

			short metadataLen = 0;
			if (metadataBox != null) {
				metadataLen = (short) metadataBox.getBox().length;
			}
			EndianUtils.writeSwappedShort(bos, metadataLen);

			byte[] pushFromNameBytesT = new byte[ProtocolDefines.PUSH_FROM_LEN];
			byte[] pushFromNameBytes = Utils.truncateUTF8StringToByteArray(pushFromName, ProtocolDefines.PUSH_FROM_LEN);
			System.arraycopy(pushFromNameBytes, 0, pushFromNameBytesT, 0, pushFromNameBytes.length);
			bos.write(pushFromNameBytesT);

			if (metadataBox != null) {
				bos.write(metadataBox.getBox());
			}

			bos.write(nonce);
			bos.write(box);

			return bos.toByteArray();
		} catch (IOException e) {
			/* should never happen as we only write to a byte array */
			logger.error("TM015", e);   /* Error while making payload */
			return null;
		}
	}

	/**
	 * Serialize this boxed message into a payload that can be sent over the network.
	 *
	 * @return prepared payload
	 */
	public Payload makePayload() {
		return new Payload(ProtocolDefines.PLTYPE_OUTGOING_MESSAGE, makeBinary());
	}

	/**
	 * Attempt to parse the given binary message representation into a boxed message
	 * @param data binary representation of message
	 * @return parsed message
	 * @throws Exception if a parse error occurs
	 */
	@SuppressWarnings("ResultOfMethodCallIgnored")
	public static MessageBox parseBinary(byte[] data) throws Exception {
		ByteArrayInputStream bis = new ByteArrayInputStream(data);
		MessageBox message = new MessageBox();

		byte[] identity = new byte[ProtocolDefines.IDENTITY_LEN];

		bis.read(identity);
		message.setFromIdentity(new String(identity, StandardCharsets.UTF_8));
		bis.read(identity);
		message.setToIdentity(new String(identity, StandardCharsets.UTF_8));

		byte[] messageId = new byte[ProtocolDefines.MESSAGE_ID_LEN];
		bis.read(messageId);
		message.setMessageId(new MessageId(messageId));

		int dateTs = EndianUtils.readSwappedInteger(bis);
		message.setDate(new Date((long)dateTs*1000));

		message.setFlags(bis.read());

		/* reserved */
		bis.read();

		short metadataLen = EndianUtils.readSwappedShort(bis);

		byte[] pushFromNameB = new byte[ProtocolDefines.PUSH_FROM_LEN];
		bis.read(pushFromNameB);
		int pushFromNameLen;
		for (pushFromNameLen = 0; pushFromNameLen < pushFromNameB.length; pushFromNameLen++) {
			if (pushFromNameB[pushFromNameLen] == 0)
				break;
		}
		message.setPushFromName(new String(pushFromNameB, 0, pushFromNameLen, StandardCharsets.UTF_8));

		if (metadataLen > 0) {
			byte[] metadataBoxData = new byte[metadataLen];
			bis.read(metadataBoxData);
			message.setMetadataBox(new MetadataBox(metadataBoxData));
		}

		byte[] nonce = new byte[NaCl.NONCEBYTES];
		bis.read(nonce);
		message.setNonce(nonce);

		byte[] box = new byte[bis.available()];
		bis.read(box);
		message.setBox(box);

		return message;
	}

	/**
	 * Attempt to parse the given payload into a boxed message
	 * @param payload the payload to parse
	 * @return parsed message
	 * @throws Exception if a parse error occurs
	 */
	public static MessageBox parsePayload(Payload payload) throws Exception {
		return parseBinary(payload.getData());
	}

	public String getFromIdentity() {
		return fromIdentity;
	}
	public void setFromIdentity(String fromIdentity) {
		this.fromIdentity = fromIdentity;
	}
	public String getToIdentity() {
		return toIdentity;
	}
	public void setToIdentity(String toIdentity) {
		this.toIdentity = toIdentity;
	}
	public MessageId getMessageId() {
		return messageId;
	}
	public void setMessageId(MessageId messageId) {
		this.messageId = messageId;
	}
	public Date getDate() {
		return date;
	}
	public void setDate(Date date) {
		this.date = date;
	}
	public int getFlags() {
		return flags;
	}
	public void setFlags(int flags) {
		this.flags = flags;
	}
	public String getPushFromName() {
		return pushFromName;
	}
	public void setPushFromName(String pushFromName) {
		this.pushFromName = pushFromName;
	}
	public MetadataBox getMetadataBox() {
		return metadataBox;
	}
	public void setMetadataBox(MetadataBox metadataBox) {
		this.metadataBox = metadataBox;
	}
	public byte[] getNonce() {
		return nonce;
	}
	public void setNonce(byte[] nonce) {
		this.nonce = nonce;
	}
	public byte[] getBox() {
		return box;
	}
	public void setBox(byte[] box) {
		this.box = box;
	}
}

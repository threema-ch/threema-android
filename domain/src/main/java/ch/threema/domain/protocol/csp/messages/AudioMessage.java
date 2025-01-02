/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2013-2024 Threema GmbH
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

package ch.threema.domain.protocol.csp.messages;

import org.apache.commons.io.EndianUtils;
import org.slf4j.Logger;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import androidx.annotation.Nullable;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.domain.protocol.csp.ProtocolDefines;
import ch.threema.protobuf.csp.e2e.fs.Version;

/**
 * A message that has an audio recording (stored on the blob server) as its content.
 *
 * The contents are referenced by the {@code audioBlobId}, the {@code audioSize} in bytes, and the
 * {@code encryptionKey} to be used when decrypting the audio blob.
 *
 * @Deprecated Use FileMessage instead
 */
@Deprecated
public class AudioMessage extends AbstractMessage {

    private final static Logger logger = LoggingUtil.getThreemaLogger("AudioMessage");

	private int duration;
	private byte[] audioBlobId;
	private int audioSize;
	private byte[] encryptionKey;

	public AudioMessage() {
		super();
	}

	@Override
	public int getType() {
		return ProtocolDefines.MSGTYPE_AUDIO;
	}

	@Override
	public boolean flagSendPush() {
		return true;
	}

	@Override
	@Nullable
	public Version getMinimumRequiredForwardSecurityVersion() {
		return Version.V1_0;
	}

	@Override
	public boolean allowUserProfileDistribution() {
		return true;
	}

	@Override
	public boolean exemptFromBlocking() {
		return false;
	}

	@Override
	public boolean createImplicitlyDirectContact() {
		return true;
	}

	@Override
	public boolean protectAgainstReplay() {
		return true;
	}

	@Override
	public boolean reflectIncoming() {
		return true;
	}

	@Override
	public boolean reflectOutgoing() {
		return true;
	}

	@Override
	public boolean reflectSentUpdate() {
		return true;
	}

	@Override
	public boolean sendAutomaticDeliveryReceipt() {
		return true;
	}

	@Override
	public boolean bumpLastUpdate() {
		return true;
	}

	@Override
    @Nullable
	public byte[] getBody() {
		ByteArrayOutputStream bos = new ByteArrayOutputStream();

		try {
			EndianUtils.writeSwappedShort(bos, (short)duration);
			bos.write(audioBlobId);
			EndianUtils.writeSwappedInteger(bos, audioSize);
			bos.write(encryptionKey);
            return bos.toByteArray();
		} catch (IOException e) {
            logger.error("Cannot create body of message", e);
            return null;
		}
	}

	public int getDuration() {
		return duration;
	}

	public void setDuration(int duration) {
		this.duration = duration;
	}

	public byte[] getAudioBlobId() {
		return audioBlobId;
	}

	public void setAudioBlobId(byte[] audioBlobId) {
		this.audioBlobId = audioBlobId;
	}

	public int getAudioSize() {
		return audioSize;
	}

	public void setAudioSize(int audioSize) {
		this.audioSize = audioSize;
	}

	public byte[] getEncryptionKey() {
		return encryptionKey;
	}

	public void setEncryptionKey(byte[] encryptionKey) {
		this.encryptionKey = encryptionKey;
	}
}

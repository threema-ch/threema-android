/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2020-2022 Threema GmbH
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

package ch.threema.app.voip.signaling;

import java.nio.ByteBuffer;

import androidx.annotation.NonNull;
import ch.threema.protobuf.callsignaling.O2OCall;

public interface ToSignalingMessage {
	/**
	 * Return an ID uniquely identifying the message type.
	 *
	 * The easiest way to implement this is to return the field number in the protobuf Envelope.
	 */
	int getType();

	/**
	 * Convert the current type into a voip signaling envelope.
	 */
	@NonNull O2OCall.Envelope toSignalingMessage();

	/**
	 * Convert the current type into voip signaling message bytes
	 * (to be sent through the signaling channel).
	 */
	default @NonNull byte[] toSignalingMessageBytes() {
		final O2OCall.Envelope envelope = this.toSignalingMessage();
		return envelope.toByteArray();
	}

	/**
	 * Convert the current type into voip signaling message bytes
	 * (to be sent through the signaling channel).
	 */
	default @NonNull ByteBuffer toSignalingMessageByteBuffer() {
		return ByteBuffer.wrap(this.toSignalingMessageBytes());
	}
}

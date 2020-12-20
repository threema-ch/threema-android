/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2020 Threema GmbH
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

import com.google.protobuf.ByteString;

import androidx.annotation.NonNull;
import ch.threema.app.utils.RandomUtil;
import ch.threema.protobuf.callsignaling.CallSignaling;
import ch.threema.protobuf.callsignaling.CallSignaling.CaptureState.CaptureDevice;
import ch.threema.protobuf.callsignaling.CallSignaling.CaptureState.Mode;

/**
 * Hold information about the capturing state for a certain device.
 */
public class CaptureState implements ToSignalingMessage {
	private final boolean capturing;
	private final @NonNull CaptureDevice device;

	private CaptureState(boolean capturing, @NonNull CaptureDevice device) {
		this.capturing = capturing;
		this.device = device;
	}

	public static @NonNull CaptureState microphone(boolean capturing) {
		return new CaptureState(capturing, CaptureDevice.MICROPHONE);
	}

	public static @NonNull CaptureState camera(boolean capturing) {
		return new CaptureState(capturing, CaptureDevice.CAMERA);
	}

	//region Getters

	@Override
	public int getType() {
		return CallSignaling.Envelope.CAPTURE_STATE_CHANGE_FIELD_NUMBER;
	}

	//endregion

	//region Protocol buffers

	@Override
	public @NonNull CallSignaling.Envelope toSignalingMessage() {
		final CallSignaling.CaptureState.Builder captureState = CallSignaling.CaptureState.newBuilder()
			.setDevice(this.device)
			.setState(this.capturing ? Mode.ON : Mode.OFF);
		return CallSignaling.Envelope.newBuilder()
			.setPadding(ByteString.copyFrom(RandomUtil.generateRandomPadding(0, 255)))
			.setCaptureStateChange(captureState)
			.build();
	}

	//endregion
}

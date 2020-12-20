/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2017-2020 Threema GmbH
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

package ch.threema.app.webclient.services;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import ch.threema.base.ThreemaException;
import ch.threema.client.Utils;

@AnyThread
public interface QRCodeParser {
	@AnyThread
	class Result {
		public final int versionNumber;
		public final boolean isSelfHosted;
		public final boolean isPermanent;
		@NonNull public final byte[] key;
		@NonNull public final byte[] authToken;
		public final int saltyRtcPort;
		@NonNull public final String saltyRtcHost;
		@Nullable public final byte[] serverKey;

		Result(
			int versionNumber,
			boolean isSelfHosted,
			boolean isPermanent,
			@NonNull byte[] key,
			@NonNull byte[] authToken,
			@Nullable byte[] serverKey,
			int saltyRtcPort,
			@NonNull String saltyRtcHost
		) {
			this.versionNumber = versionNumber;
			this.isSelfHosted = isSelfHosted;
			this.isPermanent = isPermanent;
			this.key = key;
			this.authToken = authToken;
			this.serverKey = serverKey;
			this.saltyRtcPort = saltyRtcPort;
			this.saltyRtcHost = saltyRtcHost;
		}

		@Override
		public String toString() {
			return "version: " + this.versionNumber
					+ ", isSelfHosted: " + this.isSelfHosted
					+ ", isPermanent: " + this.isPermanent
					+ ", key: " + Utils.byteArrayToHexString(this.key)
					+ ", authToken: " + Utils.byteArrayToHexString(this.authToken)
					+ ", serverKey: " + Utils.byteArrayToHexString(this.serverKey)
					+ ", saltyRtcPort: " + this.saltyRtcPort
					+ ", saltyRtcHost: " + this.saltyRtcHost;
		}
	}

	class InvalidQrCodeException extends ThreemaException {
		public InvalidQrCodeException(String msg) {
			super(msg);
		}
	}

	@NonNull
	Result parse(byte[] payload) throws InvalidQrCodeException;
}

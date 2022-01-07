/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2017-2022 Threema GmbH
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

import org.saltyrtc.client.helpers.UnsignedHelper;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

@AnyThread
@SuppressWarnings("FieldCanBeLocal")
public class QRCodeParserImpl implements QRCodeParser {

	private static int PROTOCOL_VERSION_LENGTH = 2;
	private static int OPTIONS_LENGTH = 1;
	private static int KEY_LENGTH = 32;
	private static int AUTH_TOKEN_LENGTH = 32;
	private static int SALTY_RTC_PORT_LENGTH = 2;
	private static int SERVER_KEY_LENGTH = 32;

	@NonNull
	@Override
	public Result parse(@Nullable byte[] payload) throws InvalidQrCodeException {

		if(payload == null) {
			throw new InvalidQrCodeException("invalid payload string");
		}

		int fixedLength = PROTOCOL_VERSION_LENGTH
				+ OPTIONS_LENGTH
				+ KEY_LENGTH
				+ AUTH_TOKEN_LENGTH
				+ SERVER_KEY_LENGTH
				+ SALTY_RTC_PORT_LENGTH ;

		if(payload.length < fixedLength+1) {
			throw new InvalidQrCodeException("invalid payload length");
		}

		try {
			ByteArrayInputStream bis = new ByteArrayInputStream(payload);

			byte[] protocolVersionBytes = new byte[PROTOCOL_VERSION_LENGTH];
			bis.read(protocolVersionBytes);

			int protocolVersion = UnsignedHelper.readUnsignedShort(ByteBuffer.wrap(protocolVersionBytes).getShort());

			byte[] options = new byte[OPTIONS_LENGTH];
			bis.read(options);

			boolean isSelfHosted = (options[0] & 0x01) != 0;
			boolean isPermanent = (options[0] & 0x02) != 0;

			byte[] key = new byte[KEY_LENGTH];
			bis.read(key);

			byte[] authToken = new byte[AUTH_TOKEN_LENGTH];
			bis.read(authToken);

			// Note: Server public key may consist of only 0 bytes if not defined in Threema Web
			byte[] serverKey = null;
			byte[] serverKeyTemporary = new byte[SERVER_KEY_LENGTH];
			bis.read(serverKeyTemporary);
			for (byte b : serverKeyTemporary) {
				if (b != 0) {
					serverKey = serverKeyTemporary;
					break;
				}
			}

			byte[] portBytes = new byte[SALTY_RTC_PORT_LENGTH];
			bis.read(portBytes);
			int port = UnsignedHelper.readUnsignedShort(ByteBuffer.wrap(portBytes).getShort());

			String host = new String(payload, fixedLength, payload.length - fixedLength);

			return new Result(
					protocolVersion,
					isSelfHosted,
					isPermanent,
					key,
					authToken,
					serverKey,
					port,
					host);
		} catch (IOException x) {
			throw new InvalidQrCodeException("Invalid Threema Web QR code contents");
		}
	}
}

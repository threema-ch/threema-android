/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2016-2024 Threema GmbH
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

package ch.threema.app.webclient.converter;

import androidx.annotation.AnyThread;

import ch.threema.app.webclient.exceptions.ConversionException;

@AnyThread
public class VerificationLevel extends Converter {
	public final static int UNVERIFIED = 1;
	public final static int SERVER_VERIFIED = 2;
	public final static int FULLY_VERIFIED = 3;

	public static int convert(ch.threema.domain.models.VerificationLevel verificationLevel) throws ConversionException {
		try {
			switch (verificationLevel) {
				case UNVERIFIED:
					return VerificationLevel.UNVERIFIED;
				case SERVER_VERIFIED:
					return VerificationLevel.SERVER_VERIFIED;
				case FULLY_VERIFIED:
					return VerificationLevel.FULLY_VERIFIED;
				default:
					throw new ConversionException("Unknown verification level: " + verificationLevel);
			}
		} catch (NullPointerException e) {
			throw new ConversionException(e);
		}
	}
}

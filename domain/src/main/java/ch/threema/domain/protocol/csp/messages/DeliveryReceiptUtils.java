/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2022-2024 Threema GmbH
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

import ch.threema.domain.protocol.csp.ProtocolDefines;

public class DeliveryReceiptUtils {
	private DeliveryReceiptUtils() {}

	/**
	 * Return true if the specified delivery receipt type is an ACK or DEC.
	 */
	public static boolean isReaction(int deliveryReceiptType) {
		switch (deliveryReceiptType) {
			case ProtocolDefines.DELIVERYRECEIPT_MSGUSERACK:
			case ProtocolDefines.DELIVERYRECEIPT_MSGUSERDEC:
				return true;
			default:
				return false;
		}
	}
}

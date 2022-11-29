/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2022 Threema GmbH
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

package ch.threema.domain.protocol.csp.connection;

public interface RogueDeviceMonitor {
	/**
	 * Record a hash of the client and server ephemeral public keys that the application is
	 * about to use when logging in to the server. The monitor should record this hash and keep a
	 * list of the last about 20 hashes, so it can check whether any key hash that has been used
	 * in the meantime may have been from another (rogue) device.
	 * The reason for keeping more than just the last key hash is that a server connection may be
	 * severed while the login or login ack packet is still in-flight.
	 *
	 * @param ephemeralKeyHash key hash (32 bytes) to be recorded
	 * @param postLogin whether the login ack from the server has already been received
	 */
	void recordEphemeralKeyHash(byte[] ephemeralKeyHash, boolean postLogin);

	/**
	 * Check if an ephemeral key hash that has been last used to connect to the server
	 * (according to information received from the server) is on the list of hashes that the
	 * application has used itself. If the hash is not found on the list, then the user should
	 * be warned about a possible rogue device.
	 *
	 * @param ephemeralKeyHash key hash (32 bytes) to be checked
	 */
	void checkEphemeralKeyHash(byte[] ephemeralKeyHash);
}

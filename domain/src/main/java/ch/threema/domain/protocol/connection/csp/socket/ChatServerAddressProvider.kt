/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2023-2025 Threema GmbH
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

package ch.threema.domain.protocol.connection.csp.socket

import java.net.InetSocketAddress

interface ChatServerAddressProvider {
    /**
     * Move the internal pointer to the next available address.
     * If the last address is reached, the pointer will wrap around and start with the first address again.
     */
    fun advance()

    /**
     * Get the [InetSocketAddress] the internal pointer is currently pointing to.
     * If no addresses are available (e.g. when [update] has not been called prior to this call),
     * `null` is returned
     */
    fun get(): InetSocketAddress?

    /**
     * Update the available [InetSocketAddress]es
     */
    fun update()
}

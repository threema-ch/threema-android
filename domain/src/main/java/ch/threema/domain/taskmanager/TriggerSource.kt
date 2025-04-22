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

package ch.threema.domain.taskmanager

enum class TriggerSource {
    /**
     * An update triggered by synchronisation from another device.
     *
     * This should never trigger further messages to other devices.
     */
    SYNC,

    /**
     * An update triggered locally, e.g. by a user interaction.
     *
     * This will always trigger messages to other devices.
     */
    LOCAL,

    /**
     * An update triggered remotely, e.g. by an incoming message.
     *
     * The task that was triggered by the remote message will take care of reflection, but further
     * side effects (e.g. implicit contact creation) will need to be reflected separately.
     */
    REMOTE,
}

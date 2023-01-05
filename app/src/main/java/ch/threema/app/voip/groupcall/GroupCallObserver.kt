/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2022-2023 Threema GmbH
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

package ch.threema.app.voip.groupcall

import androidx.annotation.AnyThread
import ch.threema.storage.models.GroupModel

interface GroupCallObserver {
    /**
     * Called whenever the group call state for a specific group has changed.
     *
     * @param call The current state of this group's ongoing call or null if there is no ongoing call
     */
    @AnyThread
    fun onGroupCallUpdate(call: GroupCallDescription?)

    @AnyThread
    fun onGroupCallStart(groupModel: GroupModel, call: GroupCallDescription?)
}

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

package ch.threema.app.voip.groupcall

import androidx.annotation.AnyThread

interface GroupCallObserver {
    /**
     * Called when there is an update of a group call.
     * The cases when this is called might differ depending on the subscription used.
     *
     * If a subscription is made for a specific group it will be called whenever the state of this
     * group's call changes.
     *
     * If a subscription is made for joined calls this method will be called when a call is either joined
     * or left.
     *
     * @param call The description of the ongoing call or null if there is no ongoing call
     */
    @AnyThread
    fun onGroupCallUpdate(call: GroupCallDescription?)
}

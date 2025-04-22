/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2014-2025 Threema GmbH
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

package ch.threema.app.listeners;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import ch.threema.app.services.GroupService;
import ch.threema.data.models.GroupIdentity;
import ch.threema.storage.models.GroupModel;

public interface GroupListener {
    @AnyThread
    default void onCreate(@NonNull GroupIdentity groupIdentity) {
    }

    @AnyThread
    default void onRename(@NonNull GroupIdentity groupIdentity) {
    }

    @AnyThread
    default void onUpdatePhoto(@NonNull GroupIdentity groupIdentity) {
    }

    @AnyThread
    default void onRemove(long groupDbId) {
    }

    @AnyThread
    default void onNewMember(@NonNull GroupIdentity groupIdentity, String identityNew) {
    }

    @AnyThread
    default void onMemberLeave(@NonNull GroupIdentity groupIdentity, @NonNull String identityLeft) {
    }

    @AnyThread
    default void onMemberKicked(@NonNull GroupIdentity groupIdentity, String identityKicked) {
    }

    /**
     * Group was updated.
     */
    @AnyThread
    default void onUpdate(@NonNull GroupIdentity groupIdentity) {
    }

    /**
     * User left his own group.
     */
    @AnyThread
    default void onLeave(@NonNull GroupIdentity groupIdentity) {
    }

    /**
     * Group State has possibly changed
     * Note that oldState may be equal to newState
     */
    @AnyThread
    default void onGroupStateChanged(@NonNull GroupIdentity groupIdentity, @GroupService.GroupState int oldState, @GroupService.GroupState int newState) {
    }
}

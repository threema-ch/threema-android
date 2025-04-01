/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2018-2024 Threema GmbH
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

package ch.threema.storage.models;

import java.util.Date;

import androidx.annotation.Nullable;

/**
 * Base interface for ContactModel, GroupModel and DistributionListModel.
 */
public interface ReceiverModel {
    /**
     * Set the last conversation update timestamp.
     * <p>
     * If the value is set to `null`, then the conversation will disappear
     * from the conversation list.
     */
    ReceiverModel setLastUpdate(@Nullable Date lastUpdate);

    /**
     * Return the `lastUpdate` timestamp for this receiver.
     */
    @Nullable
    Date getLastUpdate();

    /**
     * Return whether or not the conversation with this receiver is archived.
     */
    boolean isArchived();

    /**
     * Return whether the conversation with this receiver should be hidden from the conversation
     * list.
     * <p>
     * Potential use cases for this flag are contacts with acquaintance level GROUP, or ad-hoc
     * distribution lists.
     */
    boolean isHidden();
}

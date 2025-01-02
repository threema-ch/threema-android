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

package ch.threema.domain.protocol.api.work;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class WorkContact {
    public final @Nullable String firstName;
    public final @Nullable String lastName;
    public final @NonNull byte[] publicKey;
    public final @NonNull String threemaId;
    public final @Nullable String jobTitle;
    public final @Nullable String department;

    public WorkContact(
        @NonNull String threemaId,
        @NonNull byte[] publicKey,
        @Nullable String firstName,
        @Nullable String lastName,
        @Nullable String jobTitle,
        @Nullable String department
    ) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.publicKey = publicKey;
        this.threemaId = threemaId;
        this.jobTitle = jobTitle;
        this.department = department;
    }
}

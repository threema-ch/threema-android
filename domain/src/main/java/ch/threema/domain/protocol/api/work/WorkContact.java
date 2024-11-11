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

import androidx.annotation.Nullable;

public class WorkContact {
    public final String firstName;
    public final String lastName;
    public final byte[] publicKey;
    public final String threemaId;
    public final @Nullable String jobTitle;
    public final @Nullable String department;

    public WorkContact(
        String threemaId,
        byte[] publicKey,
        String firstName,
        String lastName,
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

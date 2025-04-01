/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2020-2025 Threema GmbH
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

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class WorkDirectoryContact extends WorkContact {
    @Nullable
    public final String csi;
    public final List<String> categoryIds = new ArrayList<>();
    public final WorkOrganization organization = new WorkOrganization();

    public WorkDirectoryContact(
        @NonNull String threemaId,
        @NonNull byte[] publicKey,
        @Nullable String firstName,
        @Nullable String lastName,
        @Nullable String csi,
        @Nullable String jobTitle,
        @Nullable String department
    ) {
        super(threemaId, publicKey, firstName, lastName, jobTitle, department);
        this.csi = csi;
    }

    public String getInitial(boolean sortByFirstName) {
        String name;
        if (sortByFirstName) {
            name = (firstName != null ? firstName + " " : "") +
                (lastName != null ? lastName : "");

        } else {
            name = (lastName != null ? lastName + " " : "") +
                (firstName != null ? firstName : "");
        }

        if (!name.isEmpty()) {
            return name.substring(0, 1);
        }
        return " ";
    }
}

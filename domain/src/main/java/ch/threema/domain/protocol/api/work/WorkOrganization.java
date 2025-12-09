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

import org.json.JSONException;
import org.json.JSONObject;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class WorkOrganization {
    private String name;

    public WorkOrganization() {
    }

    public WorkOrganization(@NonNull JSONObject jsonObject) {
        this.name = jsonObject.optString("name");
    }

    @Nullable
    public String getName() {
        if (name != null && name.isEmpty()) {
            return null;
        }
        return name;
    }

    public void setName(@Nullable String name) {
        this.name = name;
    }

    public String toJSON() {
        JSONObject jsonObject = new JSONObject();
        try {
            var name = getName();
            if (name != null) {
                jsonObject.put("name", name);
            }
            return jsonObject.toString();
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }
}

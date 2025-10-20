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

package ch.threema.storage.models;

import androidx.annotation.Nullable;

import java.util.Date;
import java.util.Objects;

import ch.threema.data.datatypes.IdColor;
import ch.threema.base.utils.Utils;

public class DistributionListModel implements ReceiverModel {
    public static final int DISTRIBUTIONLIST_NAME_MAX_LENGTH_BYTES = 256;

    public static final String TABLE = "distribution_list";
    public static final String COLUMN_ID = "id";
    public static final String COLUMN_NAME = "name";
    public static final String COLUMN_CREATED_AT = "createdAt";
    public static final String COLUMN_LAST_UPDATE = "lastUpdate"; /* date when the conversation was last updated */
    public static final String COLUMN_IS_ARCHIVED = "isArchived"; /* whether this distribution list has been archived by user */
    public static final String COLUMN_IS_ADHOC_DISTRIBUTION_LIST = "isHidden"; /* whether this is an ad-hoc distribution list */

    private long id;
    private String name;
    private Date createdAt;
    private @Nullable Date lastUpdate;
    private boolean isArchived, isAdHocDistributionList;
    private IdColor idColor = IdColor.invalid();

    // dummy class
    public @Nullable String getName() {
        return this.name;
    }

    public DistributionListModel setName(@Nullable String name) {
        this.name = Utils.truncateUTF8String(name, DISTRIBUTIONLIST_NAME_MAX_LENGTH_BYTES);
        return this;
    }

    public long getId() {
        return this.id;
    }

    public DistributionListModel setId(long id) {
        this.id = id;
        // Invalidate id color as it might have changed
        this.idColor = IdColor.invalid();
        return this;
    }

    public Date getCreatedAt() {
        return this.createdAt;
    }

    public DistributionListModel setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
        return this;
    }

    @Override
    public DistributionListModel setLastUpdate(@Nullable Date lastUpdate) {
        this.lastUpdate = lastUpdate;
        return this;
    }

    @Override
    public @Nullable Date getLastUpdate() {
        // Note: Never return null for distribution lists, they should always be visible
        return this.lastUpdate == null ? new Date(0) : this.lastUpdate;
    }

    @Override
    public boolean isArchived() {
        return isArchived;
    }

    public DistributionListModel setArchived(boolean archived) {
        isArchived = archived;
        return this;
    }

    /**
     * Set whether or not this is an ad-hoc distribution list.
     * <p>
     * Setting this to true will result in the distribution list being hidden from the
     * conversation list.
     */
    public DistributionListModel setAdHocDistributionList(boolean isAdHocDistributionList) {
        this.isAdHocDistributionList = isAdHocDistributionList;
        return this;
    }

    /**
     * Return whether or not this is an ad-hoc distribution list.
     */
    public boolean isAdHocDistributionList() {
        return this.isAdHocDistributionList;
    }

    @Override
    public boolean isHidden() {
        // Hide ad-hoc distribution lists from conversation list
        return this.isAdHocDistributionList();
    }

    public IdColor getIdColor() {
        if (!idColor.isValid()) {
            idColor = IdColor.ofDistributionList(id);
        }
        return idColor;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DistributionListModel)) return false;
        DistributionListModel that = (DistributionListModel) o;
        return id == that.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}

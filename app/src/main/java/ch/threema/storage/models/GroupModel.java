/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2013-2022 Threema GmbH
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
import java.util.Objects;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import ch.threema.base.utils.Utils;
import ch.threema.domain.models.GroupId;

public class GroupModel implements ReceiverModel {
	public static final int GROUP_NAME_MAX_LENGTH_BYTES = 256;

	public static final String TABLE = "m_group";
	public static final String COLUMN_ID = "id";
	public static final String COLUMN_API_GROUP_ID = "apiGroupId";
	public static final String COLUMN_NAME = "name";
	public static final String COLUMN_CREATOR_IDENTITY = "creatorIdentity";
	public static final String COLUMN_CREATED_AT= "createdAt";
	public static final String COLUMN_SYNCHRONIZED_AT= "synchronizedAt";
	public static final String COLUMN_DELETED= "deleted";
	public static final String COLUMN_IS_ARCHIVED = "isArchived"; /* whether this group has been archived by user */

	private int id;
	private GroupId apiGroupId;
	private String name;
	private String creatorIdentity;
	private Date createdAt;
	private Date synchronizedAt;
	private boolean deleted;
	private boolean isArchived;

	// dummy class
	@Nullable
	public String getName() {
		return this.name;
	}

	public GroupModel setName(@Nullable String name) {
		this.name = Utils.truncateUTF8String(name, GROUP_NAME_MAX_LENGTH_BYTES);
		return this;
	}

	public int getId() {
		return this.id;
	}

	public GroupModel setId(int id) {
		this.id = id;
		return this;
	}

	public GroupModel setApiGroupId(GroupId apiGroupId) {
		this.apiGroupId = apiGroupId;
		return this;
	}

	public @NonNull GroupId getApiGroupId() {
		return this.apiGroupId;
	}

	public String getCreatorIdentity() {
		return this.creatorIdentity;
	}

	public GroupModel setCreatorIdentity(String creatorIdentity) {
		this.creatorIdentity = creatorIdentity;
		return this;
	}

	public Date getCreatedAt() {
		return this.createdAt;
	}

	public GroupModel setCreatedAt(Date createdAt) {
		this.createdAt = createdAt;
		return this;
	}

	public boolean isDeleted() {
		return this.deleted;
	}

	public GroupModel setDeleted(boolean deleted) {
		this.deleted = deleted;
		return this;
	}

	public Date getSynchronizedAt() {
		return this.synchronizedAt;
	}

	public GroupModel setSynchronizedAt(Date synchronizedAt) {
		this.synchronizedAt = synchronizedAt;
		return this;
	}

	public boolean isArchived() {
		return isArchived;
	}

	public GroupModel setArchived(boolean archived) {
		isArchived = archived;
		return this;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (!(o instanceof GroupModel)) return false;
		GroupModel that = (GroupModel) o;
		return id == that.id;
	}

	@Override
	public int hashCode() {
		return Objects.hash(id);
	}
}

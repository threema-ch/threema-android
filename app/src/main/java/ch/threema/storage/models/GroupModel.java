/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2013-2024 Threema GmbH
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

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.slf4j.Logger;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.Objects;

import ch.threema.app.utils.ColorUtil;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.base.utils.Utils;
import ch.threema.domain.models.GroupId;

public class GroupModel implements ReceiverModel {

	private static final Logger logger = LoggingUtil.getThreemaLogger("GroupModel");

	public static final int GROUP_NAME_MAX_LENGTH_BYTES = 256;

	public static final String TABLE = "m_group";
	public static final String COLUMN_ID = "id";
	public static final String COLUMN_API_GROUP_ID = "apiGroupId";
	public static final String COLUMN_NAME = "name";
	public static final String COLUMN_CREATOR_IDENTITY = "creatorIdentity";
	public static final String COLUMN_CREATED_AT = "createdAt";
	public static final String COLUMN_SYNCHRONIZED_AT = "synchronizedAt";
	public static final String COLUMN_LAST_UPDATE = "lastUpdate"; /* date when the conversation was last updated */
	public static final String COLUMN_DELETED = "deleted";
	public static final String COLUMN_IS_ARCHIVED = "isArchived"; /* whether this group has been archived by user */
	public static final String COLUMN_GROUP_DESC = "groupDesc";
	public static final String COLUMN_GROUP_DESC_CHANGED_TIMESTAMP = "changedGroupDescTimestamp";
	public static final String COLUMN_COLOR_INDEX = "colorIndex";

	private String groupDesc;
	private Date changedGroupDescTimestamp;

	private int id;
	private GroupId apiGroupId;
	private String name;
	private String creatorIdentity;
	private Date createdAt;
	private Date synchronizedAt;
	private @Nullable Date lastUpdate;
	private boolean deleted;
	private boolean isArchived;
	private int colorIndex = -1;

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

	@Override
	public GroupModel setLastUpdate(@Nullable Date lastUpdate) {
		this.lastUpdate = lastUpdate;
		return this;
	}

	@Override
	public @Nullable Date getLastUpdate() {
		// Note: Never return null for groups, they should always be visible
		return this.lastUpdate == null ? new Date(0) : this.lastUpdate;
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

	@Override
	public boolean isArchived() {
		return isArchived;
	}

	public GroupModel setArchived(boolean archived) {
		isArchived = archived;
		return this;
	}

	@Override
	public boolean isHidden() {
		// Groups can't currently be hidden from the conversation list
		return false;
	}

	public int getColorIndex() {
		if (colorIndex < 0) {
			computeColorIndex();
		}
		return colorIndex;
	}

	public GroupModel setColorIndex(int colorIndex) {
		this.colorIndex = colorIndex;
		return this;
	}

	public GroupModel setGroupDesc(String description) {
		groupDesc = description;
		return this;
	}

	public GroupModel setGroupDescTimestamp(Date groupDescDate) {
		changedGroupDescTimestamp = groupDescDate;
		return this;
	}


	public String getGroupDesc() {
		return this.groupDesc;
	}


	public Date getGroupDescTimestamp() {
		return this.changedGroupDescTimestamp;
	}


	public int getThemedColor(@NonNull Context context) {
		if (ConfigUtils.isTheDarkSide(context)) {
			return getColorDark();
		} else {
			return getColorLight();
		}
	}

	public int getColorLight() {
		if (colorIndex < 0) {
			computeColorIndex();
		}
		return ColorUtil.getInstance().getIDColorLight(colorIndex);
	}

	public int getColorDark() {
		if (colorIndex < 0) {
			computeColorIndex();
		}
		return ColorUtil.getInstance().getIDColorDark(colorIndex);
	}

	private void computeColorIndex() {
		byte[] groupCreatorIdentity = creatorIdentity.getBytes(StandardCharsets.UTF_8);
		byte[] apiGroupIdBin = apiGroupId.getGroupId();

		try {
			MessageDigest md = MessageDigest.getInstance("SHA-256");
			md.update(groupCreatorIdentity);
			md.update(apiGroupIdBin);
			byte firstByte = md.digest()[0];
			colorIndex = ColorUtil.getInstance().getIDColorIndex(firstByte);
		} catch (NoSuchAlgorithmException e) {
			logger.error("Could not hash the identity to determine color", e);
		}
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


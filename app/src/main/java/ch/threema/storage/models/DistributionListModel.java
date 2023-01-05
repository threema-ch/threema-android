/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2014-2023 Threema GmbH
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

import org.slf4j.Logger;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.Objects;

import androidx.annotation.Nullable;
import androidx.annotation.NonNull;
import ch.threema.app.utils.ColorUtil;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.base.utils.Utils;

public class DistributionListModel implements ReceiverModel {
	private final static Logger logger = LoggingUtil.getThreemaLogger("DistributionListModel");

	public static final int DISTRIBUTIONLIST_NAME_MAX_LENGTH_BYTES = 256;

	public static final String TABLE = "distribution_list";
	public static final String COLUMN_ID = "id";
	public static final String COLUMN_NAME = "name";
	public static final String COLUMN_CREATED_AT = "createdAt";
	public static final String COLUMN_IS_ARCHIVED = "isArchived"; /* whether this distribution list has been archived by user */
	public static final String COLUMN_IS_HIDDEN = "isHidden"; /* whether this distribution list is hidden from view */

	private long id;
	private String name;
	private Date createdAt;
	private boolean isArchived, isHidden;
	private int colorIndex = -1;

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
		return this;
	}

	public Date getCreatedAt() {
		return this.createdAt;
	}

	public DistributionListModel setCreatedAt(Date createdAt) {
		this.createdAt = createdAt;
		return this;
	}

	public boolean isArchived() {
		return isArchived;
	}

	public DistributionListModel setArchived(boolean archived) {
		isArchived = archived;
		return this;
	}

	public boolean isHidden() {
		return isHidden;
	}

	public DistributionListModel setHidden(boolean hidden) {
		isHidden = hidden;
		return this;
	}

	public int getThemedColor(@NonNull Context context) {
		if (ConfigUtils.getAppTheme(context) == ConfigUtils.THEME_DARK) {
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
		try {
			byte[] idBytes = new byte[]{(byte) (id >>> 56), (byte) (id >>> 48), (byte) (id >>> 40), (byte) (id >>> 32), (byte) (id >>> 24), (byte) (id >>> 16), (byte) (id >>> 8), (byte) id};
			byte firstByte = MessageDigest.getInstance("SHA-256").digest(idBytes)[0];
			colorIndex = ColorUtil.getInstance().getIDColorIndex(firstByte);
		} catch (NoSuchAlgorithmException e) {
			logger.error("Could not hash the distribution list to determine color", e);
		}
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

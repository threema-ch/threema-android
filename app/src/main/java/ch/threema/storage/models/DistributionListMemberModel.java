/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2014-2022 Threema GmbH
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

public class DistributionListMemberModel {

	public static final String TABLE = "distribution_list_member";
	public static final String COLUMN_ID = "id";
	public static final String COLUMN_IDENTITY = "identity";
	public static final String COLUMN_DISTRIBUTION_LIST_ID = "distributionListId";
	public static final String COLUMN_IS_ACTIVE = "isActive";

	private int id;
	private String identity;
	private long distributionListId;
	private boolean isActive = true;

	public long getDistributionListId() {
		return distributionListId;
	}

	public DistributionListMemberModel setDistributionListId(long distributionListId) {
		this.distributionListId = distributionListId;
		return this;
	}

	public String getIdentity() {
		return identity;
	}

	public DistributionListMemberModel setIdentity(String identity) {
		this.identity = identity;
		return this;
	}

	public int getId() {
		return id;
	}
	public DistributionListMemberModel setId(int id) {
		this.id = id;
		return this;
	}

	public boolean isActive() {
		return this.isActive;
	}

	public DistributionListMemberModel setActive(boolean active) {
		this.isActive = active;
		return this;
	}
}

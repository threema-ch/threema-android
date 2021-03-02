/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema Java Client
 * Copyright (c) 2020-2021 Threema GmbH
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

package ch.threema.client.work;

import java.util.ArrayList;
import java.util.List;

public class WorkDirectoryFilter {
	public static final int SORT_BY_FIRST_NAME = 1;
	public static final int SORT_BY_LAST_NAME = 2;

	private String query;
	private int page = 0;
	private int sortBy = SORT_BY_FIRST_NAME;
	private boolean sortAscending = true;
	private final List<WorkDirectoryCategory> categories = new ArrayList<>();

	public WorkDirectoryFilter query(String query) {
		this.query = query;
		return this;
	}

	public String getQuery() {
		return this.query;
	}

	public WorkDirectoryFilter page(int page) {
		this.page = page;
		return this;
	}

	public int getPage() {
		return this.page;
	}

	public WorkDirectoryFilter sortBy(int sortBy, boolean sortAscending) {
		switch (this.sortBy) {
			case SORT_BY_FIRST_NAME:
			case SORT_BY_LAST_NAME:
				this.sortBy = sortBy;
				this.sortAscending = sortAscending;
				break;
		}
		return this;
	}

	public int getSortBy() {
		return this.sortBy;
	}

	public boolean isSortAscending() {
		return this.sortAscending;
	}

	public WorkDirectoryFilter addCategory(WorkDirectoryCategory category) {
		if (!this.categories.contains(category)) {
			this.categories.add(category);
		}
		return this;
	}

	public List<WorkDirectoryCategory> getCategories() {
		return this.categories;
	}

	public WorkDirectoryFilter copy() {
		WorkDirectoryFilter newFilter = new WorkDirectoryFilter();
		newFilter.sortBy = this.sortBy;
		newFilter.sortAscending = this.sortAscending;
		newFilter.page = this.page;
		newFilter.query = this.query;
		// Copy categories
		for(WorkDirectoryCategory c: this.categories) {
			newFilter.categories.add(c);
		}
		return newFilter;
	}
}

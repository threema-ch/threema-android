/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2015-2021 Threema GmbH
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

package ch.threema.storage.factories;

import android.content.ContentValues;

import net.sqlcipher.Cursor;

import java.util.ArrayList;
import java.util.List;

import ch.threema.app.services.DistributionListService;
import ch.threema.storage.CursorHelper;
import ch.threema.storage.DatabaseServiceNew;
import ch.threema.storage.QueryBuilder;
import ch.threema.storage.models.DistributionListModel;

public class DistributionListModelFactory extends ModelFactory {
	public DistributionListModelFactory(DatabaseServiceNew databaseService) {
		super(databaseService, DistributionListModel.TABLE);
	}

	public List<DistributionListModel> getAll() {
		return convertList(this.databaseService.getReadableDatabase().query(this.getTableName(),
				null,
				null,
				null,
				null,
				null,
				null));
	}

	public DistributionListModel getById(int id) {
		return getFirst(
				DistributionListModel.COLUMN_ID + "=?",
				new String[]{
						String.valueOf(id)
				});
	}

	private List<DistributionListModel> convert(
			QueryBuilder queryBuilder,
			String orderBy) {
		queryBuilder.setTables(this.getTableName());
		return convertList(queryBuilder.query(
				this.databaseService.getReadableDatabase(),
				null,
				null,
				null,
				null,
				null,
				orderBy));
	}

	private List<DistributionListModel> convertList(Cursor c) {

		List<DistributionListModel> result = new ArrayList<>();
		if(c != null) {
			try {
				while (c.moveToNext()) {
					result.add(convert(c));
				}
			}
			finally {
				c.close();
			}
		}
		return result;
	}

	private DistributionListModel convert(Cursor cursor) {
		if(cursor != null && cursor.getPosition() >= 0) {
			final DistributionListModel c = new DistributionListModel();

			//convert default
			new CursorHelper(cursor, columnIndexCache).current(new CursorHelper.Callback() {
				@Override
				public boolean next(CursorHelper cursorHelper) {
					c
							.setId(cursorHelper.getInt(DistributionListModel.COLUMN_ID))
							.setName(cursorHelper.getString(DistributionListModel.COLUMN_NAME))
							.setCreatedAt(cursorHelper.getDateByString(DistributionListModel.COLUMN_CREATED_AT))
							.setArchived(cursorHelper.getBoolean(DistributionListModel.COLUMN_IS_ARCHIVED));

					return false;
				}
			});

			return c;
		}

		return null;
	}

	public boolean createOrUpdate(DistributionListModel distributionListModel) {
		boolean insert = true;
		if(distributionListModel.getId() > 0) {
			Cursor cursor = this.databaseService.getReadableDatabase().query(
					this.getTableName(),
					null,
					DistributionListModel.COLUMN_ID + "=?",
					new String[]{
							String.valueOf(distributionListModel.getId())
					},
					null,
					null,
					null
			);
			if (cursor != null) {
				try {
					insert = !cursor.moveToNext();
				} finally {
					cursor.close();
				}
			}
		}

		if(insert) {
			return create(distributionListModel);
		}
		else {
			return update(distributionListModel);
		}
	}

	private ContentValues buildContentValues(DistributionListModel distributionListModel) {
		ContentValues contentValues = new ContentValues();
		contentValues.put(DistributionListModel.COLUMN_NAME, distributionListModel.getName());
		contentValues.put(DistributionListModel.COLUMN_CREATED_AT, distributionListModel.getCreatedAt() != null ? CursorHelper.dateAsStringFormat.get().format(distributionListModel.getCreatedAt()) : null);
		contentValues.put(DistributionListModel.COLUMN_IS_ARCHIVED, distributionListModel.isArchived());

		return contentValues;
	}

	public boolean create(DistributionListModel distributionListModel) {
		ContentValues contentValues = buildContentValues(distributionListModel);
		long newId = this.databaseService.getWritableDatabase().insertOrThrow(this.getTableName(), null, contentValues);
		if (newId > 0) {
			distributionListModel.setId((int) newId);
			return true;
		}
		return false;
	}

	public boolean update(DistributionListModel distributionListModel) {
		ContentValues contentValues = buildContentValues(distributionListModel);
		this.databaseService.getWritableDatabase().update(this.getTableName(),
				contentValues,
				DistributionListModel.COLUMN_ID + "=?",
				new String[]{
						String.valueOf(distributionListModel.getId())
				});
		return true;
	}

	public int delete(DistributionListModel distributionListModel) {
		return this.databaseService.getWritableDatabase().delete(this.getTableName(),
				DistributionListModel.COLUMN_ID + "=?",
				new String[]{
						String.valueOf(distributionListModel.getId())
				});
	}

	private DistributionListModel getFirst(String selection, String[] selectionArgs) {
		Cursor cursor = this.databaseService.getReadableDatabase().query (
				this.getTableName(),
				null,
				selection,
				selectionArgs,
				null,
				null,
				null
		);

		if(cursor != null) {
			try {
				if (cursor.moveToFirst()) {
					return convert(cursor);
				}
			}
			finally {
				cursor.close();
			}
		}

		return null;
	}

	public List<DistributionListModel> filter(DistributionListService.DistributionListFilter filter) {
		QueryBuilder queryBuilder = new QueryBuilder();

		//sort by id!
		String orderBy = null;

		if(filter != null) {
			if(!filter.sortingByDate()) {
				orderBy = DistributionListModel.COLUMN_CREATED_AT + " " + (filter.sortingAscending() ? "ASC" : "DESC");
			}
		}

		return convert(
				queryBuilder,
				orderBy);

	}

	@Override
	public String[] getStatements() {
		return new String[]{
				"CREATE TABLE `distribution_list` (`id` INTEGER PRIMARY KEY AUTOINCREMENT , `name` VARCHAR , `createdAt` VARCHAR, `isArchived` TINYINT DEFAULT 0 );"
		};
	}
}

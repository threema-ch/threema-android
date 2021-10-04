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
import net.sqlcipher.database.SQLiteException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.Nullable;
import ch.threema.app.utils.TestUtil;
import ch.threema.base.VerificationLevel;
import ch.threema.client.Utils;
import ch.threema.storage.CursorHelper;
import ch.threema.storage.DatabaseServiceNew;
import ch.threema.storage.QueryBuilder;
import ch.threema.storage.models.ContactModel;

public class ContactModelFactory extends ModelFactory {
	private static final Logger logger = LoggerFactory.getLogger(ContactModelFactory.class);

	public ContactModelFactory(DatabaseServiceNew databaseService) {
		super(databaseService, ContactModel.TABLE);
	}

	public List<ContactModel> getAll() {
		return convertList(this.databaseService.getReadableDatabase().query(this.getTableName(),
				null,
				null,
				null,
				null,
				null,
				null));
	}

	@Nullable
	public ContactModel getByIdentity(String identity) {
		return this.getFirst(ContactModel.COLUMN_IDENTITY + "=?",
				new String[]{
						identity
				});
	}

	@Nullable
	public ContactModel getByPublicKey(byte[] publicKey) {
		return getFirst(
				"" + ContactModel.COLUMN_PUBLIC_KEY + " =x'" + Utils.byteArrayToHexString(publicKey) + "'",
				null);
	}

	@Nullable
	public ContactModel getByLookupKey(String lookupKey) {
		return getFirst(ContactModel.COLUMN_ANDROID_CONTACT_LOOKUP_KEY + " =?",
				new String[]{
					lookupKey
				});
	}

	public List<ContactModel> convert(
	                                         QueryBuilder queryBuilder,
	                                         String[] args,
	                                         String orderBy) {
		queryBuilder.setTables(this.getTableName());
		return convertList(queryBuilder.query(
				this.databaseService.getReadableDatabase(),
				null,
				null,
				args,
				null,
				null,
				orderBy));
	}

	private List<ContactModel> convertList(Cursor c) {
		List<ContactModel> result = new ArrayList<>();
		if(c != null) {
			try {
				while (c.moveToNext()) {
					result.add(this.convert(new CursorHelper(c, columnIndexCache)));
				}
			}
			catch (SQLiteException e) {
				logger.debug("Exception", e);
			}
			finally {
				c.close();
			}
		}
		return result;
	}

	private ContactModel convert(CursorHelper cursorFactory) {
		final ContactModel[] cm = new ContactModel[1];
		cursorFactory.current(new CursorHelper.Callback() {
			@Override
			public boolean next(CursorHelper cursorFactory) {
				ContactModel c = new ContactModel(
					cursorFactory.getString(ContactModel.COLUMN_IDENTITY),
					cursorFactory.getBlob(ContactModel.COLUMN_PUBLIC_KEY)
				);

				c
					.setName(
						cursorFactory.getString(ContactModel.COLUMN_FIRST_NAME),
						cursorFactory.getString(ContactModel.COLUMN_LAST_NAME))
					.setPublicNickName(cursorFactory.getString(ContactModel.COLUMN_PUBLIC_NICK_NAME))
					.setState(ContactModel.State.valueOf(cursorFactory.getString(ContactModel.COLUMN_STATE)))
					.setAndroidContactLookupKey(cursorFactory.getString(ContactModel.COLUMN_ANDROID_CONTACT_LOOKUP_KEY))
					.setThreemaAndroidContactId(cursorFactory.getString(ContactModel.COLUMN_THREEMA_ANDROID_CONTACT_ID))
					.setIsSynchronized(cursorFactory.getInt(ContactModel.COLUMN_IS_SYNCHRONIZED) == 1)
					.setIsWork(cursorFactory.getInt(ContactModel.COLUMN_IS_WORK) == 1)
					.setType(cursorFactory.getInt(ContactModel.COLUMN_TYPE))
					.setFeatureMask(cursorFactory.getInt(ContactModel.COLUMN_FEATURE_LEVEL))
					.setColor(cursorFactory.getInt(ContactModel.COLUMN_COLOR))
					.setIsHidden(cursorFactory.getInt(ContactModel.COLUMN_IS_HIDDEN) == 1)
					.setAvatarExpires(cursorFactory.getDate(ContactModel.COLUMN_AVATAR_EXPIRES))
					.setProfilePicSentDate(cursorFactory.getDate(ContactModel.COLUMN_PROFILE_PIC_SENT_DATE))
					.setDateCreated(cursorFactory.getDate(ContactModel.COLUMN_DATE_CREATED))
					.setIsRestored(cursorFactory.getInt(ContactModel.COLUMN_IS_RESTORED) == 1)
					.setArchived(cursorFactory.getInt(ContactModel.COLUMN_IS_ARCHIVED) == 1)
					.setReadReceipts(cursorFactory.getInt(ContactModel.COLUMN_READ_RECEIPTS))
					.setTypingIndicators(cursorFactory.getInt(ContactModel.COLUMN_TYPING_INDICATORS));

				switch (cursorFactory.getInt(ContactModel.COLUMN_VERIFICATION_LEVEL))
				{
					case 1:
						c.setVerificationLevel(VerificationLevel.SERVER_VERIFIED);
						break;
					case 2:
						c.setVerificationLevel(VerificationLevel.FULLY_VERIFIED);
						break;
					default:
						c.setVerificationLevel(VerificationLevel.UNVERIFIED);
				}

				cm[0] = c;

				return false;
			}});

		return cm[0];
	}

	public boolean createOrUpdate(ContactModel contactModel) {
		if(TestUtil.empty(contactModel.getIdentity())) {
			logger.error("try to create or update a contact model without identity");
			return false;
		}
		Cursor cursor = this.databaseService.getReadableDatabase().query (
				this.getTableName(),
				null,
				ContactModel.COLUMN_IDENTITY + "=?",
				new String[]{
						contactModel.getIdentity()
				},
				null,
				null,
				null
		);

		boolean insert = true;
		if(cursor != null) {
			insert = !cursor.moveToNext();
			cursor.close();
		}

		ContentValues contentValues = new ContentValues();

		contentValues.put(ContactModel.COLUMN_PUBLIC_KEY, contactModel.getPublicKey());
		contentValues.put(ContactModel.COLUMN_FIRST_NAME, contactModel.getFirstName());
		contentValues.put(ContactModel.COLUMN_LAST_NAME, contactModel.getLastName());
		contentValues.put(ContactModel.COLUMN_PUBLIC_NICK_NAME, contactModel.getPublicNickName());
		contentValues.put(ContactModel.COLUMN_VERIFICATION_LEVEL, contactModel.getVerificationLevel().ordinal());

		if(contactModel.getState() == null) {
			contactModel.setState(ContactModel.State.ACTIVE);
		}
		contentValues.put(ContactModel.COLUMN_STATE, contactModel.getState().toString());
		contentValues.put(ContactModel.COLUMN_ANDROID_CONTACT_LOOKUP_KEY, contactModel.getAndroidContactLookupKey());
		contentValues.put(ContactModel.COLUMN_THREEMA_ANDROID_CONTACT_ID, contactModel.getThreemaAndroidContactId());
		contentValues.put(ContactModel.COLUMN_IS_SYNCHRONIZED, contactModel.isSynchronized());
		contentValues.put(ContactModel.COLUMN_FEATURE_LEVEL, contactModel.getFeatureMask());
		contentValues.put(ContactModel.COLUMN_COLOR, contactModel.getColor());
		contentValues.put(ContactModel.COLUMN_AVATAR_EXPIRES, contactModel.getAvatarExpires() != null ?
				contactModel.getAvatarExpires().getTime()
				: null);
		contentValues.put(ContactModel.COLUMN_IS_WORK, contactModel.isWork());
		contentValues.put(ContactModel.COLUMN_TYPE, contactModel.getType());
		contentValues.put(ContactModel.COLUMN_PROFILE_PIC_SENT_DATE, contactModel.getProfilePicSentDate() != null ?
				contactModel.getProfilePicSentDate().getTime()
				: null);
		contentValues.put(ContactModel.COLUMN_DATE_CREATED, contactModel.getDateCreated() != null ? contactModel.getDateCreated().getTime()
				: null);
		contentValues.put(ContactModel.COLUMN_IS_HIDDEN, contactModel.isHidden());
		contentValues.put(ContactModel.COLUMN_IS_RESTORED, contactModel.isRestored());
		contentValues.put(ContactModel.COLUMN_IS_ARCHIVED, contactModel.isArchived());
		contentValues.put(ContactModel.COLUMN_READ_RECEIPTS, contactModel.getReadReceipts());
		contentValues.put(ContactModel.COLUMN_TYPING_INDICATORS, contactModel.getTypingIndicators());

		if (insert) {
			//never update identity field
			//just set on update
			contentValues.put(ContactModel.COLUMN_IDENTITY, contactModel.getIdentity());
			this.databaseService.getWritableDatabase().insertOrThrow(this.getTableName(), null, contentValues);
		}
		else {
			this.databaseService.getWritableDatabase().update(this.getTableName(),
					contentValues,
					ContactModel.COLUMN_IDENTITY + "=?",
					new String[]{
							contactModel.getIdentity()
					});
		}
		return true;
	}

	public int delete(ContactModel contactModel) {
		return this.databaseService.getWritableDatabase().delete(this.getTableName(),
				ContactModel.COLUMN_IDENTITY + "=?",
				new String[]{
						contactModel.getIdentity()
				});
	}

	@Override
	public String[] getStatements() {
		return new String[] {
				"CREATE TABLE `" + ContactModel.TABLE + "` (" +
						"`" + ContactModel.COLUMN_IDENTITY + "` VARCHAR ," +
						"`" + ContactModel.COLUMN_PUBLIC_KEY + "` BLOB ," +
						"`" + ContactModel.COLUMN_FIRST_NAME + "` VARCHAR ," +
						"`" + ContactModel.COLUMN_LAST_NAME + "` VARCHAR ," +
						"`" + ContactModel.COLUMN_PUBLIC_NICK_NAME + "` VARCHAR ," +
						"`" + ContactModel.COLUMN_VERIFICATION_LEVEL + "` INTEGER ," +
						"`" + ContactModel.COLUMN_STATE + "` VARCHAR DEFAULT 'ACTIVE' NOT NULL ," +
						"`" + ContactModel.COLUMN_ANDROID_CONTACT_LOOKUP_KEY + "` VARCHAR ," +
						"`" + ContactModel.COLUMN_THREEMA_ANDROID_CONTACT_ID + "` VARCHAR ," +
						"`" + ContactModel.COLUMN_IS_SYNCHRONIZED + "` SMALLINT DEFAULT 0 ," +
						"`" + ContactModel.COLUMN_FEATURE_LEVEL + "` INTEGER DEFAULT 0 NOT NULL ," +
						"`" + ContactModel.COLUMN_COLOR + "` INTEGER ," +
						"`" + ContactModel.COLUMN_AVATAR_EXPIRES + "` BIGINT," +
						"`" + ContactModel.COLUMN_IS_WORK + "` TINYINT DEFAULT 0," +
						"`" + ContactModel.COLUMN_TYPE + "` INT DEFAULT 0," +
						"`" + ContactModel.COLUMN_PROFILE_PIC_SENT_DATE + "` BIGINT DEFAULT 0," +
						"`" + ContactModel.COLUMN_DATE_CREATED + "` BIGINT DEFAULT 0," +
						"`" + ContactModel.COLUMN_IS_HIDDEN + "` TINYINT DEFAULT 0," +
						"`" + ContactModel.COLUMN_IS_RESTORED + "` TINYINT DEFAULT 0," +
						"`" + ContactModel.COLUMN_IS_ARCHIVED + "` TINYINT DEFAULT 0," +
						"`" + ContactModel.COLUMN_READ_RECEIPTS + "` TINYINT DEFAULT 0," +
						"`" + ContactModel.COLUMN_TYPING_INDICATORS + "` TINYINT DEFAULT 0," +
					"PRIMARY KEY (`" + ContactModel.COLUMN_IDENTITY + "`) );"
		};
	}

	private @Nullable ContactModel getFirst(String selection, String[] selectionArgs) {
		try (Cursor cursor = this.databaseService.getReadableDatabase().query(
			this.getTableName(),
			null,
			selection,
			selectionArgs,
			null,
			null,
			null
		)) {
			if (cursor != null && cursor.moveToFirst()) {
				return convert(new CursorHelper(cursor, columnIndexCache));
			}
		} catch (Exception e) {
			logger.error("Exception", e);
		}
		return null;
	}
}

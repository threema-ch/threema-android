/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2015-2025 Threema GmbH
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
import android.database.Cursor;
import android.database.sqlite.SQLiteException;

import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import ch.threema.app.utils.TestUtil;
import ch.threema.base.crypto.NaCl;
import static ch.threema.base.utils.LoggingKt.getThreemaLogger;
import ch.threema.domain.models.IdentityState;
import ch.threema.domain.models.IdentityType;
import ch.threema.domain.models.VerificationLevel;
import ch.threema.domain.protocol.csp.ProtocolDefines;
import ch.threema.storage.CursorHelper;
import ch.threema.storage.DatabaseService;
import ch.threema.storage.QueryBuilder;
import ch.threema.storage.models.ContactModel;
import ch.threema.storage.models.ContactModel.AcquaintanceLevel;

public class ContactModelFactory extends ModelFactory {
    private static final Logger logger = getThreemaLogger("ContactModelFactory");

    public ContactModelFactory(DatabaseService databaseService) {
        super(databaseService, ContactModel.TABLE);
    }

    public List<ContactModel> getAll() {
        return convertList(getReadableDatabase().query(this.getTableName(),
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
            getReadableDatabase(),
            null,
            null,
            args,
            null,
            null,
            orderBy));
    }

    private List<ContactModel> convertList(Cursor c) {
        List<ContactModel> result = new ArrayList<>();
        if (c != null) {
            try (c) {
                while (c.moveToNext()) {
                    result.add(this.convert(new CursorHelper(c, getColumnIndexCache())));
                }
            } catch (SQLiteException e) {
                logger.debug("Exception", e);
            }
        }
        return result;
    }

    private ContactModel convert(@NonNull CursorHelper cursorFactory) {
        final ContactModel[] cm = new ContactModel[1];
        cursorFactory.current((CursorHelper.Callback) cursorFactory1 -> {
            ContactModel contactModel = ContactModel.createUnchecked(
                cursorFactory1.getString(ContactModel.COLUMN_IDENTITY),
                cursorFactory1.getBlob(ContactModel.COLUMN_PUBLIC_KEY)
            );

            contactModel
                .setName(
                    cursorFactory1.getString(ContactModel.COLUMN_FIRST_NAME),
                    cursorFactory1.getString(ContactModel.COLUMN_LAST_NAME)
                )
                .setPublicNickName(cursorFactory1.getString(ContactModel.COLUMN_PUBLIC_NICK_NAME))
                .setState(IdentityState.valueOf(cursorFactory1.getString(ContactModel.COLUMN_STATE)))
                .setAndroidContactLookupKey(cursorFactory1.getString(ContactModel.COLUMN_ANDROID_CONTACT_LOOKUP_KEY))
                .setIsWork(cursorFactory1.getInt(ContactModel.COLUMN_IS_WORK) == 1)
                .setIdentityType(
                    cursorFactory1.getInt(ContactModel.COLUMN_TYPE) == 1
                        ? IdentityType.WORK
                        : IdentityType.NORMAL
                )
                .setFeatureMask(cursorFactory1.getLong(ContactModel.COLUMN_FEATURE_MASK))
                .setIdColorIndex(cursorFactory1.getInt(ContactModel.COLUMN_ID_COLOR_INDEX))
                .setAcquaintanceLevel(
                    cursorFactory1.getInt(ContactModel.COLUMN_ACQUAINTANCE_LEVEL) == 1
                        ? AcquaintanceLevel.GROUP
                        : AcquaintanceLevel.DIRECT
                )
                .setLocalAvatarExpires(cursorFactory1.getDate(ContactModel.COLUMN_LOCAL_AVATAR_EXPIRES))
                .setProfilePicBlobID(cursorFactory1.getBlob(ContactModel.COLUMN_PROFILE_PIC_BLOB_ID))
                .setDateCreated(cursorFactory1.getDate(ContactModel.COLUMN_CREATED_AT))
                .setLastUpdate(cursorFactory1.getDate(ContactModel.COLUMN_LAST_UPDATE))
                .setIsRestored(cursorFactory1.getInt(ContactModel.COLUMN_IS_RESTORED) == 1)
                .setArchived(cursorFactory1.getInt(ContactModel.COLUMN_IS_ARCHIVED) == 1)
                .setReadReceipts(cursorFactory1.getInt(ContactModel.COLUMN_READ_RECEIPTS))
                .setTypingIndicators(cursorFactory1.getInt(ContactModel.COLUMN_TYPING_INDICATORS))
                .setForwardSecurityState(cursorFactory1.getInt(ContactModel.COLUMN_FORWARD_SECURITY_STATE))
                .setJobTitle(cursorFactory1.getString(ContactModel.COLUMN_JOB_TITLE))
                .setDepartment(cursorFactory1.getString(ContactModel.COLUMN_DEPARTMENT))
                .setNotificationTriggerPolicyOverride(cursorFactory1.getLong(ContactModel.COLUMN_NOTIFICATION_TRIGGER_POLICY_OVERRIDE));

            // Convert state to enum
            switch (cursorFactory1.getString(ContactModel.COLUMN_STATE)) {
                case "INACTIVE":
                    contactModel.setState(IdentityState.INACTIVE);
                    break;
                case "INVALID":
                    contactModel.setState(IdentityState.INVALID);
                    break;
                case "ACTIVE":
                case "TEMPORARY": // Legacy state, see !276
                default:
                    contactModel.setState(IdentityState.ACTIVE);
                    break;
            }

            switch (cursorFactory1.getInt(ContactModel.COLUMN_VERIFICATION_LEVEL)) {
                case 1:
                    contactModel.verificationLevel = VerificationLevel.SERVER_VERIFIED;
                    break;
                case 2:
                    contactModel.verificationLevel = VerificationLevel.FULLY_VERIFIED;
                    break;
                default:
                    contactModel.verificationLevel = VerificationLevel.UNVERIFIED;
            }

            cm[0] = contactModel;

            return false;
        });

        return cm[0];
    }

    public boolean createOrUpdate(ContactModel contactModel) {
        if (TestUtil.isEmptyOrNull(contactModel.getIdentity())) {
            logger.error("try to create or update a contact model without identity");
            return false;
        }
        if (contactModel.getIdentity().length() != ProtocolDefines.IDENTITY_LEN) {
            logger.error("Cannot add a contact with an invalid identity: {}", contactModel.getIdentity());
            return false;
        }
        if (contactModel.getPublicKey().length != NaCl.PUBLIC_KEY_BYTES) {
            logger.error("Cannot add a contact with a public key of length {}", contactModel.getPublicKey());
            return false;
        }

        Cursor cursor = getReadableDatabase().query(
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
        if (cursor != null) {
            insert = !cursor.moveToNext();
            cursor.close();
        }

        ContentValues contentValues = new ContentValues();

        contentValues.put(ContactModel.COLUMN_PUBLIC_KEY, contactModel.getPublicKey());
        contentValues.put(ContactModel.COLUMN_FIRST_NAME, contactModel.getFirstName());
        contentValues.put(ContactModel.COLUMN_LAST_NAME, contactModel.getLastName());
        contentValues.put(ContactModel.COLUMN_PUBLIC_NICK_NAME, contactModel.getPublicNickName());
        contentValues.put(ContactModel.COLUMN_VERIFICATION_LEVEL, contactModel.verificationLevel.ordinal());

        if (contactModel.getState() == null) {
            contactModel.setState(IdentityState.ACTIVE);
        }
        contentValues.put(ContactModel.COLUMN_STATE, contactModel.getState().toString());
        contentValues.put(ContactModel.COLUMN_ANDROID_CONTACT_LOOKUP_KEY, contactModel.getAndroidContactLookupKey());
        contentValues.put(ContactModel.COLUMN_FEATURE_MASK, contactModel.getFeatureMask());
        contentValues.put(ContactModel.COLUMN_ID_COLOR_INDEX, contactModel.getIdColor().getColorIndex());
        contentValues.put(ContactModel.COLUMN_LOCAL_AVATAR_EXPIRES, contactModel.getLocalAvatarExpires() != null ?
            contactModel.getLocalAvatarExpires().getTime()
            : null);
        contentValues.put(ContactModel.COLUMN_IS_WORK, contactModel.isWorkVerified());
        contentValues.put(ContactModel.COLUMN_TYPE, contactModel.getIdentityType() == IdentityType.WORK ? 1 : 0);
        contentValues.put(ContactModel.COLUMN_PROFILE_PIC_BLOB_ID, contactModel.getProfilePicBlobID());
        contentValues.put(ContactModel.COLUMN_CREATED_AT, contactModel.getDateCreated() != null ? contactModel.getDateCreated().getTime() : null);
        contentValues.put(ContactModel.COLUMN_LAST_UPDATE, contactModel.getLastUpdate() != null ? contactModel.getLastUpdate().getTime() : null);
        contentValues.put(ContactModel.COLUMN_ACQUAINTANCE_LEVEL, contactModel.getAcquaintanceLevel() == AcquaintanceLevel.GROUP ? 1 : 0);
        contentValues.put(ContactModel.COLUMN_IS_RESTORED, contactModel.isRestored());
        contentValues.put(ContactModel.COLUMN_IS_ARCHIVED, contactModel.isArchived());
        contentValues.put(ContactModel.COLUMN_READ_RECEIPTS, contactModel.getReadReceipts());
        contentValues.put(ContactModel.COLUMN_TYPING_INDICATORS, contactModel.getTypingIndicators());
        contentValues.put(ContactModel.COLUMN_FORWARD_SECURITY_STATE, contactModel.getForwardSecurityState());
        contentValues.put(ContactModel.COLUMN_JOB_TITLE, contactModel.getJobTitle());
        contentValues.put(ContactModel.COLUMN_DEPARTMENT, contactModel.getDepartment());
        contentValues.put(ContactModel.COLUMN_NOTIFICATION_TRIGGER_POLICY_OVERRIDE, contactModel.getNotificationTriggerPolicyOverride());
        // Note: Sync state not implemented in "old model" anymore

        if (insert) {
            //never update identity field
            contentValues.put(ContactModel.COLUMN_IDENTITY, contactModel.getIdentity());
            getWritableDatabase().insertOrThrow(
                this.getTableName(),
                null,
                contentValues
            );
        } else {
            getWritableDatabase().update(
                this.getTableName(),
                contentValues,
                ContactModel.COLUMN_IDENTITY + "=?",
                new String[]{
                    contactModel.getIdentity()
                }
            );
        }
        return true;
    }

    /**
     * Updates the last update flag of the given identity.
     */
    public void setLastUpdate(@NonNull String identity, @Nullable Date lastUpdate) {
        Long lastUpdateTime = lastUpdate != null ? lastUpdate.getTime() : null;
        ContentValues contentValues = new ContentValues();
        contentValues.put(ContactModel.COLUMN_LAST_UPDATE, lastUpdateTime);

        getWritableDatabase().update(
            ContactModel.TABLE,
            contentValues,
            ContactModel.COLUMN_IDENTITY + " = ?",
            new String[]{identity}
        );
    }

    /**
     * Updates the forward security state of the given identity.
     */
    public void setForwardSecurityState(
        @NonNull String identity,
        @ContactModel.ForwardSecurityState int forwardSecurityState
    ) {
        ContentValues contentValues = new ContentValues();
        contentValues.put(ContactModel.COLUMN_FORWARD_SECURITY_STATE, forwardSecurityState);

        getWritableDatabase().update(
            ContactModel.TABLE,
            contentValues,
            ContactModel.COLUMN_IDENTITY + " = ?",
            new String[]{identity}
        );
    }

    public int delete(ContactModel contactModel) {
        return getWritableDatabase().delete(this.getTableName(),
            ContactModel.COLUMN_IDENTITY + "=?",
            new String[]{
                contactModel.getIdentity()
            });
    }

    @Override
    @NonNull
    public String[] getStatements() {
        return new String[]{
            "CREATE TABLE `" + ContactModel.TABLE + "` (" +
                "`" + ContactModel.COLUMN_IDENTITY + "` VARCHAR ," +
                "`" + ContactModel.COLUMN_PUBLIC_KEY + "` BLOB ," +
                "`" + ContactModel.COLUMN_FIRST_NAME + "` VARCHAR ," +
                "`" + ContactModel.COLUMN_LAST_NAME + "` VARCHAR ," +
                "`" + ContactModel.COLUMN_PUBLIC_NICK_NAME + "` VARCHAR ," +
                "`" + ContactModel.COLUMN_VERIFICATION_LEVEL + "` INTEGER ," +
                "`" + ContactModel.COLUMN_STATE + "` VARCHAR DEFAULT 'ACTIVE' NOT NULL ," +
                "`" + ContactModel.COLUMN_ANDROID_CONTACT_LOOKUP_KEY + "` VARCHAR ," +
                "`" + ContactModel.COLUMN_FEATURE_MASK + "` INTEGER DEFAULT 0 NOT NULL ," +
                "`" + ContactModel.COLUMN_ID_COLOR_INDEX + "` INTEGER ," +
                "`" + ContactModel.COLUMN_LOCAL_AVATAR_EXPIRES + "` BIGINT," +
                "`" + ContactModel.COLUMN_IS_WORK + "` TINYINT DEFAULT 0," +
                "`" + ContactModel.COLUMN_TYPE + "` INT DEFAULT 0," +
                "`" + ContactModel.COLUMN_PROFILE_PIC_BLOB_ID + "` BLOB DEFAULT NULL," +
                "`" + ContactModel.COLUMN_CREATED_AT + "` BIGINT DEFAULT 0," +
                "`" + ContactModel.COLUMN_LAST_UPDATE + "` INTEGER," +
                "`" + ContactModel.COLUMN_ACQUAINTANCE_LEVEL + "` TINYINT DEFAULT 0 NOT NULL," +
                "`" + ContactModel.COLUMN_IS_RESTORED + "` TINYINT DEFAULT 0," +
                "`" + ContactModel.COLUMN_IS_ARCHIVED + "` TINYINT DEFAULT 0," +
                "`" + ContactModel.COLUMN_READ_RECEIPTS + "` TINYINT DEFAULT 0," +
                "`" + ContactModel.COLUMN_TYPING_INDICATORS + "` TINYINT DEFAULT 0," +
                "`" + ContactModel.COLUMN_FORWARD_SECURITY_STATE + "` TINYINT DEFAULT 0," +
                "`" + ContactModel.COLUMN_SYNC_STATE + "` INTEGER NOT NULL DEFAULT 0," +
                "`" + ContactModel.COLUMN_JOB_TITLE + "` VARCHAR DEFAULT NULL," +
                "`" + ContactModel.COLUMN_DEPARTMENT + "` VARCHAR DEFAULT NULL," +
                "`" + ContactModel.COLUMN_NOTIFICATION_TRIGGER_POLICY_OVERRIDE + "` BIGINT DEFAULT NULL," +
                "PRIMARY KEY (`" + ContactModel.COLUMN_IDENTITY + "`) );"
        };
    }

    private @Nullable ContactModel getFirst(String selection, String[] selectionArgs) {
        try (Cursor cursor = getReadableDatabase().query(
            this.getTableName(),
            null,
            selection,
            selectionArgs,
            null,
            null,
            null
        )) {
            if (cursor != null && cursor.moveToFirst()) {
                return convert(new CursorHelper(cursor, getColumnIndexCache()));
            }
        } catch (Exception e) {
            logger.error("Exception", e);
        }
        return null;
    }
}

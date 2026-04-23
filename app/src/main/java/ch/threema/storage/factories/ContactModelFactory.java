package ch.threema.storage.factories;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import ch.threema.app.stores.IdentityProvider;
import ch.threema.app.utils.TestUtil;
import ch.threema.base.crypto.NaCl;

import static ch.threema.base.utils.LoggingKt.getThreemaLogger;

import ch.threema.domain.models.IdentityState;
import ch.threema.domain.models.IdentityType;
import ch.threema.domain.models.VerificationLevel;
import ch.threema.domain.protocol.csp.ProtocolDefines;
import ch.threema.storage.CursorHelper;
import ch.threema.storage.DatabaseCreationProvider;
import ch.threema.storage.DatabaseProvider;
import ch.threema.storage.DatabaseUtil;
import ch.threema.storage.QueryBuilder;
import ch.threema.storage.models.ContactModel;
import ch.threema.storage.models.ContactModel.AcquaintanceLevel;

public class ContactModelFactory extends ModelFactory {
    private static final Logger logger = getThreemaLogger("ContactModelFactory");

    @NonNull
    private final IdentityProvider identityProvider;

    public ContactModelFactory(
        @NonNull DatabaseProvider databaseProvider,
        @NonNull IdentityProvider identityProvider
    ) {
        super(databaseProvider, ContactModel.TABLE);

        this.identityProvider = identityProvider;
    }

    public List<ContactModel> getAll() {
        return convertList(
            getReadableDatabase().query(getTableName(), null, null, null, null, null, null)
        );
    }

    @Nullable
    public ContactModel getByIdentity(@NonNull String identity) {
        return this.getFirstOrNull(
            ContactModel.COLUMN_IDENTITY + "=?",
            identity
        );
    }

    @NonNull
    public List<ContactModel> getByIdentities(@NonNull List<String> identities) {
        if (identities.isEmpty()) {
            return new ArrayList<>();
        }
        final @NonNull String placeholders = DatabaseUtil.makePlaceholders(identities.size());
        final @NonNull String selection = ContactModel.COLUMN_IDENTITY + " IN (" + placeholders + ")";
        final @NonNull String[] selectionArgs = identities.toArray(new String[0]);
        return convertList(
            getReadableDatabase().query(getTableName(), null, selection, selectionArgs, null, null, null)
        );
    }

    @Nullable
    public ContactModel getByLookupKey(@NonNull String lookupKey) {
        return getFirstOrNull(
            ContactModel.COLUMN_ANDROID_CONTACT_LOOKUP_KEY + " =?",
            lookupKey
        );
    }

    @NonNull
    public List<ContactModel> convert(
        @NonNull QueryBuilder queryBuilder,
        @Nullable String[] args,
        @Nullable String orderBy
    ) {
        queryBuilder.setTables(this.getTableName());
        return convertList(
            queryBuilder.query(getReadableDatabase(), null, null, args, null, null, orderBy)
        );
    }

    @NonNull
    private List<ContactModel> convertList(@Nullable Cursor cursor) {
        final @NonNull List<ContactModel> results = new ArrayList<>();
        if (cursor == null) {
            return results;
        }
        try (cursor) {
            while (cursor.moveToNext()) {
                final @NonNull ContactModel contactModel = convert(new CursorHelper(cursor, getColumnIndexCache()));
                results.add(contactModel);
            }
        } catch (SQLiteException e) {
            logger.debug("Exception", e);
        }
        return results;
    }

    @NonNull
    private ContactModel convert(@NonNull CursorHelper cursorHelper) {
        final @NonNull ContactModel[] cm = new ContactModel[1];
        cursorHelper.current((CursorHelper.Callback) cursorHelper1 -> {
            ContactModel contactModel = ContactModel.createUnchecked(
                cursorHelper1.getString(ContactModel.COLUMN_IDENTITY),
                cursorHelper1.getBlob(ContactModel.COLUMN_PUBLIC_KEY)
            );

            contactModel
                .setName(
                    cursorHelper1.getString(ContactModel.COLUMN_FIRST_NAME),
                    cursorHelper1.getString(ContactModel.COLUMN_LAST_NAME)
                )
                .setPublicNickName(cursorHelper1.getString(ContactModel.COLUMN_PUBLIC_NICK_NAME))
                .setState(IdentityState.valueOf(cursorHelper1.getString(ContactModel.COLUMN_STATE)))
                .setAndroidContactLookupKey(cursorHelper1.getString(ContactModel.COLUMN_ANDROID_CONTACT_LOOKUP_KEY))
                .setIsWork(cursorHelper1.getInt(ContactModel.COLUMN_IS_WORK) == 1)
                .setIdentityType(
                    cursorHelper1.getInt(ContactModel.COLUMN_TYPE) == 1
                        ? IdentityType.WORK
                        : IdentityType.NORMAL
                )
                .setFeatureMask(cursorHelper1.getLong(ContactModel.COLUMN_FEATURE_MASK))
                .setIdColorIndex(cursorHelper1.getInt(ContactModel.COLUMN_ID_COLOR_INDEX))
                .setAcquaintanceLevel(
                    cursorHelper1.getInt(ContactModel.COLUMN_ACQUAINTANCE_LEVEL) == 1
                        ? AcquaintanceLevel.GROUP
                        : AcquaintanceLevel.DIRECT
                )
                .setLocalAvatarExpires(cursorHelper1.getDate(ContactModel.COLUMN_LOCAL_AVATAR_EXPIRES))
                .setProfilePicBlobID(cursorHelper1.getBlob(ContactModel.COLUMN_PROFILE_PIC_BLOB_ID))
                .setDateCreated(cursorHelper1.getDate(ContactModel.COLUMN_CREATED_AT))
                .setLastUpdate(cursorHelper1.getDate(ContactModel.COLUMN_LAST_UPDATE))
                .setIsRestored(cursorHelper1.getInt(ContactModel.COLUMN_IS_RESTORED) == 1)
                .setArchived(cursorHelper1.getInt(ContactModel.COLUMN_IS_ARCHIVED) == 1)
                .setReadReceipts(cursorHelper1.getInt(ContactModel.COLUMN_READ_RECEIPTS))
                .setTypingIndicators(cursorHelper1.getInt(ContactModel.COLUMN_TYPING_INDICATORS))
                .setForwardSecurityState(cursorHelper1.getInt(ContactModel.COLUMN_FORWARD_SECURITY_STATE))
                .setJobTitle(cursorHelper1.getString(ContactModel.COLUMN_JOB_TITLE))
                .setDepartment(cursorHelper1.getString(ContactModel.COLUMN_DEPARTMENT))
                .setNotificationTriggerPolicyOverride(cursorHelper1.getLong(ContactModel.COLUMN_NOTIFICATION_TRIGGER_POLICY_OVERRIDE));

            // Convert state to enum
            switch (cursorHelper1.getString(ContactModel.COLUMN_STATE)) {
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

            switch (cursorHelper1.getInt(ContactModel.COLUMN_VERIFICATION_LEVEL)) {
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

    public boolean createOrUpdate(@NonNull ContactModel contactModel) {
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
        if (contactModel.getIdentity().equals(identityProvider.getIdentityString())) {
            logger.error("Cannot add user as contact");
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
        final @Nullable Long lastUpdateTime = lastUpdate != null ? lastUpdate.getTime() : null;
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

    public int delete(@NonNull ContactModel contactModel) {
        return getWritableDatabase().delete(
            this.getTableName(),
            ContactModel.COLUMN_IDENTITY + "=?",
            new String[]{
                contactModel.getIdentity()
            }
        );
    }

    @Nullable
    private ContactModel getFirstOrNull(@Nullable String selection, @Nullable String... selectionArgs) {
        final @Nullable Cursor cursor = getReadableDatabase().query(getTableName(), null, selection, selectionArgs, null, null, null);
        try (cursor) {
            if (cursor != null && cursor.moveToFirst()) {
                return convert(new CursorHelper(cursor, getColumnIndexCache()));
            }
        } catch (Exception e) {
            logger.error("Exception", e);
        }
        return null;
    }

    public static class Creator implements DatabaseCreationProvider {

        @Override
        @NonNull
        public String [] getCreationStatements() {
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
                    "`" + ContactModel.COLUMN_WORK_LAST_FULL_SYNC_AT + "` DATETIME DEFAULT NULL," +
                    "PRIMARY KEY (`" + ContactModel.COLUMN_IDENTITY + "`) );"
            };
        }
    }
}

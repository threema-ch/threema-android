package ch.threema.app.webclient.converter;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.provider.ContactsContract;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.services.ContactService;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.ContactUtil;
import ch.threema.app.utils.NameUtil;
import ch.threema.app.webclient.exceptions.ConversionException;
import ch.threema.data.datatypes.ContactNameFormat;
import ch.threema.domain.models.IdentityState;
import ch.threema.domain.models.IdentityType;
import ch.threema.domain.protocol.ThreemaFeature;
import ch.threema.storage.models.ContactModel;

@AnyThread
public class Contact extends Converter {
    private final static String PUBLIC_NICKNAME = "publicNickname";
    private final static String VERIFICATION_LEVEL = "verificationLevel";
    private final static String STATE = "state";
    private final static String HIDDEN = "hidden";
    private final static String FEATURE_MASK = "featureMask";
    private final static String FEATURE_LEVEL = "featureLevel";
    private final static String PUBLIC_KEY = "publicKey";
    private final static String FIRST_NAME = "firstName";
    private final static String LAST_NAME = "lastName";
    private final static String SYSTEM_CONTACT = "systemContact";
    private final static String SYSTEM_CONTACT_EMAILS = "emails";
    private final static String SYSTEM_CONTACT_EMAIL = "address";
    private final static String SYSTEM_CONTACT_LABEL = "label";
    private final static String SYSTEM_CONTACT_PHONE_NUMBERS = "phoneNumbers";
    private final static String SYSTEM_CONTACT_PHONE_NUMBER = "number";
    private final static String IS_WORK = "isWork";
    private final static String IDENTITY_TYPE = "identityType";
    private final static String IS_BLOCKED = "isBlocked";

    private final static String CAN_CHANGE_AVATAR = "canChangeAvatar";
    private final static String CAN_CHANGE_FIRST_NAME = "canChangeFirstName";
    private final static String CAN_CHANGE_LAST_NAME = "canChangeLastName";

    /**
     * Converts multiple contact models to MsgpackBuilder instances.
     */
    @NonNull
    public static List<MsgpackBuilder> convert(
        @NonNull List<ContactModel> contacts,
        @NonNull ContactNameFormat contactNameFormat
    ) throws ConversionException {
        List<MsgpackBuilder> list = new ArrayList<>();
        for (ContactModel contact : contacts) {
            list.add(convert(contact, contactNameFormat));
        }
        return list;
    }

    /**
     * Converts a contact model to a MsgpackObjectBuilder.
     */
    @NonNull
    public static MsgpackObjectBuilder convert(
        @NonNull ContactModel contact,
        @NonNull ContactNameFormat contactNameFormat
    ) throws ConversionException {
        MsgpackObjectBuilder builder = new MsgpackObjectBuilder();
        try {
            builder.put(Receiver.ID, getId(contact));
            builder.put(Receiver.DISPLAY_NAME, getName(contact, contactNameFormat));
            builder.put(Receiver.COLOR, getColor(contact));
            builder.maybePut(FIRST_NAME, Utils.nullIfEmpty(contact.getFirstName()));
            builder.maybePut(LAST_NAME, Utils.nullIfEmpty(contact.getLastName()));
            builder.maybePut(PUBLIC_NICKNAME, Utils.nullIfEmpty(contact.getPublicNickName()));
            builder.put(VERIFICATION_LEVEL, VerificationLevel.convert(contact.verificationLevel));
            builder.put(STATE, contact.getState().toString());
            builder.put(HIDDEN, contact.isHidden());
            builder.maybePut(IS_WORK, ConfigUtils.isWorkBuild() && contact.isWorkVerified());
            builder.put(PUBLIC_KEY, contact.getPublicKey());
            builder.put(IDENTITY_TYPE, contact.getIdentityType() == IdentityType.WORK ? 1 : 0);
            builder.put(IS_BLOCKED, getBlockedContactsService().isBlocked(contact.getIdentity()));

            final long featureMask = contact.getFeatureMask();
            builder.put(FEATURE_MASK, featureMask);
            // TODO(ANDR-2708): Remove
            builder.put(FEATURE_LEVEL, ThreemaFeature.featureMaskToLevel(featureMask));

            boolean isPrivateChat = getConversationCategoryService().isPrivateChat(ContactUtil.getUniqueIdString(contact.getIdentity()));
            builder.put(Receiver.LOCKED, isPrivateChat);
            builder.put(Receiver.VISIBLE, !isPrivateChat || !getPreferenceService().arePrivateChatsHidden());

            //define access
            builder.put(Receiver.ACCESS, (new MsgpackObjectBuilder())
                .put(Receiver.CAN_DELETE, getContactService().getAccess(contact.getIdentity()).canDelete())
                .put(CAN_CHANGE_AVATAR, ContactUtil.canChangeAvatar(contact, getPreferenceService(), getFileService()))
                .put(CAN_CHANGE_FIRST_NAME, ContactUtil.canChangeFirstName(contact))
                .put(CAN_CHANGE_LAST_NAME, ContactUtil.canChangeLastName(contact)));
        } catch (NullPointerException e) {
            throw new ConversionException(e);
        }
        return builder;
    }

    public static MsgpackObjectBuilder convertDetails(ContactModel contact) throws ConversionException {
        final MsgpackObjectBuilder builder = new MsgpackObjectBuilder();
        final MsgpackArrayBuilder phoneNumberBuilder = new MsgpackArrayBuilder();
        final MsgpackArrayBuilder emailBuilder = new MsgpackArrayBuilder();

        if (contact.isLinkedToAndroidContact()) {
            if (ContextCompat.checkSelfPermission(getAppContext(), Manifest.permission.READ_CONTACTS)
                == PackageManager.PERMISSION_GRANTED) {
                final String lookupKey = contact.getAndroidContactLookupKey();

                // Get phone details
                {
                    final String[] projection = {
                        ContactsContract.CommonDataKinds.Phone.NORMALIZED_NUMBER,
                        ContactsContract.CommonDataKinds.Phone.TYPE,
                        ContactsContract.CommonDataKinds.Phone.LABEL};
                    final String selection = ContactsContract.Data.LOOKUP_KEY + "=?" + " AND "
                        + ContactsContract.Data.MIMETYPE + "='" + ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE + "'";
                    final String[] selectionArgs = new String[]{String.valueOf(lookupKey)};
                    final Cursor cursor = getAppContext().getContentResolver()
                        .query(ContactsContract.Data.CONTENT_URI, projection, selection, selectionArgs, null);
                    if (cursor != null) {
                        while (cursor.moveToNext()) {
                            // Determine phone number
                            final String phoneNumber = cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NORMALIZED_NUMBER));

                            // Determine label
                            int type = cursor.getInt(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.TYPE));
                            String label;
                            if (type == ContactsContract.CommonDataKinds.BaseTypes.TYPE_CUSTOM) {
                                label = cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.LABEL));
                            } else {
                                label = (String) ContactsContract.CommonDataKinds.Phone.getTypeLabel(getAppContext().getResources(), type, "");
                            }

                            phoneNumberBuilder.put((new MsgpackObjectBuilder())
                                .put(SYSTEM_CONTACT_LABEL, label)
                                .put(SYSTEM_CONTACT_PHONE_NUMBER, phoneNumber));
                        }
                        cursor.close();
                    }
                }

                // Get e-mail details
                {
                    final String[] projection = new String[]{
                        ContactsContract.CommonDataKinds.Email.ADDRESS,
                        ContactsContract.CommonDataKinds.Email.TYPE,
                        ContactsContract.CommonDataKinds.Email.LABEL};
                    final String selection = ContactsContract.Data.LOOKUP_KEY + "=?" + " AND "
                        + ContactsContract.Data.MIMETYPE + "='" + ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE + "'";
                    final String[] selectionArgs = new String[]{String.valueOf(lookupKey)};
                    final Cursor cursor = getAppContext().getContentResolver()
                        .query(ContactsContract.Data.CONTENT_URI, projection, selection, selectionArgs, null);

                    if (cursor != null) {
                        while (cursor.moveToNext()) {
                            String email = cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Email.ADDRESS));

                            int type = cursor.getInt(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Email.TYPE));
                            String label;

                            if (type == ContactsContract.CommonDataKinds.BaseTypes.TYPE_CUSTOM) {
                                label = cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Email.LABEL));
                            } else {
                                label = (String) ContactsContract.CommonDataKinds.Email.getTypeLabel(getAppContext().getResources(), type, "");
                            }

                            emailBuilder.put((new MsgpackObjectBuilder())
                                .put(SYSTEM_CONTACT_LABEL, label)
                                .put(SYSTEM_CONTACT_EMAIL, email));
                        }
                        cursor.close();
                    }
                }
            }
        }

        //append system contact information
        builder.put(SYSTEM_CONTACT, (new MsgpackObjectBuilder())
            .put(SYSTEM_CONTACT_PHONE_NUMBERS, phoneNumberBuilder)
            .put(SYSTEM_CONTACT_EMAILS, emailBuilder));

        return builder;
    }

    public static MsgpackObjectBuilder getArguments(ContactModel contact) throws ConversionException {
        MsgpackObjectBuilder args = new MsgpackObjectBuilder();
        args.put(Receiver.ID, getId(contact));
        return args;
    }

    public static String getId(ContactModel contact) throws ConversionException {
        try {
            return contact.getIdentity();
        } catch (NullPointerException e) {
            throw new ConversionException(e);
        }
    }

    @NonNull
    public static String getName(ContactModel contact, @NonNull ContactNameFormat contactNameFormat) throws ConversionException {
        try {
            return NameUtil.getContactDisplayNameOrNickname(contact, true, contactNameFormat);
        } catch (NullPointerException e) {
            throw new ConversionException(e);
        }
    }

    @NonNull
    public static String getColor(ContactModel contact) throws ConversionException {
        try {
            int idColor = contact.getIdColor().getColorLight();
            return String.format("#%06X", (0xFFFFFF & idColor));
        } catch (NullPointerException e) {
            throw new ConversionException(e);
        }
    }

    /**
     * Return the filter used to query contacts from the contact service.
     */
    @NonNull
    public static ContactService.Filter getContactFilter() {
        return new ContactService.Filter() {
            @Override
            public IdentityState[] states() {
                return new IdentityState[]{
                    IdentityState.ACTIVE,
                    IdentityState.INACTIVE,
                    IdentityState.INVALID,
                };
            }

            @Override
            public Boolean fetchMissingFeatureLevel() {
                return null;
            }

            @Override
            public Boolean includeMyself() {
                return false;
            }

            @Override
            public Boolean includeHidden() {
                return true;
            }
        };
    }

    @NonNull
    private static Context getAppContext() {
        return ThreemaApplication.getAppContext();
    }

}

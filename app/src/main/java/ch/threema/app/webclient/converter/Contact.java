/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2016-2025 Threema GmbH
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

package ch.threema.app.webclient.converter;

import android.Manifest;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.Build;
import android.provider.ContactsContract;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import ch.threema.app.services.ContactService;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.ContactUtil;
import ch.threema.app.utils.NameUtil;
import ch.threema.app.webclient.exceptions.ConversionException;
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
    public static List<MsgpackBuilder> convert(List<ContactModel> contacts) throws ConversionException {
        List<MsgpackBuilder> list = new ArrayList<>();
        for (ContactModel contact : contacts) {
            list.add(convert(contact));
        }
        return list;
    }

    /**
     * Converts a contact model to a MsgpackObjectBuilder.
     */
    public static MsgpackObjectBuilder convert(ContactModel contact) throws ConversionException {
        MsgpackObjectBuilder builder = new MsgpackObjectBuilder();
        try {
            builder.put(Receiver.ID, getId(contact));
            builder.put(Receiver.DISPLAY_NAME, getName(contact));
            builder.put(Receiver.COLOR, getColor(contact));
            builder.maybePut(FIRST_NAME, Utils.nullIfEmpty(contact.getFirstName()));
            builder.maybePut(LAST_NAME, Utils.nullIfEmpty(contact.getLastName()));
            builder.maybePut(PUBLIC_NICKNAME, Utils.nullIfEmpty(contact.getPublicNickName()));
            builder.put(VERIFICATION_LEVEL, VerificationLevel.convert(contact.verificationLevel));
            builder.put(STATE, contact.getState().toString());
            builder.put(HIDDEN, contact.isHidden());
            builder.maybePut(IS_WORK, ConfigUtils.isWorkBuild() && contact.isWork());
            builder.put(PUBLIC_KEY, contact.getPublicKey());
            builder.put(IDENTITY_TYPE, contact.getIdentityType() == IdentityType.WORK ? 1 : 0);
            builder.put(IS_BLOCKED, getBlockedContactsService().isBlocked(contact.getIdentity()));

            final long featureMask = contact.getFeatureMask();
            builder.put(FEATURE_MASK, featureMask);
            // TODO(ANDR-2708): Remove
            builder.put(FEATURE_LEVEL, ThreemaFeature.featureMaskToLevel(featureMask));

            boolean isSecretChat = getHiddenChatListService().has(ContactUtil.getUniqueIdString(contact.getIdentity()));
            builder.put(Receiver.LOCKED, isSecretChat);
            builder.put(Receiver.VISIBLE, !isSecretChat || !getPreferenceService().isPrivateChatsHidden());

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
            //if android is older than version M or read contacts permission granted
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M
                || ContextCompat.checkSelfPermission(getContext(), Manifest.permission.READ_CONTACTS)
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
                    final Cursor cursor = getContext().getContentResolver()
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
                                label = (String) ContactsContract.CommonDataKinds.Phone.getTypeLabel(getContext().getResources(), type, "");
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
                    final Cursor cursor = getContext().getContentResolver()
                        .query(ContactsContract.Data.CONTENT_URI, projection, selection, selectionArgs, null);

                    if (cursor != null) {
                        while (cursor.moveToNext()) {
                            String email = cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Email.ADDRESS));

                            int type = cursor.getInt(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Email.TYPE));
                            String label;

                            if (type == ContactsContract.CommonDataKinds.BaseTypes.TYPE_CUSTOM) {
                                label = cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Email.LABEL));
                            } else {
                                label = (String) ContactsContract.CommonDataKinds.Email.getTypeLabel(getContext().getResources(), type, "");
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
    public static String getName(ContactModel contact) throws ConversionException {
        try {
            return NameUtil.getDisplayNameOrNickname(contact, true);
        } catch (NullPointerException e) {
            throw new ConversionException(e);
        }
    }

    @NonNull
    public static String getColor(ContactModel contact) throws ConversionException {
        try {
            return String.format("#%06X", (0xFFFFFF & contact.getColorLight()));
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
            public Long requiredFeature() {
                return null;
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

            @Override
            public Boolean onlyWithReceiptSettings() {
                return false;
            }
        };
    }

}

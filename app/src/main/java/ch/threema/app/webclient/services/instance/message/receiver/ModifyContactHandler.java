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

package ch.threema.app.webclient.services.instance.message.receiver;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import org.msgpack.core.MessagePackException;
import org.msgpack.value.Value;
import org.slf4j.Logger;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Map;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringDef;
import androidx.annotation.WorkerThread;
import ch.threema.app.services.ContactService;
import ch.threema.app.utils.BitmapUtil;
import ch.threema.app.utils.ContactUtil;
import ch.threema.app.webclient.Protocol;
import ch.threema.app.webclient.converter.Contact;
import ch.threema.app.webclient.converter.MsgpackObjectBuilder;
import ch.threema.app.webclient.exceptions.ConversionException;
import ch.threema.app.webclient.services.instance.MessageDispatcher;
import ch.threema.app.webclient.services.instance.MessageReceiver;
import static ch.threema.base.utils.LoggingKt.getThreemaLogger;
import ch.threema.data.models.ContactModel;
import ch.threema.data.models.ContactModelData;
import ch.threema.data.repositories.ContactModelRepository;
import ch.threema.domain.protocol.csp.ProtocolDefines;
import ch.threema.domain.taskmanager.TriggerSource;
import ch.threema.storage.models.ContactModel.AcquaintanceLevel;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Process update/contact requests from the browser.
 */
@WorkerThread
public class ModifyContactHandler extends MessageReceiver {
    private static final Logger logger = getThreemaLogger("ModifyContactHandler");

    private static final String FIELD_FIRST_NAME = "firstName";
    private static final String FIELD_LAST_NAME = "lastName";

    @Retention(RetentionPolicy.SOURCE)
    @StringDef({
        Protocol.ERROR_INVALID_CONTACT,
        Protocol.ERROR_NOT_ALLOWED_LINKED,
        Protocol.ERROR_NOT_ALLOWED_BUSINESS,
        Protocol.ERROR_VALUE_TOO_LONG,
        Protocol.ERROR_INTERNAL,
    })
    private @interface ErrorCode {
    }

    @NonNull
    private final MessageDispatcher dispatcher;
    @NonNull
    private final ContactService contactService;
    @NonNull
    private final ContactModelRepository contactModelRepository;

    @AnyThread
    public ModifyContactHandler(
        @NonNull MessageDispatcher dispatcher,
        @NonNull ContactService contactService,
        @NonNull ContactModelRepository contactModelRepository
    ) {
        super(Protocol.SUB_TYPE_CONTACT);
        this.dispatcher = dispatcher;
        this.contactService = contactService;
        this.contactModelRepository = contactModelRepository;
    }

    @Override
    protected void receive(Map<String, Value> message) throws MessagePackException {
        logger.debug("Received update contact message");

        // Process args
        final Map<String, Value> args = this.getArguments(message, false);
        if (!args.containsKey(Protocol.ARGUMENT_TEMPORARY_ID)
            || !args.containsKey(Protocol.ARGUMENT_IDENTITY)) {
            logger.error("Invalid contact update request, identity or temporaryId not set");
            return;
        }
        final String identity = args.get(Protocol.ARGUMENT_IDENTITY).asStringValue().toString();
        final String temporaryId = args.get(Protocol.ARGUMENT_TEMPORARY_ID).asStringValue().toString();

        // Validate identity
        final ContactModel contactModel = contactModelRepository.getByIdentity(identity);
        final ContactModelData contactModelData = contactModel != null ? contactModel.getData() : null;
        if (contactModelData == null) {
            this.failed(identity, temporaryId, Protocol.ERROR_INVALID_CONTACT);
            return;
        }
        if (contactModelData.isLinkedToAndroidContact()) {
            this.failed(identity, temporaryId, Protocol.ERROR_NOT_ALLOWED_LINKED);
            return;
        }

        contactModel.setAcquaintanceLevelFromLocal(AcquaintanceLevel.DIRECT);

        // Process data
        final Map<String, Value> data = this.getData(message, false);

        String nameErrorCode = updateNameFromData(contactModel, data);
        if (nameErrorCode != null) {
            failed(identity, temporaryId, nameErrorCode);
            return;
        }

        String profilePictureErrorCode = updateProfilePictureFromData(contactModel, data);
        if (profilePictureErrorCode != null) {
            failed(identity, temporaryId, profilePictureErrorCode);
            return;
        }

        contactService.invalidateCache(identity);

        // Return updated contact
        this.success(identity, temporaryId, contactService.getByIdentity(identity));
    }

    /**
     * Update the contact's name from the given data. If an error occurs, the error message is
     * returned. If null is returned, the name has been successfully updated or no update was
     * needed.
     */
    @Nullable
    private String updateNameFromData(@NonNull ContactModel contactModel, @NonNull Map<String, Value> data) {
        boolean firstNameChanged = data.containsKey(FIELD_FIRST_NAME);
        boolean lastNameChanged = data.containsKey(FIELD_LAST_NAME);

        String firstName;
        String lastName;

        if (firstNameChanged) {
            logger.info("First name is set");
            firstName = this.getValueString(data.get(FIELD_FIRST_NAME));
            if (firstName.getBytes(UTF_8).length > Protocol.LIMIT_BYTES_FIRST_NAME) {
                return Protocol.ERROR_VALUE_TOO_LONG;
            }
        } else {
            ContactModelData contactModelData = contactModel.getData();
            if (contactModelData == null) {
                logger.error("Cannot get existing first name as the contact model data is null");
                return Protocol.ERROR_INTERNAL;
            }
            firstName = contactModelData.firstName;
        }
        if (lastNameChanged) {
            logger.info("Last name is set");
            lastName = this.getValueString(data.get(FIELD_LAST_NAME));
            if (lastName.getBytes(UTF_8).length > Protocol.LIMIT_BYTES_LAST_NAME) {
                return Protocol.ERROR_VALUE_TOO_LONG;
            }
        } else {
            ContactModelData contactModelData = contactModel.getData();
            if (contactModelData == null) {
                logger.error("Cannot get existing last name as the contact model data is null");
                return Protocol.ERROR_INTERNAL;
            }
            lastName = contactModelData.lastName;
        }

        contactModel.setNameFromLocal(firstName, lastName);

        return null;
    }

    /**
     * Update the contact's profile picture from the given data. If an error occurs, the error
     * message is returned. If null is returned, the profile picture has been successfully updated
     * or no update was needed.
     */
    private String updateProfilePictureFromData(@NonNull ContactModel contactModel, @NonNull Map<String, Value> data) {
        if (!data.containsKey(Protocol.ARGUMENT_AVATAR)) {
            logger.info("Profile picture hasn't been changed");
            return null;
        }

        if (ContactUtil.isGatewayContact(contactModel.getIdentity())) {
            return Protocol.ERROR_NOT_ALLOWED_BUSINESS;
        }

        try {
            final Value avatarValue = data.get(Protocol.ARGUMENT_AVATAR);
            if (avatarValue == null || avatarValue.isNilValue()) {
                // Clear avatar
                this.contactService.removeUserDefinedProfilePicture(contactModel.getIdentity(), TriggerSource.LOCAL);
            } else {
                // Set avatar
                final byte[] bmp = avatarValue.asBinaryValue().asByteArray();
                if (bmp.length > 0) {
                    Bitmap avatar = BitmapFactory.decodeByteArray(bmp, 0, bmp.length);
                    // Resize to max allowed size
                    avatar = BitmapUtil.resizeBitmap(avatar,
                        ProtocolDefines.PROFILE_PICTURE_WIDTH_PX,
                        ProtocolDefines.PROFILE_PICTURE_HEIGHT_PX);
                    boolean success = this.contactService.setUserDefinedProfilePicture(
                        contactModel.getIdentity(),
                        // Without quality loss
                        BitmapUtil.bitmapToByteArray(avatar, Bitmap.CompressFormat.PNG,
                            100),
                        TriggerSource.LOCAL
                    );
                    if (!success) {
                        logger.error("Failed to set profile picture");
                        return Protocol.ERROR_INTERNAL;
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Error while setting profile picture", e);
            return Protocol.ERROR_INTERNAL;
        }

        return null;
    }

    /**
     * Respond with the modified contact model.
     */
    private void success(String threemaId, String temporaryId, ch.threema.storage.models.ContactModel contact) {
        logger.debug("Respond modify contact success");
        try {
            this.send(this.dispatcher,
                new MsgpackObjectBuilder()
                    .put(Protocol.SUB_TYPE_RECEIVER, Contact.convert(contact)),
                new MsgpackObjectBuilder()
                    .put(Protocol.ARGUMENT_SUCCESS, true)
                    .put(Protocol.ARGUMENT_IDENTITY, threemaId)
                    .put(Protocol.ARGUMENT_TEMPORARY_ID, temporaryId)
            );
        } catch (ConversionException e) {
            logger.error("Exception", e);
        }
    }

    /**
     * Respond with an error code.
     */
    private void failed(String threemaId, String temporaryId, @ErrorCode String errorCode) {
        logger.warn("Respond modify contact failed ({})", errorCode);
        this.send(this.dispatcher,
            new MsgpackObjectBuilder()
                .putNull(Protocol.SUB_TYPE_RECEIVER),
            new MsgpackObjectBuilder()
                .put(Protocol.ARGUMENT_SUCCESS, false)
                .put(Protocol.ARGUMENT_ERROR, errorCode)
                .put(Protocol.ARGUMENT_IDENTITY, threemaId)
                .put(Protocol.ARGUMENT_TEMPORARY_ID, temporaryId)
        );
    }

    @Override
    protected boolean maybeNeedsConnection() {
        return false;
    }
}

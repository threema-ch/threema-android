/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2016-2024 Threema GmbH
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

import androidx.annotation.AnyThread;
import androidx.annotation.StringDef;
import androidx.annotation.WorkerThread;

import org.msgpack.core.MessagePackException;
import org.msgpack.value.Value;
import org.slf4j.Logger;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import ch.threema.app.dialogs.ContactEditDialog;
import ch.threema.app.services.GroupService;
import ch.threema.app.utils.BitmapUtil;
import ch.threema.app.webclient.Protocol;
import ch.threema.app.webclient.converter.Group;
import ch.threema.app.webclient.converter.MsgpackObjectBuilder;
import ch.threema.app.webclient.converter.Receiver;
import ch.threema.app.webclient.exceptions.ConversionException;
import ch.threema.app.webclient.services.instance.MessageDispatcher;
import ch.threema.app.webclient.services.instance.MessageReceiver;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.storage.models.GroupModel;

@WorkerThread
public class ModifyGroupHandler extends MessageReceiver {
    private static final Logger logger = LoggingUtil.getThreemaLogger("ModifyGroupHandler");

    private final MessageDispatcher dispatcher;
    private final GroupService groupService;

    @Retention(RetentionPolicy.SOURCE)
    @StringDef({
        Protocol.ERROR_INVALID_GROUP,
        Protocol.ERROR_NOT_ALLOWED,
        Protocol.ERROR_NO_MEMBERS,
        Protocol.ERROR_BAD_REQUEST,
        Protocol.ERROR_VALUE_TOO_LONG,
        Protocol.ERROR_INTERNAL,
    })
    private @interface ErrorCode {
    }

    @WorkerThread
    public interface Listener {
        void onReceive();
    }

    @AnyThread
    public ModifyGroupHandler(MessageDispatcher dispatcher,
                              GroupService groupService) {
        super(Protocol.SUB_TYPE_GROUP);
        this.dispatcher = dispatcher;
        this.groupService = groupService;
    }

    @Override
    protected void receive(Map<String, Value> message) throws MessagePackException {
        logger.debug("Received update group message");
        final Map<String, Value> args = this.getArguments(message, false, new String[]{
            Protocol.ARGUMENT_TEMPORARY_ID,
        });
        final String temporaryId = args.get(Protocol.ARGUMENT_TEMPORARY_ID).asStringValue().toString();

        // Get args
        if (!args.containsKey(Receiver.ID)) {
            logger.error("Invalid group update request, id not set");
            this.failed(temporaryId, Protocol.ERROR_BAD_REQUEST);
            return;
        }
        final Integer groupId = Integer.parseInt(args.get(Receiver.ID).asStringValue().toString());

        // Get group
        final GroupModel groupModel = this.groupService.getById(groupId);
        if (groupModel == null) {
            this.failed(temporaryId, Protocol.ERROR_INVALID_GROUP);
            return;
        }
        if (!this.groupService.isGroupCreator(groupModel)) {
            logger.error("Not allowed to change a foreign group");
            this.failed(temporaryId, Protocol.ERROR_NOT_ALLOWED);
            return;
        }

        // Process data
        final Map<String, Value> data = this.getData(message, false);
        if (!data.containsKey(Protocol.ARGUMENT_MEMBERS)) {
            this.failed(temporaryId, Protocol.ERROR_NO_MEMBERS);
            return;
        }

        // Update members
        final List<Value> members = data.get(Protocol.ARGUMENT_MEMBERS).asArrayValue().list();
        final String[] identities = new String[members.size()];
        for (int n = 0; n < members.size(); n++) {
            identities[n] = members.get(n).asStringValue().toString();
        }

        // Update name
        String name = groupModel.getName();
        if (data.containsKey(Protocol.ARGUMENT_NAME)) {
            name = this.getValueString(data.get(Protocol.ARGUMENT_NAME));
            if (name.getBytes(StandardCharsets.UTF_8).length > Protocol.LIMIT_BYTES_GROUP_NAME) {
                this.failed(temporaryId, Protocol.ERROR_VALUE_TOO_LONG);
                return;
            }
        }

        // Update avatar
        Bitmap avatar = null;
        if (data.containsKey(Protocol.ARGUMENT_AVATAR)) {
            try {
                final Value avatarValue = data.get(Protocol.ARGUMENT_AVATAR);
                if (avatarValue != null && !avatarValue.isNilValue()) {
                    // Set avatar
                    final byte[] bmp = avatarValue.asBinaryValue().asByteArray();
                    if (bmp.length > 0) {
                        final Bitmap tmpAvatar = BitmapFactory.decodeByteArray(bmp, 0, bmp.length);
                        // Resize to max allowed size
                        avatar = BitmapUtil.resizeBitmap(tmpAvatar, ContactEditDialog.CONTACT_AVATAR_WIDTH_PX,
                            ContactEditDialog.CONTACT_AVATAR_HEIGHT_PX);
                    }
                }
            } catch (Exception e) {
                logger.error("Failed to save avatar", e);
                this.failed(temporaryId, Protocol.ERROR_INTERNAL);
                return;
            }
        }

        // Save changes
        try {
            this.groupService.updateGroup(groupModel, name, null, identities, avatar, false);
            this.success(temporaryId, groupModel);
        } catch (Exception e1) {
            this.failed(temporaryId, Protocol.ERROR_INTERNAL);
        }
    }

    /**
     * Respond with the modified group model.
     */
    private void success(String temporaryId, GroupModel group) {
        logger.debug("Respond modify group success");
        try {
            this.send(this.dispatcher,
                new MsgpackObjectBuilder()
                    .put(Protocol.SUB_TYPE_RECEIVER, Group.convert(group)),
                new MsgpackObjectBuilder()
                    .put(Protocol.ARGUMENT_SUCCESS, true)
                    .put(Protocol.ARGUMENT_TEMPORARY_ID, temporaryId)
            );
        } catch (ConversionException e) {
            logger.error("Exception", e);
        }
    }

    /**
     * Respond with an error code.
     */
    private void failed(String temporaryId, @ErrorCode String errorCode) {
        logger.warn("Respond modify group failed ({})", errorCode);
        this.send(this.dispatcher,
            new MsgpackObjectBuilder()
                .putNull(Protocol.SUB_TYPE_RECEIVER),
            new MsgpackObjectBuilder()
                .put(Protocol.ARGUMENT_SUCCESS, false)
                .put(Protocol.ARGUMENT_ERROR, errorCode)
                .put(Protocol.ARGUMENT_TEMPORARY_ID, temporaryId)
        );
    }

    @Override
    protected boolean maybeNeedsConnection() {
        return true;
    }
}

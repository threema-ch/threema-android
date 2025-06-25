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

import org.msgpack.core.MessagePackException;
import org.msgpack.value.Value;
import org.slf4j.Logger;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.StringDef;
import androidx.annotation.WorkerThread;
import ch.threema.app.groupflows.GroupChanges;
import ch.threema.app.groupflows.GroupFlowResult;
import ch.threema.app.protocol.ProfilePictureChange;
import ch.threema.app.protocol.SetProfilePicture;
import ch.threema.app.services.GroupFlowDispatcher;
import ch.threema.app.services.GroupService;
import ch.threema.app.services.UserService;
import ch.threema.app.utils.RuntimeUtil;
import ch.threema.app.webclient.Protocol;
import ch.threema.app.webclient.converter.Group;
import ch.threema.app.webclient.converter.MsgpackObjectBuilder;
import ch.threema.app.webclient.converter.Receiver;
import ch.threema.app.webclient.exceptions.ConversionException;
import ch.threema.app.webclient.services.instance.MessageDispatcher;
import ch.threema.app.webclient.services.instance.MessageReceiver;
import ch.threema.base.utils.CoroutinesExtensionKt;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.data.models.GroupModel;
import ch.threema.data.models.GroupModelData;
import ch.threema.data.repositories.GroupModelRepository;
import kotlin.Unit;
import kotlinx.coroutines.Deferred;

@WorkerThread
public class ModifyGroupHandler extends MessageReceiver {
    private static final Logger logger = LoggingUtil.getThreemaLogger("ModifyGroupHandler");

    private final @NonNull MessageDispatcher dispatcher;
    private final @NonNull UserService userService;
    private final @NonNull GroupFlowDispatcher groupFlowDispatcher;
    private final @NonNull GroupModelRepository groupModelRepository;
    private final @NonNull GroupService groupService;

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
    public ModifyGroupHandler(
        @NonNull MessageDispatcher dispatcher,
        @NonNull UserService userService,
        @NonNull GroupFlowDispatcher groupFlowDispatcher,
        @NonNull GroupModelRepository groupModelRepository,
        @NonNull GroupService groupService
    ) {
        super(Protocol.SUB_TYPE_GROUP);
        this.dispatcher = dispatcher;
        this.userService = userService;
        this.groupFlowDispatcher = groupFlowDispatcher;
        this.groupModelRepository = groupModelRepository;
        this.groupService = groupService;
    }

    @Override
    protected void receive(Map<String, Value> message) throws MessagePackException {
        logger.info("Received update group message");
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
        final GroupModel groupModel = this.groupModelRepository.getByLocalGroupDbId(groupId);
        if (groupModel == null) {
            this.failed(temporaryId, Protocol.ERROR_INVALID_GROUP);
            return;
        }
        final GroupModelData groupModelData = groupModel.getData().getValue();
        if (groupModelData == null) {
            logger.error("Group model data is null");
            this.failed("Group model data is null", Protocol.ERROR_INVALID_GROUP);
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
        String myIdentity = userService.getIdentity();
        final Set<String> updatedIdentities = new HashSet<>();
        for (Value member : members) {
            String identity = member.asStringValue().toString();
            // Add all provided new members except the user's identity
            if (!myIdentity.equals(identity)) {
                updatedIdentities.add(identity);
            }
        }

        // Update name
        String name = groupModelData.name;
        if (data.containsKey(Protocol.ARGUMENT_NAME)) {
            name = this.getValueString(data.get(Protocol.ARGUMENT_NAME));
            if (name.getBytes(StandardCharsets.UTF_8).length > Protocol.LIMIT_BYTES_GROUP_NAME) {
                this.failed(temporaryId, Protocol.ERROR_VALUE_TOO_LONG);
                return;
            }
        }

        // Update avatar (note that it is not possible to remove the avatar via webclient)
        ProfilePictureChange profilePictureChange = null;
        if (data.containsKey(Protocol.ARGUMENT_AVATAR)) {
            try {
                final Value avatarValue = data.get(Protocol.ARGUMENT_AVATAR);
                if (avatarValue != null && !avatarValue.isNilValue()) {
                    // Set avatar
                    byte[] avatar = avatarValue.asBinaryValue().asByteArray();
                    profilePictureChange = new SetProfilePicture(avatar, null);
                }
            } catch (Exception e) {
                logger.error("Failed to save avatar", e);
                this.failed(temporaryId, Protocol.ERROR_INTERNAL);
                return;
            }
        }

        // Save changes
        try {
            Deferred<GroupFlowResult> groupFlowResultDeferred = groupFlowDispatcher.runUpdateGroupFlow(
                new GroupChanges(
                    name,
                    profilePictureChange,
                    updatedIdentities,
                    groupModelData
                ),
                groupModel
            );

            CoroutinesExtensionKt.onCompleted(
                groupFlowResultDeferred,
                exception -> {
                    logger.error("The update-group-flow failed exceptionally", exception);
                    RuntimeUtil.runOnWorkerThread(() -> failed(temporaryId, Protocol.ERROR_INTERNAL));
                    return Unit.INSTANCE;
                },
                groupFlowResult -> {
                    RuntimeUtil.runOnWorkerThread(() -> {
                        if (groupFlowResult instanceof GroupFlowResult.Success) {
                            success(temporaryId, groupModel);
                        } else if (groupFlowResult instanceof GroupFlowResult.Failure) {
                            failed(temporaryId, Protocol.ERROR_INTERNAL);
                        }
                    });
                    return Unit.INSTANCE;
                }
            );


        } catch (Exception e1) {
            this.failed(temporaryId, Protocol.ERROR_INTERNAL);
        }
    }

    /**
     * Respond with the modified group model.
     */
    private void success(String temporaryId, @NonNull GroupModel groupModel) {
        logger.debug("Respond modify group success");

        ch.threema.storage.models.GroupModel oldGroupModel =
            groupService.getByGroupIdentity(groupModel.getGroupIdentity());

        if (oldGroupModel == null) {
            logger.error("Old group model is null");
            return;
        }

        try {
            this.send(this.dispatcher,
                new MsgpackObjectBuilder()
                    .put(Protocol.SUB_TYPE_RECEIVER, Group.convert(oldGroupModel)),
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

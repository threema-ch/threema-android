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
import androidx.annotation.Nullable;
import androidx.annotation.StringDef;
import androidx.annotation.WorkerThread;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.groupflows.GroupFlowResult;
import ch.threema.app.groupflows.GroupCreateProperties;
import ch.threema.app.groupflows.ProfilePicture;
import ch.threema.app.services.GroupFlowDispatcher;
import ch.threema.app.services.GroupService;
import ch.threema.app.utils.RuntimeUtil;
import ch.threema.app.webclient.Protocol;
import ch.threema.app.webclient.converter.Group;
import ch.threema.app.webclient.converter.MsgpackObjectBuilder;
import ch.threema.app.webclient.exceptions.ConversionException;
import ch.threema.app.webclient.services.instance.MessageDispatcher;
import ch.threema.app.webclient.services.instance.MessageReceiver;
import ch.threema.base.utils.CoroutinesExtensionKt;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.storage.models.GroupModel;
import kotlin.Unit;
import kotlinx.coroutines.Deferred;

@WorkerThread
public class CreateGroupHandler extends MessageReceiver {
    private static final Logger logger = LoggingUtil.getThreemaLogger("CreateGroupHandler");

    private final MessageDispatcher dispatcher;
    private final GroupFlowDispatcher groupFlowDispatcher;
    private final GroupService groupService;

    @Retention(RetentionPolicy.SOURCE)
    @StringDef({
        Protocol.ERROR_DISABLED_BY_POLICY,
        Protocol.ERROR_BAD_REQUEST,
        Protocol.ERROR_VALUE_TOO_LONG,
        Protocol.ERROR_INTERNAL,
    })
    private @interface ErrorCode {
    }

    @AnyThread
    public CreateGroupHandler(
        MessageDispatcher dispatcher,
        GroupFlowDispatcher groupFlowDispatcher,
        GroupService groupService
    ) {
        super(Protocol.SUB_TYPE_GROUP);
        this.dispatcher = dispatcher;
        this.groupFlowDispatcher = groupFlowDispatcher;
        this.groupService = groupService;
    }

    @Override
    protected void receive(Map<String, Value> message) throws MessagePackException {
        logger.debug("Received create group create");
        final Map<String, Value> args = this.getArguments(message, false, new String[]{
            Protocol.ARGUMENT_TEMPORARY_ID,
        });
        final Map<String, Value> data = this.getData(message, false);

        final String temporaryId = args.get(Protocol.ARGUMENT_TEMPORARY_ID).asStringValue().toString();

        // Parse members
        if (!data.containsKey(Protocol.ARGUMENT_MEMBERS)) {
            logger.error("Invalid request, members not set");
            this.failed(temporaryId, Protocol.ERROR_BAD_REQUEST);
            return;
        }

        final List<Value> members = data.get(Protocol.ARGUMENT_MEMBERS).asArrayValue().list();
        final Set<String> identities = new HashSet<>();
        for (Value member : members) {
            identities.add(member.asStringValue().toString());
        }

        // Parse group name
        String name = null;
        if (data.containsKey(Protocol.ARGUMENT_NAME) && !data.get(Protocol.ARGUMENT_NAME).isNilValue()) {
            name = data.get(Protocol.ARGUMENT_NAME).asStringValue().toString();
            if (name.getBytes(StandardCharsets.UTF_8).length > Protocol.LIMIT_BYTES_GROUP_NAME) {
                this.failed(temporaryId, Protocol.ERROR_VALUE_TOO_LONG);
                return;
            }
        }

        // Parse avatar
        byte[] avatar = null;
        Value avatarArgument = data.get(Protocol.ARGUMENT_AVATAR);
        if (avatarArgument != null && !avatarArgument.isNilValue()) {
            avatar = avatarArgument.asBinaryValue().asByteArray();
        }

        // Create group
        try {
            Deferred<GroupFlowResult> createGroupFlowResultDeferred = groupFlowDispatcher.runCreateGroupFlow(
                ThreemaApplication.getAppContext(),
                new GroupCreateProperties(
                    name != null ? name : "",
                    new ProfilePicture(avatar),
                    identities
                )
            );

            CoroutinesExtensionKt.onCompleted(
                createGroupFlowResultDeferred,
                exception -> {
                    logger.error("The create-group-flow failed exceptionally", exception);
                    RuntimeUtil.runOnWorkerThread(
                        () -> failed(temporaryId, Protocol.ERROR_INTERNAL)
                    );
                    return Unit.INSTANCE;
                },
                groupFlowResult -> {
                    RuntimeUtil.runOnWorkerThread(() -> {
                        if (groupFlowResult instanceof GroupFlowResult.Success) {
                            final @NonNull ch.threema.data.models.GroupModel createdGroupModel = ((GroupFlowResult.Success) groupFlowResult).getGroupModel();
                            final @Nullable GroupModel oldGroupModel = groupService.getByGroupIdentity(createdGroupModel.getGroupIdentity());
                            if (oldGroupModel != null) {
                                success(temporaryId, oldGroupModel);
                            } else {
                                logger.error("Could not get old group model for existing group");
                            }
                        } else if (groupFlowResult instanceof GroupFlowResult.Failure) {
                            failed(temporaryId, Protocol.ERROR_INTERNAL);
                        }
                    });
                    return Unit.INSTANCE;
                }
            );
        } catch (Exception e) {
            this.failed(temporaryId, Protocol.ERROR_INTERNAL);
        }
    }

    private void success(String temporaryId, @NonNull GroupModel group) {
        logger.debug("Respond create group success");
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

    private void failed(String temporaryId, @ErrorCode String errorCode) {
        logger.warn("Respond create group failed ({})", errorCode);
        this.send(this.dispatcher,
            (MsgpackObjectBuilder) null,
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

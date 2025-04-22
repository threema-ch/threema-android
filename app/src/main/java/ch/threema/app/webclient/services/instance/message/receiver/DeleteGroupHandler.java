/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2017-2025 Threema GmbH
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
import java.util.Map;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.StringDef;
import androidx.annotation.WorkerThread;
import ch.threema.app.groupflows.GroupDisbandIntent;
import ch.threema.app.groupflows.GroupLeaveIntent;
import ch.threema.app.services.GroupFlowDispatcher;
import ch.threema.app.webclient.Protocol;
import ch.threema.app.webclient.services.instance.MessageDispatcher;
import ch.threema.app.webclient.services.instance.MessageReceiver;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.data.models.GroupModel;
import ch.threema.data.models.GroupModelData;
import ch.threema.data.repositories.GroupModelRepository;
import kotlinx.coroutines.Deferred;

@WorkerThread
public class DeleteGroupHandler extends MessageReceiver {
    private static final Logger logger = LoggingUtil.getThreemaLogger("DeleteGroupHandler");

    private static final String TYPE_LEAVE = "leave";
    private static final String TYPE_DELETE = "delete";

    // Error codes
    @Retention(RetentionPolicy.SOURCE)
    @StringDef({
        Protocol.ERROR_INVALID_GROUP,
        Protocol.ERROR_ALREADY_LEFT,
        Protocol.ERROR_NOT_ALLOWED,
        Protocol.ERROR_BAD_REQUEST,
    })
    private @interface ErrorCode {
    }

    private @NonNull
    final MessageDispatcher responseDispatcher;
    private @NonNull
    final GroupFlowDispatcher groupFlowDispatcher;
    private @NonNull
    final GroupModelRepository groupModelRepository;
    private @NonNull
    final String myIdentity;

    @AnyThread
    public DeleteGroupHandler(
        @NonNull MessageDispatcher responseDispatcher,
        @NonNull GroupFlowDispatcher groupFlowDispatcher,
        @NonNull GroupModelRepository groupModelRepository,
        @NonNull String myIdentity
    ) {
        super(Protocol.SUB_TYPE_GROUP);
        this.responseDispatcher = responseDispatcher;
        this.groupFlowDispatcher = groupFlowDispatcher;
        this.groupModelRepository = groupModelRepository;
        this.myIdentity = myIdentity;
    }

    @Override
    protected void receive(Map<String, Value> message) throws MessagePackException {
        logger.debug("Received delete request");
        final Map<String, Value> args = this.getArguments(message, false, new String[]{
            Protocol.ARGUMENT_RECEIVER_ID,
            Protocol.ARGUMENT_DELETE_TYPE,
            Protocol.ARGUMENT_TEMPORARY_ID,
        });

        // Get temporary ID
        final String temporaryId = args.get(Protocol.ARGUMENT_TEMPORARY_ID).asStringValue().toString();

        // Load group
        final int groupId = Integer.parseInt(args.get(Protocol.ARGUMENT_RECEIVER_ID).asStringValue().toString());
        final GroupModel group = groupModelRepository.getByLocalGroupDbId(groupId);
        if (group == null) {
            logger.error("invalid group, aborting");
            this.failed(temporaryId, Protocol.ERROR_INVALID_GROUP);
            return;
        }

        final GroupModelData groupModelData = group.getData().getValue();
        if (groupModelData == null) {
            logger.error("Group model data is null");
            this.failed(temporaryId, Protocol.ERROR_INVALID_GROUP);
            return;
        }

        final boolean isLeft = !groupModelData.isMember();
        final boolean isCreator =
            groupModelData.groupIdentity.getCreatorIdentity().equals(myIdentity);

        // There are two delete types: Either we want to delete the group, or just leave it.
        switch (args.get(Protocol.ARGUMENT_DELETE_TYPE).asStringValue().toString()) {
            case TYPE_LEAVE:
                if (isLeft) {
                    logger.error("group already left");
                    this.failed(temporaryId, Protocol.ERROR_ALREADY_LEFT);
                    return;
                }
                if (isCreator) {
                    // If the group is not left and the user is the creator, then dissolve the group first
                    disbandGroup(group, temporaryId);
                } else {
                    // If the group is not left and the user is a member, then leave the group first
                    leaveGroup(group, temporaryId);
                }
                return;
            case TYPE_DELETE:
                if (!isLeft) {
                    if (isCreator) {
                        // If the group is not left and the user is the creator, then dissolve the group first
                        disbandAndRemoveGroup(group, temporaryId);
                    } else {
                        // If the group is not left and the user is a member, then leave the group first
                        leaveAndRemoveGroup(group, temporaryId);
                    }
                } else {
                    removeGroup(group, temporaryId);
                }
                break;
            default:
                logger.error("invalid delete type argument");
                this.failed(temporaryId, Protocol.ERROR_BAD_REQUEST);
        }
    }

    private void leaveGroup(@NonNull GroupModel groupModel, @NonNull String temporaryId) {
        Deferred<Boolean> result = groupFlowDispatcher.runLeaveGroupFlow(
            null,
            GroupLeaveIntent.LEAVE,
            groupModel
        );
        awaitResult(result, temporaryId);
    }

    private void disbandGroup(@NonNull GroupModel groupModel, @NonNull String temporaryId) {
        Deferred<Boolean> result = groupFlowDispatcher.runDisbandGroupFlow(
            null,
            GroupDisbandIntent.DISBAND,
            groupModel
        );
        awaitResult(result, temporaryId);
    }

    private void leaveAndRemoveGroup(@NonNull GroupModel groupModel, @NonNull String temporaryId) {
        Deferred<Boolean> result = groupFlowDispatcher.runLeaveGroupFlow(
            null,
            GroupLeaveIntent.LEAVE_AND_REMOVE,
            groupModel
        );
        awaitResult(result, temporaryId);
    }

    private void disbandAndRemoveGroup(@NonNull GroupModel groupModel, @NonNull String temporaryId) {
        Deferred<Boolean> result = groupFlowDispatcher.runDisbandGroupFlow(
            null,
            GroupDisbandIntent.DISBAND_AND_REMOVE,
            groupModel
        );
        awaitResult(result, temporaryId);
    }

    private void removeGroup(@NonNull GroupModel groupModel, @NonNull String temporaryId) {
        Deferred<Boolean> result = groupFlowDispatcher.runRemoveGroupFlow(
            null,
            groupModel
        );
        awaitResult(result, temporaryId);
    }

    private void awaitResult(@NonNull Deferred<Boolean> deferredResult, @NonNull String temporaryId) {
        deferredResult.invokeOnCompletion(throwable -> {
            Boolean result = deferredResult.getCompleted();
            if (Boolean.TRUE.equals(result)) {
                this.success(temporaryId);
            } else {
                this.failed(temporaryId, Protocol.ERROR_BAD_REQUEST);
            }
            return null;
        });
    }

    /**
     * Respond with success true.
     */
    private void success(String temporaryId) {
        logger.debug("Respond with leave group success");
        this.sendConfirmActionSuccess(this.responseDispatcher, temporaryId);
    }

    /**
     * Respond with an error code.
     */
    private void failed(String temporaryId, @ErrorCode String errorCode) {
        logger.warn("Respond with modify group failed ({})", errorCode);
        this.sendConfirmActionFailure(this.responseDispatcher, temporaryId, errorCode);
    }

    @Override
    protected boolean maybeNeedsConnection() {
        return true;
    }
}

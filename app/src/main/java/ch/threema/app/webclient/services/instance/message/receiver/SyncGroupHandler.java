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

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.StringDef;
import androidx.annotation.WorkerThread;

import org.msgpack.core.MessagePackException;
import org.msgpack.value.Value;
import org.slf4j.Logger;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Map;

import ch.threema.app.groupflows.GroupFlowResult;
import ch.threema.app.services.GroupFlowDispatcher;
import ch.threema.app.services.GroupService;
import ch.threema.app.utils.RuntimeUtil;
import ch.threema.app.webclient.Protocol;
import ch.threema.app.webclient.services.instance.MessageDispatcher;
import ch.threema.app.webclient.services.instance.MessageReceiver;
import ch.threema.base.utils.CoroutinesExtensionKt;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.data.repositories.GroupModelRepository;
import ch.threema.storage.models.GroupModel;
import kotlin.Unit;
import kotlinx.coroutines.Deferred;

@WorkerThread
public class SyncGroupHandler extends MessageReceiver {
    private static final Logger logger = LoggingUtil.getThreemaLogger("SyncGroupHandler");

    private final MessageDispatcher responseDispatcher;
    private final GroupService groupService;
    private @NonNull
    final GroupModelRepository groupModelRepository;
    private @NonNull
    final GroupFlowDispatcher groupFlowDispatcher;

    @Retention(RetentionPolicy.SOURCE)
    @StringDef({
        Protocol.ERROR_INVALID_GROUP,
        Protocol.ERROR_NOT_ALLOWED,
        Protocol.ERROR_GROUP_SYNC_FAILED,
        Protocol.ERROR_INTERNAL,
    })
    private @interface ErrorCode {
    }

    @AnyThread
    public SyncGroupHandler(
        MessageDispatcher responseDispatcher,
        GroupService groupService,
        @NonNull GroupModelRepository groupModelRepository,
        @NonNull GroupFlowDispatcher groupFlowDispatcher
    ) {
        super(Protocol.SUB_TYPE_GROUP_SYNC);
        this.responseDispatcher = responseDispatcher;
        this.groupService = groupService;
        this.groupModelRepository = groupModelRepository;
        this.groupFlowDispatcher = groupFlowDispatcher;
    }

    @Override
    protected void receive(Map<String, Value> message) throws MessagePackException {
        logger.debug("Received group sync request");

        final Map<String, Value> args = this.getArguments(message, false, new String[]{
            Protocol.ARGUMENT_RECEIVER_ID,
            Protocol.ARGUMENT_TEMPORARY_ID
        });

        final String temporaryId = args.get(Protocol.ARGUMENT_TEMPORARY_ID).asStringValue().asString();
        final String receiverId = args.get(Protocol.ARGUMENT_RECEIVER_ID).asStringValue().asString();

        final GroupModel group;
        try {
            group = this.groupService.getById(Integer.valueOf(receiverId));
        } catch (NumberFormatException x) {
            this.failed(temporaryId, Protocol.ERROR_INVALID_GROUP);
            return;
        }

        if (group == null
            // I am not the creator
            || !this.groupService.isGroupCreator(group)
            // I am not in this group (e.g. left)
            || !this.groupService.isGroupMember(group)) {
            logger.error("not allowed");
            this.failed(temporaryId, Protocol.ERROR_NOT_ALLOWED);
            return;
        }

        ch.threema.data.models.GroupModel newGroupModel = groupModelRepository.getByCreatorIdentityAndId(group.getCreatorIdentity(), group.getApiGroupId());
        if (newGroupModel == null) {
            logger.error("New group model is null");
            this.failed(temporaryId, Protocol.ERROR_INTERNAL);
            return;
        }

        Deferred<GroupFlowResult> resyncGroupFlowResultDeferred = groupFlowDispatcher.runGroupResyncFlow(newGroupModel);

        CoroutinesExtensionKt.onCompleted(
            resyncGroupFlowResultDeferred,
            exception -> {
                logger.error("resync-group-flow was completed exceptionally", exception);
                RuntimeUtil.runOnWorkerThread(() -> {
                    failed(temporaryId, Protocol.ERROR_GROUP_SYNC_FAILED);
                });
                return Unit.INSTANCE;
            },
            result -> {
                RuntimeUtil.runOnWorkerThread(() -> {
                    if (result instanceof GroupFlowResult.Success) {
                        success(temporaryId);
                    } else if (result instanceof GroupFlowResult.Failure) {
                        failed(temporaryId, Protocol.ERROR_GROUP_SYNC_FAILED);
                    }
                });
                return Unit.INSTANCE;
            }
        );
    }

    private void success(String temporaryId) {
        logger.debug("Respond sync group success");
        this.sendConfirmActionSuccess(this.responseDispatcher, temporaryId);
    }

    private void failed(String temporaryId, @ErrorCode String errorCode) {
        logger.warn("Respond sync group failed ({})", errorCode);
        this.sendConfirmActionFailure(this.responseDispatcher, temporaryId, errorCode);
    }

    @Override
    protected boolean maybeNeedsConnection() {
        return true;
    }
}

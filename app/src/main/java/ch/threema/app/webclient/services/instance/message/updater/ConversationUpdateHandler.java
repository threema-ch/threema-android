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

package ch.threema.app.webclient.services.instance.message.updater;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import org.msgpack.core.MessagePackException;
import org.slf4j.Logger;

import ch.threema.app.listeners.ConversationListener;
import ch.threema.app.managers.ListenerManager;
import ch.threema.app.services.ContactService;
import ch.threema.app.services.ConversationCategoryService;
import ch.threema.app.services.DistributionListService;
import ch.threema.app.services.GroupService;
import ch.threema.app.utils.ContactUtil;
import ch.threema.app.utils.GroupUtil;
import ch.threema.app.utils.executor.HandlerExecutor;
import ch.threema.app.utils.TestUtil;
import ch.threema.app.webclient.Protocol;
import ch.threema.app.webclient.converter.Conversation;
import ch.threema.app.webclient.converter.MsgpackBuilder;
import ch.threema.app.webclient.converter.MsgpackObjectBuilder;
import ch.threema.app.webclient.exceptions.ConversionException;
import ch.threema.app.webclient.services.instance.MessageDispatcher;
import ch.threema.app.webclient.services.instance.MessageUpdater;
import static ch.threema.base.utils.LoggingKt.getThreemaLogger;
import ch.threema.storage.models.ConversationModel;

import static ch.threema.app.webclient.Protocol.ARGUMENT_MODE;
import static ch.threema.app.webclient.Protocol.ARGUMENT_MODE_MODIFIED;
import static ch.threema.app.webclient.Protocol.ARGUMENT_MODE_NEW;
import static ch.threema.app.webclient.Protocol.ARGUMENT_MODE_REMOVED;

@WorkerThread
public class ConversationUpdateHandler extends MessageUpdater {
    private static final Logger logger = getThreemaLogger("ConversationUpdateHandler");

    // Handler
    private final @NonNull HandlerExecutor handler;

    // Listeners
    private final ConversationListener listener;

    // Dispatchers
    private final MessageDispatcher updateDispatcher;

    // Services
    private final ContactService contactService;
    private final GroupService groupService;
    private final DistributionListService distributionListService;
    @NonNull
    private final ConversationCategoryService conversationCategoryService;

    private final int sessionId;

    @AnyThread
    public ConversationUpdateHandler(
        @NonNull HandlerExecutor handler,
        MessageDispatcher updateDispatcher,
        ContactService contactService,
        GroupService groupService,
        DistributionListService distributionListService,
        @NonNull ConversationCategoryService conversationCategoryService,
        int sessionId
    ) {
        super(Protocol.SUB_TYPE_CONVERSATION);
        this.handler = handler;
        this.updateDispatcher = updateDispatcher;
        this.contactService = contactService;
        this.groupService = groupService;
        this.distributionListService = distributionListService;
        this.conversationCategoryService = conversationCategoryService;
        this.listener = new Listener();
        this.sessionId = sessionId;
    }

    @Override
    public void register() {
        logger.debug("register({})", this.sessionId);
        ListenerManager.conversationListeners.add(this.listener);
    }

    /**
     * This method can be safely called multiple times without any negative side effects
     */
    @Override
    public void unregister() {
        logger.debug("unregister({})", this.sessionId);
        ListenerManager.conversationListeners.remove(this.listener);
    }

    private void respond(@NonNull final ConversationModel model, final String mode) {
        // Respond only if the conversation is not a private chat
        String uniqueId = null;
        if (model.isGroupConversation()) {
            uniqueId = GroupUtil.getUniqueIdString(model.getGroup());
        } else if (model.isContactConversation()) {
            String identity = null;
            if (model.getContact() != null) {
                identity = model.getContact().getIdentity();
            }
            uniqueId = ContactUtil.getUniqueIdString(identity);
        } else if (model.isDistributionListConversation()) {
            uniqueId = this.distributionListService.getUniqueIdString(model.getDistributionList());
        }

        if (TestUtil.isEmptyOrNull(uniqueId)) {
            logger.warn("Cannot send updates, unique ID is null");
            return;
        } else if (this.conversationCategoryService.isPrivateChat(uniqueId)) {
            logger.debug("Don't send updates for a private conversation");
            return;
        }

        try {
            final MsgpackObjectBuilder args = new MsgpackObjectBuilder()
                .put(ARGUMENT_MODE, mode);
            final MsgpackBuilder data = Conversation.convert(model);
            logger.debug("Sending conversation update ({} mode {})", model.getUid(), mode);
            send(updateDispatcher, data, args);
        } catch (ConversionException | MessagePackException e) {
            logger.error("Exception", e);
        }
    }

    @AnyThread
    private class Listener implements ConversationListener {
        @Override
        @AnyThread
        public void onNew(@NonNull ConversationModel conversationModel) {
            logger.info("Conversation created, sending update to Threema Web (conversation={})", conversationModel.getUid());
            // Notify webclient in background thread
            handler.post(() -> ConversationUpdateHandler.this.respond(conversationModel, ARGUMENT_MODE_NEW));
        }

        @Override
        @AnyThread
        public void onModified(@NonNull ConversationModel modifiedConversationModel, @Nullable Integer oldPosition) {
            logger.info("Conversation modified, sending update to Threema Web (conversation={})", modifiedConversationModel.getUid());
            logger.info("Move item from: {} to {}", oldPosition, modifiedConversationModel.getPosition());
            // Notify webclient in background thread
            handler.post(() -> ConversationUpdateHandler.this.respond(modifiedConversationModel, ARGUMENT_MODE_MODIFIED));
        }

        @Override
        @AnyThread
        public void onRemoved(@NonNull ConversationModel conversationModel) {
            logger.info("Conversation removed, sending update to Threema Web (conversation={})", conversationModel.getUid());
            // Notify webclient in background thread
            handler.post(() -> ConversationUpdateHandler.this.respond(conversationModel, ARGUMENT_MODE_REMOVED));
        }

        @Override
        @AnyThread
        public void onModifiedAll() {
            logger.info("onModifiedAll");
        }
    }
}

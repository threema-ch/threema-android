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

import androidx.annotation.AnyThread;
import androidx.annotation.WorkerThread;

import org.msgpack.core.MessagePackException;
import org.msgpack.value.Value;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import ch.threema.app.services.ConversationService;
import ch.threema.app.webclient.Protocol;
import ch.threema.app.webclient.converter.Conversation;
import ch.threema.app.webclient.converter.MsgpackBuilder;
import ch.threema.app.webclient.converter.MsgpackObjectBuilder;
import ch.threema.app.webclient.exceptions.ConversionException;
import ch.threema.app.webclient.services.instance.MessageDispatcher;
import ch.threema.app.webclient.services.instance.MessageReceiver;
import static ch.threema.base.utils.LoggingKt.getThreemaLogger;
import ch.threema.storage.models.ConversationModel;

/**
 * Webclient is requesting conversations.
 */
@WorkerThread
public class ConversationRequestHandler extends MessageReceiver {
    private static final Logger logger = getThreemaLogger("ConversationRequestHandler");

    private static final int INITIAL_AVATAR_COUNT = 15;
    private final MessageDispatcher dispatcher;
    private final ConversationService conversationService;
    private final Listener listener;
    private int avatarAppended;

    @WorkerThread
    public interface Listener {
        void onRespond();

        void onAnswered();
    }

    @AnyThread
    public ConversationRequestHandler(MessageDispatcher dispatcher,
                                      ConversationService conversationService,
                                      Listener listener) {
        super(Protocol.SUB_TYPE_CONVERSATIONS);
        this.dispatcher = dispatcher;
        this.conversationService = conversationService;
        this.listener = listener;
    }

    @Override
    protected void receive(Map<String, Value> message) throws MessagePackException {
        logger.debug("Received conversation request");

        final Map<String, Value> args = this.getArguments(message, true, new String[]{
            Protocol.ARGUMENT_MAX_SIZE
        });

        Integer avatarMaxSize = null;
        if (args.containsKey(Protocol.ARGUMENT_MAX_SIZE)) {
            avatarMaxSize = args.get(Protocol.ARGUMENT_MAX_SIZE).asIntegerValue().toInt();
        }

        this.respond(avatarMaxSize);
    }

    private boolean appendNextAvatar() {
        return this.avatarAppended++ < INITIAL_AVATAR_COUNT;
    }

    private void respond(final Integer avatarMaxSize) {
        try {
            this.avatarAppended = 0;

            // Shallow copy to prevent a ConcurrentModificationException
            final List<ConversationModel> conversations =
                new ArrayList<>(this.conversationService.getAll(false));
            final List<MsgpackBuilder> data = Conversation.convert(
                conversations,
                (builder, conversation, modelWrapper) -> {
                    if (!appendNextAvatar()) {
                        return;
                    }
                    try {
                        final byte[] avatar = modelWrapper.getAvatar(false, avatarMaxSize);
                        if (avatar != null) {
                            builder.put("avatar", avatar);
                        }
                    } catch (ConversionException e) {
                        logger.warn("Failed to append avatar: {}", e.getMessage());
                        //ignore exception
                    }

                }
            );

            if (this.listener != null) {
                this.listener.onRespond();
            }

            // Send response
            logger.debug("Sending conversation response");
            final MsgpackObjectBuilder args = new MsgpackObjectBuilder();
            this.send(this.dispatcher, data, args);

            if (this.listener != null) {
                this.listener.onAnswered();
            }
        } catch (ConversionException | MessagePackException e) {
            logger.error("Exception", e);
        }
    }

    @Override
    protected boolean maybeNeedsConnection() {
        return false;
    }
}

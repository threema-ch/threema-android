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

import org.msgpack.core.MessagePackException;
import org.msgpack.value.Value;
import org.slf4j.Logger;

import java.util.Map;

import androidx.annotation.AnyThread;
import androidx.annotation.WorkerThread;
import ch.threema.app.emojis.EmojiUtil;
import ch.threema.app.services.MessageService;
import ch.threema.app.services.notification.NotificationService;
import ch.threema.app.utils.ConversationNotificationUtil;
import ch.threema.app.webclient.Protocol;
import ch.threema.app.webclient.exceptions.ConversionException;
import ch.threema.app.webclient.services.instance.MessageReceiver;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.storage.models.AbstractMessageModel;

@WorkerThread
public class AcknowledgeRequestHandler extends MessageReceiver {
    private static final Logger logger = LoggingUtil.getThreemaLogger("AcknowledgeRequestHandler");

    private final MessageService messageService;
    private final NotificationService notificationService;

    @AnyThread
    public AcknowledgeRequestHandler(MessageService messageService,
                                     NotificationService notificationService) {
        super(Protocol.SUB_TYPE_ACK);
        this.messageService = messageService;
        this.notificationService = notificationService;
    }

    @Override
    protected void receive(Map<String, Value> message) throws MessagePackException {
        logger.info("Received ack request");
        Map<String, Value> args = this.getArguments(message, false, new String[]{
            Protocol.ARGUMENT_RECEIVER_TYPE,
            Protocol.ARGUMENT_RECEIVER_ID,
            Protocol.ARGUMENT_MESSAGE_ID,
            Protocol.ARGUMENT_MESSAGE_ACKNOWLEDGED
        });

        // Receiver is sending acknowledge or decline
        boolean isAcknowledged = args.get(Protocol.ARGUMENT_MESSAGE_ACKNOWLEDGED).asBooleanValue().getBoolean();
        final String messageIdStr = args.get(Protocol.ARGUMENT_MESSAGE_ID).asStringValue().asString();
        final int messageId = Integer.parseInt(messageIdStr);

        final ch.threema.app.messagereceiver.MessageReceiver receiver;
        try {
            receiver = this.getReceiver(args);
        } catch (ConversionException e) {
            logger.error("Exception", e);
            return;
        }

        if (receiver == null) {
            logger.error("Invalid receiver");
            return;
        }

        //load message
        AbstractMessageModel messageModel = null;
        switch (receiver.getType()) {
            case ch.threema.app.messagereceiver.MessageReceiver.Type_CONTACT:
                messageModel = this.messageService.getContactMessageModel(messageId);
                break;
            case ch.threema.app.messagereceiver.MessageReceiver.Type_GROUP:
                messageModel = this.messageService.getGroupMessageModel(messageId);
                break;
            case ch.threema.app.messagereceiver.MessageReceiver.Type_DISTRIBUTION_LIST:
                messageModel = this.messageService.getDistributionListMessageModel(messageId);
                break;
        }

        if (messageModel == null) {
            logger.error("No valid message model to acknowledge found");
            return;
        }

        try {
            if (isAcknowledged) {
                this.messageService.sendEmojiReaction(messageModel, EmojiUtil.THUMBS_UP_SEQUENCE, receiver, true);
            } else {
                this.messageService.sendEmojiReaction(messageModel, EmojiUtil.THUMBS_DOWN_SEQUENCE, receiver, true);
            }
        } catch (Exception e) {
            logger.error("Unable to send emoji reaction", e);
        }

        notificationService.cancelConversationNotification(ConversationNotificationUtil.getUid(messageModel));
    }

    @Override
    protected boolean maybeNeedsConnection() {
        return true;
    }
}

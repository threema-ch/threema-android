/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2024 Threema GmbH
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

package ch.threema.domain.protocol.csp.messages;

/**
 * This interface defines methods for all message flags.
 * <p>
 * All methods have default implementations that disable all flags.
 */
public interface MessageFlags {
    /**
     * Flag 0x01: Send push notification
     * <p>
     * The server will send a push message to the receiver of the message.
     * Only use this for messages that require a notification. For example, do not
     * set this for delivery receipts.
     */
    default boolean flagSendPush() {
        return false;
    }

    /**
     * Flag 0x02: No server queuing
     * <p>
     * Use this for messages that can be discarded by the chat server in case the receiver
     * is not connected to the chat server, e.g. the typing indicator.
     */
    default boolean flagNoServerQueuing() {
        return false;
    }

    /**
     * Flag 0x04: No server acknowledgement
     * <p>
     * Use this for messages where reliable delivery and acknowledgement is not essential,
     * e.g. the typing indicator. Will not be acknowledged by the chat server when sending.
     * No acknowledgement should be sent by the receiver to the chat server.
     */
    default boolean flagNoServerAck() {
        return false;
    }

    /**
     * Flag 0x10: Group message marker (DEPRECATED)
     * <p>
     * Use this for all group messages. In iOS clients, this will be used for notifications
     * to reflect that a group message has been received in case no connection to the server
     * could be established.
     */
    default boolean flagGroupMessage() {
        return false;
    }

    /**
     * Flag 0x20: Short-lived server queuing
     * <p>
     * Messages with this flag will only be queued for 60 seconds.
     */
    default boolean flagShortLivedServerQueuing() {
        return false;
    }
}

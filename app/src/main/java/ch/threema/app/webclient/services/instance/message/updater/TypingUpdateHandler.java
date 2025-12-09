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
import androidx.annotation.WorkerThread;

import org.msgpack.core.MessagePackException;
import org.slf4j.Logger;

import ch.threema.app.listeners.ContactTypingListener;
import ch.threema.app.managers.ListenerManager;
import ch.threema.app.utils.executor.HandlerExecutor;
import ch.threema.app.webclient.Protocol;
import ch.threema.app.webclient.converter.Contact;
import ch.threema.app.webclient.converter.ContactTyping;
import ch.threema.app.webclient.converter.MsgpackObjectBuilder;
import ch.threema.app.webclient.exceptions.ConversionException;
import ch.threema.app.webclient.services.instance.MessageDispatcher;
import ch.threema.app.webclient.services.instance.MessageUpdater;
import static ch.threema.base.utils.LoggingKt.getThreemaLogger;
import ch.threema.storage.models.ContactModel;

@WorkerThread
public class TypingUpdateHandler extends MessageUpdater {
    private static final Logger logger = getThreemaLogger("TypingUpdateHandler");

    // Handler
    private final @NonNull HandlerExecutor handler;

    // Listeners
    private final Listener listener = new Listener();

    // Dispatchers
    private MessageDispatcher dispatcher;

    @AnyThread
    public TypingUpdateHandler(@NonNull HandlerExecutor handler, MessageDispatcher dispatcher) {
        super(Protocol.SUB_TYPE_TYPING);
        this.handler = handler;
        this.dispatcher = dispatcher;
    }

    @Override
    public void register() {
        ListenerManager.contactTypingListeners.add(this.listener);
    }

    /**
     * This method can be safely called multiple times without any negative side effects
     */
    @Override
    public void unregister() {
        ListenerManager.contactTypingListeners.remove(this.listener);
    }

    private void update(final ContactModel contact, final boolean isTyping) {
        try {
            // Convert typing notification and prepare arguments
            final MsgpackObjectBuilder args = Contact.getArguments(contact);
            final MsgpackObjectBuilder data = ContactTyping.convert(isTyping);

            // Send message
            logger.debug("Sending typing update");
            send(dispatcher, data, args);
        } catch (ConversionException | MessagePackException e) {
            logger.error("Exception", e);
        }
    }

    @AnyThread
    private class Listener implements ContactTypingListener {
        @Override
        public void onContactIsTyping(@NonNull ContactModel contactModel, boolean isTyping) {
            handler.post(new Runnable() {
                @Override
                @WorkerThread
                public void run() {
                    TypingUpdateHandler.this.update(contactModel, isTyping);
                }
            });
        }
    }
}

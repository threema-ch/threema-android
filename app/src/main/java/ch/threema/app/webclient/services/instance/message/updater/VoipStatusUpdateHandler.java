/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2017-2024 Threema GmbH
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


import org.msgpack.core.MessagePackException;
import org.slf4j.Logger;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Date;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringDef;
import androidx.annotation.WorkerThread;
import ch.threema.app.utils.executor.HandlerExecutor;
import ch.threema.app.voip.listeners.VoipCallEventListener;
import ch.threema.app.voip.managers.VoipListenerManager;
import ch.threema.app.webclient.Protocol;
import ch.threema.app.webclient.converter.MsgpackObjectBuilder;
import ch.threema.app.webclient.converter.VoipStatus;
import ch.threema.app.webclient.services.instance.MessageDispatcher;
import ch.threema.app.webclient.services.instance.MessageUpdater;
import ch.threema.base.utils.LoggingUtil;

/**
 * Subscribe to Voip Status listener. Send them to Threema Web as update messages.
 */
@WorkerThread
public class VoipStatusUpdateHandler extends MessageUpdater {
    private static final Logger logger = LoggingUtil.getThreemaLogger("VoipStatusUpdateHandler");

    @Retention(RetentionPolicy.SOURCE)
    @StringDef({
        TYPE_RINGING, TYPE_STARTED, TYPE_FINISHED,
        TYPE_REJECTED, TYPE_MISSED, TYPE_ABORTED,
    })
    private @interface StatusType {
    }

    private final static String TYPE_RINGING = "ringing";
    private final static String TYPE_STARTED = "started";
    private final static String TYPE_FINISHED = "finished";
    private final static String TYPE_REJECTED = "rejected";
    private final static String TYPE_MISSED = "missed";
    private final static String TYPE_ABORTED = "aborted";

    // Handler
    private final @NonNull HandlerExecutor handler;

    // Listeners
    private final Listener listener = new Listener();

    // Dispatchers
    private MessageDispatcher dispatcher;

    // Local variables
    private final int sessionId;

    @AnyThread
    public VoipStatusUpdateHandler(@NonNull HandlerExecutor handler, int sessionId, MessageDispatcher dispatcher) {
        super(Protocol.SUB_TYPE_VOIP_STATUS);
        this.handler = handler;
        this.sessionId = sessionId;
        this.dispatcher = dispatcher;
    }

    @Override
    public void register() {
        logger.debug("register(" + this.sessionId + ")");
        VoipListenerManager.callEventListener.add(this.listener);
    }

    /**
     * This method can be safely called multiple times without any negative side effects
     */
    @Override
    public void unregister() {
        logger.debug("unregister(" + this.sessionId + ")");
        VoipListenerManager.callEventListener.remove(this.listener);
    }

    private void update(final MsgpackObjectBuilder data, @StatusType String type) {
        try {
            logger.info("Sending voip status update (" + type + ")");
            final MsgpackObjectBuilder args = new MsgpackObjectBuilder().put("type", type);
            send(dispatcher, data, args);
        } catch (MessagePackException e) {
            logger.error("Exception", e);
        }
    }

    @AnyThread
    private class Listener implements VoipCallEventListener {
        @Override
        public void onRinging(String peerIdentity) {
            this.update(VoipStatus.convertOnRinging(peerIdentity), TYPE_RINGING);
        }

        @Override
        public void onStarted(String peerIdentity, boolean outgoing) {
            this.update(VoipStatus.convertOnStarted(peerIdentity, outgoing), TYPE_STARTED);
        }

        @Override
        public void onFinished(long callId, @NonNull String peerIdentity, boolean outgoing, int duration) {
            this.update(VoipStatus.convertOnFinished(peerIdentity, outgoing, duration), TYPE_FINISHED);
        }

        @Override
        public void onRejected(long callId, String peerIdentity, boolean outgoing, byte reason) {
            this.update(VoipStatus.convertOnRejected(peerIdentity, outgoing, reason), TYPE_REJECTED);
        }

        @Override
        public void onMissed(long callId, String peerIdentity, boolean accepted, @Nullable Date date) {
            this.update(VoipStatus.convertOnMissed(peerIdentity), TYPE_MISSED);
        }

        @Override
        public void onAborted(long callId, String peerIdentity) {
            this.update(VoipStatus.convertOnAborted(peerIdentity), TYPE_ABORTED);
        }

        private void update(final MsgpackObjectBuilder data, @StatusType String type) {
            handler.post(new Runnable() {
                @Override
                @WorkerThread
                public void run() {
                    VoipStatusUpdateHandler.this.update(data, type);
                }
            });
        }
    }
}

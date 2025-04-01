/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2014-2024 Threema GmbH
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

package ch.threema.app.services;

import org.slf4j.Logger;

import java.io.FileNotFoundException;
import java.util.HashMap;
import java.util.concurrent.Future;

import ch.threema.app.ThreemaApplication;
import ch.threema.app.managers.ServiceManager;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.ExponentialBackOffUtil;
import ch.threema.base.utils.LoggingUtil;

public class MessageSendingServiceExponentialBackOff implements MessageSendingService {
    private static final Logger logger = LoggingUtil.getThreemaLogger("MessageSendingServiceExponentialBackOff");

    private final MessageSendingServiceState messageSendingServiceState;
    private final HashMap<String, Future<?>> backoffFutures = new HashMap<>();

    public MessageSendingServiceExponentialBackOff(MessageSendingServiceState messageSendingServiceState) {
        this.messageSendingServiceState = messageSendingServiceState;
    }

    @Override
    public void addToQueue(final MessageSendingProcess process) {
        logger.debug("{} Add message to queue", process.getMessageModel().getUid());
        Future<?> backoffFuture = ExponentialBackOffUtil.getInstance().run(new ExponentialBackOffUtil.BackOffRunnable() {
            @Override
            public void run(int currentRetry) throws Exception {
                try {
                    process.send();
                } catch (Exception x) {
                    logger.error("Sending message failed", x);
                    messageSendingServiceState.exception(x, 0);
                    if (x instanceof FileNotFoundException && ConfigUtils.isOnPremBuild()) {
                        ServiceManager serviceManager = ThreemaApplication.getServiceManager();
                        if (serviceManager != null) {
                            logger.info("Invalidating auth token");
                            serviceManager.getApiService().invalidateAuthToken();
                        }
                    }
                    throw x;
                }
            }

            @Override
            public void finished(int currentRetry) {
                synchronized (backoffFutures) {
                    backoffFutures.remove(process.getMessageModel().getUid());
                }
                logger.debug("{} Exponential backoff finished successfully", process.getMessageModel().getUid());
            }

            @Override
            public void exception(Exception e, int currentRetry) {
                synchronized (backoffFutures) {
                    backoffFutures.remove(process.getMessageModel().getUid());
                }
                logger.debug("{} Exponential backoff failed", process.getMessageModel().getUid());

                messageSendingServiceState.processingFailed(process.getMessageModel(), process.getReceiver());
            }
        }, 5, process.getMessageModel().getUid());

        if (backoffFuture != null) {
            synchronized (backoffFutures) {
                backoffFutures.put(process.getMessageModel().getUid(), backoffFuture);
            }
        }
    }

    @Override
    public void abort(String messageUid) {
        synchronized (backoffFutures) {
            Future<?> backoffFuture = backoffFutures.get(messageUid);
            if (backoffFuture != null) {
                if (!backoffFuture.isCancelled() && !backoffFuture.isDone()) {
                    logger.debug("{} Cancelling backoff", messageUid);
                    backoffFuture.cancel(true);
                }
                backoffFutures.remove(messageUid);
            }
        }
    }
}

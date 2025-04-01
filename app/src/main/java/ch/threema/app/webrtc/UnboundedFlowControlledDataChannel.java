/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2019-2025 Threema GmbH
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

package ch.threema.app.webrtc;

import org.saltyrtc.tasks.webrtc.exceptions.IllegalStateError;
import org.slf4j.Logger;
import org.webrtc.DataChannel;

import java.util.concurrent.ExecutionException;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import ch.threema.base.utils.LoggingUtil;
import java8.util.concurrent.CompletableFuture;
import java8.util.function.Function;

/**
 * A flow-controlled (sender side) data channel that allows to queue an
 * infinite amount of messages.
 * <p>
 * While this cancels the effect of the flow control, it prevents the data
 * channel's underlying buffer from becoming saturated by queueing all messages
 * in application space.
 */
@AnyThread
public class UnboundedFlowControlledDataChannel extends FlowControlledDataChannel {
    @NonNull
    final private Logger logger = LoggingUtil.getThreemaLogger("UnboundedFlowControlledDataChannel");
    @NonNull
    private CompletableFuture<Void> queue;

    /**
     * Create a flow-controlled (sender side) data channel with an infinite
     * buffer.
     *
     * @param dc The data channel to be flow-controlled
     */
    public UnboundedFlowControlledDataChannel(
        @NonNull final String logPrefix,
        @NonNull final DataChannel dc
    ) {
        this(logPrefix, dc, null);
    }

    /**
     * Create a flow-controlled (sender side) data channel with an infinite
     * buffer.
     *
     * @param dc            The data channel to be flow-controlled
     * @param initialFuture Allows to delay forwarding writes to the data
     *                      channel until the initialFuture has been completed.
     */
    public UnboundedFlowControlledDataChannel(
        @NonNull final String logPrefix,
        @NonNull final DataChannel dc,
        @Nullable final CompletableFuture<?> initialFuture
    ) {
        super(logPrefix, dc);
        if (initialFuture != null) {
            this.queue = initialFuture.thenCompose(v -> this.ready());
        } else {
            this.queue = this.ready();
        }
    }

    /**
     * Create a flow-controlled (sender side) data channel with an infinite
     * buffer.
     *
     * @param dc            The data channel to be flow-controlled
     * @param initialFuture Allows to delay forwarding writes to the data
     *                      channel until the initialFuture has been completed.
     * @param lowWaterMark  The low water mark unpauses the data channel once
     *                      the buffered amount of bytes becomes less or equal to it.
     * @param highWaterMark The high water mark pauses the data channel once
     *                      the buffered amount of bytes becomes greater or equal to it.
     */
    public UnboundedFlowControlledDataChannel(
        @NonNull final String logPrefix,
        @NonNull final DataChannel dc,
        @Nullable final CompletableFuture<?> initialFuture,
        final long lowWaterMark,
        final long highWaterMark
    ) {
        super(logPrefix, dc, lowWaterMark, highWaterMark);
        if (initialFuture != null) {
            this.queue = initialFuture.thenCompose(v -> this.ready());
        } else {
            this.queue = this.ready();
        }
    }

    /**
     * Write a message to the data channel's internal or application buffer for
     * delivery to the remote side.
     *
     * @param message The message to be sent.
     */
    public synchronized void write(@NonNull final DataChannel.Buffer message) {
        // Note: This very simple technique allows for ordered message
        //       queueing by using future chaining.
        this.queue = this.queue.thenRunAsync(new Runnable() {
            @Override
            @AnyThread
            public void run() {
                // Wait until ready
                try {
                    UnboundedFlowControlledDataChannel.this.ready().get();
                } catch (ExecutionException error) {
                    // Should not happen
                    logger.error("Woops!", error);
                    return;
                } catch (InterruptedException error) {
                    // Can happen when the channel has been closed abruptly
                    logger.error("Unable to send pending chunk! Channel closed abruptly?", error);
                    return;
                }

                // Write message
                UnboundedFlowControlledDataChannel.super.write(message);
            }
        });
        this.queue.exceptionally(new Function<Throwable, Void>() {
            @Override
            @AnyThread
            public Void apply(@NonNull final Throwable error) {
                // Ignore if the data channel has been disposed or is currently
                // closing. This can happen when a peer connection is being closed
                // abruptly.
                if (error.getCause() instanceof IllegalStateException ||
                    error.getCause() instanceof IllegalStateError) {
                    logger.info("Write queue aborted: {}", error.getMessage());
                    return null;
                }

                // Complain!
                logger.error("Exception in write queue", error);
                return null;
            }
        });
    }
}

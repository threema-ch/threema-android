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

package ch.threema.app.webclient.webrtc;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.saltyrtc.chunkedDc.Chunker;
import org.saltyrtc.chunkedDc.Unchunker;
import org.saltyrtc.client.crypto.CryptoException;
import org.saltyrtc.client.exceptions.OverflowException;
import org.saltyrtc.client.exceptions.ProtocolException;
import org.saltyrtc.client.exceptions.ValidationError;
import org.saltyrtc.client.keystore.Box;
import org.saltyrtc.tasks.webrtc.WebRTCTask;
import org.saltyrtc.tasks.webrtc.crypto.DataChannelCryptoContext;
import org.saltyrtc.tasks.webrtc.exceptions.IllegalStateError;
import org.slf4j.Logger;
import org.webrtc.DataChannel;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.ExecutionException;

import ch.threema.annotation.SameThread;
import ch.threema.app.webclient.exceptions.WouldBlockException;
import ch.threema.app.webrtc.FlowControlledDataChannel;
import static ch.threema.base.utils.LoggingKt.getThreemaLogger;
import ch.threema.logging.ThreemaLogger;
import java8.util.concurrent.CompletableFuture;
import java8.util.function.Function;

/**
 * Wraps a flow-controlled (sender-side) data channel, applies additional
 * encryption and fragmentation/reassembly when sending/receiving depending
 * on the parameters provided.
 * <p>
 * Important: The passed executor MUST always schedule execution on the same
 * thread that is being used for any other method!
 */
@SameThread
public class DataChannelContext {
    private final static long MAX_CHUNK_SIZE = 256 * 1024;

    private static final Logger logger = getThreemaLogger("DataChannelContext");

    @NonNull
    private final DataChannel dc;
    @NonNull
    public final FlowControlledDataChannel fcdc;
    @Nullable
    private final DataChannelCryptoContext crypto;
    @Nullable
    private Unchunker unchunker;
    @NonNull
    private CompletableFuture<Void> queue;
    private final int chunkLength;
    private long messageId = 0;

    public DataChannelContext(
        @NonNull final String logPrefix,
        @NonNull final DataChannel dc,
        @NonNull final WebRTCTask task,
        final long maxMessageSize,
        @NonNull final Unchunker.MessageListener messageListener
    ) {
        // Set logger prefix
        if (logger instanceof ThreemaLogger) {
            ((ThreemaLogger) logger).setPrefix(logPrefix + "." + dc.label() + "/" + dc.id());
        }
        this.dc = dc;

        // Wrap as flow-controlled data channel
        this.fcdc = new FlowControlledDataChannel(logPrefix, dc);

        // Create crypto context
        this.crypto = task.createCryptoContext(dc.id());

        // Create unchunker
        this.unchunker = new Unchunker();
        this.unchunker.onMessage(new Unchunker.MessageListener() {
            @Override
            @SameThread
            public void onMessage(ByteBuffer buffer) {
                // Decrypt message
                final Box box = new Box(buffer, DataChannelCryptoContext.NONCE_LENGTH);
                try {
                    buffer = ByteBuffer.wrap(Objects.requireNonNull(DataChannelContext.this.crypto).decrypt(box));
                } catch (ValidationError | ProtocolException error) {
                    logger.error("Invalid packet received", error);
                    return;
                } catch (CryptoException error) {
                    logger.error("Unable to decrypt", error);
                    return;
                }

                // Hand out message
                logger.debug("Incoming message of length {}", buffer.remaining());
                messageListener.onMessage(buffer);
            }
        });

        // Determine chunk length
        // Important: We need to do this here because the "open" state may not
        //            be fired in case we're receiving a data channel. Why?
        //            Because libwebrtc, that's why!
        this.chunkLength = (int) Math.min(maxMessageSize, MAX_CHUNK_SIZE);

        // Initialise queue
        this.queue = this.fcdc.ready();
    }

    /**
     * Send a message asynchronously via this channel's write queue. The
     * message will be fragmented into chunks.
     */
    @NonNull
    public CompletableFuture<Void> sendAsync(@NonNull final ByteBuffer buffer) {
        return this.enqueue(new Runnable() {
            @Override
            @AnyThread
            public void run() {
                DataChannelContext.this.sendSync(buffer);
            }
        });
    }

    /**
     * Send a message synchronously from the same thread. Will throw if the message would block
     * the thread.
     */
    public void sendSyncUnsafe(@NonNull final ByteBuffer buffer) throws WouldBlockException {
        if (!this.fcdc.ready().isDone()) {
            throw new WouldBlockException();
        } else {
            this.sendSync(buffer);
        }
    }

    /**
     * Send a message synchronously, fragmented into chunks.
     * <p>
     * Important: This may only be called from the future queue or synchronously from the worker
     * thread.
     */
    @AnyThread
    private synchronized void sendSync(@NonNull ByteBuffer buffer) {
        try {
            logger.debug("Outgoing message of length {}", buffer.remaining());

            // Encrypt message
            final Box box = Objects.requireNonNull(this.crypto).encrypt(bufferToBytes(buffer));
            buffer = ByteBuffer.wrap(box.toBytes());

            // Write chunks
            final Chunker chunker = new Chunker(this.messageId++, buffer, this.chunkLength);
            while (chunker.hasNext()) {
                // Wait until we can send
                // Note: This will block!
                try {
                    this.fcdc.ready().get();
                } catch (InterruptedException | ExecutionException e) {
                    logger.error("Error while waiting for fcdc.ready()", e);
                    return;
                }
                buffer = chunker.next();

                // Write chunk
                final DataChannel.Buffer chunk = new DataChannel.Buffer(buffer, true);
                logger.debug("Outgoing chunk of length {}", chunk.data.remaining());
                this.fcdc.write(chunk);
            }
        } catch (OverflowException error) {
            logger.error("CSN overflow", error);
            this.close();
        } catch (CryptoException error) {
            logger.error("Unable to encrypt", error);
            this.close();
        }
    }

    /**
     * Enqueue an operation to be run in order on this channel's write queue.
     */
    private CompletableFuture<Void> enqueue(@NonNull final Runnable operation) {
        this.queue = this.queue.thenRunAsync(operation);
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
        return this.queue;
    }

    /**
     * Hand in a chunk for reassembly.
     *
     * @param buffer The chunk to be added to the reassembly buffer.
     */
    public void receive(@NonNull ByteBuffer buffer) {
        logger.debug("Incoming chunk of length {}", buffer.remaining());

        // Ensure we can reassemble
        if (this.unchunker == null) {
            logger.warn("Unchunker has been removed");
            return;
        }

        // Reassemble
        try {
            this.unchunker.add(buffer);
        } catch (OutOfMemoryError error) {
            // Delete unchunker
            logger.warn("Removing unchunker due to out of memory error");
            this.unchunker = null;

            // Rethrow
            throw error;
        }
    }

    /**
     * Convert a ByteBuffer to a byte array.
     */
    @NonNull
    static private byte[] bufferToBytes(@NonNull final ByteBuffer buffer) {
        // Strip the buffer's array from unnecessary bytes
        byte[] bytes = buffer.array();
        if (buffer.position() != 0 || buffer.remaining() != bytes.length) {
            bytes = Arrays.copyOf(buffer.array(), buffer.remaining());
        }
        return bytes;
    }

    /**
     * Close the underlying data channel.
     */
    @AnyThread
    public void close() {
        this.dc.close();
    }
}

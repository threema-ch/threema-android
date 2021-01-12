/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2019-2021 Threema GmbH
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
import org.slf4j.LoggerFactory;
import org.webrtc.DataChannel;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import ch.threema.logging.ThreemaLogger;
import java8.util.concurrent.CompletableFuture;


/**
 * A flow-controlled (sender side) data channel.
 *
 * When using this, make sure to properly call `bufferedAmountChange` when the corresponding
 * event on the data channel is received.
 */
@AnyThread
public class FlowControlledDataChannel {
	@NonNull final private Logger logger = LoggerFactory.getLogger("FlowControlledDataChannel");
	@NonNull public final DataChannel dc;
	private final long lowWaterMark;
	private final long highWaterMark;
	@NonNull private CompletableFuture<Void> readyFuture = CompletableFuture.completedFuture(null);

	/**
	 * Create a flow-controlled (sender side) data channel.
	 *
	 * @param dc The data channel to be flow-controlled
	 */
	public FlowControlledDataChannel(@NonNull final String logPrefix, @NonNull final DataChannel dc) {
		this(logPrefix, dc, 256 * 1024, 1024 * 1024);
	}

	/**
	 * Create a flow-controlled (sender side) data channel.
	 *
	 * @param dc The data channel to be flow-controlled
	 * @param lowWaterMark The low water mark unpauses the data channel once
	 *   the buffered amount of bytes becomes less or equal to it.
	 * @param highWaterMark The high water mark pauses the data channel once
	 *   the buffered amount of bytes becomes greater or equal to it.
	 */
	public FlowControlledDataChannel(
		@NonNull final String logPrefix,
		@NonNull final DataChannel dc,
		final long lowWaterMark,
		final long highWaterMark
	) {
		// Set logger prefix
		if (logger instanceof ThreemaLogger) {
			((ThreemaLogger) logger).setPrefix(logPrefix + "." + dc.label() + "/" + dc.id());
		}

		this.dc = dc;
		this.lowWaterMark = lowWaterMark;
		this.highWaterMark = highWaterMark;
	}

	/**
	 * Return the low water mark.
	 */
	public long getLowWaterMark() {
		return this.lowWaterMark;
	}

	/**
	 * Return the high water mark.
	 */
	public long getHighWaterMark() {
		return this.highWaterMark;
	}

	/**
	 * A future whether the data channel is ready to be written on.
	 */
	@NonNull public synchronized CompletableFuture<Void> ready() {
		return this.readyFuture;
	}

	/**
	 * Write a message to the data channel's internal buffer for delivery to
	 * the remote side.
	 *
	 * Important: Before calling this, the `ready` Promise must be awaited.
	 *
	 * @param message The message to be sent.
	 * @throws IllegalStateError in case the data channel is currently paused.
	 */
	public synchronized void write(@NonNull final DataChannel.Buffer message) {
		// Note: Locked since the "onBufferedAmountChange" event must run in parallel to the send
		//       calls.

		// Throw if paused
		if (!this.ready().isDone()) {
			throw new IllegalStateError("Unable to write, data channel is paused!");
		}

		// Try sending
		// Note: Technically we should be able to catch an Exception in case the
		//       underlying buffer is full. However, webrtc.org is utterly
		//       outdated and just closes when its buffer would overflow. Thus,
		//       we use a well-tested high water mark instead and try to never
		//       fill the buffer completely.
		if (!this.dc.send(message)) {
			// This can happen when the data channel is closing.
			throw new IllegalStateError("Unable to send in state " + this.dc.state());
		}

		// Pause once high water mark has been reached
		final long bufferedAmount = this.dc.bufferedAmount();
		if (bufferedAmount >= this.highWaterMark) {
			this.readyFuture = new CompletableFuture<>();
			if (logger.isDebugEnabled()) {
				logger.debug("{} paused (buffered={})", this.dc.label(), bufferedAmount);
			}
		}
	}

	/**
	 * Must be called when the data channel's buffered amount changed.
	 *
	 * Important: You MUST ensure that you're not calling this from the send thread of the data
	 *            channel! When in doubt, post it to some other thread!
	 */
	public synchronized void bufferedAmountChange() {
		final long bufferedAmount = this.dc.bufferedAmount();

		// Unpause once low water mark has been reached
		if (bufferedAmount <= this.lowWaterMark && !this.readyFuture.isDone()) {
			if (logger.isDebugEnabled()) {
				logger.debug("{} resumed (buffered={})", this.dc.label(), bufferedAmount);
			}
			this.readyFuture.complete(null);
		}
	}
}

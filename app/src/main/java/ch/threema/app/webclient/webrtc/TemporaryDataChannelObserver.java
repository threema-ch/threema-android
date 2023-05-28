/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2019-2023 Threema GmbH
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
import ch.threema.app.webrtc.DataChannelObserver;
import ch.threema.base.utils.LoggingUtil;

import org.slf4j.Logger;
import org.webrtc.DataChannel;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Buffers data channel events until they can be dispatched.
 */
@AnyThread
public class TemporaryDataChannelObserver extends DataChannelObserver {
	private static final Logger logger = LoggingUtil.getThreemaLogger("TemporaryDataChannelObserver");

	@NonNull final private List<Object> events = new ArrayList<>();
	@Nullable private DataChannelObserver observer;

	@Override
	public synchronized void onBufferedAmountChange(final long bufferedAmount) {
		if (this.observer != null) {
			this.observer.onBufferedAmountChange(bufferedAmount);
		} else {
			this.events.add(bufferedAmount);
		}
	}

	@Override
	public synchronized void onStateChange(@NonNull final DataChannel.State state) {
		if (this.observer != null) {
			this.observer.onStateChange(state);
		} else {
			this.events.add(state);
		}
	}

	@Override
	public synchronized void onMessage(@NonNull final DataChannel.Buffer buffer) {
		if (this.observer != null) {
			this.observer.onMessage(buffer);
		} else {
			// Copy the message since the underlying buffer will be reused immediately
			final ByteBuffer copy = ByteBuffer.allocate(buffer.data.remaining());
			copy.put(buffer.data);
			copy.flip();
			this.events.add(new DataChannel.Buffer(copy, buffer.binary));
		}
	}

	public synchronized void replace(@NonNull final DataChannel dc, @NonNull final DataChannelObserver observer) {
		logger.debug("Flushing {} events", this.events.size());
		this.observer = observer;
		for (final Object event: this.events) {
			if (event instanceof Long) {
				observer.onBufferedAmountChange((Long) event);
			} else if (event instanceof DataChannel.State) {
				observer.onStateChange((DataChannel.State) event);
			} else if (event instanceof DataChannel.Buffer) {
				observer.onMessage((DataChannel.Buffer) event);
			} else {
				logger.error("Invalid buffered data channel event type: {}", event.getClass());
			}
		}

		// Note: We'll permanently dispatch via this observer since webrtc.org
		//       segfaults if we attempt to unregister the current observer.
		logger.debug("Events flushed, replacing observer");
		this.events.clear();
	}
}

/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2019-2022 Threema GmbH
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

import androidx.annotation.NonNull;

import org.webrtc.DataChannel;

/**
 * An improved data channel observer that passes changed values to
 * the change listeners.
 *
 * Example: This wrapper passes the data channel state to the
 * {@link #onStateChange(DataChannel.State)} method while the original
 * WebRTC observer does not.
 */
abstract public class DataChannelObserver {
	public static void register(
		@NonNull final DataChannel dc,
		@NonNull final DataChannelObserver observer
	) {
		observer.register(dc);
	}

	public void register(@NonNull final DataChannel dc) {
		dc.registerObserver(new DataChannel.Observer() {
			@Override
			public void onBufferedAmountChange(final long bufferedAmount) {
				DataChannelObserver.this.onBufferedAmountChange(bufferedAmount);
			}

			@Override
			public void onStateChange() {
				DataChannelObserver.this.onStateChange(dc.state());
			}

			@Override
			public void onMessage(@NonNull final DataChannel.Buffer buffer) {
				DataChannelObserver.this.onMessage(buffer);
			}
		});
	}

	abstract public void onBufferedAmountChange(final long bufferedAmount);
	abstract public void onStateChange(@NonNull final DataChannel.State state);
	abstract public void onMessage(@NonNull final DataChannel.Buffer buffer);
}

package ch.threema.app.webrtc;

import androidx.annotation.NonNull;

import org.webrtc.DataChannel;

/**
 * An improved data channel observer that passes changed values to
 * the change listeners.
 * <p>
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

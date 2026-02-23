package ch.threema.app.webclient.listeners;

import org.webrtc.DataChannel;

import androidx.annotation.AnyThread;
import ch.threema.app.webclient.state.PeerConnectionState;

@AnyThread
public interface PeerConnectionListener {
    void onStateChanged(PeerConnectionState oldState, PeerConnectionState newState);

    void onDataChannel(DataChannel dataChannel);
}

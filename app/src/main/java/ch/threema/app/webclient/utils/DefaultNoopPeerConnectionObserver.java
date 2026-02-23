package ch.threema.app.webclient.utils;

import androidx.annotation.AnyThread;

import org.webrtc.DataChannel;
import org.webrtc.IceCandidate;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.RtpReceiver;

/**
 * A PeerConnection.Observer that by default does not do anything (default methods everywhere).
 */
@AnyThread
public interface DefaultNoopPeerConnectionObserver extends PeerConnection.Observer {
    @Override
    default void onSignalingChange(PeerConnection.SignalingState signalingState) {
    }

    @Override
    default void onIceConnectionChange(PeerConnection.IceConnectionState iceConnectionState) {
    }

    @Override
    default void onIceConnectionReceivingChange(boolean b) {
    }

    @Override
    default void onIceGatheringChange(PeerConnection.IceGatheringState iceGatheringState) {
    }

    @Override
    default void onIceCandidate(IceCandidate iceCandidate) {
    }

    @Override
    default void onIceCandidatesRemoved(IceCandidate[] iceCandidates) {
    }

    @Override
    default void onAddStream(MediaStream mediaStream) {
    }

    @Override
    default void onRemoveStream(MediaStream mediaStream) {
    }

    @Override
    default void onDataChannel(DataChannel dataChannel) {
    }

    @Override
    default void onRenegotiationNeeded() {
    }

    @Override
    default void onAddTrack(RtpReceiver rtpReceiver, MediaStream[] mediaStreams) {
    }
}

package ch.threema.app.voip.listeners;

import java.util.Date;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Events that happen before, during and after a call
 * (e.g. a rejected call or a missed call).
 */
public interface VoipCallEventListener {

    @AnyThread
    void onRinging(String peerIdentity);

    /**
     * A call was successfully started (meaning that it was accepted and that the connection has
     * been established successfully).
     *
     * @param peerIdentity The identity of the peer.
     * @param outgoing     Whether this is an outgoing call (initiated by us).
     */
    @AnyThread
    void onStarted(String peerIdentity, boolean outgoing);

    /**
     * A call was finished.
     *
     * @param callId       The call id of the finished call (might be 0).
     * @param peerIdentity The identity of the peer.
     * @param outgoing     Whether this is an outgoing call (initiated by us).
     * @param duration     The duration of the call in seconds.
     */
    @AnyThread
    void onFinished(long callId, @NonNull String peerIdentity, boolean outgoing, int duration);

    /**
     * A call was rejected.
     *
     * @param callId       The call id of the rejected call (might be 0).
     * @param peerIdentity The identity of the peer.
     * @param outgoing     Whether the rejected call was an outgoing call (initiated by us).
     * @param reason       The reject reason. The meaning can be determined using the
     *                     `VoipCallAnswerData.RejectReason` class.
     */
    @AnyThread
    void onRejected(long callId, String peerIdentity, boolean outgoing, byte reason);

    /**
     * An incoming call was missed or failed to be established.
     *
     * @param callId       The call id of the missed call (might be 0).
     * @param peerIdentity The identity of the peer.
     * @param accepted     Whether the call was accepted by the user or not.
     * @param date         The created-at date of the hangup message, or {@code null} if the current date should be used
     */
    @AnyThread
    void onMissed(long callId, String peerIdentity, boolean accepted, @Nullable Date date);

    /**
     * An outgoing call was aborted or failed to be established.
     *
     * @param callId       The call id of the aborted call (might be 0).
     * @param peerIdentity The identity of the peer.
     */
    @AnyThread
    void onAborted(long callId, String peerIdentity);
}

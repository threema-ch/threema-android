package ch.threema.app.voip.listeners;

import androidx.annotation.AnyThread;
import ch.threema.domain.protocol.csp.messages.voip.VoipCallAnswerData;
import ch.threema.domain.protocol.csp.messages.voip.VoipCallHangupData;
import ch.threema.domain.protocol.csp.messages.voip.VoipCallOfferData;
import ch.threema.domain.protocol.csp.messages.voip.VoipCallRingingData;

public interface VoipMessageListener {
    @AnyThread
    default void onOffer(final String identity, final VoipCallOfferData data) {
    }

    @AnyThread
    default void onAnswer(final String identity, final VoipCallAnswerData data) {
    }

    @AnyThread
    default void onRinging(final String identity, final VoipCallRingingData data) {
    }

    @AnyThread
    default void onHangup(final String identity, final VoipCallHangupData data) {
    }

    /**
     * Return true if events for this identity should be handled.
     *
     * @param identity The Threema identity
     */
    @AnyThread
    boolean handle(final String identity);
}

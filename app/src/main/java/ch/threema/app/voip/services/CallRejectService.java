package ch.threema.app.voip.services;

import android.app.IntentService;
import android.content.Intent;

import org.koin.java.KoinJavaComponent;
import org.slf4j.Logger;

import androidx.annotation.Nullable;
import static ch.threema.base.utils.LoggingKt.getThreemaLogger;

import ch.threema.data.repositories.ContactModelRepository;
import ch.threema.domain.protocol.csp.messages.voip.VoipCallAnswerData;

import static ch.threema.app.voip.services.VoipCallService.EXTRA_CALL_ID;
import static ch.threema.app.voip.services.VoipCallService.EXTRA_CONTACT_IDENTITY;

/**
 * A small intent service that rejects an incoming call.
 */
public class CallRejectService extends IntentService {
    private static final String name = "CallRejectService";
    private static final Logger logger = getThreemaLogger(name);

    public static final String EXTRA_REJECT_REASON = "REJECT_REASON";

    public CallRejectService() {
        super(name);
    }

    @Override
    protected void onHandleIntent(@Nullable Intent intent) {
        logger.info("onHandleIntent");

        // Ignore null intents
        if (intent == null) {
            logger.debug("Empty Intent");
            return;
        }

        // Intent parameters
        final String contactIdentity = intent.getStringExtra(EXTRA_CONTACT_IDENTITY);
        final long callId = intent.getLongExtra(EXTRA_CALL_ID, 0L);
        final byte rejectReason = intent.getByteExtra(EXTRA_REJECT_REASON, VoipCallAnswerData.RejectReason.UNKNOWN);

        // Reject the call
        VoipStateService voipStateService = KoinJavaComponent.getOrNull(VoipStateService.class);
        ContactModelRepository contactModelRepository = KoinJavaComponent.getOrNull(ContactModelRepository.class);
        if (contactIdentity != null && voipStateService != null && contactModelRepository != null) {
            CallRejectWorkerKt.rejectCall(voipStateService, contactModelRepository, callId, contactIdentity, rejectReason);
        }
    }

}

package ch.threema.app.activities;

import com.google.android.search.verification.client.SearchActionVerificationClientActivity;
import com.google.android.search.verification.client.SearchActionVerificationClientService;

import ch.threema.app.services.VoiceActionService;

public class VoiceActionActivity extends SearchActionVerificationClientActivity {
    @Override
    public Class<? extends SearchActionVerificationClientService> getServiceClass() {
        return VoiceActionService.class;
    }
}

package ch.threema.app.stores;

import ch.threema.app.preference.service.PreferenceService;
import ch.threema.domain.stores.TokenStoreInterface;

public class MatchTokenStore implements TokenStoreInterface {

    private final PreferenceService preferenceService;

    public MatchTokenStore(PreferenceService preferenceService) {
        this.preferenceService = preferenceService;
    }

    @Override
    public String getToken() {
        return preferenceService.getMatchToken();
    }

    @Override
    public void storeToken(String matchToken) {
        preferenceService.setMatchToken(matchToken);
    }
}

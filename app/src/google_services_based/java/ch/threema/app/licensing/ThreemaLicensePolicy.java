package ch.threema.app;

import com.google.android.vending.licensing.Policy;
import com.google.android.vending.licensing.ResponseData;

public class ThreemaLicensePolicy implements Policy {

    private int lastResponse = Policy.RETRY;
    private ResponseData lastResponseData;

    @Override
    public void processServerResponse(int response, ResponseData rawData) {
        lastResponse = response;
        lastResponseData = rawData;
    }

    @Override
    public boolean allowAccess() {
        return (lastResponse == Policy.LICENSED);
    }

    @Override
    public String getLicensingUrl() {
        return null;
    }

    public ResponseData getLastResponseData() {
        return lastResponseData;
    }
}

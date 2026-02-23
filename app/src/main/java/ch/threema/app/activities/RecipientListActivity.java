package ch.threema.app.activities;

/**
 * Dummy activity for external use via intent filters
 * Allow for different task related parameters in Manifest
 */
public class RecipientListActivity extends RecipientListBaseActivity {
    @Override
    public boolean isCalledFromExternalApp() {
        return true;
    }
}

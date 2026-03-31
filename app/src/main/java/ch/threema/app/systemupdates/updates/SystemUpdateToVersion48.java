package ch.threema.app.systemupdates.updates;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.Context;

import org.koin.java.KoinJavaComponent;
import org.slf4j.Logger;

import java.util.Arrays;

import androidx.preference.PreferenceManager;
import ch.threema.app.R;
import ch.threema.app.stores.PreferenceStore;
import kotlin.Lazy;

import static ch.threema.base.utils.LoggingKt.getThreemaLogger;

/**
 * rename account manager accounts
 */
public class SystemUpdateToVersion48 implements SystemUpdate {
    private static final Logger logger = getThreemaLogger("SystemUpdateToVersion48");

    private final Lazy<Context> appContextLazy = KoinJavaComponent.inject(Context.class);

    @Override
    public void run() {
        var appContext = appContextLazy.getValue();
        final AccountManager accountManager = AccountManager.get(appContext);
        final String myIdentity = PreferenceManager.getDefaultSharedPreferences(appContext).getString(PreferenceStore.PREFS_IDENTITY, null);

        if (accountManager != null && myIdentity != null) {
            try {
                Account accountToRename = null;

                for (Account account : Arrays.asList(accountManager.getAccountsByType(appContext.getPackageName()))) {
                    if (account.name.equals(myIdentity)) {
                        accountToRename = account;
                    } else {
                        if (!account.name.equals(appContext.getString(R.string.title_mythreemaid))) {
                            accountManager.removeAccount(account, null, null);
                        }
                    }
                }

                // rename old-style ID-based account to generic name
                if (accountToRename != null) {
                    accountManager.renameAccount(accountToRename, appContext.getString(R.string.title_mythreemaid), null, null);
                }
            } catch (Exception e) {
                logger.error("Exception", e);
            }
        }
    }

    @Override
    public int getVersion() {
        return 48;
    }
}

package ch.threema.app.systemupdates.updates;

import android.Manifest;
import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.ContentResolver;
import android.content.Context;
import android.provider.ContactsContract;

import org.koin.java.KoinJavaComponent;
import org.slf4j.Logger;

import androidx.preference.PreferenceManager;
import ch.threema.app.BuildFlavor;
import ch.threema.app.R;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.SynchronizeContactsUtil;
import kotlin.Lazy;

import static ch.threema.base.utils.LoggingKt.getThreemaLogger;

/**
 * Fix Contact sync account type for Threema Libre
 */
public class SystemUpdateToVersion91 implements SystemUpdate {
    private static final Logger logger = getThreemaLogger("SystemUpdateToVersion91");

    private final Lazy<Context> appContextLazy = KoinJavaComponent.inject(Context.class);

    @Override
    public void run() {
        if (BuildFlavor.getCurrent().isLibre()) {
            var appContext = appContextLazy.getValue();
            final boolean isSyncContacts = PreferenceManager.getDefaultSharedPreferences(appContext).getBoolean(appContext.getString(R.string.preferences__sync_contacts), false);

            if (!SynchronizeContactsUtil.isRestrictedProfile(appContext) && isSyncContacts) {
                if (ConfigUtils.isPermissionGranted(appContext, Manifest.permission.WRITE_CONTACTS)) {
                    final AccountManager accountManager = AccountManager.get(appContext);

                    if (accountManager != null) {
                        try {
                            for (Account account : accountManager.getAccountsByTypeForPackage("ch.threema.app", appContext.getPackageName())) {
                                if (account.name.equals(appContext.getString(R.string.app_name))) {
                                    accountManager.removeAccount(account, null, null);
                                }
                            }

                            // we don't need to wait until removal is complete to create a new account that differs from the existing one(s)
                            Account newAccount = new Account(appContext.getString(R.string.app_name), appContext.getString(R.string.package_name));
                            accountManager.addAccountExplicitly(newAccount, "", null);
                            ContentResolver.setIsSyncable(newAccount, ContactsContract.AUTHORITY, 1);
                            if (!ContentResolver.getSyncAutomatically(newAccount, ContactsContract.AUTHORITY)) {
                                ContentResolver.setSyncAutomatically(newAccount, ContactsContract.AUTHORITY, true);
                            }
                        } catch (Exception e) {
                            logger.error("Exception", e);
                        }
                    }
                }
            }
        }
    }

    @Override
    public String getDescription() {
        return "fix libre account type";
    }

    @Override
    public int getVersion() {
        return 91;
    }
}

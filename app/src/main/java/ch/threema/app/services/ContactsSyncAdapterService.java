package ch.threema.app.services;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

import ch.threema.app.adapters.ContactsSyncAdapter;

public class ContactsSyncAdapterService extends Service {

    private static ContactsSyncAdapter contactsSyncAdapter = null;
    private static final Object syncAdapterLock = new Object();

    private static boolean isSyncEnabled = true;

    public static void enableSync() {
        synchronized (syncAdapterLock) {
            isSyncEnabled = true;
            setAdapterSyncEnabled();
        }
    }

    public static void disableSync() {
        synchronized (syncAdapterLock) {
            isSyncEnabled = false;
            setAdapterSyncEnabled();
        }
    }

    private static void setAdapterSyncEnabled() {
        synchronized (syncAdapterLock) {
            if (contactsSyncAdapter != null) {
                contactsSyncAdapter.setSyncEnabled(isSyncEnabled);
            }
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();

        synchronized (syncAdapterLock) {
            if (contactsSyncAdapter == null) {
                contactsSyncAdapter = new ContactsSyncAdapter(getApplicationContext(), true);
                contactsSyncAdapter.setSyncEnabled(isSyncEnabled);
            }
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return contactsSyncAdapter.getSyncAdapterBinder();
    }
}

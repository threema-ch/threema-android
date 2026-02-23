package ch.threema.app.voip.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;

import androidx.annotation.NonNull;
import androidx.core.net.ConnectivityManagerCompat;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

public class MeteredStatusChangedReceiver extends BroadcastReceiver implements DefaultLifecycleObserver {
    private final Context context;
    private final ConnectivityManager connectivityManager;
    private final MutableLiveData<Boolean> metered;

    /**
     * Broadcast receiver for network status
     *
     * @param context        Context
     * @param lifecycleOwner Lifecycle to bind to
     */
    public MeteredStatusChangedReceiver(@NonNull Context context, @NonNull LifecycleOwner lifecycleOwner) {
        this.context = context;
        this.connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        this.metered = new MutableLiveData<>();

        this.metered.setValue(ConnectivityManagerCompat.isActiveNetworkMetered(connectivityManager));
        lifecycleOwner.getLifecycle().addObserver(this);
    }

    @Override
    public void onCreate(@NonNull LifecycleOwner owner) {
        context.registerReceiver(this, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
    }

    @Override
    public void onDestroy(@NonNull LifecycleOwner owner) {
        context.unregisterReceiver(this);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        metered.postValue(ConnectivityManagerCompat.isActiveNetworkMetered(connectivityManager));
    }

    @NonNull
    public LiveData<Boolean> getMetered() {
        return metered;
    }
}

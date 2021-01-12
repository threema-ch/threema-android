/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2020-2021 Threema GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

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
	 * @param context Context
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

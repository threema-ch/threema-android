/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2017-2021 Threema GmbH
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

package ch.threema.app.utils;

import android.content.Context;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;

import java.util.concurrent.Executor;

import androidx.annotation.NonNull;
import androidx.annotation.UiThread;
import ch.threema.app.BuildConfig;

public class RuntimeUtil {
	private static Boolean isInTestMode;
	public static Handler handler = new Handler(Looper.getMainLooper());

	/**
	 * check if current running environment is a test suite
	 *
	 * @return
	 */
	public static boolean isInTest() {
		if (isInTestMode == null) {
			try {
				Class.forName("ch.threema.app.ThreemaTestRunner");
				isInTestMode = true;
			} catch (ClassNotFoundException e) {
				isInTestMode = false;
			}
		}

		return isInTestMode;
	}

	/**
	 * Return true if the calling thread is in the UI thread
	 */
	public static boolean isOnUiThread() {
		return Looper.myLooper() == Looper.getMainLooper();
	}

	/**
	 * Run the specified runnable on the UI thread.
	 */
	public static void runOnUiThread(final Runnable runnable) {
		if (isOnUiThread()) {
			if (runnable != null) {
				runnable.run();
			}
		} else {
			handler.post(runnable);
		}
	}

	/**
	 * Run the specified runnable in an async task.
	 */
	@UiThread
	public static void runInAsyncTask(@NonNull final Runnable runnable) {
		new AsyncTask<Void, Void, Void>() {
			@Override
			protected Void doInBackground(Void... voids) {
				runnable.run();
				return null;
			}
		}.execute();
	}

	/**
	 * Run the provided runnable while holding a partial wakelock
	 * @param context context
	 * @param timeout wakelock timeout
	 * @param tag wakelock tag
	 * @param runnable the runnable to run
	 */
	public static void runInWakelock(@NonNull Context context, long timeout, @NonNull String tag, @NonNull final Runnable runnable) {
		PowerManager powerManager = (PowerManager) context.getApplicationContext().getSystemService(Context.POWER_SERVICE);
		PowerManager.WakeLock wakeLock = null;
		try {
			wakeLock = acquireWakeLock(powerManager, timeout, tag);
			runnable.run();
		} finally {
			if (wakeLock != null && wakeLock.isHeld()) {
				wakeLock.release();
			}
		}
	}

	private static PowerManager.WakeLock acquireWakeLock(PowerManager powerManager, long timeout, String tag) {
		try {
			PowerManager.WakeLock wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, BuildConfig.APPLICATION_ID + ":" + tag);
			wakeLock.acquire(timeout);
			return wakeLock;
		} catch (Exception e) {
			return null;
		}
	}

	public static class MainThreadExecutor implements Executor {
		private final Handler handler = new Handler(Looper.getMainLooper());

		@Override
		public void execute(@NonNull Runnable runnable) {
			handler.post(runnable);
		}
	}
}

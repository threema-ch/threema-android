/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2019-2021 Threema GmbH
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

/*
 * Copyright (C) 2012 ZXing authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ch.threema.app.qrscanner.camera;

import android.hardware.Camera;
import android.os.AsyncTask;

import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.RejectedExecutionException;

/**
 * @date 2016-11-18 16:58
 * @auther GuoJinyu
 * @description modified
 */
final class AutoFocusManager implements Camera.AutoFocusCallback {

	private static final String TAG = AutoFocusManager.class.getSimpleName();

	private static final long AUTO_FOCUS_INTERVAL_MS = 1000L;
	private static final Collection<String> FOCUS_MODES_CALLING_AF;

	static {
		FOCUS_MODES_CALLING_AF = new ArrayList<>(2);
		FOCUS_MODES_CALLING_AF.add(Camera.Parameters.FOCUS_MODE_AUTO);
		FOCUS_MODES_CALLING_AF.add(Camera.Parameters.FOCUS_MODE_MACRO);
	}

	private final boolean useAutoFocus;
	private final Camera camera;
	private boolean stopped;
	private boolean focusing;
	private AsyncTask<?, ?, ?> outstandingTask;

	AutoFocusManager(Camera camera) {
		this.camera = camera;
		String currentFocusMode = camera.getParameters().getFocusMode();
		useAutoFocus = FOCUS_MODES_CALLING_AF.contains(currentFocusMode);
		//Log.i(TAG, "Current focus mode '" + currentFocusMode + "'; use auto focus? " + useAutoFocus);
		start();
	}

	@Override
	public synchronized void onAutoFocus(boolean success, Camera theCamera) {
		focusing = false;
		autoFocusAgainLater();
	}

	private synchronized void autoFocusAgainLater() {
		if (!stopped && outstandingTask == null) {
			AutoFocusTask newTask = new AutoFocusTask();
			try {
				newTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
				outstandingTask = newTask;
			} catch (RejectedExecutionException ree) {
				//Log.w(TAG, "Could not request auto focus", ree);
			}
		}
	}

	synchronized void start() {
		if (useAutoFocus) {
			outstandingTask = null;
			if (!stopped && !focusing) {
				try {
					camera.autoFocus(this);
					focusing = true;
				} catch (RuntimeException re) {
					// Have heard RuntimeException reported in Android 4.0.x+; continue?
					//Log.w(TAG, "Unexpected exception while focusing", re);
					// Try again later to keep cycle going
					autoFocusAgainLater();
				}
			}
		}
	}

	private synchronized void cancelOutstandingTask() {
		if (outstandingTask != null) {
			if (outstandingTask.getStatus() != AsyncTask.Status.FINISHED) {
				outstandingTask.cancel(true);
			}
			outstandingTask = null;
		}
	}

	synchronized void stop() {
		stopped = true;
		if (useAutoFocus) {
			cancelOutstandingTask();
			// Doesn't hurt to call this even if not focusing
			try {
				camera.cancelAutoFocus();
			} catch (RuntimeException re) {
				// Have heard RuntimeException reported in Android 4.0.x+; continue?
				//Log.w(TAG, "Unexpected exception while cancelling focusing", re);
			}
		}
	}

	private final class AutoFocusTask extends AsyncTask<Object, Object, Object> {
		@Override
		protected Object doInBackground(Object... voids) {
			try {
				Thread.sleep(AUTO_FOCUS_INTERVAL_MS);
			} catch (InterruptedException e) {
				// continue
			}
			start();
			return null;
		}
	}

}

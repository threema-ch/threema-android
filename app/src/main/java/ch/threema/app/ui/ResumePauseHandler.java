/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2014-2022 Threema GmbH
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

package ch.threema.app.ui;

import android.app.Activity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;

import ch.threema.app.utils.RuntimeUtil;
import ch.threema.app.utils.TestUtil;

public class ResumePauseHandler {
	private static final Logger logger = LoggerFactory.getLogger(ResumePauseHandler.class);

	private static final Map<String, ResumePauseHandler> instances = new HashMap<>();
	private static final Object lock = new Object();

	private final Map<String, RunIfActive> runIfActiveList = new HashMap<String, RunIfActive>();
	private final WeakReference<Activity> activityReference;
	private boolean isActive;
	private boolean hasHandlers = false;

	private ResumePauseHandler(Activity activity) {
		this.activityReference = new WeakReference<>(activity);
	}

	public static ResumePauseHandler getByActivity(Object useInObject, Activity activity) {
		final String key = useInObject.getClass().toString();
		ResumePauseHandler instance = instances.get(key);
		if (instance == null) {
			synchronized (lock) {
				instance = instances.get(key);
				if (instance == null) {
					instance = new ResumePauseHandler(activity);
					instances.put(key, instance);
				}
			}
		}
		return instance;
	}

	public interface RunIfActive {
		void runOnUiThread();
	}

	public void runOnActive(String tag, RunIfActive runIfActive) {
		this.runOnActive(tag, runIfActive, false);
	}
	public void runOnActive(String tag, RunIfActive runIfActive, boolean lowPriority) {
		if(runIfActive == null) {
			return;
		}

		if(this.isActive) {
			this.run(runIfActive);
		}
		else {
			//pending
			synchronized (this.runIfActiveList) {
				if(!lowPriority || !this.runIfActiveList.containsKey(tag)) {
					this.runIfActiveList.put(tag, runIfActive);
					this.hasHandlers = true;
				}
			}
		}
	}

	public void onResume() {
		if(!this.isActive) {
			this.isActive = true;
			if(this.hasHandlers) {
				synchronized (this.runIfActiveList) {
					for(RunIfActive r: this.runIfActiveList.values()) {
						if(r != null && this.isActive) {
							this.run(r);
						}
					}

					this.runIfActiveList.clear();
					this.hasHandlers = false;
				}
			}
		}
	}

	public void onPause() {
		this.isActive = false;
	}

	public void onDestroy(Object object) {
		synchronized (this.runIfActiveList) {
			this.isActive = false;
			this.runIfActiveList.clear();
			instances.remove(object.getClass().toString());
		}
	}

	private boolean run(final RunIfActive runIfActive) {
		if(TestUtil.required(runIfActive, this.activityReference.get())) {
			RuntimeUtil.runOnUiThread(() -> runIfActive.runOnUiThread());
		}
		return true;
	}

}

/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2017-2023 Threema GmbH
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

package ch.threema.app.services;

import android.content.Context;
import android.content.RestrictionsManager;
import android.os.Build;
import android.os.Bundle;

import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;

import java.util.Iterator;
import java.util.Map;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.services.license.UserCredentials;
import ch.threema.app.utils.RuntimeUtil;
import ch.threema.base.ThreemaException;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.domain.protocol.api.APIConnector;
import ch.threema.domain.protocol.api.work.WorkData;
import ch.threema.domain.protocol.api.work.WorkMDMSettings;

/**
 * Hold all Work App Restrictions
 */
public class AppRestrictionService {
	private static final Logger logger = LoggingUtil.getThreemaLogger("AppRestrictionService");

	private Bundle appRestrictions;
	private volatile WorkMDMSettings workMDMSettings;
	private boolean hasExternalMDMRestrictions;
	private static final String PREFERENCE_KEY = "wrk_app_restriction";

	/**
	 * Save the given WorkMDMSettings and reload the AppRestrictions
	 */
	public boolean storeWorkMDMSettings(final WorkMDMSettings settings) {
		if (this.workMDMSettings != settings) {
			if (ThreemaApplication.getServiceManager() != null
					&& ThreemaApplication.getServiceManager().getPreferenceStore() != null) {
				ThreemaApplication.getServiceManager().getPreferenceStore()
						.save(PREFERENCE_KEY, convert(settings), true);
				this.workMDMSettings = settings;
				this.reload();
			}
		}
		return true;
	}

	/**
	 * Get the current fetched or saved WorkMDMSettings
	 */
	public WorkMDMSettings getWorkMDMSettings() {
		if (this.workMDMSettings == null) {
			// Load from preference store
			if (ThreemaApplication.getServiceManager() != null
					&& ThreemaApplication.getServiceManager().getPreferenceStore() != null) {
				JSONObject object = ThreemaApplication.getServiceManager().getPreferenceStore()
						.getJSONObject(PREFERENCE_KEY, true);

				if (object != null) {
					this.workMDMSettings = this.convert(object);
				}
			}
		}
		return this.workMDMSettings;
	}

	/**
	 * Get the source of active mdm parameters in text representation.
	 *
	 * If at least one Threema-MDM parameter and at least one external MDM parameter is active, "me" is returned.
	 * If at least one Threema-MDM parameter is active, append "m" is returned.
	 * If at least one external MDM parameter is active, append "e" is returned.
	 *
	 * (See https://confluence.threema.ch/display/EN/Update+Work+Info)
	 *
	 * @return the source(s) of active mdm parameters as text, null if no mdm parameters are active
	 */
	public @Nullable String getMdmSource() {
		StringBuilder mdmSource = new StringBuilder();
		if (hasThreemaMDMRestrictions()) {
			mdmSource.append("m");
		}
		if (hasExternalMDMRestrictions()) {
			mdmSource.append("e");
		}
		return mdmSource.length() > 0 ? mdmSource.toString() : null;
	}

	/**
	 * Determine if this app is under control of Threema MDM and has at least one parameter set
	 * @return true if Threema MDM is active
	 */
	private boolean hasThreemaMDMRestrictions() {
		return this.workMDMSettings != null && this.workMDMSettings.parameters != null && this.workMDMSettings.parameters.size() > 0;
	}

	/**
	 * Determine if this app is under control of an external MDM/EMM with a local DPC and at least one parameter set
	 * @return true if an external MDM is active
	 */
	private boolean hasExternalMDMRestrictions() {
		return this.hasExternalMDMRestrictions;
	}

	/**
	 * Fetch the MDM Settings
	 */
	@WorkerThread
	public boolean fetchAndStoreWorkMDMSettings(APIConnector apiConnector,
	                                                    UserCredentials credentials) throws Exception {
		// Verify notnull instances
		if (apiConnector == null || credentials == null) {
			return false;
		}

		if (RuntimeUtil.isOnUiThread()) {
			throw new ThreemaException("failed to fetch MDM settings in the main thread");
		}

		// Fetch data from work
		WorkData result = apiConnector.fetchWorkData(
				credentials.username,
				credentials.password,
				new String[]{});

		return null != result && this.storeWorkMDMSettings(result.mdm);
	}

	/**
	 * Reload restriction (without fetching work data)
	 */
	public void reload() {
		if (Build.VERSION.SDK_INT > Build.VERSION_CODES.LOLLIPOP) {
			RestrictionsManager restrictionsManager = (RestrictionsManager)
				ThreemaApplication.getAppContext().getSystemService(Context.RESTRICTIONS_SERVICE);
			this.appRestrictions = restrictionsManager.getApplicationRestrictions();
		}

		if (this.appRestrictions == null) {
			this.appRestrictions = new Bundle();
		}

		hasExternalMDMRestrictions = this.appRestrictions.size() > 0;

		WorkMDMSettings settings = this.getWorkMDMSettings();

		// Get Threema MDM Settings and override
		if (settings != null) {
			for(Map.Entry<String, Object> miniMDMSetting: settings.parameters.entrySet()) {
				if (settings.override
						|| !appRestrictions.containsKey(miniMDMSetting.getKey()))
				{
					if (miniMDMSetting.getValue() instanceof Integer) {
						appRestrictions.putInt(miniMDMSetting.getKey(), (Integer)miniMDMSetting.getValue());
					}
					else if (miniMDMSetting.getValue() instanceof Boolean) {
						appRestrictions.putBoolean(miniMDMSetting.getKey(), (Boolean)miniMDMSetting.getValue());
					}
					else if (miniMDMSetting.getValue() instanceof String) {
						appRestrictions.putString(miniMDMSetting.getKey(), (String)miniMDMSetting.getValue());
					}
					else if (miniMDMSetting.getValue() instanceof Long) {
						appRestrictions.putLong(miniMDMSetting.getKey(), (Long)miniMDMSetting.getValue());
					}
					else if (miniMDMSetting.getValue() instanceof Double) {
						appRestrictions.putDouble(miniMDMSetting.getKey(), (Double)miniMDMSetting.getValue());
					}
				}
			}
		}

	}

	public Bundle getAppRestrictions() {
		return this.appRestrictions;
	}

	/**
	 * Convert a json Object to a valid WorkMDMSettings object
	 */
	public WorkMDMSettings convert(JSONObject jsonObject) {
		WorkMDMSettings settings = new WorkMDMSettings();
		if (null != jsonObject) {
			try {
				if (jsonObject.has("override")) {
					settings.override = jsonObject.getBoolean("override");
				}

				if (jsonObject.has("parameters")) {
					JSONObject parameters  = jsonObject.getJSONObject("parameters");
					Iterator<String> keys = parameters.keys();
					while (keys.hasNext()) {
						String key = keys.next();
						settings.parameters.put(key, parameters.get(key));
					}
				}
			}
			catch (JSONException x) {
				logger.error("failed to convert json to WorkMDMSettings", x);
			}
		}
		return settings;
	}

	/**
	 * Convert a WorkMDMSettings Object to a valid JSON Object
	 */
	public JSONObject convert(WorkMDMSettings mdmSettings) {
		JSONObject json = new JSONObject();
		if (mdmSettings != null) {
			try {
				json.put("override", mdmSettings.override);
				JSONObject parameters = new JSONObject();
				if (mdmSettings.parameters != null) {
					for(Map.Entry<String, Object> settings: mdmSettings.parameters.entrySet()) {
						parameters.put(settings.getKey(), settings.getValue());
					}
				}
				json.put("parameters", parameters);
			}
			catch (JSONException x) {
				logger.error("failed to convert WorkMDMSettings to json", x);
				return null;
			}
		}
		return json;
	}

	/***********************************************************************************************
	 * Singleton Stuff
	 ***********************************************************************************************/
	private static volatile AppRestrictionService instance;
	private static final Object lock = new Object();

	@NonNull
	public static AppRestrictionService getInstance() {
		if (instance == null) {
			synchronized (lock) {
				if (instance == null) {
					instance = new AppRestrictionService();
				}
			}
		}
		return instance;
	}
}

/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2017-2025 Threema GmbH
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

package ch.threema.app.restrictions;

import android.content.Context;
import android.content.RestrictionsManager;
import android.os.Bundle;

import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.managers.ServiceManager;
import ch.threema.app.routines.UpdateWorkInfoRoutine;
import ch.threema.app.services.license.LicenseServiceUser;
import ch.threema.app.stores.EncryptedPreferenceStore;
import ch.threema.domain.models.UserCredentials;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.RuntimeUtil;
import ch.threema.app.utils.TestUtil;
import ch.threema.base.ThreemaException;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.domain.protocol.api.APIConnector;
import ch.threema.domain.protocol.api.work.WorkData;
import ch.threema.domain.protocol.api.work.WorkMDMSettings;
import java8.util.stream.Collectors;
import java8.util.stream.StreamSupport;

/**
 * Hold all Work App Restrictions
 */
public class AppRestrictionService {
    private static final Logger logger = LoggingUtil.getThreemaLogger("AppRestrictionService");

    private Bundle appRestrictions;

    /**
     * Always make sure, that only parameters allowed for the threema MDM are stored in these settings.
     */
    private volatile WorkMDMSettings workMDMSettings;
    private boolean hasExternalMDMRestrictions;
    private static final String PREFERENCE_KEY = "wrk_app_restriction";

    // List of mdm restrictions that shall be ignored when provided by the threema mdm
    private static final List<Integer> EXTERNAL_MDM_ONLY_RESTRICTIONS_IDS = Arrays.asList(
        R.string.restriction__id_backup,
        R.string.restriction__id_backup_password,
        R.string.restriction__safe_password,
        R.string.restriction__license_username,
        R.string.restriction__license_password
    );
    private Set<String> externalMdmOnlyRestrictions;

    AppRestrictionService() {
    }

    /**
     * Save the given WorkMDMSettings and reload the AppRestrictions
     */
    public void storeWorkMDMSettings(@NonNull final WorkMDMSettings settings) {
        if (Objects.equals(this.workMDMSettings, settings)) {
            logger.debug("MDM parameters did not change");
            return;
        }

        ServiceManager serviceManager = ThreemaApplication.getServiceManager();
        if (serviceManager == null) {
            logger.error("Cannot store mdm settings as service manager is null");
            return;
        }

        logger.debug("Store work mdm settings");
        var json = convertWorkMDMToJSON(settings);
        if (json != null) {
            serviceManager.getEncryptedPreferenceStore().save(PREFERENCE_KEY, json);
        }
        this.workMDMSettings = filterWorkMdmSettings(settings);
        this.reload();
    }

    /**
     * Get the current fetched or saved WorkMDMSettings
     */
    public WorkMDMSettings getWorkMDMSettings() {
        if (this.workMDMSettings == null) {
            // Load from preference store
            if (ThreemaApplication.getServiceManager() != null) {
                EncryptedPreferenceStore encryptedPreferenceStore = ThreemaApplication.getServiceManager().getEncryptedPreferenceStore();
                if (encryptedPreferenceStore.containsKey(PREFERENCE_KEY)) {
                    JSONObject object = encryptedPreferenceStore.getJSONObject(PREFERENCE_KEY);
                    if (object != null) {
                        this.workMDMSettings = filterWorkMdmSettings(convertJSONToWorkMDM(object));
                    }
                } else {
                    logger.warn("No work mdm settings stored");
                }
            }
        }
        return this.workMDMSettings;
    }

    /**
     * Get the source of active mdm parameters in text representation.
     * <p>
     * If at least one Threema-MDM parameter and at least one external MDM parameter is active, "me" is returned.
     * If at least one Threema-MDM parameter is active, append "m" is returned.
     * If at least one external MDM parameter is active, append "e" is returned.
     * <p>
     * (See "Update Work Info" in documentation)
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
     * Create a copy of the WorkMDMSettings with parameters removed that are not available for the threema mdm.
     * This is to prevent invalid parameters from a malicious threema mdm server.
     */
    @NonNull
    @VisibleForTesting()
    public WorkMDMSettings filterWorkMdmSettings(@NonNull WorkMDMSettings unfilteredSettings) {
        Set<String> nonWorkMdmSettings = getExternalMdmOnlyRestrictions();
        WorkMDMSettings filteredSettings = new WorkMDMSettings();
        filteredSettings.override = unfilteredSettings.override;
        for (Map.Entry<String, Object> entry : unfilteredSettings.parameters.entrySet()) {
            if (!nonWorkMdmSettings.contains(entry.getKey())) {
                filteredSettings.parameters.put(entry.getKey(), entry.getValue());
            } else {
                logger.warn("Non work mdm restriction in WorkMDMSettings: {}", entry.getKey());
            }
        }
        return filteredSettings;
    }

    private Set<String> getExternalMdmOnlyRestrictions() {
        synchronized (EXTERNAL_MDM_ONLY_RESTRICTIONS_IDS) {
            Context context = ThreemaApplication.getAppContext();
            if (externalMdmOnlyRestrictions == null) {
                externalMdmOnlyRestrictions = StreamSupport.stream(EXTERNAL_MDM_ONLY_RESTRICTIONS_IDS)
                    .map(context::getString)
                    .collect(Collectors.toSet());
            }
        }
        return externalMdmOnlyRestrictions;
    }

    /**
     * Determine if this app is under control of Threema MDM and has at least one parameter set
     *
     * @return true if Threema MDM is active
     */
    private boolean hasThreemaMDMRestrictions() {
        return this.workMDMSettings != null && !this.workMDMSettings.parameters.isEmpty();
    }

    /**
     * Determine if this app is under control of an external MDM/EMM with a local DPC and at least one parameter set
     *
     * @return true if an external MDM is active
     */
    private boolean hasExternalMDMRestrictions() {
        return this.hasExternalMDMRestrictions;
    }

    /**
     * Fetch the MDM Settings
     */
    @WorkerThread
    public void fetchAndStoreWorkMDMSettings(APIConnector apiConnector, UserCredentials credentials) throws Exception {
        // Verify notnull instances
        if (apiConnector == null || credentials == null) {
            return;
        }

        if (RuntimeUtil.isOnUiThread()) {
            throw new ThreemaException("failed to fetch MDM settings in the main thread");
        }

        // Fetch data from work
        WorkData result = apiConnector.fetchWorkData(
            credentials.username,
            credentials.password,
            new String[]{}
        );

        if (result.responseCode > 0) {
            logger.error("Failed to fetch2 work data. Server response code = {}", result.responseCode);
        } else {
            this.storeWorkMDMSettings(result.mdm);
        }
    }

    /**
     * Reload restriction (without fetching work data)
     */
    public void reload() {
        Context context = ThreemaApplication.getAppContext();
        RestrictionsManager restrictionsManager = (RestrictionsManager) context.getSystemService(Context.RESTRICTIONS_SERVICE);
        this.appRestrictions = restrictionsManager.getApplicationRestrictions();

        if (this.appRestrictions == null) {
            this.appRestrictions = new Bundle();
        }

        hasExternalMDMRestrictions = !this.appRestrictions.isEmpty();

        if (ConfigUtils.isWorkBuild() && hasExternalMDMRestrictions) {
            updateUserCredentials();
        }

        WorkMDMSettings settings = this.getWorkMDMSettings();

        // Get Threema MDM Settings and override
        if (settings != null) {
            for (Map.Entry<String, Object> miniMDMSetting : settings.parameters.entrySet()) {
                if (settings.override
                    || !appRestrictions.containsKey(miniMDMSetting.getKey())) {
                    if (miniMDMSetting.getValue() instanceof Integer) {
                        appRestrictions.putInt(miniMDMSetting.getKey(), (Integer) miniMDMSetting.getValue());
                    } else if (miniMDMSetting.getValue() instanceof Boolean) {
                        appRestrictions.putBoolean(miniMDMSetting.getKey(), (Boolean) miniMDMSetting.getValue());
                    } else if (miniMDMSetting.getValue() instanceof String) {
                        appRestrictions.putString(miniMDMSetting.getKey(), (String) miniMDMSetting.getValue());
                    } else if (miniMDMSetting.getValue() instanceof Long) {
                        appRestrictions.putLong(miniMDMSetting.getKey(), (Long) miniMDMSetting.getValue());
                    } else if (miniMDMSetting.getValue() instanceof Double) {
                        appRestrictions.putDouble(miniMDMSetting.getKey(), (Double) miniMDMSetting.getValue());
                    }
                }
            }
        }

        ApplyAppRestrictionsWorker.Companion.applyAppRestrictions(context);
    }

    public Bundle getAppRestrictions() {
        return this.appRestrictions;
    }

    /**
     * Convert a json Object to a valid WorkMDMSettings object
     */
    @NonNull
    public WorkMDMSettings convertJSONToWorkMDM(@Nullable JSONObject jsonObject) {
        WorkMDMSettings settings = new WorkMDMSettings();
        if (null != jsonObject) {
            try {
                if (jsonObject.has("override")) {
                    settings.override = jsonObject.getBoolean("override");
                }

                if (jsonObject.has("parameters")) {
                    JSONObject parameters = jsonObject.getJSONObject("parameters");
                    Iterator<String> keys = parameters.keys();
                    while (keys.hasNext()) {
                        String key = keys.next();
                        settings.parameters.put(key, parameters.get(key));
                    }
                }
            } catch (JSONException x) {
                logger.error("failed to convert json to WorkMDMSettings", x);
            }
        }
        return settings;
    }

    /**
     * Convert a WorkMDMSettings Object to a valid JSON Object
     */
    @Nullable
    public JSONObject convertWorkMDMToJSON(@Nullable WorkMDMSettings mdmSettings) {
        JSONObject json = new JSONObject();
        if (mdmSettings != null) {
            try {
                json.put("override", mdmSettings.override);
                JSONObject parameters = new JSONObject();
                for (Map.Entry<String, Object> settings : mdmSettings.parameters.entrySet()) {
                    parameters.put(settings.getKey(), settings.getValue());
                }
                json.put("parameters", parameters);
            } catch (JSONException x) {
                logger.error("failed to convert WorkMDMSettings to json", x);
                return null;
            }
        }
        return json;
    }

    /**
     * Update the stored credentials if changed username or password are provided via mdm.
     */
    private void updateUserCredentials() {
        ServiceManager serviceManager = ThreemaApplication.getServiceManager();
        if (serviceManager != null) {
            Context context = serviceManager.getContext();
            @Nullable String mdmUsername = getStringRestriction(context.getString(R.string.restriction__license_username));
            @Nullable String mdmPassword = getStringRestriction(context.getString(R.string.restriction__license_password));

            if (TestUtil.isEmptyOrNull(mdmUsername) && TestUtil.isEmptyOrNull(mdmPassword)) {
                logger.debug("No credentials provided via mdm");
                return;
            }

            LicenseServiceUser licenseService = getLicenseService(serviceManager);
            if (licenseService == null) {
                logger.error("User license service not available");
                return;
            }

            UserCredentials currentCredentials = licenseService.loadCredentials();
            UserCredentials mergedCredentials = mergeCurrentAndMdmCredentials(
                currentCredentials,
                mdmUsername,
                mdmPassword
            );

            if (mergedCredentials != null && !mergedCredentials.equals(currentCredentials)) {
                logger.info("Update changed work credentials");
                licenseService.saveCredentials(mergedCredentials);

                if (serviceManager.getUserService().hasIdentity()) {
                    updateWorkInfo(licenseService);
                }
            }
        } else {
            logger.warn("Could not update mdm credentials. Service manager not available");
        }
    }

    /**
     * Updates the work info if the stored credentials are valid.
     */
    @AnyThread
    private void updateWorkInfo(@NonNull LicenseServiceUser licenseService) {
        logger.info("Schedule work info update");
        new Thread(() -> {
            String error = licenseService.validate(false);
            if (error == null) {
                UpdateWorkInfoRoutine routine = UpdateWorkInfoRoutine.create();
                if (routine != null) {
                    routine.run();
                }
            } else {
                logger.info("Credentials could not be validated, do not update work info: {}", error);
            }
        }).start();
    }

    @Nullable
    private LicenseServiceUser getLicenseService(@NonNull ServiceManager serviceManager) {
        try {
            return (LicenseServiceUser) serviceManager.getLicenseService();
        } catch (ClassCastException e) {
            logger.error("Could not get license service", e);
            return null;
        }
    }

    @Nullable
    private UserCredentials mergeCurrentAndMdmCredentials(
        @Nullable UserCredentials currentCredentials,
        @Nullable String mdmUsername,
        @Nullable String mdmPassword
    ) {
        @Nullable String currentUsername = currentCredentials != null ? currentCredentials.username : null;
        @Nullable String currentPassword = currentCredentials != null ? currentCredentials.password : null;

        String username = !TestUtil.isEmptyOrNull(mdmUsername) && !mdmUsername.equals(currentUsername)
            ? mdmUsername
            : currentUsername;

        String password = !TestUtil.isEmptyOrNull(mdmPassword) && !mdmPassword.equals(currentPassword)
            ? mdmPassword
            : currentPassword;

        return username != null && password != null
            ? new UserCredentials(username, password)
            : null;
    }

    @Nullable
    private String getStringRestriction(String key) {
        if (appRestrictions != null && appRestrictions.containsKey(key)) {
            String value = appRestrictions.getString(key);
            if (!TestUtil.isEmptyOrNull(value)) {
                return value;
            }
        }
        return null;
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

/*
 * Copyright 2020. Huawei Technologies Co., Ltd. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.DrmSDK.util;

import android.content.Context;

/**
 * DeviceSession 类
 *
 * @since 2020/07/01
 */
public class DeviceSession {

    private static final Object SESSION_LOCK = new Object();

    private static final String APP_STORE_PACKAGE_NAME = "drmsdk.appStorePackageName";

    private static final String APP_STORE_DOWNLOAD_URL = "drmsdk.appStoreDownloadUrl";

    private static final String APP_STORE_BUSINESS = "drmsdk.appStoreBusiness";

    private static DeviceSession deviceSession;
    private SharedPreferencesWrapper mPreferences;

    private DeviceSession(Context context) {
        mPreferences = SharedPreferencesWrapper.getSharedPreference("DeviceSessionDrmSDK_V1", context);
    }

    /**
     * 获取Session
     * Obtains sessions.
     *
     * @return DeviceSession devicesession
     */
    public static DeviceSession getSession() {
        synchronized (SESSION_LOCK) {
            if (null == deviceSession) {
                deviceSession = new DeviceSession(ApplicationWrapper.getInstance().getContext());
            }
            return deviceSession;
        }
    }


    /**
     * 保存 business
     * Save business
     *
     * @param appStoreBusiness business
     */
    public void setAppStoreBusiness(String appStoreBusiness) {
        mPreferences.putString(APP_STORE_BUSINESS, appStoreBusiness);
    }

    /**
     * 获取business
     * Obtains business.
     */
    public String getAppStoreBusiness() {
        return mPreferences.getString(APP_STORE_BUSINESS, "");
    }

    /**
     * 保存获取到应用安装来源
     * Save the obtained application installation source.
     *
     * @param appStorePkgName 应用市场
     */
    public void setAppStorePkgName(String appStorePkgName) {
        mPreferences.putString(APP_STORE_PACKAGE_NAME, appStorePkgName);
    }


    /**
     * 获取缓存中的安装来源
     * Gets the installation source in the cache.
     *
     * @return 安装来源
     */
    public String getAppStorePkgName() {
        return mPreferences.getString(APP_STORE_PACKAGE_NAME, "");
    }

    /**
     * 缓存应用市场的下载地址
     * Cache AppGallery Download Address
     *
     * @param appStoreDownloadUrl 下载地址
     */
    public void setAppStoreDownloadUrl(String key, String appStoreDownloadUrl) {
        mPreferences.putString(key, appStoreDownloadUrl);
    }

    /**
     * 获取缓存的应用市场下载的地址
     * Obtains the cache download address from the App Store.
     *
     * @return 下载地址(Download URL)
     */
    public String getAppStoreDownloadUrl(String key) {
        return mPreferences.getString(key, "");
    }

}

/*
 * Copyright 2020. Huawei Technologies Co., Ltd. All rights reserved.

 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at

 *http://www.apache.org/licenses/LICENSE-2.0

 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.DrmSDK;

import android.app.Activity;
import android.util.Log;

import com.DrmSDK.util.ApplicationWrapper;
import com.DrmSDK.util.DeviceSession;

/**
 * DRM对外接口类
 * DRM external interface class
 *
 * @since 2020/07/01
 */
public class Drm {

    private static final String TAG = "DrmLite";

    /**
     * Checking whether user has purchased this application or not.
     *
     * @param activity        Main activity of application that needs verification.
     * @param pkgName         Package name of application that needs verification.
     * @param drmId           Drm id that assigned by The Huawei Developer.
     * @param drmPublicKey    Drm public key that assigned by The Huawei Developer.
     * @param showErrorDialog value indicates whether the SDK displays error messages. Default is true.
     * @param callback        Callback of check result.
     */
    public static void check(final Activity activity, final String pkgName, final String drmId, final String drmPublicKey, final boolean showErrorDialog, final DrmCheckCallback callback) {
        Log.i(TAG, "begin check showErrorDialog" + showErrorDialog);
        Log.d(TAG, "check: pkgName=" + pkgName);
        ApplicationWrapper.init(activity);
        checkFromCache(activity, pkgName, drmId, drmPublicKey, showErrorDialog, callback);
    }

    /**
     * Checking whether user has purchased this application or not.
     *
     * @param activity     Main activity of application that needs verification.
     * @param pkgName      Package name of application that needs verification.
     * @param drmId        Drm id that assigned by The Huawei Developer.
     * @param drmPublicKey Drm public key that assigned by The Huawei Developer.
     * @param callback     Callback of check result.
     */
    public static void check(Activity activity, String pkgName, String drmId, String drmPublicKey, DrmCheckCallback callback) {
        Log.i("Drm", "begin check");
        check(activity, pkgName, drmId, drmPublicKey, true, callback);
    }

    /**
     * 从缓存里获取数据
     * Obtain data from the cache.
     */
    private static void checkFromCache(Activity activity, String pkgName, String drmId, String publicKey, Boolean showErrorDailog, DrmCheckCallback callback) {
        String appStorePkgName = DeviceSession.getSession().getAppStorePkgName();
        String appStoreBusiness = DeviceSession.getSession().getAppStoreBusiness();
        Log.d(TAG, "checkFromCache: appStoreBusiness=" + appStoreBusiness + " appStorePkgName=" + appStorePkgName);
        DrmKernel.setAppStoreBusiness(appStoreBusiness);
        DrmKernel.check(activity, pkgName, drmId, publicKey, appStorePkgName, showErrorDailog, callback);
    }
}

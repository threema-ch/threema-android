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
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.IBinder;
import android.os.RemoteException;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;
import android.widget.Toast;

import com.DrmSDK.util.NetUtils;
import com.DrmSDK.util.ReportUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.UnsupportedEncodingException;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.SignatureException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import ch.threema.app.R;

/**
 * DRM主处理类
 * DRM main processing class.
 *
 * @since 2020/07/01
 */
public class DrmKernel {
    /**
     * 字符集
     * character set
     */
    private static final String CHARSET = "utf-8";

    /**
     * 运营商类型
     * Carrier Type
     */
    private static String appStoreBusiness = "";
    /**
     * 应用的安装来源
     * Application installation source
     */
    private static String appStorePkgName;
    /**
     * 应用市场包名
     * AppGallery Package Name
     */
    private static final String PACKAGE_NAME_HIAPP = "com.huawei.appmarket";
    /**
     * 应用市场获取签名service的Action
     * Action for obtaining the signature service in the AppGallery
     */
    private static final String SERVICE_ACTION = "com.huawei.appmarket.drm.GET_SIGN";

    /**
     * 签名有效期（3天）
     * Signature validity period (3 days)
     */
    private static final int TIME_VALID_CACHE = 3 * 24 * 60 * 60 * 1000;
    /**
     * 重试的次数
     * Retry times
     */
    private static int sTimes = 0;
    /**
     * 最大的重试次数
     * Maximum number of retry times
     */
    private static final int MAX_TIMES = 1;
    /**
     * 参数传入的开发者Id
     * Developer ID transferred in the parameter.
     */
    private static String sDeveloperId;
    /**
     * 参数传入的公钥
     * Public key input by the parameter
     */
    private static String sPublicKey;
    /**
     * 参数传入的程序包名
     * Package name passed by the parameter.
     */
    private static String sPkgName;
    /**
     * 参数传入的第三方Activity实例
     * Third-party activity instance transferred through parameters
     */
    private static Activity sActivity;
    /**
     * 唯一设备号
     * Unique device ID.
     */
    private static String sPayDeviceId;
    /**
     * 是否需要DRM SDK根据错误码提示用户
     * Indicates whether the DRM SDK prompts users based on error codes.
     */
    private static boolean sShowErrorDailog = true;
    /**
     * DrmCheckCallback实例
     * DrmCheckCallback instance
     */
    private static DrmCheckCallback sCallback;
    /**
     * 鉴权结果:成功
     * Authentication result: success
     */
    public static final int CHECK_SUCCESS = 0;
    /**
     * 鉴权结果：失败
     * Authentication result: Failed
     */
    public static final int CHECK_FAILED = 1;
    /**
     * 版本号
     * Version number
     */
    private static final String VERSION_CODE = "v1.0.0.300";
    /**
     * 支持购买的应用市场版本号
     * AppGallery versions that can be purchased
     */
    private static final int VERSION_CODE_STORE = 70201304;
    /**
     * 当前是否在进行鉴权操作
     * Indicates whether authentication is being performed.
     */
    private static boolean sIsRunning = false;
    /**
     * 是否按返回键取消
     * Press the back key to cancel the operation.
     */
    private static boolean sIsInterrupt = false;
    /**
     * 是否绑定服务
     * Bind Service
     */
    private static boolean sIsBound = false;
    /**
     * 应用详情Action
     * Application Details Action
     */
    private static String sAppAction = null;
    /**
     * 应用市场服务
     * AppGallery service
     */
    private static IDrmSignService sSignService;
    /**
     * 通过AIDL连接的市场的连接
     * Connectivity to markets connected via AIDL
     */
    private static DrmServiceConnection conn = new DrmServiceConnection();
    /**
     * Coherent slf4j logger
     */
    private static final Logger logger = LoggerFactory.getLogger(DrmKernel.class);

    /**
     * Checking whether user has purchased this application or not.
     *
     * @param activity        main activity of application that needs verification.
     * @param pkgName         package name of application that needs verification.
     * @param drmId           Drm id that assigned by The Huawei Developer.
     * @param publicKey       Drm public key that assigned by The Huawei Developer.
     * @param showErrorDailog value indicates whether the SDK displays error messages. Default is true.
     * @param callback        Public key that assigned by The Huawei Developer.
     */
    public static void check(Activity activity, String pkgName, String drmId, String publicKey, String appStoreName,
                             Boolean showErrorDailog, DrmCheckCallback callback) {
        sCallback = callback;
        // Multiple authentication operations cannot be performed at the same time.
        if (sIsRunning) {
            logger.error("check sIsRunning true, end!");
            return;
        }

        if (!TextUtils.isEmpty(appStoreName)) {
            appStorePkgName = appStoreName;
            logger.error(appStorePkgName);
        }
        logger.info(VERSION_CODE);
        sActivity = activity;

        sDeveloperId = drmId;
        sPublicKey = publicKey;
        sPkgName = pkgName;
        sShowErrorDailog = showErrorDailog;
        sIsRunning = true;
        sIsInterrupt = false;
        showDialogOrReturnCode(ViewHelper.DIALOG_WAITING, DrmStatusCodes.CODE_CANCEL);
    }

    /**
     * 运营商类型
     * Carrier Type
     */
    public static void setAppStoreBusiness(String storeBusiness) {
        appStoreBusiness = storeBusiness;
        logger.debug("setAppStoreBusiness: " + appStoreBusiness);
    }

    /**
     * 处理拉起的Activity的回调。
     * Process the callback of the started activity.
     *
     * @param resultCode 结果值(Result value)。
     */
    public static void onActivityResult(int resultCode) {
        logger.info("resultCode: " + resultCode);
        switch (resultCode) {
            // 同意协议或登陆成功后重试鉴权
            // Agree to the agreement or retry authentication after successful login.
            case Constants.RESULT_CODE_AGREEMENT_AGREED:
            case Constants.RESULT_CODE_LOGIN_SUCCESS:
            case Constants.RESULT_CODE_JOIN_MEMBER_SUCCESS:
                retryCheck();
                break;
            // 不同意协议不能继续使用程序
            // Do not agree to the agreement and cannot continue using the program.
            case Constants.RESULT_CODE_AGREEMENT_DECLINED:
                logger.info("RESULT_CODE_AGREEMENT_DECLINED Hiapp");
                report(ReportUtils.KEY_GET_SIGN_FAILED,
                    ReportUtils.CODE_HIAPP_AGREEMENT_DECLINED, null);
                showDialogOrReturnCode(ViewHelper.DIALOG_HIAPP_AGREEMENT_DECLINED, DrmStatusCodes.CODE_PROTOCAL_UNAGREE);
                break;
            // 不登陆不能继续使用程序
            // You cannot continue using the program without logging in.
            case Constants.RESULT_CODE_LOGIN_FAILED:
                report(ReportUtils.KEY_GET_SIGN_FAILED,
                    ReportUtils.CODE_ACCOUNT_NOT_LOGGED, null);
                showDialogOrReturnCode(ViewHelper.DIALOG_NOT_LOGGED, DrmStatusCodes.CODE_LOGIN_FAILED);
                break;
            case Constants.RESULT_CODE_JOIN_MEMBER_FAILED:
                logger.error("RESULT_CODE_JOIN_MEMBER_FAILED");
                sActivity.runOnUiThread(new DrmCheckFailRunnable(DrmStatusCodes.CODE_INVALID_MEMBERSHIP));
                unbindService();
                break;
            case Activity.RESULT_CANCELED:
                sIsInterrupt = true;
                report(ReportUtils.KEY_CHECK_SIGN_FAILED,
                    ReportUtils.CODE_USER_INTERRUPT, null);
                unbindService();
                showDialogOrReturnCode(ViewHelper.DIALOG_USER_INTERRUPT, DrmStatusCodes.CODE_CANCEL);
                break;
            default:
                break;
        }
    }

    /**
     * 对话框按键处理
     * Dialog Key Processing
     *
     * @param action 处理类型(Processing type)
     */
    public static void onDialogClicked(int action, int errorCode) {
        logger.info("onDialogClicked " + action);
        switch (action) {
            case ViewHelper.OPERATION_NONE:
                sCallback.onCheckFailed(errorCode);
                sIsRunning = false;
                break;
            case ViewHelper.OPERATION_RETRY:
            case ViewHelper.OPERATION_AGREEMENT:
            case ViewHelper.OPERATION_LOGIN:
                retryCheck();
                break;
            case ViewHelper.OPERATION_BUY:
                // 点击购买必然跳转到详情页，直接用该action
                // When a user clicks Purchase, the details page is displayed. The action is used.
                Intent buyIntent = new Intent(Constants.DOOR_TO_ALLY_OF_DETAIL);
                buyIntent.putExtra("APP_PACKAGENAME", sPkgName);
                buyIntent.setPackage(PACKAGE_NAME_HIAPP);
                sActivity.startActivity(buyIntent);
                sCallback.onCheckFailed(errorCode);
                sIsRunning = false;
                break;
            case ViewHelper.OPERATION_INSTALL:
                // 捕获异常，导致无浏览器崩溃。
                //Exceptions are captured, causing crashes due to no browser.
                String downloadurl;
                downloadurl = sActivity.getResources().getString(R.string.hiapp_download_url);
                if (!TextUtils.isEmpty(downloadurl)) {
                    logger.debug("downloadurl: " + downloadurl);
                    try {
                        Intent intent = new Intent(Intent.ACTION_VIEW,
                            Uri.parse(downloadurl));
                        sActivity.startActivity(intent);
                    } catch (ActivityNotFoundException e) {
                        logger.error("OPERATION_INSTALL ActivityNotFoundException!");
                    }
                }
                sCallback.onCheckFailed(errorCode);
                sIsRunning = false;
                break;
            case ViewHelper.OPERATION_USER_INTERRUPT:
                sIsInterrupt = true;
                report(ReportUtils.KEY_CHECK_SIGN_FAILED,
                    ReportUtils.CODE_USER_INTERRUPT, null);
                unbindService();
                showDialogOrReturnCode(ViewHelper.DIALOG_USER_INTERRUPT, DrmStatusCodes.CODE_CANCEL);
                break;
            case ViewHelper.OPERATION_LOGIN_CHANGE:
                // 跳转至账号切换页面, 使用双包名
                // Switch to the account switching page and use the dual package name.
                try {
                    // 获取HMS包名
                    // Obtaining the HMS Package Name
                    List<ResolveInfo> resolveInfoList = sActivity.getApplicationContext()
                        .getPackageManager()
                        .queryIntentServices(new Intent("com.huawei.hms.core.aidlservice"),
                            PackageManager.GET_META_DATA);
                    if (resolveInfoList != null && resolveInfoList.size() > 0) {
                        ResolveInfo resolveInfo = resolveInfoList.get(0);
                        String packageName = "";

                        if (resolveInfo != null) {
                            packageName = resolveInfo.serviceInfo.applicationInfo.packageName;
                        }

                        if (!TextUtils.isEmpty(packageName)) {
                            Intent intent = new Intent("com.huawei.hwid.ACTION_MAIN_SETTINGS");
                            intent.setPackage(packageName);
                            sActivity.startActivity(intent);
                        }
                    }
                } catch (ActivityNotFoundException e) {
                    logger.error("DrmKernel OPERATION_LOGIN_CHANGE ActivityNotFoundException!");
                }
                sCallback.onCheckFailed(errorCode);
                sIsRunning = false;
                break;
            default:
                break;
        }
    }

    /**
     * 鉴权等待对话框弹出后连接远程服务
     * Connect to the remote service after the authentication waiting dialog box is displayed.
     */
    public static void initBinding() {
        logger.info("initBinding");
        if (!bindService(PACKAGE_NAME_HIAPP)) {
            logger.error("bindService HIAPP failure");
            showDialogWhenStoreNotAvailable();
        }
    }


    private static void startActivity(String action, String extra) {
        logger.info("startActivity(action, extra)");
        Intent intent = new Intent();
        intent.putExtra(Constants.KEY_EXTRA_ACTION, action);
        intent.putExtra(Constants.KEY_EXTRA_PACKAGE, PACKAGE_NAME_HIAPP);
        intent.putExtra(Constants.KEY_EXTRA_EXTRA, extra);
        intent.setClass(sActivity, DrmDialogActivity.class);
        sActivity.startActivity(intent);
    }

    /**
     * 重试鉴权流程。
     * Retry the authentication process.
     */
    private static void retryCheck() {
        sIsRunning = false;
        check(sActivity, sPkgName, sDeveloperId, sPublicKey, appStorePkgName, sShowErrorDailog, sCallback);
    }

    /**
     * 绑定市场service。
     * Bind the market service.
     *
     * @param pkgName 市场的包名(Market Package Name)。
     * @return 绑定结果，市场不存在或不支持鉴权返回false(Binding result. If the market does not exist or authentication is not supported, false is returned.)。
     */
    private static boolean bindService(String pkgName) {
        boolean isSuccess = false;
        Intent serviceIntent = new Intent(SERVICE_ACTION);
        serviceIntent.setPackage(pkgName);

        isSuccess = sActivity.getApplicationContext().bindService(serviceIntent, conn,
            Context.BIND_AUTO_CREATE);
        sIsBound = isSuccess;
        return isSuccess;
    }

    /**
     * 解绑市场服务
     * Unbind Market Service
     */
    private static void unbindService() {
        if (sIsBound) {
            sActivity.getApplicationContext().unbindService(conn);
            sIsBound = false;
        }
    }

    /**
     * Verify Signature.
     *
     * @param payDeviceId Unique ID of a device.
     * @param publicKey   Public key
     * @param signData    Number of milliseconds when signatures are obtained from the server
     * @param sign        Signature returned by the Marketplace
     * @return true：the verification is successful. false: The verification fails.
     * @throws NoSuchAlgorithmException
     * @throws InvalidKeySpecException
     * @throws InvalidKeyException
     * @throws UnsupportedEncodingException
     * @throws SignatureException
     */
    private static boolean verify(String payDeviceId, String publicKey, String signData, String sign)
        throws NoSuchAlgorithmException, InvalidKeySpecException, InvalidKeyException,
        SignatureException, UnsupportedEncodingException, IllegalArgumentException {

        if (TextUtils.isEmpty(publicKey) || TextUtils.isEmpty(sign)) {
            logger.error("publicKey empty or sign empty");
            return false;
        }

        sPayDeviceId = payDeviceId;
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        byte[] encodedKey = null;
        try {
            encodedKey = Base64.decode(publicKey, Base64.DEFAULT);
        } catch (Exception e) {
            logger.error("Base64 decode Exception: {}", e.getClass().getSimpleName());
            return false;
        }

        PublicKey pubKey = keyFactory.generatePublic(new X509EncodedKeySpec(
            encodedKey));

        java.security.Signature signature = java.security.Signature
            .getInstance("SHA256WithRSA");
        signature.initVerify(pubKey);
        signature.update(signData.getBytes(CHARSET));

        boolean verifyResult = false;
        try {
            verifyResult = signature.verify(Base64.decode(sign, Base64.DEFAULT));
        } catch (Exception e) {
            logger.error("verify Base64 decode Exception: {}", e.getClass().getSimpleName());
        }

        return verifyResult;
    }

    /**
     * 通过Activity弹出对话框或直接返回错误码
     * A dialog box is displayed or an error code is returned.
     *
     * @param type      对话框类型(Dialog Type)
     * @param errorCode 鉴权失败错误码(Authentication failure error code)
     */
    private static void showDialogOrReturnCode(int type, int errorCode) {
        if (sShowErrorDailog || type == ViewHelper.DIALOG_WAITING) {
            sActivity.runOnUiThread(new DrmStartActivityRunnable(type, null, errorCode));
        } else {
            sActivity.runOnUiThread(new DrmCheckFailRunnable(errorCode));
        }

    }

    /**
     * 通过Activity弹出对话框或直接返回错误码
     * A dialog box is displayed or an error code is returned.
     *
     * @param type      对话框类型(Dialog Type)
     * @param extra     额外参数(Additional parameters)
     * @param errorCode 鉴权失败错误码(Authentication failure error code)
     */
    private static void showDialogOrReturnCode(int type, String extra, int errorCode) {
        if (sShowErrorDailog || type == ViewHelper.DIALOG_WAITING) {
            sActivity.runOnUiThread(new DrmStartActivityRunnable(type, extra, errorCode));
        } else {
            sActivity.runOnUiThread(new DrmCheckFailRunnable(errorCode));
        }

    }


    /**
     * 应用市场版本不支持。
     * The HiApp version is not supported.
     */
    private static void showDialogWhenStoreNotAvailable() {
        if (NetUtils.isNetworkAvailable(sActivity)) {
            report(ReportUtils.KEY_GET_SIGN_FAILED,
                ReportUtils.CODE_STORE_NOT_AVAILABLE, null);
            showDialogOrReturnCode(ViewHelper.DIALOG_STORE_NOT_AVAILABLE, DrmStatusCodes.CODE_APP_UNSUPPORT);
        } else {
            report(ReportUtils.KEY_GET_SIGN_FAILED,
                ReportUtils.CODE_NO_NETWORK, null);
            showDialogOrReturnCode(ViewHelper.DIALOG_NO_NETWORK, DrmStatusCodes.CODE_NEED_INTERNET);
        }
    }

    /**
     * 处理市场服务返回的结果
     * Processing the result returned by the marketing service
     *
     * @param result 市场服务返回的结果(Result returned by the marketing service.)
     */
    private static void onDrmSignResult(Map result) {
        logger.info("onDrmSignResult");
        // 获取签名失败，验签失败
        // Failed to obtain the signature. Signature verification failed.
        if (result == null) {
            logger.error("result empty");
            report(ReportUtils.KEY_GET_SIGN_FAILED,
                ReportUtils.CODE_GET_SIGN_FAILED, null);
            showDialogOrReturnCode(ViewHelper.DIALOG_GET_DRM_SIGN_FAILED, DrmStatusCodes.CODE_DEFAULT);
            // 解除service绑定
            // Unbind a service.
            unbindService();
            return;
        }
        String version = (String) result.get("appstore_version");
        if (ViewHelper.isEmpty(version)) {
            logger.error("version code empty");
            showDialogWhenStoreNotAvailable();
            unbindService();
            return;
        } else {
            try {
                int versionCode = Integer.parseInt(version);
                if (versionCode < VERSION_CODE_STORE) {
                    logger.error("low version code");
                    showDialogWhenStoreNotAvailable();
                    unbindService();
                    return;
                }
            } catch (NumberFormatException e) {
                logger.error("version code NumberFormatException");
                showDialogWhenStoreNotAvailable();
                unbindService();
                return;
            }

        }
        // 根据返回值处理
        // Processing based on the return value
        Object obj = result.get("rtnCode");
        int code = DrmStatusCodes.CODE_DEFAULT;
        if (obj instanceof String) {
            try {
                code = Integer.parseInt((String) obj);
            } catch (NumberFormatException e) {
                logger.error("rtnCode NumberFormatException");
            }
        } else if (obj instanceof Integer) {
            code = (Integer) obj;
        }
        logger.info("code: {}", code);
        handleReturnCode(result, code);
    }

    /**
     * 处理验签流程
     * Process the signature verification process
     *
     * @param result 市场服务返回的结果(Result returned by the marketing service.)
     */
    private static void verifyProcess(Map result, String ts) {
        List<Map> signList = (List<Map>) result.get("signList");
        if (signList == null || signList.size() == 0) {
            logger.error("signList empty");
            report(ReportUtils.KEY_CHECK_SIGN_FAILED,
                ReportUtils.CODE_SIGN_EMPTY, null);
            showDialogOrReturnCode(ViewHelper.DIALOG_GET_DRM_SIGN_FAILED, DrmStatusCodes.CODE_DEFAULT);
            return;
        }
        boolean verifyResult = false;
        for (int i = 0; i < signList.size(); i++) {
            Map map = signList.get(i);

            if (map == null) {
                logger.error("map empty");
                report(ReportUtils.KEY_CHECK_SIGN_FAILED,
                    ReportUtils.CODE_SIGN_EMPTY, null);
                showDialogOrReturnCode(ViewHelper.DIALOG_GET_DRM_SIGN_FAILED, DrmStatusCodes.CODE_DEFAULT);
                return;
            }

            String payDeviceId = (String) map.get("payDeviceId");
            String signItem = (String) map.get("signItem");
            // 构造验签的明文信息（顺序不能改变）
            String signData = payDeviceId + sPkgName + sDeveloperId + ts;
            final String formattedSignData = payDeviceId + '|' + sPkgName + '|' + sDeveloperId + '|' + ts;

            // 验签
            try {
                verifyResult = verify(payDeviceId,
                    sPublicKey, signData, signItem) || verifyResult;

            } catch (InvalidKeyException e) {
                report(ReportUtils.KEY_CHECK_SIGN_FAILED,
                    ReportUtils.CODE_CHECK_FAILED, "InvalidKeyException");
                logger.error("InvalidKeyException ");
            } catch (NoSuchAlgorithmException e) {
                report(ReportUtils.KEY_CHECK_SIGN_FAILED,
                    ReportUtils.CODE_CHECK_FAILED,
                    "NoSuchAlgorithmException");
                logger.error("NoSuchAlgorithmException ");
            } catch (InvalidKeySpecException e) {
                report(ReportUtils.KEY_CHECK_SIGN_FAILED,
                    ReportUtils.CODE_CHECK_FAILED,
                    "InvalidKeySpecException");
                logger.error("InvalidKeySpecException ");
            } catch (SignatureException e) {
                report(ReportUtils.KEY_CHECK_SIGN_FAILED,
                    ReportUtils.CODE_CHECK_FAILED, "SignatureException");
                logger.error("SignatureException ");
            } catch (UnsupportedEncodingException e) {
                report(ReportUtils.KEY_CHECK_SIGN_FAILED,
                    ReportUtils.CODE_CHECK_FAILED,
                    "UnsupportedEncodingException");
                logger.error("UnsupportedEncodingException ");
            } catch (IllegalArgumentException e) {
                report(ReportUtils.KEY_CHECK_SIGN_FAILED,
                    ReportUtils.CODE_CHECK_FAILED,
                    "IllegalArgumentException");
                logger.error("IllegalArgumentException：", e);
            }
            // 验签通过的话，直接返回
            // If the verification is successful, return directly.
            if (verifyResult) {
                logger.info("verifyResult success");
                report(ReportUtils.KEY_CHECK_SIGN_SUCCESS,
                    ReportUtils.CODE_SUCCESS, null);
                final String successTips = (String) result.get("success_tips");
                if (!TextUtils.isEmpty(successTips)) {
                    sActivity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(sActivity, successTips, Toast.LENGTH_SHORT).show();
                        }
                    });
                }
                sActivity.runOnUiThread(new DrmCheckSuccessRunnable(formattedSignData, signItem));
                return;
            }
        }
        // 验签失败
        // Failed to verify the signature.
        if (sTimes > 0) {
            logger.error("verifyResult failure CODE_SIGN_INVALID_WITH_INTERNET");
            report(ReportUtils.KEY_CHECK_SIGN_FAILED,
                ReportUtils.CODE_SIGN_INVALID_WITH_INTERNET, null);
        } else {
            logger.error("verifyResult failure CODE_CHECK_FAILED");
            report(ReportUtils.KEY_CHECK_SIGN_FAILED,
                ReportUtils.CODE_CHECK_FAILED, null);
        }
        showDialogOrReturnCode(ViewHelper.DIALOG_CHECK_FAILED, DrmStatusCodes.CODE_NO_ORDER);
    }

    /**
     * 成功返回签名时的处理
     * Processing when a signature is returned successfully
     *
     * @param result Returned Map
     */
    private static void handleResultSuccess(Map result) {
        String ts = (String) result.get("ts");
        if (ts == null || "".equals(ts)) {
            logger.error("ts empty");
            report(ReportUtils.KEY_CHECK_SIGN_FAILED,
                ReportUtils.CODE_TS_INVALID, null);
            showDialogOrReturnCode(ViewHelper.DIALOG_GET_DRM_SIGN_FAILED, DrmStatusCodes.CODE_DEFAULT);
            return;
        }

        long tsCached = 0;
        try {
            tsCached = Long.parseLong(ts);
        } catch (NumberFormatException e) {
            logger.error("NumberFormatException");
            report(ReportUtils.KEY_CHECK_SIGN_FAILED,
                ReportUtils.CODE_TS_INVALID, "NumberFormatException");
            showDialogOrReturnCode(ViewHelper.DIALOG_GET_DRM_SIGN_FAILED, DrmStatusCodes.CODE_DEFAULT);
            return;
        }

        long tsLocal = System.currentTimeMillis();
        long tsRange = tsLocal - tsCached;
        // Judge whether the signature is invalid
        int timeValid;
        timeValid = TIME_VALID_CACHE;
        if (tsRange < 0 || tsRange > timeValid) {
            if (sTimes < MAX_TIMES) {
                // Try again with the network
                if (NetUtils.isNetworkAvailable(sActivity)) {
                    sTimes++;
                    retryCheck();
                    return;
                }
            }
        }
        verifyProcess(result, ts);
    }

    private static void handleResultLoginFail(int code) {
        showDialogOrReturnCode(ViewHelper.DIALOG_NOT_LOGGED, code);
    }

    /**
     * 根据返回值进行处理
     * Processing based on the returned value
     *
     * @param result Returned Map
     * @param code   Return Code
     */
    private static void handleReturnCode(Map result, int code) {
        logger.info("handleReturnCode {}", code);
        switch (code) {
            // Signature obtained successfully.
            case DrmStatusCodes.CODE_SUCCESS:
                report(ReportUtils.KEY_GET_SIGN_SUCCESS, ReportUtils.CODE_SUCCESS,
                    null);
                handleResultSuccess(result);
                break;
            // No network connection
            case DrmStatusCodes.CODE_NEED_INTERNET:
                report(ReportUtils.KEY_GET_SIGN_FAILED,
                    ReportUtils.CODE_NO_NETWORK, null);
                showDialogOrReturnCode(ViewHelper.DIALOG_NO_NETWORK, code);
                break;
            // Failed to log in to the HUAWEI ID.
            case DrmStatusCodes.CODE_LOGIN_FAILED:
                report(ReportUtils.KEY_GET_SIGN_FAILED,
                    ReportUtils.CODE_STORE_NOT_LOGGED, null);
                handleResultLoginFail(code);
                break;
            case DrmStatusCodes.CODE_NOT_LOGIN:
                showDialogOrReturnCode(ViewHelper.DIALOG_NOT_LOGGED, code);
                break;
            case DrmStatusCodes.CODE_PROTOCAL_UNAGREE:
                logger.info("RETURN_CODE_PROTOCAL_DISAGREE Hiapp");
                report(ReportUtils.KEY_GET_SIGN_FAILED,
                    ReportUtils.CODE_HIAPP_AGREEMENT_DECLINED, null);
                showDialogOrReturnCode(ViewHelper.DIALOG_HIAPP_AGREEMENT_DECLINED, code);
                break;
            // The activity needs to be started, and the app store needs to be started if the agreement is not agreed or the app store is not logged in.
            case DrmStatusCodes.CODE_START_ACTIVITY:
                if (sShowErrorDailog) {
                    sAppAction = (String) result.get("activity_action");
                    String extra = (String) result.get("activity_extra");
                    startActivity(sAppAction, extra);
                } else {
                    String errorCodeStr = (String) result.get("activity_error_code");
                    if (!TextUtils.isEmpty(errorCodeStr)) {
                        sActivity.runOnUiThread(new DrmCheckFailRunnable(getExtraErrorCode(errorCodeStr)));
                        return;
                    }
                    sActivity.runOnUiThread(new DrmCheckFailRunnable(DrmStatusCodes.CODE_NOT_LOGIN));
                }
                break;
            case DrmStatusCodes.CODE_NO_ORDER:
                sAppAction = (String) result.get("activity_action");
                String extra = (String) result.get("account_name");
                report(ReportUtils.KEY_GET_SIGN_FAILED, ReportUtils.CODE_NO_ORDER,
                    null);
                showDialogOrReturnCode(ViewHelper.DIALOG_CHECK_FAILED, extra, code);
                break;
            case DrmStatusCodes.CODE_OVER_LIMIT:
                String nameextra = (String) result.get("account_name");
                report(ReportUtils.KEY_GET_SIGN_FAILED, ReportUtils.CODE_OVER_LIMIT,
                    null);
                showDialogOrReturnCode(ViewHelper.DIALOG_OVER_LIMIT, nameextra, code);

                break;
            // Other Errors
            default:
                report(ReportUtils.KEY_GET_SIGN_FAILED, ReportUtils.CODE_OTHERS,
                    null);
                showDialogOrReturnCode(ViewHelper.DIALOG_UNKNOW_ERROR, code + "", code);
                break;
        }
        unbindService();
    }

    private static int getExtraErrorCode(String extraErrorCode) {
        int errorCode = DrmStatusCodes.CODE_DEFAULT;
        try {
            errorCode = Integer.parseInt(extraErrorCode);
        } catch (NumberFormatException e) {
            logger.error("rtnCode NumberFormatException");
        }
        return errorCode;
    }

    /**
     * 代码容错的异常处理，一般不会发生
     * Code error tolerance exception handling, which does not occur in most cases.
     *
     * @param activity activity
     */
    public static void handlerCodeException(Activity activity) {
        if (sShowErrorDailog) {
            ViewHelper.showDailog(activity, ViewHelper.DIALOG_GET_DRM_SIGN_FAILED, null, DrmStatusCodes.CODE_DEFAULT);
        } else {
            sActivity.runOnUiThread(new DrmCheckFailRunnable(DrmStatusCodes.CODE_DEFAULT));
        }
    }

    /**
     * 获取AppTouch应用安装来源的应用名
     * Obtains the application name from which the AppTouch is installed.
     *
     * @return 应用名（Application Name）
     */
    private static String getAppName() {
        PackageManager packageManager = sActivity.getPackageManager();
        try {
            ApplicationInfo applicationInfo = packageManager.getApplicationInfo(appStorePkgName, PackageManager.GET_META_DATA);
            CharSequence applicationLabel = packageManager.getApplicationLabel(applicationInfo);
            if (applicationLabel != null) {
                return applicationLabel.toString();
            }
        } catch (Exception e) {
            logger.error("getAppName Exception");
        }
        return "";
    }

    /**
     * 打点上报（Dotting reporting）
     *
     * @param key    Report key type
     * @param code   Error code/success code
     * @param reason Error cause. The value cannot be empty.
     */
    private static void report(String key, int code, String reason) {
        try {
            String[] args = null;
            if (ReportUtils.KEY_GET_SIGN_SUCCESS.equals(key)) {
                args = new String[]
                    {VERSION_CODE, sPkgName, sDeveloperId, String.valueOf(code)};
            } else if (ReportUtils.KEY_CHECK_SIGN_SUCCESS.equals(key)) {
                args = new String[]
                    {VERSION_CODE, sPkgName, sDeveloperId, String.valueOf(code),
                        sPayDeviceId};
            } else if (ReportUtils.KEY_GET_SIGN_FAILED.equals(key)) {
                args = new String[]
                    {VERSION_CODE, sPkgName, sDeveloperId, String.valueOf(code)};
            } else if (ReportUtils.KEY_CHECK_SIGN_FAILED.equals(key)) {
                args = new String[]
                    {VERSION_CODE, sPkgName, sDeveloperId, String.valueOf(code),
                        sPayDeviceId, reason};
            }
            Map params = ReportUtils.generateReport(key, args);
            if (params != null && sSignService != null) {
                sSignService.report(params);
            }
        } catch (RemoteException e) {
            logger.error("report exception");
        }
    }

    /**
     * 连接到市场服务的ServiceConnection
     * Service Connection to Market Service
     */
    private static class DrmServiceConnection implements ServiceConnection {

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            logger.info("DrmServiceConnection ServiceConnected");
            ICallback callback = new DrmStub();
            // 传入参数等待获取签名
            // Input parameters to be obtained
            sSignService = IDrmSignService.Stub.asInterface(service);
            if (sSignService == null) {
                logger.error("signService null");
                showDialogOrReturnCode(ViewHelper.DIALOG_GET_DRM_SIGN_FAILED, DrmStatusCodes.CODE_DEFAULT);
                return;
            }
            Map params = new HashMap<String, String>();
            params.put("pkgName", sPkgName);
            params.put("developerId", sDeveloperId);
            if (sShowErrorDailog) {
                params.put("supportPrompt", true);
            } else {
                // 标识不自动弹出提示对话框.
                // Indicates that the prompt dialog box is not automatically displayed.
                params.put("supportPrompt", false);
            }

            try {
                sSignService.getSign(params, callback);
            } catch (RemoteException e) {
                logger.error("RemoteException");
                report(ReportUtils.KEY_GET_SIGN_FAILED,
                    ReportUtils.CODE_GET_SIGN_FAILED, null);
                showDialogOrReturnCode(ViewHelper.DIALOG_GET_DRM_SIGN_FAILED, DrmStatusCodes.CODE_DEFAULT);
            }

        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.i("ServiceConnection", "onServiceDisconnected() called");
        }

    }

    /**
     * 连接市场服务的callback实现类
     * Callback implementation class for connecting to the marketing service.
     */
    private static class DrmStub extends ICallback.Stub {

        @Override
        public void onResult(Map result) throws RemoteException {
            logger.info("DrmStub onResult {}", sIsInterrupt);
            if (!sIsInterrupt) {
                onDrmSignResult(result);
            }
        }
    }

    /**
     * 验签通过的Runnable
     * Runnable that passes the verification
     */
    private static class DrmCheckSuccessRunnable implements Runnable {
        private String signData;
        private String signature;

        public DrmCheckSuccessRunnable(String signData, String signature) {
            this.signData = signData;
            this.signature = signature;
        }

        @Override
        public void run() {
            DialogTrigger.getInstance().closeDialog();
            sIsRunning = false;

            if (sCallback != null) {
                sCallback.onCheckSuccess(signData, signature);
            }
        }
    }

    /**
     * 验签失败的Runnable
     * Runnable for signature verification failure
     */
    private static class DrmCheckFailRunnable implements Runnable {
        /**
         * 失败错误码
         * Failure Error Code
         */
        private int errorCode;

        public DrmCheckFailRunnable(int errorCode) {
            super();
            this.errorCode = errorCode;
        }

        @Override
        public void run() {
            DialogTrigger.getInstance().closeDialog();
            sIsRunning = false;
            sCallback.onCheckFailed(errorCode);
        }

    }

    /**
     * 拉起Activity的Runnable
     * Start the Runnable of the activity.
     */
    private static class DrmStartActivityRunnable implements Runnable {
        /**
         * Dialog类型
         * Dialog type
         */
        private int type;
        /**
         * 其他数据
         * Other data
         */
        private String extra;
        /**
         * 鉴权失败错误码
         * Authentication failure error code
         */
        private int errorCode;

        public DrmStartActivityRunnable(int type, String extra, int errorCode) {
            super();
            this.type = type;
            this.extra = extra;
            this.errorCode = errorCode;
        }

        @Override
        public void run() {
            logger.info("DrmStartActivityRunnable type" + type + "sIsInterrupt" + sIsInterrupt);
            if (sIsInterrupt && type != ViewHelper.DIALOG_USER_INTERRUPT) {
                return;
            }
            // 关闭等待对话框
            // Close the Wait dialog box.
            if (type != ViewHelper.DIALOG_WAITING) {
                DialogTrigger.getInstance().closeDialog();
                sTimes = 0;
            }
            Intent intent = new Intent();
            intent.putExtra(Constants.KEY_EXTRA_DIALOG, type);
            intent.putExtra(Constants.KEY_EXTRA_CODE, errorCode);
            intent.putExtra(Constants.KEY_EXTRA_EXTRA, extra);
            intent.setClass(sActivity, DrmDialogActivity.class);
            sActivity.startActivity(intent);
        }

    }

}

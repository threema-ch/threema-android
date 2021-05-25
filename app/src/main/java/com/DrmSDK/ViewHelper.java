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

import java.util.concurrent.atomic.AtomicInteger;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnDismissListener;
import android.os.Build;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.RelativeLayout.LayoutParams;
import android.widget.TextView;
import android.widget.Toast;

/**
 * View辅助类
 * View auxiliary class
 *
 * @since 2020/07/01
 */
public class ViewHelper {
    /**
     * Dialog类型：提示等待鉴权
     * Dialog type: prompt for authentication
     */
    public static final int DIALOG_WAITING = 0;
    /**
     * Dialog类型：获取签名失败
     * Dialog type: Failed to obtain the signature.
     */
    public static final int DIALOG_GET_DRM_SIGN_FAILED = 1;
    /**
     * Dialog类型：无网络连接
     * Dialog type: no network connection
     */
    public static final int DIALOG_NO_NETWORK = 2;
    /**
     * Dialog类型：拒绝应用市场使用协议
     * Dialog type: Deny the application of the AppGallery protocol.
     */
    public static final int DIALOG_HIAPP_AGREEMENT_DECLINED = 3;
    /**
     * Dialog类型：鉴权失败
     * Dialog type: authentication failure
     */
    public static final int DIALOG_CHECK_FAILED = 5;
    /**
     * Dialog类型：用户按返回键取消鉴权
     * Dialog type: The user presses the return key to cancel authentication.
     */
    public static final int DIALOG_USER_INTERRUPT = 6;
    /**
     * Dialog类型：没有登陆华为账号
     * Dialog type: No Huawei ID is logged in.
     */
    public static final int DIALOG_NOT_LOGGED = 7;
    /**
     * Dialog类型：市场不支持鉴权
     * Dialog type: The market does not support authentication.
     */
    public static final int DIALOG_STORE_NOT_AVAILABLE = 8;
    /**
     * Dialog类型：超过使用设备数量
     * Dialog type: Exceeded the number of used devices.
     */
    public static final int DIALOG_OVER_LIMIT = 9;
    /**
     * Dialog类型：未知错误码
     * Dialog type: unknown error code
     */
    public static final int DIALOG_UNKNOW_ERROR = 10;
    /**
     * 没有传递弹出Dialog的类型信息
     * Dialog type information is not transferred.
     */
    public static final int NO_DIALOG_INFO = -1;
    /**
     * Dialog按键处理：什么也不做
     * Dialog key processing: nothing
     */
    public static final int OPERATION_NONE = 0;
    /**
     * Dialog按键处理：安装
     * Dialog Key Processing:Installing
     */
    public static final int OPERATION_INSTALL = 1;
    /**
     * Dialog按键处理：重试
     * Dialog key processing: retry
     */
    public static final int OPERATION_RETRY = 2;
    /**
     * Dialog按键处理：购买
     * Dialog key processing: purchase
     */
    public static final int OPERATION_BUY = 3;
    /**
     * Dialog按键处理：登录
     * Dialog key processing: login
     */
    public static final int OPERATION_LOGIN = 4;
    /**
     * Dialog按键处理：拉起协议
     * Dialog key processing: starting the protocol
     */
    public static final int OPERATION_AGREEMENT = 5;
    /**
     * Dialog按键处理：用户按返回键
     * Dialog key processing: A user presses the return key.
     */
    public static final int OPERATION_USER_INTERRUPT = 6;
    /**
     * Dialog按键处理：切换账号登录
     * Dialog key processing: switching account login
     */
    public static final int OPERATION_LOGIN_CHANGE = 7;

    private static final AtomicInteger S_NEXT_GENERATED_ID = new AtomicInteger(1);

    /**
     * 为一个View生成一个id。
     * Generate an ID for a view.
     */
    @SuppressLint("NewApi")
    public static int generateViewId() {

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR1) {
            for (; ; ) {
                final int result = S_NEXT_GENERATED_ID.get();
                // aapt-generated IDs have the high byte nonzero; clamp to the
                // range under that.
                int newValue = result + 1;
                if (newValue > 0x00FFFFFF) {
                    newValue = 1; // Roll over to 1, not 0.
                }
                if (S_NEXT_GENERATED_ID.compareAndSet(result, newValue)) {
                    return result;
                }
            }
        } else {
            return View.generateViewId();
        }
    }

    /**
     * 弹出提示等待Dialog
     * A dialog box is displayed, prompting you to wait for the Dialog.
     *
     * @param activity Dialog Context
     * @return Dialog box instance
     */
    public static AlertDialog showWaitingDialog(final Activity activity) {
        Log.i("DRM_SDK", "showWaitingDialog");

        RelativeLayout layout = new RelativeLayout(activity);
        LayoutParams msgLayout = new LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);

        DisplayMetrics metrics = new DisplayMetrics();
        activity.getWindowManager().getDefaultDisplay().getMetrics(metrics);

        EMUISupportUtil util = EMUISupportUtil.getInstance();

        int marginRight = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 8, metrics);
        int marginLR = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 16, metrics);
        int marginTB = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 20, metrics);
        // EMUI3 的Dialog自带水平方向 16dip padding
        // EMUI3 Dialog has a 16-dip padding in the horizontal direction.
        if (util.isSupportEMUI() && util.isEMUI3()) {
            marginLR = 0;
        }
        layout.setPadding(marginLR, marginTB, marginLR, marginTB);

        ProgressBar progress = new ProgressBar(activity);
        LayoutParams loadLayout = new LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        loadLayout.addRule(RelativeLayout.CENTER_VERTICAL);
        loadLayout.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
        loadLayout.addRule(RelativeLayout.ALIGN_PARENT_END);
        layout.addView(progress, loadLayout);
        progress.setId(generateViewId());

        TextView message = new TextView(activity);
        message.setText(activity.getString(DrmResource.string(activity,
                "drm_dialog_message_waiting")));
        message.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        msgLayout.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
        msgLayout.addRule(RelativeLayout.ALIGN_PARENT_START);
        msgLayout.addRule(RelativeLayout.CENTER_VERTICAL);
        msgLayout.addRule(RelativeLayout.LEFT_OF, progress.getId());
        msgLayout.addRule(RelativeLayout.START_OF, progress.getId());
        msgLayout.rightMargin = marginRight;
        msgLayout.setMarginEnd(marginRight);
        layout.addView(message, msgLayout);

        AlertDialog dialog = new AlertDialog.Builder(activity).setView(layout)
                .create();
        dialog.setCanceledOnTouchOutside(false);
        dialog.setOnCancelListener(new OnCancelListener() {

            @Override
            public void onCancel(DialogInterface dialog) {
                activity.finish();
                DrmKernel.onDialogClicked(OPERATION_USER_INTERRUPT, DrmStatusCodes.CODE_CANCEL);
            }
        });
        dialog.setOnDismissListener(new OnDismissListener() {

            @Override
            public void onDismiss(DialogInterface dialog) {
                activity.finish();
            }
        });
        dialog.show();
        Log.i("ViewHelper", "showWaitingDialog");
        return dialog;
    }
    /**
     * 弹出Dialog
     * A basic dialog box is displayed.
     *
     * @param activity   Dialog pops up the required context.
     * @param dialogType Dialog type
     * @param errorCode  Error Code
     */
    private static AlertDialog showDailogPro(final Activity activity, int dialogType, String extra, final int errorCode,
                                                int operation, String resourceNameMessage, String resourceNameText) {
        final int operationId = operation;
        int msgId = DrmResource.string(activity, resourceNameMessage);
        int textId = DrmResource.string(activity, resourceNameText);
        int quitId = DrmResource.string(activity, "drm_dialog_text_quit");
        String msg = activity.getString(msgId);
        if (dialogType == DIALOG_CHECK_FAILED || dialogType == DIALOG_OVER_LIMIT
                || dialogType == DIALOG_UNKNOW_ERROR) {
            if (!isEmpty(extra)) {
                msg = activity.getString(msgId, extra);
            }
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(activity)
                .setMessage(msg)
                .setOnCancelListener(new OnCancelListener() {

                    @Override
                    public void onCancel(DialogInterface dialog) {
                        activity.finish();
                        DrmKernel.onDialogClicked(OPERATION_NONE, errorCode);
                    }
                })
                .setPositiveButton(textId,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog,
                                                int which) {
                                activity.finish();
                                DrmKernel.onDialogClicked(operationId, errorCode);
                            }
                        });
        if (dialogType != DIALOG_UNKNOW_ERROR) {
            builder.setNegativeButton(quitId,
                    new DialogInterface.OnClickListener() {

                        @Override
                        public void onClick(DialogInterface dialog,
                                            int which) {
                            activity.finish();
                            DrmKernel.onDialogClicked(OPERATION_NONE, errorCode);
                        }
                    });
        }
        AlertDialog dialog = builder.create();
        dialog.show();
        return dialog;
    }
    /**
     * activity 有效性判断
     *
     * @param activity   Dialog pops up the required context.
     */
    private static boolean activityIsValid(final Activity activity) {
        if (activity == null || activity.isFinishing()) {
            return false;
        }
        return true;
    }
    /**
     * 弹出一个基本的Dialog
     * A basic dialog box is displayed.
     *
     * @param activity   Dialog pops up the required context.
     * @param dialogType Dialog type
     * @param errorCode  Error Code
     */
    public static AlertDialog showDailog(final Activity activity, int dialogType, String extra, final int errorCode) {
        if (!activityIsValid(activity)) {
            return null;
        }
        boolean stringIsEmpty = isEmpty(extra);
        String resourceNameMessage = null;
        String resourceNameText = null;
        int operation = OPERATION_NONE;
        switch (dialogType) {
            case DIALOG_WAITING:
                return showWaitingDialog(activity);
            case DIALOG_CHECK_FAILED:
                resourceNameMessage = (stringIsEmpty) ? "drm_dialog_message_check_failed_without_name" : "drm_dialog_message_check_failed";
                resourceNameText = "drm_dialog_text_buy";
                operation = OPERATION_BUY;
                break;
            case DIALOG_GET_DRM_SIGN_FAILED:
                resourceNameMessage = "drm_dialog_message_get_sign_failed";
                resourceNameText = "drm_dialog_text_retry";
                operation = OPERATION_RETRY;
                break;
            case DIALOG_NO_NETWORK:
                resourceNameMessage = "drm_dialog_message_no_internet";
                resourceNameText = "drm_dialog_text_retry";
                operation = OPERATION_RETRY;
                break;
            case DIALOG_NOT_LOGGED:
                resourceNameMessage = "drm_dialog_message_not_logged";
                resourceNameText = "drm_dialog_text_login";
                operation = OPERATION_LOGIN;
                break;
            case DIALOG_USER_INTERRUPT:
                resourceNameMessage = "drm_dialog_message_user_interrupt";
                resourceNameText = "drm_dialog_text_retry";
                operation = OPERATION_RETRY;
                break;
            case DIALOG_HIAPP_AGREEMENT_DECLINED:
                resourceNameMessage = "drm_dialog_message_hiapp_agreement";
                resourceNameText = "drm_dialog_text_ok";
                operation = OPERATION_AGREEMENT;
                break;
            case DIALOG_STORE_NOT_AVAILABLE:
                resourceNameMessage = "drm_dialog_message_hiapp_not_installed";
                resourceNameText = "drm_dialog_text_install";
                operation = OPERATION_INSTALL;
                break;
            case DIALOG_OVER_LIMIT:
                resourceNameMessage = (stringIsEmpty) ? "drm_dialog_message_over_limit_without_name" : "drm_dialog_message_over_limit";
                resourceNameText = "drm_dialog_text_ok";
                operation = OPERATION_LOGIN_CHANGE;
                break;
            case DIALOG_UNKNOW_ERROR:
                resourceNameMessage = "drm_dialog_message_other_errorcode";
                resourceNameText = "drm_dialog_text_hasknow";
                operation = OPERATION_NONE;
                break;
            default:
                return null;
        }
        return showDailogPro(activity, dialogType, extra, errorCode, operation, resourceNameMessage, resourceNameText);
    }
    /**
     * 判断字符串是否为空。
     * Check whether the character string is empty.
     *
     * @param str 字符串(String)
     * @return true:空, false:非空(true: empty; false: not empty)
     */
    public static boolean isEmpty(String str) {
        return (str == null || str.length() == 0);
    }

}

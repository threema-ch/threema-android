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
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.Window;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * 弹出对话框和处理拉起Activity的空Activity。
 * A dialog box is displayed and the empty activity that starts the activity is processed.
 *
 * @since 2020/07/01
 */
public class DrmDialogActivity extends Activity implements DialogObserver {
	private final Logger logger = LoggerFactory.getLogger(DrmDialogActivity.class);
    /**
     * 透明状态栏属性
     * Transparent Status Bar Properties
     */
    public static final int FLAG_TRANSLUCENT_STATUS = 0x04000000;
    /**
     * 透明导航栏属性
     * Transparent Navigation Bar Properties
     */
    public static final int FLAG_TRANS_NAVIGATION_BAR = 0x08000000;

    private AlertDialog waitingDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        logger.info("DrmDialogActivity onCreate");
        setTransparency(this);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        super.onCreate(savedInstanceState);
        View view = new View(this);
        view.setBackgroundColor(getResources().getColor(
                android.R.color.transparent));
        setContentView(view);
        // 根据传递的Action启动Activity
        // Initiate an activity based on the transferred action.
        Intent dataIntent = getIntent();
        if (dataIntent == null) {
            logger.error("DrmDialogActivity dataIntent null!");
            DrmKernel.handlerCodeException(this);
            return;
        }

        Bundle data = null;
        try {
            data = dataIntent.getExtras();
        } catch (Exception e) {
            logger.error("data getExtras Exception");
        }

        if (data == null) {
            logger.error("DrmDialogActivity dataIntent getExtras null!");
            DrmKernel.handlerCodeException(this);
            return;
        }

        int dialog = data.getInt(Constants.KEY_EXTRA_DIALOG,
                ViewHelper.NO_DIALOG_INFO);
        logger.info("DrmDialogActivity dialog" + dialog);
        String extra = data.getString(Constants.KEY_EXTRA_EXTRA);
        switch (dialog) {
            // 不弹出对话框，拉出Activity
            // Do not display the dialog box and pull out the activity.
            case ViewHelper.DIALOG_WAITING:
                if (!DialogTrigger.getInstance().hasObserver()) {
                    logger.error("DrmDialogActivity no hasObserver");
                    DialogTrigger.getInstance().registerObserver(this);
                    waitingDialog = ViewHelper.showWaitingDialog(this);
                } else {
                    logger.error("DrmDialogActivity hasObserver finish");
                    finish();
                }
                DrmKernel.initBinding();
                break;
            case ViewHelper.NO_DIALOG_INFO:
                String action = "";
                String pkg = "";
                try {
                    action = getIntent().getStringExtra(Constants.KEY_EXTRA_ACTION);
                } catch (Exception e) {
                    logger.error("action getStringExtra Exception");
                }

                try {
                    pkg = getIntent().getStringExtra(Constants.KEY_EXTRA_PACKAGE);
                } catch (Exception e) {
                    logger.error("pkg getStringExtra Exception");
                }

                if (TextUtils.isEmpty(action) || TextUtils.isEmpty(pkg)) {
                    logger.error("DrmDialogActivity NO_DIALOG_INFO null");
                    return;
                }
                Intent intent = new Intent(action);
                intent.putExtra(Constants.KEY_JSON_EXTRA, extra);
                intent.setPackage(pkg);
                startActivityForResult(intent, 0);
                break;
            default:
                int errorCode = data.getInt(Constants.KEY_EXTRA_CODE,
                        DrmStatusCodes.CODE_DEFAULT);
                ViewHelper.showDailog(this, dialog, extra, errorCode);
                break;
        }

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        logger.info("DRM_SDK DrmDialogActivity onActivityResult resultCode {}", resultCode);
        // 直接返回的DRM处理
        // Directly returned DRM processing
        DrmKernel.onActivityResult(resultCode);
        finish();
    }

    @Override
    public void overridePendingTransition(int enterAnim, int exitAnim) {
        // 去掉Activity切换动画
        // The activity switching animation is deleted.
        super.overridePendingTransition(0, 0);
    }

    @Override
    public void closeDlg() {
        finish();
    }

    @Override
    public void finish() {
        super.finish();
        dismissDialog(waitingDialog);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        dismissDialog(waitingDialog);
    }

    private void dismissDialog(AlertDialog dialog) {
        try {
            if (dialog != null && dialog.isShowing()) {
                dialog.dismiss();
                DialogTrigger.getInstance().registerObserver(null);
            }
        } catch (Exception e) {
            logger.error("DrmDialogActivity dismissDialog {}", e.getMessage());
        } finally {
            dialog = null;
        }

    }

    public static void setTransparency(Activity activity) {
        // 使通知栏变透明
        // Make the notification bar transparent
        Window window = activity.getWindow();
        window.setFlags(FLAG_TRANSLUCENT_STATUS, FLAG_TRANSLUCENT_STATUS);
        setHwFloating(activity, true);
    }

    public static boolean setHwFloating(Activity activity,
                                        Boolean boolHwFloating) {
        try {
            Window w = activity.getWindow();
            HwInvoke.invokeFun(w.getClass(), w, "setHwFloating", new Class[]
                    {boolean.class}, new Object[]
                    {boolHwFloating});
            return true;
        } catch (Exception e) {
            Log.e("DrmDialogActivity", "Exception");
        }
        return false;
    }

}

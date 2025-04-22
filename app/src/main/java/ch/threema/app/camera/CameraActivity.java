/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2019-2025 Threema GmbH
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

package ch.threema.app.camera;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;

import androidx.annotation.Nullable;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.google.common.util.concurrent.ListenableFuture;

import org.slf4j.Logger;

import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.activities.ThreemaAppCompatActivity;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.base.utils.LoggingUtil;

import static android.view.KeyEvent.KEYCODE_VOLUME_DOWN;
import static android.view.KeyEvent.KEYCODE_VOLUME_UP;

public class CameraActivity extends ThreemaAppCompatActivity implements CameraFragment.CameraCallback, CameraFragment.CameraConfiguration {
    private static final Logger logger = LoggingUtil.getThreemaLogger("CameraActivity");

    public static final String KEY_EVENT_ACTION = "key_event_action";
    public static final String KEY_EVENT_EXTRA = "key_event_extra";
    public static final String EXTRA_VIDEO_OUTPUT = "vidOut";
    public static final String EXTRA_VIDEO_RESULT = "videoResult";
    public static final String EXTRA_NO_VIDEO = "noVideo";

    private static final int RESULT_ERROR = 2;

    private String cameraFilePath, videoFilePath;
    private boolean noVideo;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        logger.info("onCreate");

        super.onCreate(savedInstanceState);

        setContentView(R.layout.camerax_activity_camera);

        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // we want dark icons, i.e. a light status bar
            getWindow().getDecorView().setSystemUiVisibility(
                getWindow().getDecorView().getSystemUiVisibility() | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }

        if (getIntent() != null) {
            cameraFilePath = getIntent().getStringExtra(MediaStore.EXTRA_OUTPUT);
            videoFilePath = getIntent().getStringExtra(EXTRA_VIDEO_OUTPUT);
            noVideo = getIntent().getBooleanExtra(EXTRA_NO_VIDEO, false);
        }

        if (cameraFilePath == null) {
            finish();
        }
    }

    @Override
    protected void onDestroy() {
        logger.info("onDestroy");

        try {
            super.onDestroy();
        } catch (Exception e) {
            logger.error("Exception", e);
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // Use volume down key as shutter button
        if (keyCode == KEYCODE_VOLUME_DOWN || keyCode == KEYCODE_VOLUME_UP) {
            Intent intent = new Intent(KEY_EVENT_ACTION);
            intent.putExtra(KEY_EVENT_EXTRA, keyCode);
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
        } else {
            return super.onKeyDown(keyCode, event);
        }
        return true;
    }

    @Override
    protected void onResume() {
        logger.info("onResume");

        super.onResume();
    }

    @Override
    public void onImageReady() {
        removeFragment();
        setResult(RESULT_OK);
        this.finish();
    }

    @Override
    public void onVideoReady() {
        Intent intent = new Intent();
        intent.putExtra(EXTRA_VIDEO_RESULT, true);
        setResult(RESULT_OK, intent);
        this.finish();
    }

    @Override
    public void onError(String message) {
        logger.error("Could not take a picture or record a video: {}", message);
        setResult(RESULT_ERROR);
        this.finish();
    }

    @Override
    public String getVideoFilePath() {
        return videoFilePath;
    }

    @Override
    public String getCameraFilePath() {
        return cameraFilePath;
    }

    private void removeFragment() {
        FragmentManager fragmentManager = getSupportFragmentManager();

        Fragment fragment = fragmentManager.findFragmentById(R.id.fragment_container);
        if (fragment != null && fragment.isAdded()) {
            fragmentManager.beginTransaction().remove(fragment).commit();
        }
    }

    @Override
    public boolean getVideoEnable() {
        return ConfigUtils.supportsVideoCapture() && !noVideo;
    }
}

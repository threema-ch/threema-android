/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2016-2025 Threema GmbH
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

package ch.threema.app.activities;

import android.annotation.TargetApi;
import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.text.format.DateUtils;
import android.view.Gravity;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import org.slf4j.Logger;

import ch.threema.app.BuildConfig;
import ch.threema.app.R;
import ch.threema.app.dialogs.GenericAlertDialog;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.base.utils.LoggingUtil;

import static ch.threema.app.fragments.BackupDataFragment.REQUEST_ID_DISABLE_BATTERY_OPTIMIZATIONS;
import static ch.threema.app.utils.PowermanagerUtil.isIgnoringBatteryOptimizations;
import static ch.threema.app.utils.ActiveScreenLoggerKt.logScreenVisibility;

/**
 * Guides user through the process of disabling battery optimization energy saving option.
 * <p>
 * If the app has the REQUEST_IGNORE_BATTERY_OPTIMIZATIONS permission, then the
 * ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS intent is used instead.
 */
public class DisableBatteryOptimizationsActivity extends ThreemaActivity implements GenericAlertDialog.DialogClickListener {
    private static final Logger logger = LoggingUtil.getThreemaLogger("DisableBatteryOptimizationsActivity");

    private static final int REQUEST_CODE_IGNORE_BATTERY_OPTIMIZATIONS = 778;
    private static final String DIALOG_TAG_DISABLE_BATTERY_OPTIMIZATIONS = "des";
    private static final String DIALOG_TAG_BATTERY_OPTIMIZATIONS_REMINDER = "esr";
    private static final String DIALOG_TAG_MIUI_WARNING = "miui";

    /**
     * The name of the affected system (e.g. "Threema Web").
     */
    public static final String EXTRA_NAME = "name";
    /**
     * If set to true, then a "do you really want to keep battery optimizations enabled"
     * confirmation dialog will be shown. Default false.
     */
    public static final String EXTRA_CONFIRM = "confirm";
    /**
     * Set this to a string resource ID in order to override the "continue anyways" text.
     */
    public static final String EXTRA_CANCEL_LABEL = "cancel";
    /**
     * Set this to true if the activity is called from the wizard. Default false.
     */
    public static final String EXTRA_WIZARD = "wizard";

    /**
     * Set this to true to disable the rationale dialog before guiding the user through the disable process
     */
    public static final String EXTRA_DISABLE_RATIONALE = "disable_rationale";


    private String name;
    @StringRes
    private int cancelLabel;
    private boolean confirm, disableRationale;
    private int actionBarSize = 0;
    private Handler dropDownHandler, listSelectHandler;

    @Override
    @TargetApi(Build.VERSION_CODES.M)
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        logScreenVisibility(this, logger);

        if (isIgnoringBatteryOptimizations(this)) {
            setResult(RESULT_OK);
            finish();
            return;
        }

        Intent intent = getIntent();

        final boolean usedInWizard = intent.getBooleanExtra(EXTRA_WIZARD, false);
        if (usedInWizard) {
            setTheme(R.style.Theme_Threema_Wizard_Translucent);
        } else if (ConfigUtils.isTheDarkSide(this)) {
            setTheme(R.style.Theme_Threema_Translucent_Dark);
        }

        if (ConfigUtils.getMIUIVersion() >= 11) {
            String bodyText = getString(R.string.miui_battery_optimization, getString(R.string.app_name));
            GenericAlertDialog.newInstance(R.string.battery_optimizations_title, bodyText, R.string.ok, 0).show(getSupportFragmentManager(), DIALOG_TAG_MIUI_WARNING);
            return;
        }

        // Get extras
        this.name = intent.getStringExtra(EXTRA_NAME);
        this.confirm = intent.getBooleanExtra(EXTRA_CONFIRM, false);
        this.cancelLabel = intent.getIntExtra(EXTRA_CANCEL_LABEL, R.string.continue_anyway);
        this.disableRationale = intent.getBooleanExtra(EXTRA_DISABLE_RATIONALE, false);

        // Determine action bar size
        this.actionBarSize = ConfigUtils.getActionBarSize(this);

        if (this.disableRationale) {
            launchDisableFlow();
        } else {
            showRationaleDialog();
        }
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    private void showRationaleDialog() {
        GenericAlertDialog dialog = GenericAlertDialog.newInstance(
            R.string.battery_optimizations_title,
            String.format(getString(R.string.battery_optimizations_explain), name, getString(R.string.app_name)),
            R.string.disable,
            cancelLabel
        );
        dialog.show(getSupportFragmentManager(), DIALOG_TAG_DISABLE_BATTERY_OPTIMIZATIONS);
    }

    @Override
    public void onYes(String tag, Object data) {
        switch (tag) {
            case DIALOG_TAG_DISABLE_BATTERY_OPTIMIZATIONS:
                launchDisableFlow();
                break;
            case DIALOG_TAG_BATTERY_OPTIMIZATIONS_REMINDER:
                // user wants to continue at his own risk
                setResult(RESULT_OK);
                finish();
                break;
            case DIALOG_TAG_MIUI_WARNING:
                setResult(RESULT_CANCELED);
                finish();
                break;
        }
    }

    @TargetApi(Build.VERSION_CODES.M)
    private void launchDisableFlow() {
        // If the REQUEST_IGNORE_BATTERY_OPTIMIZATIONS permission is granted (versions that
        // aren't distributed through Google Play), then a permission can be requested directly.
        if (ConfigUtils.checkManifestPermission(this, getPackageName(), "android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS")) {
            final Uri appUri = Uri.parse("package:" + BuildConfig.APPLICATION_ID);
            final Intent newIntent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, appUri);
            try {
                startActivityForResult(newIntent, REQUEST_ID_DISABLE_BATTERY_OPTIMIZATIONS);
            } catch (Exception e) {
                // Some Samsung devices don't bother implementing this API
                logger.error("Could not request battery optimization exemption", e);
                setResult(RESULT_OK);
                finish();
            }

            // Otherwise we need to guide the user through the battery optimization settings.
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivityForResult(intent, REQUEST_ID_DISABLE_BATTERY_OPTIMIZATIONS);
            } else {
                final Intent intent = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
                // Samsung Galaxy S5 with API 23 does not know this intent
                if (intent.resolveActivity(getPackageManager()) != null) {
                    startActivityForResult(intent, REQUEST_CODE_IGNORE_BATTERY_OPTIMIZATIONS);

                    dropDownHandler = new Handler();
                    dropDownHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            Toast toast = Toast.makeText(getApplicationContext(), R.string.battery_optimizations_disable_guide, Toast.LENGTH_LONG);
                            toast.setGravity(Gravity.TOP | Gravity.LEFT, 0, actionBarSize * 2);
                            toast.show();
                        }
                    }, 2 * DateUtils.SECOND_IN_MILLIS);

                    listSelectHandler = new Handler();
                    listSelectHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            Toast ctdToast = Toast.makeText(getApplicationContext(), String.format(getString(R.string.battery_optimizations_disable_guide_ctd), getString(R.string.app_name)), Toast.LENGTH_LONG);
                            ctdToast.setGravity(Gravity.CENTER, 0, 0);
                            ctdToast.show();
                        }
                    }, 8 * DateUtils.SECOND_IN_MILLIS);
                }
            }
        }
    }

    @Override
    public void onNo(String tag, Object data) {
        switch (tag) {
            case DIALOG_TAG_DISABLE_BATTERY_OPTIMIZATIONS:
                if (confirm) {
                    final GenericAlertDialog dialog = GenericAlertDialog.newInstance(R.string.battery_optimizations_title, String.format(getString(R.string.battery_optimizations_disable_confirm), getString(R.string.app_name), name), R.string.yes, R.string.no);
                    dialog.show(getSupportFragmentManager(), DIALOG_TAG_BATTERY_OPTIMIZATIONS_REMINDER);
                } else {
                    setResult(RESULT_CANCELED);
                    finish();
                }
                break;
            case DIALOG_TAG_BATTERY_OPTIMIZATIONS_REMINDER:
                showRationaleDialog();
                break;
        }
    }

    private void removeHandlers() {
        if (dropDownHandler != null) {
            dropDownHandler.removeCallbacksAndMessages(null);
        }
        if (listSelectHandler != null) {
            listSelectHandler.removeCallbacksAndMessages(null);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        switch (requestCode) {
            case REQUEST_ID_DISABLE_BATTERY_OPTIMIZATIONS:
                // back from system dialog
                if (isIgnoringBatteryOptimizations(this)) {
                    setResult(RESULT_OK);
                } else {
                    setResult(RESULT_CANCELED);
                }
                finish();
                break;
            case REQUEST_CODE_IGNORE_BATTERY_OPTIMIZATIONS:
                // backup from overlay hack
                removeHandlers();
                if (isIgnoringBatteryOptimizations(this) || disableRationale) {
                    setResult(RESULT_OK);
                    finish();
                } else {
                    showRationaleDialog();
                }
                break;
            default:
                break;
        }
    }

    @Override
    public void finish() {
        // used to avoid flickering of status and navigation bar when activity is closed
        super.finish();
        overridePendingTransition(0, 0);
    }
}

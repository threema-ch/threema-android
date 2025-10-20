/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2015-2025 Threema GmbH
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

package ch.threema.app.activities.wizard;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;

import org.koin.java.KoinJavaComponent;
import org.slf4j.Logger;

import java.time.Instant;

import androidx.annotation.NonNull;
import ch.threema.app.R;
import ch.threema.app.di.DependencyContainer;
import ch.threema.app.dialogs.GenericAlertDialog;
import ch.threema.app.dialogs.GenericProgressDialog;
import ch.threema.app.dialogs.WizardDialog;
import ch.threema.app.licensing.StoreLicenseCheck;
import ch.threema.app.ui.InsetSides;
import ch.threema.app.ui.NewWizardFingerPrintView;
import ch.threema.app.ui.SpacingValues;
import ch.threema.app.ui.ViewExtensionsKt;
import ch.threema.app.utils.DialogUtil;
import ch.threema.app.utils.TestUtil;
import ch.threema.base.ThreemaException;
import ch.threema.base.utils.LoggingUtil;

import static ch.threema.app.utils.ActiveScreenLoggerKt.logScreenVisibility;

public class WizardFingerPrintActivity extends WizardBackgroundActivity
    implements WizardDialog.WizardDialogCallback, GenericAlertDialog.DialogClickListener {

    private static final Logger logger = LoggingUtil.getThreemaLogger("WizardFingerPrintActivity");

    public static final int PROGRESS_MAX = 100;
    private static final String DIALOG_TAG_CREATE_ID = "ci";
    private static final String DIALOG_TAG_CREATE_ERROR = "ni";
    private static final String DIALOG_TAG_FINGERPRINT_INFO = "fi";
    private ProgressBar swipeProgress;
    private ImageView fingerView;

    @NonNull
    private final DependencyContainer dependencies = KoinJavaComponent.get(DependencyContainer.class);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        logScreenVisibility(this, logger);

        if (!dependencies.isAvailable()) {
            finish();
            return;
        }

        setContentView(R.layout.activity_new_fingerprint);

        ViewExtensionsKt.applyDeviceInsetsAsPadding(
            findViewById(R.id.new_fingerprint_content),
            InsetSides.vertical(),
            SpacingValues.all(R.dimen.grid_unit_x2)
        );

        swipeProgress = findViewById(R.id.wizard1_swipe_progress);
        swipeProgress.setMax(PROGRESS_MAX);
        swipeProgress.setProgress(0);

        fingerView = findViewById(R.id.finger_overlay);
        findViewById(R.id.wizard_icon_info).setOnClickListener(v -> {
            WizardDialog wizardDialog = WizardDialog.newInstance(R.string.new_wizard_info_fingerprint, R.string.ok);
            wizardDialog.show(getSupportFragmentManager(), DIALOG_TAG_FINGERPRINT_INFO);
        });

        ((NewWizardFingerPrintView) findViewById(R.id.wizard1_finger_print))
            .setOnSwipeByte((bytes, step, maxSteps) -> {
                swipeProgress.setProgress(step);

                if (fingerView != null) {
                    fingerView.setVisibility(View.GONE);
                    fingerView = null;
                }

                if (step >= maxSteps) {
                    // disable fingerprint widget
                    findViewById(R.id.wizard1_finger_print).setEnabled(false);
                    // generate id and stuff
                    createIdentity(bytes);
                }
            }, PROGRESS_MAX);

        findViewById(R.id.cancel_compose).setOnClickListener(v -> finish());
    }

    @SuppressLint("StaticFieldLeak")
    private void createIdentity(final byte[] bytes) {
        new AsyncTask<Void, Void, String>() {
            @Override
            protected void onPreExecute() {
                GenericProgressDialog.newInstance(R.string.wizard_first_create_id,
                    R.string.please_wait).show(getSupportFragmentManager(), DIALOG_TAG_CREATE_ID);
            }

            @Override
            protected String doInBackground(Void... params) {
                try {
                    if (!dependencies.getUserService().hasIdentity()) {
                        dependencies.getUserService().createIdentity(bytes);
                        dependencies.getPreferenceService().resetIDBackupCount();
                        dependencies.getPreferenceService().setLastIDBackupReminderTimestamp(Instant.now());
                        dependencies.getNotificationPreferenceService().setWizardRunning(true);
                    }
                } catch (final ThreemaException e) {
                    logger.error("Exception", e);
                    return e.getMessage();
                } catch (final Exception e) {
                    logger.error("Exception", e);
                    return getString(R.string.new_wizard_need_internet);
                }
                return null;
            }

            @Override
            protected void onPostExecute(String errorString) {
                DialogUtil.dismissDialog(getSupportFragmentManager(), DIALOG_TAG_CREATE_ID, true);

                if (TestUtil.isEmptyOrNull(errorString)) {
                    Intent intent = new Intent(WizardFingerPrintActivity.this, WizardBaseActivity.class);
                    intent.putExtra(WizardBaseActivity.EXTRA_NEW_IDENTITY_CREATED, true);
                    startActivity(intent);

                    overridePendingTransition(R.anim.abc_fade_in, R.anim.abc_fade_out);
                    finish();
                } else {
                    try {
                        dependencies.getUserService().removeIdentity();
                    } catch (Exception e) {
                        logger.error("Exception", e);
                    }
                    GenericAlertDialog dialog = GenericAlertDialog.newInstance(
                        R.string.error,
                        errorString,
                        R.string.try_again,
                        R.string.cancel);
                    dialog.setData(bytes);
                    getSupportFragmentManager().beginTransaction().add(dialog, DIALOG_TAG_CREATE_ERROR).commitAllowingStateLoss();
                }
            }
        }.execute();
    }

    @Override
    public void onYes(String tag, Object data) {
        if (tag.equals(DIALOG_TAG_CREATE_ERROR)) {
            // check again for a valid license and try to create identity
            StoreLicenseCheck.checkLicense(this, dependencies.getUserService());
            createIdentity((byte[]) data);
        }
    }

    @Override
    public void onNo(String tag, Object data) {
        finish();
    }

    @Override
    public void onNo(String tag) {
    }
}

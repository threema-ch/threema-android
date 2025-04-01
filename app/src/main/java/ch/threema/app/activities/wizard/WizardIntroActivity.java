/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2015-2024 Threema GmbH
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

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.drawable.AnimationDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContract;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.activities.PrivacyPolicyActivity;
import ch.threema.app.activities.SimpleWebViewActivity;
import ch.threema.app.backuprestore.csv.RestoreService;
import ch.threema.app.threemasafe.ThreemaSafeMDMConfig;
import ch.threema.app.utils.AnimationUtil;
import ch.threema.app.utils.AppRestrictionUtil;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.SynchronizeContactsUtil;
import ch.threema.app.utils.TestUtil;

public class WizardIntroActivity extends WizardBackgroundActivity {
    private static final int ACTIVITY_RESULT_PRIVACY_POLICY = 9442;
    private AnimationDrawable frameAnimation;

    private final ActivityResultLauncher<Void> backupResult = registerForActivityResult(new ActivityResultContract<>() {
        @NonNull
        @Override
        public Intent createIntent(@NonNull Context context, Void v) {
            return new Intent(WizardIntroActivity.this, WizardBackupRestoreActivity.class);
        }

        @Override
        public Boolean parseResult(int resultCode, @Nullable Intent intent) {
            return resultCode == Activity.RESULT_OK;
        }
    }, (ActivityResultCallback<Boolean>) result -> {
        if (Boolean.TRUE.equals(result) &&
            (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                || ConfigUtils.isPermissionGranted(WizardIntroActivity.this, Manifest.permission.POST_NOTIFICATIONS))
        ) {
            // When the backup is being restored and notifications can be shown, then exit the intro
            // activity. Otherwise the activity is resumed and if a backup is being restored, the
            // BackupRestoreProgressActivity is shown.
            finish();
        }
    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_wizard_intro);

        if (ConfigUtils.isWorkRestricted()) {
            // Skip privacy policy check if admin pre-set a backup to restore - either Safe or ID
            if (ThreemaSafeMDMConfig.getInstance().isRestoreForced()) {
                startActivity(new Intent(this, WizardSafeRestoreActivity.class));
                finish();
                return;
            } else {
                String backupString = AppRestrictionUtil.getStringRestriction(getString(R.string.restriction__id_backup));
                String backupPassword = AppRestrictionUtil.getStringRestriction(getString(R.string.restriction__id_backup_password));
                if (!TestUtil.isEmptyOrNull(backupString) && !TestUtil.isEmptyOrNull(backupPassword)) {
                    Intent intent = new Intent(this, WizardBackupRestoreActivity.class);
                    intent.putExtra(ThreemaApplication.INTENT_DATA_ID_BACKUP, backupString);
                    intent.putExtra(ThreemaApplication.INTENT_DATA_ID_BACKUP_PW, backupPassword);
                    startActivity(intent);
                    overridePendingTransition(R.anim.abc_fade_in, R.anim.abc_fade_out);
                    finish();
                    return;
                }
            }
        }

        LinearLayout buttonLayout = findViewById(R.id.button_layout);
        if (savedInstanceState == null) {
            buttonLayout.setVisibility(View.GONE);
            buttonLayout.postDelayed(() -> AnimationUtil.slideInFromBottomOvershoot(buttonLayout), 200);
        }

        ImageView imageView = findViewById(R.id.three_dots);
        imageView.setBackgroundResource(R.drawable.animation_wizard2);
        frameAnimation = (AnimationDrawable) imageView.getBackground();
        frameAnimation.setOneShot(false);
        frameAnimation.start();

        TextView privacyPolicyExplainText = findViewById(R.id.wizard_privacy_policy_explain);
        if (TestUtil.isEmptyOrNull(ThreemaApplication.getAppContext().getString(R.string.privacy_policy_url)) ||
            (ConfigUtils.isOnPremBuild() && !ConfigUtils.isDemoOPServer(preferenceService))) {
            privacyPolicyExplainText.setVisibility(View.GONE);
        } else {
            String privacyPolicy = getString(R.string.privacy_policy);
            SpannableStringBuilder builder = new SpannableStringBuilder();
            builder.append(String.format(getString(R.string.privacy_policy_explain), getString(R.string.app_name), privacyPolicy));
            int index = TextUtils.indexOf(builder, privacyPolicy);
            builder.setSpan(new ClickableSpan() {
                @Override
                public void onClick(View widget) {
                    Intent intent = new Intent(WizardIntroActivity.this, PrivacyPolicyActivity.class);
                    intent.putExtra(SimpleWebViewActivity.FORCE_DARK_THEME, true);
                    startActivityForResult(intent, ACTIVITY_RESULT_PRIVACY_POLICY);
                }
            }, index, index + privacyPolicy.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            privacyPolicyExplainText.setText(builder);
            privacyPolicyExplainText.setMovementMethod(LinkMovementMethod.getInstance());
        }

        ((TextView) findViewById(R.id.new_to_threema_title)).setText(getString(R.string.new_to_threema, getString(R.string.app_name)));
        ((TextView) findViewById(R.id.back_to_threema_title)).setText(getString(R.string.back_to_threema, getString(R.string.app_name)));

        findViewById(R.id.restore_backup).setOnClickListener(this::restoreBackup);
        findViewById(R.id.setup_threema).setOnClickListener(this::setupThreema);

        isContactSyncSettingConflict();
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (RestoreService.isRunning()) {
            finish();
        }
    }

    public void setupThreema(View view) {
        if (isContactSyncSettingConflict()) {
            return;
        }

        if (!userService.hasIdentity()) {
            startActivity(new Intent(this, WizardFingerPrintActivity.class));
        } else {
            startActivity(new Intent(this, WizardBaseActivity.class));
        }
        overridePendingTransition(R.anim.abc_fade_in, R.anim.abc_fade_out);
    }

    /**
     * Called from button in XML
     *
     * @param view
     */
    public void restoreBackup(View view) {
        if (isContactSyncSettingConflict()) {
            return;
        }

        backupResult.launch(null);
        overridePendingTransition(R.anim.abc_fade_in, R.anim.abc_fade_out);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        if (frameAnimation != null) {
            if (hasFocus) {
                frameAnimation.start();
            } else {
                frameAnimation.stop();
            }
        }
    }

    @Override
    protected boolean enableOnBackPressedCallback() {
        // Override the behavior of WizardBackgroundActivity to allow normal back navigation
        return false;
    }

    /**
     * Checks whether th_contact_sync conflicts with user restriction DISALLOW_MODIFY_ACCOUNTS.
     * If it conflicts, it shows an information dialog.
     *
     * @return false if there is no conflict, true if it is incompatible
     */
    private boolean isContactSyncSettingConflict() {
        if (ConfigUtils.isWorkBuild()) {
            Boolean contactSync = AppRestrictionUtil.getBooleanRestriction(getString(R.string.restriction__contact_sync));
            if (contactSync != null && contactSync && SynchronizeContactsUtil.isRestrictedProfile(this)) {
                showMDMContactsSyncDialog();
                return true;
            }
        }
        return false;
    }

    private void showMDMContactsSyncDialog() {
        AlertDialog alertDialog = new AlertDialog.Builder(this).create();
        alertDialog.setTitle(R.string.error);
        alertDialog.setMessage(getString(R.string.wizard_incompatible_contact_sync_params));
        alertDialog.setButton(DialogInterface.BUTTON_NEUTRAL, getString(R.string.ok), (dialog, which) -> dialog.dismiss());
        alertDialog.setOnDismissListener(dialog -> finish());
        alertDialog.setOnCancelListener(dialog -> finish());
        alertDialog.show();
    }
}

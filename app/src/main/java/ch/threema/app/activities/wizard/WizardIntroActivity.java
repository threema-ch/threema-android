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

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.koin.java.KoinJavaComponent;
import org.slf4j.Logger;

import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContract;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import ch.threema.app.AppConstants;
import ch.threema.app.R;
import ch.threema.app.di.DependencyContainer;
import ch.threema.app.webviews.PrivacyPolicyActivity;
import ch.threema.app.backuprestore.csv.RestoreService;
import ch.threema.app.threemasafe.ThreemaSafeMDMConfig;
import ch.threema.app.ui.InsetSides;
import ch.threema.app.ui.SpacingValues;
import ch.threema.app.ui.ViewExtensionsKt;
import ch.threema.app.utils.AnimationUtil;
import ch.threema.app.restrictions.AppRestrictionUtil;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.SynchronizeContactsUtil;
import ch.threema.app.utils.TestUtil;
import ch.threema.base.utils.LoggingUtil;

import static ch.threema.app.utils.ActiveScreenLoggerKt.logScreenVisibility;

public class WizardIntroActivity extends WizardBackgroundActivity {
    private static final Logger logger = LoggingUtil.getThreemaLogger("WizardIntroActivity");

    private static final int ACTIVITY_RESULT_PRIVACY_POLICY = 9442;

    @NonNull
    private final DependencyContainer dependencies = KoinJavaComponent.get(DependencyContainer.class);

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
        logScreenVisibility(this, logger);

        if (!dependencies.isAvailable()) {
            finish();
            return;
        }

        setContentView(R.layout.activity_wizard_intro);

        // Not every layout variation file of this activity has this company_logo ImageView
        ViewExtensionsKt.applyDeviceInsetsAsMargin(
            findViewById(R.id.company_logo),
            InsetSides.top(),
            SpacingValues.top(R.dimen.grid_unit_x4)
        );

        final LinearLayout buttonsLayout = findViewById(R.id.button_layout);
        ViewExtensionsKt.applyDeviceInsetsAsMargin(
            buttonsLayout,
            InsetSides.bottom(),
            SpacingValues.all(R.dimen.grid_unit_x2)
        );

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
                    intent.putExtra(AppConstants.INTENT_DATA_ID_BACKUP, backupString);
                    intent.putExtra(AppConstants.INTENT_DATA_ID_BACKUP_PW, backupPassword);
                    startActivity(intent);
                    overridePendingTransition(R.anim.abc_fade_in, R.anim.abc_fade_out);
                    finish();
                    return;
                }
            }
        }

        if (savedInstanceState == null && buttonsLayout != null) {
            buttonsLayout.setVisibility(View.GONE);
            buttonsLayout.postDelayed(() -> AnimationUtil.slideInFromBottomOvershoot(buttonsLayout), 200);
        }

        TextView privacyPolicyExplainText = findViewById(R.id.wizard_privacy_policy_explain);
        if (TestUtil.isEmptyOrNull(getString(R.string.privacy_policy_url)) ||
            (ConfigUtils.isOnPremBuild() && !ConfigUtils.isDemoOPServer(dependencies.getPreferenceService()))) {
            privacyPolicyExplainText.setVisibility(View.GONE);
        } else {
            String privacyPolicy = getString(R.string.privacy_policy);
            SpannableStringBuilder builder = new SpannableStringBuilder();
            builder.append(String.format(getString(R.string.privacy_policy_explain), privacyPolicy));
            int index = TextUtils.indexOf(builder, privacyPolicy);
            builder.setSpan(new ClickableSpan() {
                @Override
                public void onClick(@NonNull View widget) {
                    Intent intent = PrivacyPolicyActivity.createIntent(WizardIntroActivity.this, true);
                    startActivityForResult(intent, ACTIVITY_RESULT_PRIVACY_POLICY);
                }
            }, index, index + privacyPolicy.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            privacyPolicyExplainText.setText(builder);
            privacyPolicyExplainText.setMovementMethod(LinkMovementMethod.getInstance());
        }

        ((TextView) findViewById(R.id.new_to_threema_title)).setText(getString(R.string.new_to_threema, getString(R.string.app_name)));
        ((TextView) findViewById(R.id.back_to_threema_title)).setText(getString(R.string.back_to_threema, getString(R.string.app_name)));

        findViewById(R.id.setup_threema_compose).setOnClickListener(v -> setupThreema());
        findViewById(R.id.restore_backup_compose).setOnClickListener(v -> restoreBackup());

        isContactSyncSettingConflict();
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (RestoreService.isRunning()) {
            finish();
        }
    }

    public void setupThreema() {
        if (isContactSyncSettingConflict()) {
            return;
        }

        if (!dependencies.getUserService().hasIdentity()) {
            startActivity(new Intent(this, WizardFingerPrintActivity.class));
        } else {
            startActivity(new Intent(this, WizardBaseActivity.class));
        }
        overridePendingTransition(R.anim.abc_fade_in, R.anim.abc_fade_out);
    }

    public void restoreBackup() {
        if (isContactSyncSettingConflict()) {
            return;
        }
        backupResult.launch(null);
        overridePendingTransition(R.anim.abc_fade_in, R.anim.abc_fade_out);
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

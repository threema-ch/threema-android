/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2015-2020 Threema GmbH
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

import android.content.Intent;
import android.graphics.drawable.AnimationDrawable;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Date;

import androidx.appcompat.widget.AppCompatCheckBox;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.activities.PrivacyPolicyActivity;
import ch.threema.app.dialogs.WizardDialog;
import ch.threema.app.services.PreferenceService;
import ch.threema.app.threemasafe.ThreemaSafeMDMConfig;
import ch.threema.app.utils.AnimationUtil;
import ch.threema.app.utils.AppRestrictionUtil;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.TestUtil;

public class WizardIntroActivity extends WizardBackgroundActivity implements WizardDialog.WizardDialogCallback {

	private static final String DIALOG_TAG_CHECK_PP = "pp";
	private static final int ACTIVITY_RESULT_PRIVACY_POLICY = 9442;

	private AnimationDrawable frameAnimation;
	private AppCompatCheckBox privacyPolicyCheckBox;
	private LinearLayout buttonLayout;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_wizard_intro);

		privacyPolicyCheckBox = findViewById(R.id.wizard_switch_accept_privacy_policy);
		privacyPolicyCheckBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				if (!isChecked) {
					if (preferenceService.getPrivacyPolicyAccepted() != null) {
						preferenceService.clearPrivacyPolicyAccepted();
					}
				} else {
					privacyPolicyCheckBox.setBackgroundDrawable(getResources().getDrawable(R.drawable.shape_switch));
				}
			}
		});
		TextView privacyPolicyExplainText = findViewById(R.id.wizard_privacy_policy_explain);
		buttonLayout = findViewById(R.id.button_layout);

		if (ConfigUtils.isWorkRestricted()) {
			// Skip privacy policy check if admin pre-set a backup to restore - either Safe or ID
			if (ThreemaSafeMDMConfig.getInstance().isRestoreForced()) {
				checkPrivacyPolicy(true);
				restoreBackup(null);
				finish();
				return;
			} else {
				String backupString = AppRestrictionUtil.getStringRestriction(getString(R.string.restriction__id_backup));
				String backupPassword = AppRestrictionUtil.getStringRestriction(getString(R.string.restriction__id_backup_password));
				if (!TestUtil.empty(backupString) && !TestUtil.empty(backupPassword)) {
					checkPrivacyPolicy(true);
					Intent intent = new Intent(this, WizardRestoreMainActivity.class);

					if (!TestUtil.empty(backupString) && !TestUtil.empty(backupPassword)) {
						intent.putExtra(ThreemaApplication.INTENT_DATA_ID_BACKUP, backupString);
						intent.putExtra(ThreemaApplication.INTENT_DATA_ID_BACKUP_PW, backupPassword);
					}
					startActivity(intent);
					overridePendingTransition(R.anim.abc_fade_in, R.anim.abc_fade_out);
					finish();
					return;
				}
			}
		}

		if (savedInstanceState == null) {
			buttonLayout.setVisibility(View.GONE);
			buttonLayout.postDelayed(() -> AnimationUtil.slideInFromBottomOvershoot(buttonLayout), 200);
		}

		ImageView imageView = findViewById(R.id.three_dots);
		imageView.setBackgroundResource(R.drawable.animation_wizard2);
		frameAnimation = (AnimationDrawable) imageView.getBackground();
		frameAnimation.setOneShot(false);
		frameAnimation.start();

		if (preferenceService.getPrivacyPolicyAccepted() != null) {
			privacyPolicyCheckBox.setVisibility(View.GONE);
			privacyPolicyExplainText.setVisibility(View.GONE);
		} else {
			String privacyPolicy = getString(R.string.privacy_policy);
			SpannableStringBuilder builder = new SpannableStringBuilder();
			builder.append(String.format(getString(R.string.privacy_policy_explain), getString(R.string.app_name), privacyPolicy));
			int index = TextUtils.indexOf(builder, privacyPolicy);
			builder.setSpan(new ClickableSpan() {
				@Override
				public void onClick(View widget) {
					ConfigUtils.setAppTheme(ConfigUtils.THEME_DARK);
					startActivityForResult(new Intent(WizardIntroActivity.this, PrivacyPolicyActivity.class), ACTIVITY_RESULT_PRIVACY_POLICY);
				}
			}, index, index + privacyPolicy.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
			privacyPolicyExplainText.setText(builder);
			privacyPolicyExplainText.setMovementMethod(LinkMovementMethod.getInstance());
		}

		findViewById(R.id.restore_backup).setOnClickListener(this::restoreBackup);
		findViewById(R.id.setup_threema).setOnClickListener(this::setupThreema);
	}

	public void setupThreema(View view) {
		if (checkPrivacyPolicy(false)) {
			if (!userService.hasIdentity()) {
				startActivity(new Intent(this, WizardFingerPrintActivity.class));
			} else {
				startActivity(new Intent(this, WizardBaseActivity.class));
			}
			overridePendingTransition(R.anim.abc_fade_in, R.anim.abc_fade_out);
		}
	}

	/**
	 * Called from button in XML
	 * @param view
	 */
	public void restoreBackup(View view) {
		if (checkPrivacyPolicy(false)) {
			startActivity(new Intent(this, WizardRestoreMainActivity.class));
			overridePendingTransition(R.anim.abc_fade_in, R.anim.abc_fade_out);
		}
	}

	private boolean checkPrivacyPolicy(boolean force) {
		if (preferenceService.getPrivacyPolicyAccepted() != null) {
			return true;
		}

		if (!privacyPolicyCheckBox.isChecked() && !force) {
			WizardDialog.newInstance(String.format(getString(R.string.privacy_policy_check_confirm), getString(R.string.app_name)), R.string.ok).show(getSupportFragmentManager(), DIALOG_TAG_CHECK_PP);
			return false;
		}

		preferenceService.setPrivacyPolicyAccepted(new Date(), force ? PreferenceService.PRIVACY_POLICY_ACCEPT_IMPLICIT : PreferenceService.PRIVACY_POLICY_ACCEPT_EXCPLICIT);
		return true;
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
	public void onYes(String tag, Object data) {
		privacyPolicyCheckBox.setBackgroundDrawable(getResources().getDrawable(R.drawable.shape_switch_alert));
		privacyPolicyCheckBox.postDelayed(() -> {
			if (!isFinishing()) {
				privacyPolicyCheckBox.setBackgroundDrawable(getResources().getDrawable(R.drawable.shape_switch));
			}
		}, 400);
		privacyPolicyCheckBox.postDelayed(() -> {
			if (!isFinishing()) {
				privacyPolicyCheckBox.setBackgroundDrawable(getResources().getDrawable(R.drawable.shape_switch_alert));
			}
		}, 600);
	}

	@Override
	public void onNo(String tag) {

	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);

		ConfigUtils.resetAppTheme();
	}
}

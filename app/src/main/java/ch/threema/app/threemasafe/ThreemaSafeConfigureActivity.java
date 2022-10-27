/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2018-2022 Threema GmbH
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

package ch.threema.app.threemasafe;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.textfield.TextInputLayout;

import org.slf4j.Logger;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;
import androidx.appcompat.app.ActionBar;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.activities.HomeActivity;
import ch.threema.app.activities.ThreemaToolbarActivity;
import ch.threema.app.dialogs.GenericAlertDialog;
import ch.threema.app.dialogs.GenericProgressDialog;
import ch.threema.app.fragments.wizard.WizardFragment1;
import ch.threema.app.services.UserService;
import ch.threema.app.utils.AppRestrictionUtil;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.DialogUtil;
import ch.threema.app.utils.TestUtil;
import ch.threema.app.utils.TextUtil;
import ch.threema.base.ThreemaException;
import ch.threema.base.utils.LoggingUtil;

import static ch.threema.app.threemasafe.ThreemaSafeServiceImpl.MIN_PW_LENGTH;

public class ThreemaSafeConfigureActivity extends ThreemaToolbarActivity implements ThreemaSafeAdvancedDialog.WizardDialogCallback, GenericAlertDialog.DialogClickListener {
	private static final Logger logger = LoggingUtil.getThreemaLogger("ThreemaSafeConfigureActivity");

	private static final String DIALOG_TAG_PREPARING = "prep";
	private static final String DIALOG_TAG_ADVANCED = "adv";

	public static final String EXTRA_CHANGE_PASSWORD = "cp";
	public static final String EXTRA_WORK_FORCE_PASSWORD = "fp";
	public static final String EXTRA_OPEN_HOME_ACTIVITY = "oha";
	private static final String DIALOG_TAG_UNSAFE_PASSWORD = "unsafe";
	private static final String DIALOG_TAG_UNSAFE_PASSWORD_WORK = "unsafework";

	private ThreemaSafeService threemaSafeService;
	private UserService userService;

	private EditText password1, password2;
	private String safePassword = null;
	private TextInputLayout password1layout, password2layout;
	private Button nextButton;
	private boolean updatePasswordOnly;
	private ThreemaSafeServerInfo serverInfo;
	private boolean openHomeActivity = false;

	@SuppressLint("SetTextI18n")
	@Override
	protected boolean initActivity(Bundle savedInstanceState) {
		super.initActivity(savedInstanceState);

		ActionBar actionBar = getSupportActionBar();
		if (actionBar == null) {
			return false;
		}
		actionBar.setDisplayHomeAsUpEnabled(true);

		try {
			threemaSafeService = ThreemaApplication.getServiceManager().getThreemaSafeService();
			userService = ThreemaApplication.getServiceManager().getUserService();
		} catch (Exception e) {
			return false;
		}

		Intent intent = getIntent();

		this.password1 = findViewById(R.id.safe_password1);
		this.password2 = findViewById(R.id.safe_password2);
		this.password1layout = findViewById(R.id.password1layout);
		this.password2layout = findViewById(R.id.password2layout);

		this.password1.addTextChangedListener(new PasswordWatcher());
		this.password2.addTextChangedListener(new PasswordWatcher());

		Button advancedButton = findViewById(R.id.advanced_options);
		if ((intent != null && intent.getBooleanExtra(EXTRA_CHANGE_PASSWORD, false))) {
			updatePasswordOnly = true;
			actionBar.setTitle(R.string.safe_change_password);
			advancedButton.setVisibility(View.INVISIBLE);
		} else {
			updatePasswordOnly = false;
			actionBar.setTitle(R.string.safe_configure_choose_password_title);
			advancedButton.setVisibility(View.VISIBLE);
			advancedButton.setOnClickListener(v -> {
				ThreemaSafeAdvancedDialog dialog = ThreemaSafeAdvancedDialog.newInstance(serverInfo, true);
				dialog.show(getSupportFragmentManager(), DIALOG_TAG_ADVANCED);
			});

			if (ConfigUtils.isWorkRestricted() && intent != null && intent.getBooleanExtra(EXTRA_WORK_FORCE_PASSWORD, false)) {
				TextView explainText = findViewById(R.id.safe_enable_explain);
				explainText.setText(getString(R.string.work_safe_forced_explain) + "\n\n" + getString(R.string.safe_configure_choose_password));
			}
		}

		nextButton = findViewById(R.id.next);
		nextButton.setOnClickListener(v -> {
			// finish up
			saveChangesAndExit(safePassword);
		});
		nextButton.setEnabled(false);
		nextButton.setVisibility(View.VISIBLE);

		this.serverInfo = preferenceService.getThreemaSafeServerInfo();

		if (ConfigUtils.isWorkRestricted()) {
			ThreemaSafeMDMConfig safeMDMConfig = ThreemaSafeMDMConfig.getInstance();
			if (safeMDMConfig.isBackupExpertSettingsDisabled()) {
				advancedButton.setVisibility(View.INVISIBLE);
				this.serverInfo = safeMDMConfig.getServerInfo();
			}
		}

		openHomeActivity = (intent != null && intent.getBooleanExtra(EXTRA_OPEN_HOME_ACTIVITY, false))
			|| (savedInstanceState != null && savedInstanceState.getBoolean(EXTRA_OPEN_HOME_ACTIVITY, false));

		return true;
	}

	@Override
	public void onSaveInstanceState(@NonNull Bundle outState, @NonNull PersistableBundle outPersistentState) {
		super.onSaveInstanceState(outState, outPersistentState);

		outState.putBoolean(EXTRA_OPEN_HOME_ACTIVITY, openHomeActivity);
	}

	@Override
	public int getLayoutResource() {
		return R.layout.activity_threema_safe_configure;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case android.R.id.home:
				onBackPressed();
				break;
		}
		return false;
	}

	@Override
	public void onBackPressed() {
		super.onBackPressed();
	}

	@SuppressLint("StaticFieldLeak")
	private void saveChangesAndExit(final String safePassword) {
		if (!TestUtil.empty(safePassword)) {
			new AsyncTask<Void, Void, Boolean>() {
				byte[] masterkey;

				@Override
				protected void onPreExecute() {
					GenericProgressDialog.newInstance(R.string.preparing_threema_safe, R.string.please_wait).show(getSupportFragmentManager(), DIALOG_TAG_PREPARING);
				}

				@Override
				protected Boolean doInBackground(Void... voids) {
					masterkey = threemaSafeService.deriveMasterKey(safePassword, userService.getIdentity());

					if (!TextUtil.checkBadPassword(ThreemaSafeConfigureActivity.this, safePassword)) {
						if (updatePasswordOnly) {
							deleteExistingBackup();
						}
						return true;
					}
					return false;
				}

				@Override
				protected void onPostExecute(Boolean passwordOK) {
					DialogUtil.dismissDialog(getSupportFragmentManager(), DIALOG_TAG_PREPARING, true);

					if (masterkey != null) {
						if (!passwordOK) {
							Context context = ThreemaSafeConfigureActivity.this;

							if (AppRestrictionUtil.isSafePasswordPatternSet(context)) {
								GenericAlertDialog.newInstance(R.string.password_bad, AppRestrictionUtil.getSafePasswordMessage(context), R.string.try_again, 0, false).show(getSupportFragmentManager(), DIALOG_TAG_UNSAFE_PASSWORD_WORK);
							} else {
								GenericAlertDialog dialog = GenericAlertDialog.newInstance(R.string.password_bad, R.string.password_bad_explain, R.string.continue_anyway, R.string.try_again, false);
								dialog.setData(masterkey);
								dialog.show(getSupportFragmentManager(), DIALOG_TAG_UNSAFE_PASSWORD);
							}
						} else {
							storeKeyAndFinish(masterkey);
						}
					} else {
						Toast.makeText(ThreemaSafeConfigureActivity.this, R.string.safe_error_preparing, Toast.LENGTH_LONG).show();
						finish();
					}
				}
			}.execute();
		} else {
			threemaSafeService.storeMasterKey(new byte[0]);
			finish();
		}
	}

	@WorkerThread
	private boolean deleteExistingBackup() {
		try {
			threemaSafeService.deleteBackup();
			return true;
		} catch (ThreemaException e) {
			logger.error("Exception", e);
		}
		return false;
	}

	private void storeKeyAndFinish(byte[] masterkey) {
		threemaSafeService.storeMasterKey(masterkey);
		preferenceService.setThreemaSafeServerInfo(serverInfo);
		threemaSafeService.setEnabled(true);
		threemaSafeService.uploadNow(this, true);

		if (updatePasswordOnly) {
			Toast.makeText(ThreemaApplication.getAppContext(), R.string.safe_password_updated, Toast.LENGTH_LONG).show();
		} else {
			Toast.makeText(ThreemaApplication.getAppContext(), R.string.safe_activated, Toast.LENGTH_LONG).show();
		}

		if (openHomeActivity) {
			startActivity(new Intent(ThreemaSafeConfigureActivity.this, HomeActivity.class));
		}

		finish();
	}

	private class PasswordWatcher implements TextWatcher {
		private PasswordWatcher() {}

		@Override
		public void beforeTextChanged(CharSequence s, int start, int count, int after) {
		}

		@Override
		public void onTextChanged(CharSequence s, int start, int before, int count) {
		}

		@Override
		public void afterTextChanged(Editable s) {
			boolean passwordOk = getPasswordOK(password1.getText().toString(), password2.getText().toString());

			if (passwordOk) {
				safePassword = s.toString();
				nextButton.setEnabled(true);
			} else {
				safePassword = null;
				nextButton.setEnabled(false);
			}
		}
	}

	private boolean getPasswordOK(String password1Text, String password2Text) {
		boolean lengthOk = WizardFragment1.getPasswordLengthOK(password1Text, AppRestrictionUtil.isSafePasswordPatternSet(this) ? 1 :MIN_PW_LENGTH);
		boolean passwordsMatch = password1Text.equals(password2Text);

		if (!lengthOk && password1Text.length() > 0) {
			this.password1layout.setError(getString(R.string.password_too_short_generic));
			this.password2layout.setError(null);
		} else {
			this.password1layout.setError(null);
			if (!TestUtil.empty(this.password2.getText())) {
				this.password2layout.setError(passwordsMatch ? null : getString(R.string.passwords_dont_match));
			} else {
				this.password2layout.setError(null);
			}
		}

		return (lengthOk && passwordsMatch);
	}

	@Override
	public void onYes(String tag, ThreemaSafeServerInfo serverInfo) {
		this.serverInfo = serverInfo;
	}

	@Override
	public void onNo(String tag) {}

	@SuppressLint("StaticFieldLeak")
	@Override
	public void onYes(String tag, Object data) {
		if (!DIALOG_TAG_UNSAFE_PASSWORD_WORK.equals(tag)) {
			if (updatePasswordOnly) {
				new AsyncTask<Void, Void, Boolean>() {
					@Override
					protected Boolean doInBackground(Void... voids) {
						return deleteExistingBackup();
					}

					@Override
					protected void onPostExecute(Boolean success) {
						storeKeyAndFinish((byte[]) data);
					}
				}.execute();
			} else {
				storeKeyAndFinish((byte[]) data);
			}
		}
	}

	@Override
	public void onNo(String tag, Object data) {
		// stay put
	}

	@Override
	public void onConfigurationChanged(@NonNull Configuration newConfig) {
		super.onConfigurationChanged(newConfig);

		ConfigUtils.adjustToolbar(this, getToolbar());
	}
}

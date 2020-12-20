/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2013-2020 Threema GmbH
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

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.Editable;
import android.text.Html;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.text.method.LinkMovementMethod;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.dialogs.GenericProgressDialog;
import ch.threema.app.exceptions.FileSystemNotPresentException;
import ch.threema.app.services.AppRestrictionService;
import ch.threema.app.services.license.LicenseService;
import ch.threema.app.services.license.LicenseServiceUser;
import ch.threema.app.services.license.SerialCredentials;
import ch.threema.app.services.license.UserCredentials;
import ch.threema.app.utils.AppRestrictionUtil;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.DialogUtil;
import ch.threema.app.utils.EditTextUtil;
import ch.threema.app.utils.TestUtil;

// this should NOT extend ThreemaToolbarActivity
public class EnterSerialActivity extends ThreemaActivity {
	private static final Logger logger = LoggerFactory.getLogger(EnterSerialActivity.class);

	private static final String BUNDLE_PASSWORD = "bupw";
	private static final String BUNDLE_LICENSE_KEY = "bulk";
	private static final String DIALOG_TAG_CHECKING = "check";
	private TextView stateTextView, privateExplainText = null;
	private EditText licenseKeyText, passwordText;
	private ImageView unlockButton;
	private Button loginButton;
	private LicenseService licenseService;

	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		if (!ConfigUtils.isSerialLicensed()) {
			finish();
			return;
		}

		setContentView(R.layout.activity_enter_serial);

		try {
			licenseService = ThreemaApplication.getServiceManager().getLicenseService();
		} catch (NullPointerException|FileSystemNotPresentException e) {
			logger.error("Exception", e);
			Toast.makeText(this, "Service Manager not available", Toast.LENGTH_LONG).show();
			finish();
			return;
		}

		if (licenseService == null) {
			finish();
			return;
		}

		stateTextView = findViewById(R.id.unlock_state);
		licenseKeyText = findViewById(R.id.passphrase);
		passwordText = findViewById(R.id.password);

		if (!ConfigUtils.isWorkBuild()) {
			licenseKeyText.addTextChangedListener(new PasswordWatcher());
			licenseKeyText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
			licenseKeyText.setFilters(new InputFilter[]{new InputFilter.AllCaps(), new InputFilter.LengthFilter(11)});
			licenseKeyText.setOnKeyListener(new View.OnKeyListener() {
				@Override
				public boolean onKey(View v, int keyCode, KeyEvent event) {
					if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_ENTER) {
						if (licenseKeyText.getText().length() == 11) {
							doUnlock();
						}
						return true;
					}
					return false;
				}
			});
			unlockButton = findViewById(R.id.unlock_button);
			unlockButton.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					doUnlock();
				}
			});

			this.enableLogin(false);
		} else {
			privateExplainText = findViewById(R.id.private_explain);
			if (privateExplainText != null) {
				privateExplainText.setText(Html.fromHtml(getString(R.string.private_threema_download)));
				privateExplainText.setClickable(true);
				privateExplainText.setMovementMethod (LinkMovementMethod.getInstance());
			}
			licenseKeyText.addTextChangedListener(new TextChangeWatcher());
			passwordText.addTextChangedListener(new TextChangeWatcher());
			loginButton = findViewById(R.id.unlock_button_work);
			loginButton.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					doUnlock();
				}
			});

			//always enable login button
			this.enableLogin(true);
		}

		String scheme = null;
		Uri data = null;
		Intent intent = getIntent();
		if (intent != null) {
			data = intent.getData();
			if (data != null) {
				scheme = data.getScheme();
			}
		}

		if (!ConfigUtils.isSerialLicenseValid()) {
			if (scheme != null) {
				if (scheme.startsWith(ThreemaApplication.uriScheme)) {
					parseUrlAndCheck(data);
				} else if (scheme.startsWith("https")) {
					String path = data.getPath();

					if (path != null && path.length() > 1) {
						path = path.substring(1);
						if (path.startsWith("license")) {
							parseUrlAndCheck(data);
						}
					}
				}
			}

			if (ConfigUtils.isWorkRestricted()) {
				String username = AppRestrictionUtil.getStringRestriction(getString(R.string.restriction__license_username));
				String password = AppRestrictionUtil.getStringRestriction(getString(R.string.restriction__license_password));
				if (!TestUtil.empty(username) && !TestUtil.empty(password)) {
					check(new UserCredentials(username, password));
				}
			}
		} else {
			// we get here if called from url intent and we're already licensed
			if (scheme != null) {
				Toast.makeText(this, R.string.already_licensed, Toast.LENGTH_LONG).show();
				finish();
			}
		}
	}

	private void enableLogin(boolean enable) {
		if (!ConfigUtils.isWorkBuild()) {
			if (this.unlockButton != null) {
				unlockButton.setClickable(enable);
				unlockButton.setEnabled(enable);
			}
		} else {
			if (this.loginButton != null) {
				this.loginButton.setClickable(true);
				this.loginButton.setEnabled(true);
			}
		}
	}

	private void parseUrlAndCheck(Uri data) {
		String query = data.getQuery();

		if (!TestUtil.empty(query)) {
			if (licenseService instanceof LicenseServiceUser) {
				final String username = data.getQueryParameter("username");
				final String password = data.getQueryParameter("password");
				if (!TestUtil.empty(username) && !TestUtil.empty(password)) {
					check(new UserCredentials(username, password));
					return;
				}
			} else {
				final String key = data.getQueryParameter("key");
				if (!TestUtil.empty(key)) {
					check(new SerialCredentials(key));
					return;
				}
			}
		}
		Toast.makeText(this, R.string.invalid_input, Toast.LENGTH_LONG).show();
	}

	private void doUnlock() {
		// hide keyboard to make error message visible on low resolution displays
		EditTextUtil.hideSoftKeyboard(this.licenseKeyText);

		this.enableLogin(false);

		if (ConfigUtils.isWorkBuild()) {
			if (!TestUtil.empty(this.licenseKeyText.getText().toString()) && !TestUtil.empty(this.passwordText.getText().toString())) {
				this.check(new UserCredentials(this.licenseKeyText.getText().toString(), this.passwordText.getText().toString()));
			} else {
				this.enableLogin(true);
				this.stateTextView.setText(getString(R.string.invalid_input));
			}
		} else {
			this.check(new SerialCredentials(this.licenseKeyText.getText().toString()));
		}
	}

	private class PasswordWatcher implements TextWatcher {
		@Override
		public void beforeTextChanged(CharSequence s, int start, int count, int after) {
		}

		@Override
		public void onTextChanged(CharSequence s, int start, int before, int count) {
		}

		@Override
		public void afterTextChanged(Editable s) {
			String initial = s.toString();
			String processed = initial.replaceAll("[^a-zA-Z0-9]", "");
			processed = processed.replaceAll("([a-zA-Z0-9]{5})(?=[a-zA-Z0-9])", "$1-");

			if (!initial.equals(processed)) {
				s.replace(0, initial.length(), processed);
			}

			//enable login only if the length of the key is 11 chars
			enableLogin(s.length() == 11);
		}
	}

	public class TextChangeWatcher implements TextWatcher {
		@Override
		public void beforeTextChanged(CharSequence s, int start, int count, int after) {
		}

		@Override
		public void onTextChanged(CharSequence s, int start, int before, int count) {
		}

		@Override
		public void afterTextChanged(Editable s) {
			if (stateTextView != null) {
				stateTextView.setText("");
			}
			if (privateExplainText != null) {
				privateExplainText.setVisibility(View.VISIBLE);
			}
		}
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);

		if (!TestUtil.empty(licenseKeyText.getText())) {
			outState.putString(BUNDLE_LICENSE_KEY, licenseKeyText.getText().toString());
		}

		if (!TestUtil.empty(passwordText.getText())) {
			outState.putString(BUNDLE_PASSWORD, passwordText.getText().toString());
		}
	}

	@SuppressLint("StaticFieldLeak")
	private void check(final LicenseService.Credentials credentials) {
		new AsyncTask<Void, Void, String>() {
			@Override
			protected void onPreExecute() {
				GenericProgressDialog.newInstance(R.string.checking_serial, R.string.please_wait).show(getSupportFragmentManager(), DIALOG_TAG_CHECKING);
			}

			@Override
			protected String doInBackground(Void... voids) {
				String error = getString(R.string.error);
				try {
					error = licenseService.validate(credentials);
					if (error == null) {
						// validated
						if (ConfigUtils.isWorkBuild()) {
							AppRestrictionService.getInstance()
									.fetchAndStoreWorkMDMSettings(
											ThreemaApplication.getServiceManager().getAPIConnector(),
											(UserCredentials) credentials
									);
						}
					}
				} catch (Exception e) {
					logger.error("Exception", e);
				}
				return error;
			}

			@Override
			protected void onPostExecute(String error) {
				DialogUtil.dismissDialog(getSupportFragmentManager(), DIALOG_TAG_CHECKING, true);
				enableLogin(true);
				if (error == null) {
					ConfigUtils.recreateActivity(EnterSerialActivity.this);
				} else {
					changeState(error);
				}
			}
		}.execute();
	}

	private void changeState(String state) {
		this.stateTextView.setText(state);
	}

	@Override
	public void onBackPressed() {
		// finish application
		moveTaskToBack(true);
		finish();
	}

	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		// We override this method to avoid restarting the entire
		// activity when the keyboard is opened or orientation changes
		super.onConfigurationChanged(newConfig);
	}
}

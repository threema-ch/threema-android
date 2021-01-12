/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2013-2021 Threema GmbH
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

import android.app.NotificationManager;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.PorterDuff;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.material.textfield.TextInputLayout;

import java.util.Arrays;

import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.dialogs.GenericProgressDialog;
import ch.threema.app.services.PassphraseService;
import ch.threema.app.ui.ThreemaTextInputEditText;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.DialogUtil;
import ch.threema.app.utils.EditTextUtil;
import ch.threema.app.utils.RuntimeUtil;
import ch.threema.localcrypto.MasterKey;

// this should NOT extend ThreemaToolbarActivity
public class UnlockMasterKeyActivity extends ThreemaActivity {

	private static final String DIALOG_TAG_UNLOCKING = "dtu";
	private ThreemaTextInputEditText passphraseText;
	private TextInputLayout passphraseLayout;
	private ImageView unlockButton;
	private MasterKey masterKey = ThreemaApplication.getMasterKey();

	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);

		setContentView(R.layout.activity_unlock_masterkey);

		TextView infoText = findViewById(R.id.unlock_info);
		TypedArray array = getTheme().obtainStyledAttributes(new int[]{android.R.attr.textColorSecondary});
		infoText.getCompoundDrawables()[0].setColorFilter(array.getColor(0, -1), PorterDuff.Mode.SRC_IN);
		array.recycle();

		passphraseLayout = findViewById(R.id.passphrase_layout);
		passphraseText = findViewById(R.id.passphrase);
		passphraseText.addTextChangedListener(new PasswordWatcher());
		passphraseText.setOnKeyListener(new View.OnKeyListener() {
			@Override
			public boolean onKey(View v, int keyCode, KeyEvent event) {
				if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_ENTER) {
					if (isValidEntry(passphraseText)) {
						doUnlock();
					}
					return true;
				}
				return false;
			}
		});
		passphraseText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
			@Override
			public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
				boolean handled = false;
				if (actionId == EditorInfo.IME_ACTION_GO) {
					if (isValidEntry(passphraseText)) {
						doUnlock();
						handled = true;
					}
				}
				return handled;
			}
		});

		unlockButton = findViewById(R.id.unlock_button);
		unlockButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				doUnlock();
			}
		});
		unlockButton.setClickable(false);
		unlockButton.setEnabled(false);
	}

	@Override
	protected void onApplyThemeResource(Resources.Theme theme, int resid, boolean first) {
		if (ConfigUtils.getAppTheme(this) == ConfigUtils.THEME_DARK) {
			theme.applyStyle(R.style.Theme_Threema_WithToolbar_Dark, true);
		} else {
			super.onApplyThemeResource(theme, resid, first);
		}
	}

	@Override
	protected void onResume() {
		super.onResume();
		//check if the key is unlocked!
		if(!this.justCheck() && !this.masterKey.isLocked()) {
			this.finish();
		}
	}

	private void doUnlock() {
		unlockButton.setEnabled(false);
		unlockButton.setClickable(false);

		// hide keyboard to make error message visible on low resolution displays
		EditTextUtil.hideSoftKeyboard(this.passphraseText);

		this.unlock(this.passphraseText.getPassphrase());
	}

	public class PasswordWatcher implements TextWatcher {
		@Override
		public void beforeTextChanged(CharSequence s, int start, int count, int after) {
		}

		@Override
		public void onTextChanged(CharSequence s, int start, int before, int count) {
		}

		@Override
		public void afterTextChanged(Editable s) {
			if (unlockButton != null) {
				if (passphraseText != null && passphraseText.getText() != null && passphraseText.getText().length() > 0) {
					passphraseLayout.setError(null);
				}
				unlockButton.setEnabled(isValidEntry(passphraseText));
				unlockButton.setClickable(isValidEntry(passphraseText));
			}
		}
	}

	private boolean isValidEntry(EditText passphraseText) {
		return passphraseText != null && passphraseText.getText() != null && passphraseText.getText().length() >= 8;
	}

	private void unlock(final char[] passphrase) {
		final boolean justCheck = this.justCheck();

		if (justCheck || this.masterKey.isLocked()) {
			//only change on master key!
			GenericProgressDialog.newInstance(R.string.masterkey_unlocking, R.string.please_wait).show(getSupportFragmentManager(), DIALOG_TAG_UNLOCKING);

			new Thread(new Runnable() {
				@Override
				public void run() {
					boolean isValid;
					if (justCheck) {
						isValid = masterKey.checkPassphrase(passphrase);
					} else {
						isValid = masterKey.unlock(passphrase);
					}

					// clear passphrase
					Arrays.fill(passphrase, ' ');

					if (!isValid) {
					 	RuntimeUtil.runOnUiThread(() -> {
							 passphraseLayout.setError(getString(R.string.invalid_passphrase));
							 passphraseText.setText("");
						 });
					} else {
						if (justCheck) {
						 	RuntimeUtil.runOnUiThread(() -> {
								 UnlockMasterKeyActivity.this.setResult(RESULT_OK);
								 UnlockMasterKeyActivity.this.finish();
							 });
						} else {
							//finish after unlock
							RuntimeUtil.runOnUiThread(() -> {
								ThreemaApplication.reset();

								new Thread(() -> {
									/* trigger a connection now - as there was no identity before the master key was unlocked */
									ThreemaApplication.getServiceManager().getLifetimeService().acquireConnection("UnlockMasterKey");
								}).start();

								// cancel all notifications...if any
								NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
								notificationManager.cancelAll();

								/* show persistent notification */
								PassphraseService.start(UnlockMasterKeyActivity.this.getApplicationContext());

								UnlockMasterKeyActivity.this.finish();
							});
						}
					}
				 	RuntimeUtil.runOnUiThread(() -> DialogUtil.dismissDialog(getSupportFragmentManager(), DIALOG_TAG_UNLOCKING, true));
				}
			}).start();
		} else {
			this.finish();
		}
	}

	private boolean justCheck() {
		return getIntent().getBooleanExtra(ThreemaApplication.INTENT_DATA_PASSPHRASE_CHECK, false);
	}

	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		// We override this method to avoid restarting the entire
		// activity when the keyboard is opened or orientation changes
		super.onConfigurationChanged(newConfig);
	}
}

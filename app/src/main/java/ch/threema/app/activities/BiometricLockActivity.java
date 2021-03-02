/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2019-2021 Threema GmbH
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

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.biometric.BiometricPrompt;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.managers.ServiceManager;
import ch.threema.app.services.LockAppService;
import ch.threema.app.services.PreferenceService;
import ch.threema.app.services.SystemScreenLockService;
import ch.threema.app.utils.BiometricUtil;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.NavigationUtil;
import ch.threema.app.utils.RuntimeUtil;

public class BiometricLockActivity extends ThreemaAppCompatActivity {
	private static final Logger logger = LoggerFactory.getLogger(BiometricLockActivity.class);

	private static final int REQUEST_CODE_SYSTEM_SCREENLOCK_CHECK = 551;
	public static final String INTENT_DATA_AUTHENTICATION_TYPE = "auth_type";

	private LockAppService lockAppService;
	private PreferenceService preferenceService;
	private SystemScreenLockService systemScreenLockService;
	private boolean isCheckOnly = false;
	private String authenticationType = null;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		logger.debug("onCreate");

		if (ConfigUtils.getAppTheme(this) == ConfigUtils.THEME_DARK) {
			setTheme(R.style.Theme_Threema_BiometricUnlock_Dark);
		}

		super.onCreate(savedInstanceState);

		ServiceManager serviceManager = ThreemaApplication.getServiceManager();
		if (serviceManager == null) {
			finish();
			return;
		}

		preferenceService = serviceManager.getPreferenceService();
		lockAppService = serviceManager.getLockAppService();
		systemScreenLockService = serviceManager.getScreenLockService();

		setContentView(R.layout.activity_biometric_lock);

		getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);

		isCheckOnly = getIntent().getBooleanExtra(ThreemaApplication.INTENT_DATA_CHECK_ONLY, false);
		if (getIntent().hasExtra(INTENT_DATA_AUTHENTICATION_TYPE)) {
			authenticationType = getIntent().getStringExtra(INTENT_DATA_AUTHENTICATION_TYPE);
		}

		if (authenticationType == null) {
			authenticationType = preferenceService.getLockMechanism();
		}

		if (!lockAppService.isLocked() && !isCheckOnly) {
			finish();
		}

		switch (authenticationType) {
			case PreferenceService.LockingMech_SYSTEM:
				showSystemScreenLock();
				break;
			case PreferenceService.LockingMech_BIOMETRIC:
				if (BiometricUtil.isBiometricsSupported(this)) {
					showBiometricPrompt();
				} else {
					// no enrolled fingerprints - try system screen lock
					showSystemScreenLock();
				}
				break;
			default:
				break;
		}
	}

	@Override
	public void finish() {
		logger.debug("finish");
		try {
			super.finish();
			overridePendingTransition(0, 0);
		} catch (Exception ignored) {}
	}

	private void showBiometricPrompt() {
		KeyguardManager keyguardManager = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);

		BiometricPrompt.PromptInfo.Builder promptInfoBuilder = new BiometricPrompt.PromptInfo.Builder()
			.setTitle(getString(R.string.prefs_title_access_protection))
			.setSubtitle(getString(R.string.biometric_enter_authentication))
			.setConfirmationRequired(false);

		if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P && keyguardManager != null && keyguardManager.isDeviceSecure()) {
			// allow fallback to device credentials such as PIN, passphrase or pattern
			promptInfoBuilder.setDeviceCredentialAllowed(true);
		} else {
			promptInfoBuilder.setNegativeButtonText(getString(R.string.cancel));
		}

		BiometricPrompt.PromptInfo promptInfo = promptInfoBuilder.build();
		BiometricPrompt biometricPrompt = new BiometricPrompt(this, new RuntimeUtil.MainThreadExecutor(), new BiometricPrompt.AuthenticationCallback() {
			@Override
			public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
				super.onAuthenticationError(errorCode, errString);
				if (errorCode != BiometricPrompt.ERROR_USER_CANCELED && errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
					Toast.makeText(BiometricLockActivity.this, errString + " (" + errorCode + ")", Toast.LENGTH_LONG).show();
				}
				BiometricLockActivity.this.onAuthenticationFailed();
			}

			@Override
			public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
				super.onAuthenticationSucceeded(result);
				BiometricLockActivity.this.onAuthenticationSuccess();
			}

			@Override
			public void onAuthenticationFailed() {
				super.onAuthenticationFailed();
				BiometricLockActivity.this.onAuthenticationFailed();
			}
		});
		biometricPrompt.authenticate(promptInfo);
	}

	private void showSystemScreenLock() {
		logger.debug("showSystemScreenLock");
		if (isCheckOnly) {
			if (systemScreenLockService.tryEncrypt(this, REQUEST_CODE_SYSTEM_SCREENLOCK_CHECK)) {
				onAuthenticationSuccess();
			}
		} else {
			if (systemScreenLockService.systemUnlock(this)) {
				onAuthenticationSuccess();
			}
		}
	}

	@Override
	public void onWindowFocusChanged(boolean hasFocus) {
		super.onWindowFocusChanged(hasFocus);
		if (hasFocus) {
			getWindow().getDecorView().setSystemUiVisibility(
				// Set the content to appear under the system bars so that the
				// content doesn't resize when the system bars hide and show.
				View.SYSTEM_UI_FLAG_LAYOUT_STABLE
				| View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
				| View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
				| View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
				| View.SYSTEM_UI_FLAG_FULLSCREEN
			);
		}
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
		logger.debug("onActivityResult requestCode: " + requestCode + " result: " + resultCode);

		super.onActivityResult(requestCode, resultCode, data);

		if (requestCode == ThreemaActivity.ACTIVITY_ID_CONFIRM_DEVICE_CREDENTIALS || requestCode == REQUEST_CODE_SYSTEM_SCREENLOCK_CHECK) {
			// Challenge completed, proceed with using cipher
			if (resultCode != Activity.RESULT_CANCELED) {
				onAuthenticationSuccess();
			} else {
				// The user canceled or didn’t complete the lock screen
				onAuthenticationFailed();
			}
		}
	}

	private void onAuthenticationSuccess() {
		logger.debug("Authentication successful");
		if (!isCheckOnly) {
			lockAppService.unlock(null);
		}
		this.setResult(RESULT_OK);
		this.finish();
	}

	private void onAuthenticationFailed() {
		logger.debug("Authentication failed");
		if (!isCheckOnly) {
			NavigationUtil.navigateToLauncher(this);
		}
		this.setResult(RESULT_CANCELED);
		this.finish();
	}
}

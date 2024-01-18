/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2016-2024 Threema GmbH
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

package ch.threema.app.services;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.widget.Toast;

import org.slf4j.Logger;

import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.ProviderException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;

import ch.threema.app.R;
import ch.threema.app.activities.ThreemaActivity;
import ch.threema.base.utils.LoggingUtil;

public class SystemScreenLockServiceImpl implements SystemScreenLockService {
	private static final Logger logger = LoggingUtil.getThreemaLogger("SystemScreenLockServiceImpl");

	/* Alias for our key in the Android Key Store. */
	private static final String ANDROID_KEY_STORE = "AndroidKeyStore";
	private static final String KEY_NAME = "threema_pinlock_key";
	private static final byte[] SECRET_BYTE_ARRAY = new byte[]{4, 5, 1, 4, 9, 6};
	private static final int AUTHENTICATION_DURATION_SECONDS = 3;
	private static long lastAuthenticationTimeStamp = 0;

	private KeyguardManager keyguardManager;

	private final LockAppService lockAppService;
	private final PreferenceService preferenceService;
	private final Context context;

	public SystemScreenLockServiceImpl(Context context, LockAppService lockAppService, PreferenceService preferenceService) {
		this.context = context;
		this.lockAppService = lockAppService;
		this.preferenceService = preferenceService;
		this.keyguardManager = (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
			if (keyguardManager != null && keyguardManager.isDeviceSecure()) {
				createKey();
			}
		}
	}

	@Override
	public boolean systemUnlock(Activity currentActivity) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
//			logger.debug("systemUnlock activity = " + currentActivity.getLocalClassName());

			if (!keyguardManager.isDeviceSecure()) {
				// User has disabled lockscreen in the meantime. Show a message that the user hasn't set up a lock screen.
				Toast.makeText(context, R.string.no_lockscreen_set, Toast.LENGTH_LONG).show();
				// allow access anyway
				if (lockAppService != null) {
					lockAppService.unlock(null);
					if (preferenceService != null) {
						// disable setting
						preferenceService.setLockMechanism(PreferenceService.LockingMech_NONE);
						preferenceService.setPrivateChatsHidden(false);
					}
				}
			}
			return tryEncrypt(currentActivity, ThreemaActivity.ACTIVITY_ID_CONFIRM_DEVICE_CREDENTIALS);
		}
		return true;
	}

	@Override
	public void setAuthenticated(boolean authenticated) {
		if (authenticated) {
			lastAuthenticationTimeStamp = System.currentTimeMillis();
		} else {
			lastAuthenticationTimeStamp = 0;
		}
	}

	/************************ Boilerplate code from google's api samples ***********************/

	/**
	 * Tries to encrypt some data with the generated key in {@link #createKey} which is
	 * only works if the user has just authenticated via device credentials.
	 */
	@Override
	public boolean tryEncrypt(Activity activity, int id) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
			if (lastAuthenticationTimeStamp < System.currentTimeMillis() - (AUTHENTICATION_DURATION_SECONDS * 1000)) {
				try {
					KeyStore keyStore = KeyStore.getInstance(ANDROID_KEY_STORE);
					keyStore.load(null);
					SecretKey secretKey = (SecretKey) keyStore.getKey(KEY_NAME, null);
					Cipher cipher = Cipher.getInstance(
						KeyProperties.KEY_ALGORITHM_AES + "/" + KeyProperties.BLOCK_MODE_CBC + "/"
							+ KeyProperties.ENCRYPTION_PADDING_PKCS7);

					// Try encrypting something, it will only work if the user authenticated within
					// the last AUTHENTICATION_DURATION_SECONDS seconds.
					cipher.init(Cipher.ENCRYPT_MODE, secretKey);
					cipher.doFinal(SECRET_BYTE_ARRAY);

					// If the user has recently authenticated, you will reach here.
					showAlreadyAuthenticated();
					return true;
				} catch (UnrecoverableKeyException e) {
					// java.security.UnrecoverableKeyException: Failed to obtain information about key on OnePlus phone
					Toast.makeText(activity, "Error in Android Key Store implementation. Please contact your phone manufacturer and try again later", Toast.LENGTH_LONG).show();
					logger.error("Exception", e);
				} catch (InvalidKeyException e) { // UserNotAuthenticatedException leads to runtime java.lang.VerifyError on Dell Venue 8
					// User is not authenticated, let's authenticate with device credentials.
					showAuthenticationScreen(activity, id);
				} catch (BadPaddingException e) {
					showAuthenticationScreen(activity, id);
				} catch (IllegalBlockSizeException | KeyStoreException |
						CertificateException | IOException
						| NoSuchPaddingException | NoSuchAlgorithmException e) {
					logger.error("Exception", e);
				}
			} else {
				showAlreadyAuthenticated();
				return true;
			}
		}
		return false;
	}

	/**
	 * Creates a symmetric key in the Android Key Store which can only be used after the user has
	 * authenticated with device credentials within the last X seconds.
	 */
	private boolean createKey() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
			// Generate a key to decrypt payment credentials, tokens, etc.
			// This will most likely be a registration step for the user when they are setting up your app.
			try {
				KeyStore keyStore = KeyStore.getInstance(ANDROID_KEY_STORE);
				keyStore.load(null);
				KeyGenerator keyGenerator = KeyGenerator.getInstance(
						KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE);

				// Set the alias of the entry in Android KeyStore where the key will appear
				// and the constrains (purposes) in the constructor of the Builder
				keyGenerator.init(new KeyGenParameterSpec.Builder(KEY_NAME,
						KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
						.setBlockModes(KeyProperties.BLOCK_MODE_CBC)
						.setUserAuthenticationRequired(true)
								// Require that the user has unlocked in the last 30 seconds
						.setUserAuthenticationValidityDurationSeconds(AUTHENTICATION_DURATION_SECONDS)
						.setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7)
						.build());
				keyGenerator.generateKey();
			} catch (NoSuchAlgorithmException | NoSuchProviderException
					| InvalidAlgorithmParameterException | KeyStoreException
					| CertificateException | IOException | ProviderException e) {
				logger.error("Exception", e);
				return false;
			}
		}
		return true;
	}

	private void showAuthenticationScreen(Activity activity, int id) {
		logger.debug("showAuthenticationScreen");

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
			// Create the Confirm Credentials screen. You can customize the title and description. Or
			// we will provide a generic one for you if you leave it null
			Intent intent = keyguardManager.createConfirmDeviceCredentialIntent(null, null);
			if (intent != null) {
				activity.startActivityForResult(intent, id);
				activity.overridePendingTransition(0,0);
			}
		}
	}

	private void showAlreadyAuthenticated() {
		logger.debug("AlreadyAuthenticated");

		if (lockAppService != null) {
			lockAppService.unlock(null);
		}
	}
}

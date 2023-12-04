/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2018-2023 Threema GmbH
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

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.hardware.display.DisplayManager;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.view.Display;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.lambdaworks.crypto.SCrypt;
import com.neilalexander.jnacl.NaCl;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Locale;
import java.util.Map;
import java.util.zip.Deflater;
import java.util.zip.GZIPInputStream;

import javax.net.ssl.HttpsURLConnection;

import ch.threema.app.BuildConfig;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.exceptions.EntryAlreadyExistsException;
import ch.threema.app.exceptions.InvalidEntryException;
import ch.threema.app.exceptions.PolicyViolationException;
import ch.threema.app.services.ContactService;
import ch.threema.app.services.DeadlineListService;
import ch.threema.app.services.DistributionListService;
import ch.threema.app.services.FileService;
import ch.threema.app.services.GroupService;
import ch.threema.app.services.IdListService;
import ch.threema.app.services.LocaleService;
import ch.threema.app.services.PreferenceService;
import ch.threema.app.services.UserService;
import ch.threema.app.stores.IdentityStore;
import ch.threema.app.utils.AppRestrictionUtil;
import ch.threema.app.utils.BitmapUtil;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.GzipOutputStream;
import ch.threema.app.utils.RuntimeUtil;
import ch.threema.app.utils.StringConversionUtil;
import ch.threema.app.utils.TestUtil;
import ch.threema.app.utils.WorkManagerUtil;
import ch.threema.app.workers.ThreemaSafeUploadWorker;
import ch.threema.base.ThreemaException;
import ch.threema.base.utils.Base64;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.base.utils.Utils;
import ch.threema.domain.models.GroupId;
import ch.threema.domain.models.IdentityState;
import ch.threema.domain.models.VerificationLevel;
import ch.threema.domain.protocol.ProtocolStrings;
import ch.threema.domain.protocol.api.APIConnector;
import ch.threema.domain.protocol.csp.ProtocolDefines;
import ch.threema.storage.DatabaseServiceNew;
import ch.threema.storage.factories.ContactModelFactory;
import ch.threema.storage.factories.DistributionListMemberModelFactory;
import ch.threema.storage.factories.GroupMemberModelFactory;
import ch.threema.storage.factories.GroupModelFactory;
import ch.threema.storage.models.ContactModel;
import ch.threema.storage.models.DistributionListMemberModel;
import ch.threema.storage.models.DistributionListModel;
import ch.threema.storage.models.GroupMemberModel;
import ch.threema.storage.models.GroupModel;

import static android.view.Display.DEFAULT_DISPLAY;
import static ch.threema.app.ThreemaApplication.WORKER_PERIODIC_THREEMA_SAFE_UPLOAD;
import static ch.threema.app.ThreemaApplication.WORKER_THREEMA_SAFE_UPLOAD;
import static ch.threema.app.services.PreferenceService.PROFILEPIC_RELEASE_EVERYONE;
import static ch.threema.app.services.PreferenceService.PROFILEPIC_RELEASE_NOBODY;
import static ch.threema.app.services.PreferenceService.PROFILEPIC_RELEASE_SOME;
import static ch.threema.app.threemasafe.ThreemaSafeConfigureActivity.EXTRA_OPEN_HOME_ACTIVITY;
import static ch.threema.app.threemasafe.ThreemaSafeConfigureActivity.EXTRA_WORK_FORCE_PASSWORD;
import static ch.threema.app.threemasafe.ThreemaSafeServerTestResponse.CONFIG_MAX_BACKUP_BYTES;
import static ch.threema.app.threemasafe.ThreemaSafeServerTestResponse.CONFIG_RETENTION_DAYS;

public class ThreemaSafeServiceImpl implements ThreemaSafeService {
	private static final Logger logger = LoggingUtil.getThreemaLogger("ThreemaSafeServiceImpl");

	// Scrypt parameters as recommended by OWASP:
	// https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html#scrypt
	//
	// These values correspond to a minimum memory requirement
	// of 64 MiB (128 * 65536 * 8 * 1 bytes).
	//
	// Note: Values must be chosen so that the scrypt operation can still be
	// calculated on an old phone (with little RAM) within a few seconds.
	// For example, a Nexus S (512 MiB RAM) takes around 4 seconds.
	private static final int SCRYPT_N = 65536;
	private static final int SCRYPT_R = 8;
	private static final int SCRYPT_P = 1;

	private static final int MASTERKEY_LENGTH = 64;
	private static final int PROFILEPIC_MAX_WIDTH = 400;
	private static final int PROFILEPIC_QUALITY = 60;

	private static final String PROFILE_PIC_RELEASE_ALL_PLACEHOLDER = "*";

	private static final int ENCRYPTION_KEY_LENGTH = NaCl.SYMMKEYBYTES;
	private static final int PROTOCOL_VERSION = 1;

	public static final int MIN_PW_LENGTH = 8;
	public static final int MAX_PW_LENGTH = 4096;

	/* Threema Safe tags */
	private static final String TAG_SAFE_INFO = "info";
	private static final String TAG_SAFE_INFO_VERSION = "version";
	private static final String TAG_SAFE_INFO_DEVICE = "device";

	private static final String TAG_SAFE_USER = "user";
	private static final String TAG_SAFE_USER_PRIVATE_KEY = "privatekey";
	private static final String TAG_SAFE_USER_NICKNAME = "nickname";
	private static final String TAG_SAFE_USER_PROFILE_PIC = "profilePic";
	private static final String TAG_SAFE_USER_PROFILE_PIC_RELEASE = "profilePicRelease";
	private static final String TAG_SAFE_USER_LINKS = "links";
	private static final String TAG_SAFE_USER_LINK_TYPE = "type";
	private static final String TAG_SAFE_USER_LINK_VALUE = "value";
	private static final String TAG_SAFE_USER_LINK_TYPE_MOBILE = "mobile";
	private static final String TAG_SAFE_USER_LINK_TYPE_EMAIL = "email";

	private static final String TAG_SAFE_CONTACTS = "contacts";
	private static final String TAG_SAFE_CONTACT_IDENTITY = "identity";
	private static final String TAG_SAFE_CONTACT_PUBLIC_KEY = "publickey";
	private static final String TAG_SAFE_CONTACT_CREATED_AT = "createdAt";
	private static final String TAG_SAFE_CONTACT_VERIFICATION_LEVEL = "verification";
	private static final String TAG_SAFE_CONTACT_WORK_VERIFIED = "workVerified";
	private static final String TAG_SAFE_CONTACT_FIRST_NAME = "firstname";
	private static final String TAG_SAFE_CONTACT_LAST_NAME = "lastname";
	private static final String TAG_SAFE_CONTACT_NICKNAME = "nickname";
	private static final String TAG_SAFE_CONTACT_HIDDEN = "hidden";
	private static final String TAG_SAFE_CONTACT_PRIVATE = "private";
	private static final String TAG_SAFE_CONTACT_READ_RECEIPTS = "readReceipts";
	private static final String TAG_SAFE_CONTACT_TYPING_INDICATORS = "typingIndicators";

	private static final String TAG_SAFE_GROUPS = "groups";
	private static final String TAG_SAFE_GROUP_ID = "id";
	private static final String TAG_SAFE_GROUP_CREATOR = "creator";
	private static final String TAG_SAFE_GROUP_NAME = "groupname";
	private static final String TAG_SAFE_GROUP_CREATED_AT = "createdAt";
	private static final String TAG_SAFE_GROUP_MEMBERS = "members";
	private static final String TAG_SAFE_GROUP_DELETED = "deleted";
	private static final String TAG_SAFE_GROUP_PRIVATE = "private";

	private static final String TAG_SAFE_DISTRIBUTIONLISTS = "distributionlists";
	private static final String TAG_SAFE_DISTRIBUTIONLIST_ID = "id";
	private static final String TAG_SAFE_DISTRIBUTIONLIST_NAME = "name";
	private static final String TAG_SAFE_DISTRIBUTIONLIST_CREATED_AT = "createdAt";
	private static final String TAG_SAFE_DISTRIBUTIONLIST_MEMBERS = "members";
	private static final String TAG_SAFE_DISTRIBUTIONLIST_PRIVATE = "private";

	private static final String TAG_SAFE_SETTINGS = "settings";
	private static final String TAG_SAFE_SETTINGS_SYNC_CONTACTS = "syncContacts";
	private static final String TAG_SAFE_SETTINGS_BLOCK_UNKNOWN = "blockUnknown";
	private static final String TAG_SAFE_SETTINGS_READ_RECEIPTS = "readReceipts";
	private static final String TAG_SAFE_SETTINGS_SEND_TYPING = "sendTyping";
	private static final String TAG_SAFE_SETTINGS_BLOCKED_CONTACTS = "blockedContacts";
	private static final String TAG_SAFE_SETTINGS_THREEMA_CALLS = "threemaCalls";
	private static final String TAG_SAFE_SETTINGS_RELAY_THREEMA_CALLS = "relayThreemaCalls";
	private static final String TAG_SAFE_SETTINGS_DISABLE_SCREENSHOTS = "disableScreenshots";
	private static final String TAG_SAFE_SETTINGS_INCOGNITO_KEAYBOARD = "incognitoKeyboard";
	private static final String TAG_SAFE_SETTINGS_SYNC_EXCLUDED_CONTACTS = "syncExcludedIds";
	private static final String TAG_SAFE_SETTINGS_RECENT_EMOJIS = "recentEmojis";
	private static final String KEY_USER_AGENT = "User-Agent";

	private final Context context;
	private final PreferenceService preferenceService;
	private final UserService userService;
	private final IdentityStore identityStore;
	private final APIConnector apiConnector;
	private final LocaleService localeService;
	private final ContactService contactService;
	private final FileService fileService;
	private final IdListService profilePicRecipientsService;
	private final DatabaseServiceNew databaseServiceNew;
	private final DeadlineListService hiddenChatsListService;

	public ThreemaSafeServiceImpl(Context context, PreferenceService preferenceService, UserService userService, ContactService contactService, LocaleService localeService, FileService fileService, IdListService profilePicRecipientsService, DatabaseServiceNew databaseServiceNew, IdentityStore identityStore, APIConnector apiConnector, DeadlineListService hiddehChatsListService) {
		this.context = context;
		this.preferenceService = preferenceService;
		this.userService = userService;
		this.contactService = contactService;
		this.identityStore = identityStore;
		this.apiConnector = apiConnector;
		this.localeService = localeService;
		this.databaseServiceNew = databaseServiceNew;
		this.fileService = fileService;
		this.profilePicRecipientsService = profilePicRecipientsService;
		this.hiddenChatsListService = hiddehChatsListService;
	}

	@Override
	@Nullable
	public byte[] deriveMasterKey(String password, String identity) {
		if (!TextUtils.isEmpty(password) && !TextUtils.isEmpty(identity)) {
			try {
				final byte[] passwordBytes = password.getBytes(StandardCharsets.UTF_8);
				final byte[] identityBytes = identity.getBytes(StandardCharsets.UTF_8);
				return SCrypt.scrypt(passwordBytes, identityBytes, SCRYPT_N, SCRYPT_R, SCRYPT_P, MASTERKEY_LENGTH);
			} catch (GeneralSecurityException e) {
				logger.error("Exception", e);
			}
		}
		return null;
	}

	@Override
	public boolean storeMasterKey(byte[] masterKey) {
		if (masterKey != null) {
			preferenceService.setThreemaSafeMasterKey(masterKey);
		}
		return false;
	}

	@Override
	@Nullable
	public byte[] getThreemaSafeBackupId() {
		byte[] masterKey = preferenceService.getThreemaSafeMasterKey();

		if (masterKey != null && masterKey.length == MASTERKEY_LENGTH) {
			return Arrays.copyOfRange(masterKey, 0, BACKUP_ID_LENGTH);
		}
		return null;
	}

	@Override
	@Nullable
	public byte[] getThreemaSafeEncryptionKey() {
		byte[] masterKey = preferenceService.getThreemaSafeMasterKey();

		if (masterKey != null && masterKey.length == MASTERKEY_LENGTH) {
			return Arrays.copyOfRange(masterKey, BACKUP_ID_LENGTH, BACKUP_ID_LENGTH + ENCRYPTION_KEY_LENGTH);
		}
		return null;
	}

	@Override
	public byte[] getThreemaSafeMasterKey() {
		return preferenceService.getThreemaSafeMasterKey();
	}

	@Override
	public ThreemaSafeServerTestResponse testServer(ThreemaSafeServerInfo serverInfo) throws ThreemaException {
		URL configUrl = serverInfo.getConfigUrl(getThreemaSafeBackupId());

		HttpsURLConnection urlConnection;
		try {
			urlConnection = (HttpsURLConnection) configUrl.openConnection();
		} catch (IOException e) {
			logger.error("Exception", e);
			throw new ThreemaException("Unable to connect to server");
		}

		try {
			urlConnection.setSSLSocketFactory(ConfigUtils.getSSLSocketFactory(configUrl.getHost()));
			urlConnection.setConnectTimeout(15000);
			urlConnection.setReadTimeout(30000);
			urlConnection.setRequestMethod("GET");
			urlConnection.setRequestProperty("Accept", "application/json");
			urlConnection.setRequestProperty(KEY_USER_AGENT, ProtocolStrings.USER_AGENT);
			serverInfo.addAuthorization(urlConnection);
			urlConnection.setDoOutput(false);

			byte[] buf;
			try (BufferedInputStream bis = new BufferedInputStream(urlConnection.getInputStream())) {
				int bufLength = 4096;

				buf = new byte[bufLength];
				int bytesRead = bis.read(buf,0, bufLength);

				if (bytesRead <= 0) {
					throw new ThreemaException("Config file empty or not readable");
				}
			}

			final int responseCode = urlConnection.getResponseCode();
			if (responseCode != 200) {
				throw new ThreemaException("Server error: " + responseCode);
			}

			String configJson = new String(buf, StandardCharsets.UTF_8);
			ThreemaSafeServerTestResponse response = new ThreemaSafeServerTestResponse();

			JSONObject jsonObject = new JSONObject(configJson);
			response.maxBackupBytes = jsonObject.getLong(CONFIG_MAX_BACKUP_BYTES);
			response.retentionDays = jsonObject.getInt(CONFIG_RETENTION_DAYS);

			preferenceService.setThreemaSafeServerMaxUploadSize(response.maxBackupBytes);
			preferenceService.setThreemaSafeServerRetention(response.retentionDays);

			return response;
		} catch (IOException e) {
			try {
				int responseCode = urlConnection.getResponseCode();
				String responseMessage = urlConnection.getResponseMessage();
				if (e instanceof FileNotFoundException && responseCode == 404) {
					throw new ThreemaException("Config file not found");
				} else {
					throw new ThreemaException(responseCode + ": " + responseMessage);
				}
			} catch (IOException e1) {
				logger.error("I/O Exception", e1);
			}
			throw new ThreemaException("IO Exception: " + e.getMessage());
		} catch (JSONException e) {
			throw new ThreemaException("Malformed server response");
		} catch (IllegalArgumentException e) {
			throw new ThreemaException(e.getMessage());
		} finally {
			urlConnection.disconnect();
		}
	}

	@Override
	public boolean schedulePeriodicUpload() {
		logger.info("Scheduling Threema Safe upload");
		WorkManager workManager = WorkManager.getInstance(ThreemaApplication.getAppContext());
		boolean reschedule = WorkManagerUtil.shouldScheduleNewWorkManagerInstance(workManager, WORKER_PERIODIC_THREEMA_SAFE_UPLOAD, SCHEDULE_PERIOD);
		return schedulePeriodicWork(reschedule);
	}

	@Override
	public boolean reschedulePeriodicUpload() {
		logger.info("Rescheduling Threema Safe upload");
		return schedulePeriodicWork(true);
	}

	@Override
	public void unschedulePeriodicUpload() {
		logger.info("Unscheduling Threema Safe upload");

		WorkManager workManager = WorkManager.getInstance(ThreemaApplication.getAppContext());
		workManager.cancelUniqueWork(WORKER_PERIODIC_THREEMA_SAFE_UPLOAD);
	}

	@Override
	public void setEnabled(boolean enabled) {
		if (ConfigUtils.isWorkRestricted() && ThreemaSafeMDMConfig.getInstance().isBackupDisabled()) {
			enabled = false;
		}

		preferenceService.setThreemaSafeEnabled(enabled);
		if (enabled) {
			schedulePeriodicUpload();
		} else {
			// disable Safe
			unschedulePeriodicUpload();
			preferenceService.setThreemaSafeEnabled(false);
			preferenceService.setThreemaSafeMasterKey(new byte[0]);
			preferenceService.setThreemaSafeServerInfo(null);
			preferenceService.setThreemaSafeUploadDate(new Date(0));
			preferenceService.setThreemaSafeBackupDate(new Date(0));
			preferenceService.setThreemaSafeHashString("");
			preferenceService.setThreemaSafeErrorCode(ERROR_CODE_OK);
		}
	}

	@Override
	public void uploadNow(boolean force) {
		try {
			WorkManager workManager = WorkManager.getInstance(ThreemaApplication.getAppContext());

			// If the periodic safe upload is scheduled, we can reschedule it so that it does not
			// get executed within the next schedule period.
			if (WorkManagerUtil.isWorkManagerInstanceScheduled(workManager, WORKER_PERIODIC_THREEMA_SAFE_UPLOAD)) {
				reschedulePeriodicUpload();
			}

			// Upload the threema safe once
			OneTimeWorkRequest workRequest = ThreemaSafeUploadWorker.Companion.buildOneTimeWorkRequest(force);
			workManager.enqueueUniqueWork(WORKER_THREEMA_SAFE_UPLOAD, ExistingWorkPolicy.REPLACE, workRequest);
		} catch (IllegalStateException e) {
			logger.error("Unable to schedule safe upload one time work", e);
		}
	}

	@Override
	public void createBackup(boolean force) throws ThreemaSafeUploadException {
		logger.info("Starting Threema Safe backup");

		if (!preferenceService.getThreemaSafeEnabled()) {
			throw new ThreemaSafeUploadException("Disabled", false);
		}

		ThreemaSafeServerInfo serverInfo = preferenceService.getThreemaSafeServerInfo();
		if (serverInfo == null) {
			throw new ThreemaSafeUploadException("No server info", true);
		}

		// test server to update configuration
		final ThreemaSafeServerTestResponse serverTestResponse;
		try {
			serverTestResponse = testServer(serverInfo);
		} catch (ThreemaException e) {
			preferenceService.setThreemaSafeErrorCode(ERROR_CODE_SERVER_FAIL);
			throw new ThreemaSafeUploadException("Server test failed. " + e.getMessage(), true);
		}

		String json = getJson();
		if (json == null) {
			preferenceService.setThreemaSafeErrorCode(ERROR_CODE_JSON_FAIL);
			throw new ThreemaSafeUploadException("Json failed", true);
		}

		// get a hash of the json to determine if there are any changes
		String hashString;
		try {
			MessageDigest messageDigest = MessageDigest.getInstance("SHA-1");
			messageDigest.update(json.getBytes(StandardCharsets.UTF_8));
			hashString = StringConversionUtil.byteArrayToString(messageDigest.digest());
		} catch (NoSuchAlgorithmException e) {
			preferenceService.setThreemaSafeErrorCode(ERROR_CODE_HASH_FAIL);
			throw new ThreemaSafeUploadException("Hash calculation failed", true);
		}

		if (!force) {
			if (hashString.equals(preferenceService.getThreemaSafeHashString())) {
				long halfRetentionTimeMillis = (serverTestResponse.retentionDays == 0 ? 180 : serverTestResponse.retentionDays) * DateUtils.DAY_IN_MILLIS / 2;
				Date reUploadThreshold = new Date(System.currentTimeMillis() - halfRetentionTimeMillis);
				if (preferenceService.getThreemaSafeErrorCode() == ERROR_CODE_OK &&
					reUploadThreshold.before(preferenceService.getThreemaSafeUploadDate())) {
					logger.info("Threema Safe contents unchanged. NOT uploaded");
					return;
				}
			} else {
				Date reUploadThreshold = new Date(System.currentTimeMillis() - (DateUtils.HOUR_IN_MILLIS * 23));
				if (preferenceService.getThreemaSafeErrorCode() == ERROR_CODE_OK &&
					preferenceService.getThreemaSafeUploadDate() != null &&
					reUploadThreshold.before(preferenceService.getThreemaSafeUploadDate())) {
					throw new ThreemaSafeUploadException("Grace time not yet reached. NOT uploaded", false);
				}
			}
		}

		byte[] gzippedPlaintext = gZipCompress(json.getBytes());
		if (gzippedPlaintext == null || gzippedPlaintext.length <= 0) {
			preferenceService.setThreemaSafeErrorCode(ERROR_CODE_GZIP_FAIL);
			throw new ThreemaSafeUploadException("Compression failed", true);
		}

		if (getThreemaSafeEncryptionKey() == null) {
			throw new ThreemaSafeUploadException("No key", true);
		}

		SecureRandom random = new SecureRandom();

		byte[] nonce = new byte[NaCl.NONCEBYTES];
		random.nextBytes(nonce);

		try {
			byte[] encdata = NaCl.symmetricEncryptData(gzippedPlaintext, getThreemaSafeEncryptionKey(), nonce);

			byte[] threemaSafeEncryptedBackup = new byte[nonce.length + encdata.length];
			System.arraycopy(nonce, 0, threemaSafeEncryptedBackup, 0, nonce.length);
			System.arraycopy(encdata, 0, threemaSafeEncryptedBackup, nonce.length, encdata.length);

			if (threemaSafeEncryptedBackup.length <= serverTestResponse.maxBackupBytes) {
				uploadData(serverInfo, threemaSafeEncryptedBackup);
				preferenceService.setThreemaSafeBackupSize(threemaSafeEncryptedBackup.length);
				preferenceService.setThreemaSafeUploadDate(new Date());
				preferenceService.setThreemaSafeBackupDate(new Date());
				preferenceService.setThreemaSafeHashString(hashString);
				preferenceService.setThreemaSafeErrorCode(ERROR_CODE_OK);
			} else {
				preferenceService.setThreemaSafeBackupSize(threemaSafeEncryptedBackup.length);
				throw new UploadSizeExceedException("Upload size exceeded");
			}
		} catch (UploadSizeExceedException e) {
			logger.error("Exception", e);
			preferenceService.setThreemaSafeErrorCode(ERROR_CODE_SIZE_EXCEEDED);
			throw new ThreemaSafeUploadException(e.getMessage(), true);
		} catch (Exception e) {
			logger.error("Exception", e);
			preferenceService.setThreemaSafeErrorCode(ERROR_CODE_UPLOAD_FAIL);
			throw new ThreemaSafeUploadException("Upload failed", true);
		}

		if (force) {
			RuntimeUtil.runOnUiThread(() -> {
				Context windowContext = context;
				if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
					final DisplayManager dm;
					dm = context.getSystemService(DisplayManager.class);
					final Display primaryDisplay = dm.getDisplay(DEFAULT_DISPLAY);
					try {
						windowContext = context.createDisplayContext(primaryDisplay)
							.createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null);
					} catch (SecurityException e) {
						logger.error("Unable to create WindowContext for Toast", e);
					}
				}
				Toast.makeText(windowContext, R.string.threema_safe_upload_successful, Toast.LENGTH_LONG).show();
			});
		}

		logger.info("Threema Safe backup successfully created and uploaded");
	}

	@Override
	public void deleteBackup() throws ThreemaException {
		ThreemaSafeServerInfo serverInfo = preferenceService.getThreemaSafeServerInfo();
		if (serverInfo == null) {
			throw new ThreemaException("No server info");
		}

		URL serverUrl = serverInfo.getBackupUrl(getThreemaSafeBackupId());

		HttpsURLConnection urlConnection;
		try {
			urlConnection = (HttpsURLConnection) serverUrl.openConnection();
		} catch (IOException e) {
			logger.error("Exception", e);
			throw new ThreemaException("Unable to connect to server");
		}

		try {
			urlConnection.setSSLSocketFactory(ConfigUtils.getSSLSocketFactory(serverUrl.getHost()));
			urlConnection.setConnectTimeout(15000);
			urlConnection.setReadTimeout(30000);
			urlConnection.setRequestMethod("DELETE");
			urlConnection.setRequestProperty(KEY_USER_AGENT, ProtocolStrings.USER_AGENT);
			serverInfo.addAuthorization(urlConnection);
			urlConnection.setDoOutput(false);

			final int responseCode = urlConnection.getResponseCode();
			if (responseCode != 200 && responseCode != 201 && responseCode != 204) {
				throw new ThreemaException("Unable to delete backup. Response code: " + responseCode);
			}
		} catch (IOException e) {
			throw new ThreemaException("IO Exception");
		} catch (IllegalArgumentException e) {
			throw new ThreemaException(e.getMessage());
		} finally {
			urlConnection.disconnect();
		}
	}

	@Override
	public void restoreBackup(String identity, String password, ThreemaSafeServerInfo serverInfo) throws ThreemaException, IOException {
		if (TestUtil.empty(password) || serverInfo == null || TestUtil.empty(identity) || identity.length() != ProtocolDefines.IDENTITY_LEN) {
			throw new ThreemaException("Illegal arguments");
		}

		byte[] masterKey = deriveMasterKey(password, identity);
		if (masterKey == null) {
			throw new ThreemaException("Unable to derive master key");
		}

		preferenceService.setThreemaSafeMasterKey(masterKey);
		preferenceService.setThreemaSafeServerInfo(serverInfo);

		URL serverUrl = serverInfo.getBackupUrl(getThreemaSafeBackupId());

		HttpsURLConnection urlConnection;
		try {
			urlConnection = (HttpsURLConnection) serverUrl.openConnection();
		} catch (IOException e) {
			throw new ThreemaException("Unable to connect to server");
		}

		byte[] threemaSafeEncryptedBackup;

		try {
			urlConnection.setSSLSocketFactory(ConfigUtils.getSSLSocketFactory(serverUrl.getHost()));
			urlConnection.setConnectTimeout(15000);
			urlConnection.setReadTimeout(30000);
			urlConnection.setRequestMethod("GET");
			urlConnection.setRequestProperty("Accept", "application/octet-stream");
			urlConnection.setRequestProperty(KEY_USER_AGENT, ProtocolStrings.USER_AGENT);
			serverInfo.addAuthorization(urlConnection);
			urlConnection.setDoOutput(false);

			try (ByteArrayOutputStream baos = new ByteArrayOutputStream(); BufferedInputStream bis = new BufferedInputStream(urlConnection.getInputStream())) {
				byte[] buf = new byte[16384];
				int nread;
				while ((nread = bis.read(buf)) > 0) {
					baos.write(buf, 0, nread);
				}

				threemaSafeEncryptedBackup = baos.toByteArray();

				final int responseCode = urlConnection.getResponseCode();
				if (responseCode != 200) {
					throw new ThreemaException("Server error: " + responseCode);
				}
			}
		} catch (IllegalArgumentException e) {
			throw new ThreemaException(e.getMessage());
		} finally {
			urlConnection.disconnect();
		}

		byte[] nonce = new byte[NaCl.NONCEBYTES];
		byte[] gzippedData = new byte[threemaSafeEncryptedBackup.length - NaCl.NONCEBYTES];
		System.arraycopy(threemaSafeEncryptedBackup, 0, nonce, 0, NaCl.NONCEBYTES);
		System.arraycopy(threemaSafeEncryptedBackup, NaCl.NONCEBYTES, gzippedData, 0, threemaSafeEncryptedBackup.length - NaCl.NONCEBYTES);

		if (!NaCl.symmetricDecryptDataInplace(gzippedData, getThreemaSafeEncryptionKey(), nonce)) {
			throw new ThreemaException("Unable to decrypt");
		}

		byte[] uncompressedData = gZipUncompress(gzippedData);
		if (uncompressedData == null) {
			throw new ThreemaException("Uncompress failed");
		}

		String json = new String(uncompressedData, StandardCharsets.UTF_8);

		restoreJson(identity, json);

		// successfully restored - update mdm settings config
		ThreemaSafeMDMConfig.getInstance().saveConfig(preferenceService);
	}

	private boolean schedulePeriodicWork(boolean reschedule) {
		if (preferenceService.getThreemaSafeEnabled()) {
			ExistingPeriodicWorkPolicy policy = reschedule ? ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE : ExistingPeriodicWorkPolicy.KEEP;

			try {
				// Schedule the start of the worker every 24 hours
				WorkManager workManager = WorkManager.getInstance(ThreemaApplication.getAppContext());
				PeriodicWorkRequest workRequest = ThreemaSafeUploadWorker.Companion.buildPeriodicWorkRequest(SCHEDULE_PERIOD);
				workManager.enqueueUniquePeriodicWork(
					WORKER_PERIODIC_THREEMA_SAFE_UPLOAD,
					policy,
					workRequest
				);

				return true;
			} catch (IllegalStateException e) {
				logger.error("Unable to schedule periodic safe upload", e);
			}
		} else {
			logger.info("Threema Safe disabled");
		}
		return false;
	}

	private void restoreJson(String identity, String json) throws ThreemaException {
		JSONObject jsonObject;

		try {
			jsonObject = new JSONObject(json);
			parseInfo(jsonObject.getJSONObject(TAG_SAFE_INFO));
		} catch (JSONException e) {
			throw new ThreemaException("Missing Info object or version mismatch");
		}

		try {
			restoreUser(identity, jsonObject.getJSONObject(TAG_SAFE_USER));
		} catch (IOException | JSONException e) {
			throw new ThreemaException("Unable to restore user");
		}

		try {
			restoreSettings(jsonObject.getJSONObject(TAG_SAFE_SETTINGS));
		} catch (JSONException e) {
			// no settings - ignore and continue
		}

		try {
			restoreContacts(jsonObject.getJSONArray(TAG_SAFE_CONTACTS));
		} catch (JSONException e) {
			// no contacts - stop here as groups and distributions lists are of no use without contacts
			return;
		}

		try {
			restoreGroups(jsonObject.getJSONArray(TAG_SAFE_GROUPS));
		} catch (JSONException e) {
			// no groups - ignore and continue
		}

		try {
			restoreDistributionlists(jsonObject.getJSONArray(TAG_SAFE_DISTRIBUTIONLISTS));
		} catch (JSONException e) {
			// no distribution lists - ignore and continue
		}

		// TODO(ANDR-2296): Restore group invites
	}

	private void restoreUser(String identity, JSONObject user) throws ThreemaException, IOException, JSONException {
		byte[] privateKey, publicKey;

		String encodedPrivateKey = user.getString(TAG_SAFE_USER_PRIVATE_KEY);
		if (TestUtil.empty(encodedPrivateKey)) {
			throw new ThreemaException("Invalid JSON");
		}
		privateKey = Base64.decode(encodedPrivateKey);
		publicKey = NaCl.derivePublicKey(privateKey);

		try {
			userService.restoreIdentity(identity, privateKey, publicKey);
		} catch (Exception e) {
			throw new ThreemaException(context.getString(R.string.unable_to_restore_identity_because, e.getMessage()));
		}

		String nickname = user.optString(TAG_SAFE_USER_NICKNAME, identity);

		ContactModel contactModel = contactService.getByIdentity(userService.getIdentity());
		if (contactModel != null) {
			userService.setPublicNickname(nickname);


			boolean isLinksRestricted = false;
			if (ConfigUtils.isWorkRestricted()) {
				// if links have been set do not restore links if readonly profile is set to true and the user is unable to change or remove links
				String stringPreset;
				stringPreset = AppRestrictionUtil.getStringRestriction(context.getString(R.string.restriction__linked_email));
				if (stringPreset != null) {
					isLinksRestricted = true;
					addUserLink(TAG_SAFE_USER_LINK_TYPE_EMAIL, stringPreset);
				}
				stringPreset = AppRestrictionUtil.getStringRestriction(context.getString(R.string.restriction__linked_phone));
				if (stringPreset != null) {
					isLinksRestricted = true;
					addUserLink(TAG_SAFE_USER_LINK_TYPE_MOBILE, stringPreset);
				}
				// do not restore links if readonly profile is set to true and the user is unable to change or remove links later
				Boolean booleanRestriction = AppRestrictionUtil.getBooleanRestriction(context.getString(R.string.restriction__readonly_profile));
				if (booleanRestriction != null && booleanRestriction) {
					isLinksRestricted = true;
				}
			}

			if (!isLinksRestricted) {
				restoreLinks(user.optJSONArray(TAG_SAFE_USER_LINKS));
			}

			String profilePic = user.optString(TAG_SAFE_USER_PROFILE_PIC, null);
			if (profilePic != null) {
				try {
					contactService.setAvatar(contactModel, Base64.decode(profilePic));
				} catch (Exception e) {
					// base 64 decoding or avatar setting failed - forget about the pic
				}
			}

			JSONArray profilePicRelease = user.optJSONArray(TAG_SAFE_USER_PROFILE_PIC_RELEASE);
			if (profilePicRelease != null) {
				preferenceService.setProfilePicRelease(PROFILEPIC_RELEASE_SOME);

				for (int i = 0; i < profilePicRelease.length(); i++) {
					String id = profilePicRelease.getString(i);
					if (id == null) {
						preferenceService.setProfilePicRelease(PROFILEPIC_RELEASE_NOBODY);
						break;
					}
					if (PROFILE_PIC_RELEASE_ALL_PLACEHOLDER.equals(id)) {
						preferenceService.setProfilePicRelease(PROFILEPIC_RELEASE_EVERYONE);
						break;
					}
					if (id.length() == ProtocolDefines.IDENTITY_LEN) {
						profilePicRecipientsService.add(id);
					}
				}
			}
		}
	}

	private void addUserLink(String type, String value) {
		if (TestUtil.empty(type, value)) return;

		switch (type) {
			case TAG_SAFE_USER_LINK_TYPE_EMAIL:
				try {
					userService.linkWithEmail(value);
				} catch (Exception e) {
					// ignore "already linked" exceptions
				}
				break;
			case TAG_SAFE_USER_LINK_TYPE_MOBILE:
				try {
					// should always be a fully qualified phone number starting with a "+"
					userService.linkWithMobileNumber(value.startsWith("+") ? value : "+" + value);
				} catch (Exception e) {
					// ignore "already linked" exceptions
				}
				break;
			default:
				break;
		}
	}

	private void restoreLink(JSONObject link) {
		String type = link.optString(TAG_SAFE_USER_LINK_TYPE);
		String value = link.optString(TAG_SAFE_USER_LINK_VALUE);

		addUserLink(type, value);
	}

	private void restoreLinks(JSONArray links) {
		if (links == null) return;

		for (int i = 0; i < links.length(); i++) {
			JSONObject link = links.optJSONObject(i);
			if (link != null) {
				restoreLink(link);
			}
		}
	}

	private void restoreContacts(JSONArray contacts) {
		if (contacts == null) return;
		if (databaseServiceNew == null) return;

		ContactModelFactory contactModelFactory = databaseServiceNew.getContactModelFactory();

		ArrayList<String> identities = new ArrayList<>();
		for (int i = 0; i < contacts.length(); i++) {
			try {
				identities.add(contacts.getJSONObject(i).getString(TAG_SAFE_CONTACT_IDENTITY));
			}
			catch (JSONException e) {
				// ignore & continue with next contact
			}
		}

		if (identities.size() == 0) {
			return;
		}

		ArrayList<APIConnector.FetchIdentityResult> results;
		try {
			results = this.apiConnector.fetchIdentities(identities);
		}
		catch (Exception e) {
			return;
		}

		for (int i = 0; i < contacts.length(); i++) {
			try {
				JSONObject contact = contacts.getJSONObject(i);
				String identity = contact.getString(TAG_SAFE_CONTACT_IDENTITY);
				String publicKey = contact.optString(TAG_SAFE_CONTACT_PUBLIC_KEY);
				VerificationLevel verificationLevel = VerificationLevel.from(contact.optInt(TAG_SAFE_CONTACT_VERIFICATION_LEVEL, VerificationLevel.UNVERIFIED.getCode()));

				APIConnector.FetchIdentityResult result = apiConnector.getFetchResultByIdentity(results, identity);

				if (result != null) {
					ContactModel contactModel = contactService.getByIdentity(result.identity);
					if (contactModel == null) {
						// create a new contact
						if (verificationLevel == VerificationLevel.FULLY_VERIFIED && !TestUtil.empty(publicKey)) {
							// use the public key from the backup
							contactModel = new ContactModel(identity, Base64.decode(publicKey));
							contactModel.setVerificationLevel(verificationLevel);
						} else {
							// use the fetched key
							contactModel = new ContactModel(result.identity, result.publicKey);
							contactModel.setVerificationLevel(VerificationLevel.UNVERIFIED);
						}
						contactModel.setFeatureMask(result.featureMask);
						contactModel.setIdentityType(result.type);
						switch (result.state) {
							case IdentityState.ACTIVE:
								contactModel.setState(ContactModel.State.ACTIVE);
								break;
							case IdentityState.INACTIVE:
								contactModel.setState(ContactModel.State.INACTIVE);
								break;
							case IdentityState.INVALID:
								contactModel.setState(ContactModel.State.INVALID);
								break;
						}
						contactModel.setIsWork(contact.optBoolean(TAG_SAFE_CONTACT_WORK_VERIFIED));
						contactModel.setFirstName(contact.optString(TAG_SAFE_CONTACT_FIRST_NAME));
						contactModel.setLastName(contact.optString(TAG_SAFE_CONTACT_LAST_NAME));
						contactModel.setPublicNickName(contact.optString(TAG_SAFE_CONTACT_NICKNAME));
						contactModel.setIsHidden(contact.optBoolean(TAG_SAFE_CONTACT_HIDDEN, false));
						contactModel.setDateCreated(new Date(contact.optLong(TAG_SAFE_CONTACT_CREATED_AT, System.currentTimeMillis())));
						contactModel.setIsRestored(true);
						contactModel.setTypingIndicators(contact.optInt(TAG_SAFE_CONTACT_TYPING_INDICATORS, ContactModel.DEFAULT));
						contactModel.setReadReceipts(contact.optInt(TAG_SAFE_CONTACT_READ_RECEIPTS, ContactModel.DEFAULT));
						contactModelFactory.createOrUpdate(contactModel);

						if (contact.optBoolean(TAG_SAFE_CONTACT_PRIVATE, false)) {
							hiddenChatsListService.add(contactService.getUniqueIdString(contactModel), DeadlineListService.DEADLINE_INDEFINITE);
						}
					}
				}
			} catch (JSONException | IOException e) {
				logger.error("Exception", e);
			}
		}
	}

	private void restoreGroups(JSONArray groups) {
		if (groups == null) return;
		if (databaseServiceNew == null) return;
		final GroupService groupService;
		try {
			groupService = ThreemaApplication.getServiceManager().getGroupService();
		} catch (Exception e) {
			logger.error("Exception", e);
			return;
		}

		GroupModelFactory groupModelFactory = databaseServiceNew.getGroupModelFactory();
		GroupMemberModelFactory groupMemberModelFactory = databaseServiceNew.getGroupMemberModelFactory();

		for (int i = 0; i < groups.length(); i++) {
			try {
				JSONObject group = groups.getJSONObject(i);
				String creatorIdentity = group.getString(TAG_SAFE_GROUP_CREATOR);

				// do not create group if creator no longer exists (i.e. was revoked)
				if (contactService.getByIdentity(creatorIdentity) != null) {
					GroupModel groupModel = new GroupModel();

					long createdAt = group.optLong(TAG_SAFE_GROUP_CREATED_AT, 0L);

					groupModel.setApiGroupId(new GroupId(group.getString(TAG_SAFE_GROUP_ID).toLowerCase()));
					groupModel.setCreatorIdentity(creatorIdentity);
					groupModel.setName(group.optString(TAG_SAFE_GROUP_NAME, ""));
					groupModel.setCreatedAt(new Date(createdAt));
					groupModel.setDeleted(group.getBoolean(TAG_SAFE_GROUP_DELETED));
					groupModel.setSynchronizedAt(new Date(0));

					if (groupModelFactory.create(groupModel)) {
						if (group.optBoolean(TAG_SAFE_GROUP_PRIVATE, false)) {
							hiddenChatsListService.add(groupService.getUniqueIdString(groupModel), DeadlineListService.DEADLINE_INDEFINITE);
						}

						JSONArray members = group.getJSONArray(TAG_SAFE_GROUP_MEMBERS);
						for (int j = 0; j < members.length(); j++) {
							String identity = members.getString(j);
							if (!TestUtil.empty(identity)) {
								if (contactService.getByIdentity(identity) == null) {
									// fetch group contact if not in contact list
									try {
										contactService.createContactByIdentity(identity, true, true);
									} catch (InvalidEntryException | EntryAlreadyExistsException | PolicyViolationException e) {
										// do not add as group member if contact cannot be created
										continue;
									}
								}

								GroupMemberModel groupMemberModel = new GroupMemberModel();
								groupMemberModel.setGroupId(groupModel.getId());
								groupMemberModel.setIdentity(identity);
								groupMemberModel.setActive(true);

								groupMemberModelFactory.create(groupMemberModel);
							}
						}

						if (!groupModel.isDeleted()) {
							if (groupService.isGroupCreator(groupModel)) {
								groupService.sendSync(groupModel);
							} else {
								groupService.requestSync(creatorIdentity, groupModel.getApiGroupId());
							}
						}
					}
				}
			} catch (JSONException | NullPointerException | ThreemaException e){
				// ignore and continue with next group
			}
		}
	}

	private void restoreDistributionlists(JSONArray distributionlists) {
		if (distributionlists == null) return;
		if (databaseServiceNew == null) return;
		final DistributionListService distributionListService;
		try {
			distributionListService = ThreemaApplication.getServiceManager().getDistributionListService();
		} catch (Exception e) {
			logger.error("Exception", e);
			return;
		}

		DistributionListMemberModelFactory distributionListMemberModelFactory = databaseServiceNew.getDistributionListMemberModelFactory();

		for (int i = 0; i < distributionlists.length(); i++) {
			try {
				JSONObject distributionlist = distributionlists.getJSONObject(i);
				DistributionListModel distributionListModel = new DistributionListModel();

				long createdAt = distributionlist.optLong(TAG_SAFE_DISTRIBUTIONLIST_CREATED_AT, 0L);

				String id = distributionlist.optString(TAG_SAFE_DISTRIBUTIONLIST_ID);
				if (id.length() > 0) {
					distributionListModel.setId(Long.valueOf(id, 16));
				}
				distributionListModel.setName(distributionlist.getString(TAG_SAFE_DISTRIBUTIONLIST_NAME));
				distributionListModel.setCreatedAt(new Date(createdAt));

				databaseServiceNew.getDistributionListModelFactory().create(distributionListModel);

				if (distributionlist.optBoolean(TAG_SAFE_DISTRIBUTIONLIST_PRIVATE, false)) {
					hiddenChatsListService.add(distributionListService.getUniqueIdString(distributionListModel), DeadlineListService.DEADLINE_INDEFINITE);
				}

				JSONArray members = distributionlist.getJSONArray(TAG_SAFE_DISTRIBUTIONLIST_MEMBERS);
				for (int j = 0; j < members.length(); j++) {
					String identity = members.getString(j);
					if (!TestUtil.empty(identity)) {
						if (contactService.getByIdentity(identity) == null) {
							// fetch contact if not in contact list
							try {
								contactService.createContactByIdentity(identity, true, true);
							} catch (InvalidEntryException | EntryAlreadyExistsException | PolicyViolationException e) {
								// do not add as distribution list member if contact cannot be created
								continue;
							}
						}

						DistributionListMemberModel distributionListMemberModel = new DistributionListMemberModel();
						distributionListMemberModel.setIdentity(identity);
						distributionListMemberModel.setDistributionListId(distributionListModel.getId());
						distributionListMemberModel.setActive(true);
						distributionListMemberModelFactory.create(distributionListMemberModel);
					}
				}
			} catch (JSONException | NullPointerException e) {
				// ignore and continue with next distribution list
			}
		}
	}

	private void restoreSettings(JSONObject settings) {
		boolean syncContactsRestricted = false;
		if (ConfigUtils.isWorkRestricted()) {
			Boolean booleanPreset = AppRestrictionUtil.getBooleanRestriction(context.getString(R.string.restriction__contact_sync));
			if (booleanPreset != null) {
				preferenceService.setSyncContacts(booleanPreset);
				syncContactsRestricted = true;
			}
		}

		if (!syncContactsRestricted) {
			preferenceService.setSyncContacts(settings.optBoolean(TAG_SAFE_SETTINGS_SYNC_CONTACTS, false));
		}

		preferenceService.setBlockUnkown(settings.optBoolean(TAG_SAFE_SETTINGS_BLOCK_UNKNOWN, false));
		preferenceService.setTypingIndicator(settings.optBoolean(TAG_SAFE_SETTINGS_SEND_TYPING, true));
		preferenceService.setReadReceipts(settings.optBoolean(TAG_SAFE_SETTINGS_READ_RECEIPTS, true));
		preferenceService.setVoipEnabled(settings.optBoolean(TAG_SAFE_SETTINGS_THREEMA_CALLS, true));
		preferenceService.setForceTURN(settings.optBoolean(TAG_SAFE_SETTINGS_RELAY_THREEMA_CALLS, false));
		preferenceService.setDisableScreenshots(settings.optBoolean(TAG_SAFE_SETTINGS_DISABLE_SCREENSHOTS, false));
		preferenceService.setIncognitoKeyboard(settings.optBoolean(TAG_SAFE_SETTINGS_INCOGNITO_KEAYBOARD, false));

		setSettingsBlockedContacts(settings.optJSONArray(TAG_SAFE_SETTINGS_BLOCKED_CONTACTS));
		setSettingsSyncExcluded(settings.optJSONArray(TAG_SAFE_SETTINGS_SYNC_EXCLUDED_CONTACTS));
		setSettingsRecentEmojis(settings.optJSONArray(TAG_SAFE_SETTINGS_RECENT_EMOJIS));
	}

	private void parseInfo(JSONObject info) throws ThreemaException, JSONException {
		int version = info.getInt(TAG_SAFE_INFO_VERSION);
		if (version > PROTOCOL_VERSION) {
			throw new ThreemaException(context.getResources().getString(R.string.backup_version_mismatch));
		}
	}

	@Override
	@Nullable
	public ArrayList<String> searchID(String phone, String email) {
		if (phone != null  || email != null) {
			Map<String, Object> phoneMap = new HashMap<>();
			phoneMap.put(phone, null);

			Map<String, Object> emailMap = new HashMap<>();
			emailMap.put(email, null);

			try {
				Map<String, APIConnector.MatchIdentityResult> results = apiConnector.matchIdentities(emailMap, phoneMap, localeService.getCountryIsoCode(), true, identityStore, null);
				if (results.size() > 0) {
					return new ArrayList<>(results.keySet());
				}
			} catch (Exception e) {
				logger.error("Exception", e);
			}
		}
		return null;
	}

	@Override
	public void launchForcedPasswordDialog(Activity activity, boolean openHomeActivity) {
		// ask user for a new password
		Intent intent = new Intent(activity, ThreemaSafeConfigureActivity.class);
		intent.putExtra(EXTRA_WORK_FORCE_PASSWORD, true);
		intent.putExtra(EXTRA_OPEN_HOME_ACTIVITY, openHomeActivity);
		activity.startActivity(intent);
		activity.overridePendingTransition(R.anim.slide_in_right_short, R.anim.slide_out_left_short);
	}

	private void uploadData(ThreemaSafeServerInfo serverInfo, byte[] data) throws ThreemaException {
		URL serverUrl = serverInfo.getBackupUrl(getThreemaSafeBackupId());

		HttpsURLConnection urlConnection;
		try {
			urlConnection = (HttpsURLConnection) serverUrl.openConnection();
		} catch (IOException e) {
			throw new ThreemaException("Unable to connect to server");
		}

		try {
			urlConnection.setSSLSocketFactory(ConfigUtils.getSSLSocketFactory(serverUrl.getHost()));
			urlConnection.setConnectTimeout(15000);
			urlConnection.setReadTimeout(30000);
			urlConnection.setRequestMethod("PUT");
			urlConnection.setRequestProperty("Content-Type", "application/octet-stream");
			urlConnection.setRequestProperty(KEY_USER_AGENT, ProtocolStrings.USER_AGENT);
			serverInfo.addAuthorization(urlConnection);
			urlConnection.setDoOutput(true);
			urlConnection.setDoInput(true);
			urlConnection.setFixedLengthStreamingMode(data.length);

			try (
				ByteArrayInputStream bis = new ByteArrayInputStream(data);
				BufferedOutputStream bos = new BufferedOutputStream(urlConnection.getOutputStream())
			) {
				byte[] buf = new byte[16384];
				int nread;
				while ((nread = bis.read(buf)) > 0) {
					bos.write(buf, 0, nread);
				}
			}

			final int responseCode = urlConnection.getResponseCode();
			if (responseCode != 200 && responseCode != 201 && responseCode != 204) {
				throw new ThreemaException("Server error: " + responseCode);
			}
		} catch (IOException e) {
			throw new ThreemaException("HTTPS IO Exception: " + e.getMessage());
		} catch (IllegalArgumentException e) {
			throw new ThreemaException(e.getMessage());
		} finally {
			urlConnection.disconnect();
		}
	}

	private byte[] gZipCompress(byte[] uncompressedBytes) {
		try {
			ByteArrayOutputStream outputStream = new ByteArrayOutputStream(uncompressedBytes.length);
			// Use gzip for backward compatibility, but don't actually compress (to avoid compression oracles)
			GzipOutputStream gzipOutputStream = new GzipOutputStream(outputStream, Deflater.NO_COMPRESSION, 512, false);
			gzipOutputStream.write(uncompressedBytes);
			gzipOutputStream.close();
			byte[] compressedBytes = outputStream.toByteArray();
			outputStream.close();
			return compressedBytes;
		} catch (Exception e) {
			logger.error("Error compressing", e);
			return null;
		}
	}

	private byte[] gZipUncompress(byte[] compressedBytes) {
		byte[] buffer = new byte[16384];
		try {
			GZIPInputStream gzipInputStream = new GZIPInputStream(new ByteArrayInputStream(compressedBytes));
			ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
			int len;
			while ((len = gzipInputStream.read(buffer)) > 0) {
				outputStream.write(buffer, 0, len);
			}
			gzipInputStream.close();
			byte[] uncompressedBytes = outputStream.toByteArray();
			outputStream.close();
			return uncompressedBytes;
		} catch (Exception e) {
			logger.error("Error uncompressing", e);
			return null;
		}
	}

	private JSONObject getLink(String type, String value) throws JSONException {
		JSONObject link = new JSONObject();

		link.put(TAG_SAFE_USER_LINK_TYPE, type);
		link.put(TAG_SAFE_USER_LINK_VALUE, value);

		return link;
	}

	private JSONArray getLinks() throws JSONException {
		JSONArray linksArray = new JSONArray();

		// currently, there's only one set of links
		if (userService.getMobileLinkingState() == UserService.LinkingState_LINKED) {
			String linkedMobile = userService.getLinkedMobileE164();
			if (linkedMobile != null) {
				// make sure + is stripped from number
				linksArray.put(getLink(TAG_SAFE_USER_LINK_TYPE_MOBILE, linkedMobile.length() > 1 && linkedMobile.startsWith("+") ? linkedMobile.substring(1) : linkedMobile));
			}
		}
		if (userService.getEmailLinkingState() == UserService.LinkingState_LINKED) {
			String linkedEmail = userService.getLinkedEmail();
			if (linkedEmail != null) {
				linksArray.put(getLink(TAG_SAFE_USER_LINK_TYPE_EMAIL, linkedEmail));
			}
		}

		return linksArray;
	}

	private JSONObject getContact(@NonNull ContactModel contactModel) throws JSONException {
		JSONObject contact = new JSONObject();

		contact.put(TAG_SAFE_CONTACT_IDENTITY, contactModel.getIdentity());
		boolean contactIsVerified = contactModel.getVerificationLevel() == VerificationLevel.FULLY_VERIFIED;
		boolean contactIsRevoked = contactModel.getState() == ContactModel.State.INVALID;
		if ((contactIsVerified || contactIsRevoked) && contactModel.getPublicKey() != null) {
			// Back up the public key if the contact is verified, or if it's revoked.
			//
			// Rationale: If the contact is unverified, then it doesn't really matter and the
			// public key can be re-fetched. By not including the key in the backup, we can reduce
			// its size. On the other hand, if a contact is revoked, then the public key cannot be
			// re-fetched, so it must be included.
			contact.put(TAG_SAFE_CONTACT_PUBLIC_KEY, Base64.encodeBytes(contactModel.getPublicKey()));
		}
		if (contactModel.getDateCreated() != null) {
			contact.put(TAG_SAFE_CONTACT_CREATED_AT, Utils.getUnsignedTimestamp(contactModel.getDateCreated()));
		} else {
			contact.put(TAG_SAFE_CONTACT_CREATED_AT, 0);
		}
		contact.put(TAG_SAFE_CONTACT_VERIFICATION_LEVEL, contactModel.getVerificationLevel().getCode());
		contact.put(TAG_SAFE_CONTACT_WORK_VERIFIED, contactModel.isWork());
		contact.put(TAG_SAFE_CONTACT_FIRST_NAME, contactModel.getFirstName());
		contact.put(TAG_SAFE_CONTACT_LAST_NAME, contactModel.getLastName());
		contact.put(TAG_SAFE_CONTACT_NICKNAME, contactModel.getPublicNickName());
		contact.put(TAG_SAFE_CONTACT_HIDDEN, contactModel.isHidden());
		contact.put(TAG_SAFE_CONTACT_TYPING_INDICATORS, contactModel.getTypingIndicators());
		contact.put(TAG_SAFE_CONTACT_READ_RECEIPTS, contactModel.getReadReceipts());
		contact.put(TAG_SAFE_CONTACT_PRIVATE, hiddenChatsListService.has(contactService.getUniqueIdString(contactModel)));

		return contact;
	}

	private JSONArray getContacts() throws JSONException {
		JSONArray contactsArray = new JSONArray();

		for (final ContactModel contactModel : contactService.find(null)) {
			if (contactModel != null) {
				contactsArray.put(getContact(contactModel));
			}
		}

		return contactsArray;
	}

	private JSONArray getGroupMembers(String[] groupMembers) {
		JSONArray membersArray = new JSONArray();

		for (final String groupMember : groupMembers) {
			membersArray.put(groupMember);
		}

		return membersArray;
	}

	private JSONObject getGroup(GroupService groupService, GroupModel groupModel) throws JSONException {
		JSONObject group = new JSONObject();

		group.put(TAG_SAFE_GROUP_ID, groupModel.getApiGroupId());
		group.put(TAG_SAFE_GROUP_CREATOR, groupModel.getCreatorIdentity());
		if (groupModel.getName() != null) {
			group.put(TAG_SAFE_GROUP_NAME, groupModel.getName());
		}
		if (groupModel.getCreatedAt() != null) {
			group.put(TAG_SAFE_GROUP_CREATED_AT, Utils.getUnsignedTimestamp(groupModel.getCreatedAt()));
		} else {
			group.put(TAG_SAFE_GROUP_CREATED_AT, 0);
		}
		group.put(TAG_SAFE_GROUP_MEMBERS, getGroupMembers(groupService.getGroupIdentities(groupModel)));
		group.put(TAG_SAFE_GROUP_DELETED, groupModel.isDeleted());
		group.put(TAG_SAFE_GROUP_PRIVATE, hiddenChatsListService.has(groupService.getUniqueIdString(groupModel)));

		return group;
	}

	private JSONArray getGroups() throws JSONException {
		final GroupService groupService;
		try {
			groupService = ThreemaApplication.getServiceManager().getGroupService();
		} catch (Exception e) {
			logger.error("Exception", e);
			return null;
		}

		JSONArray groupsArray = new JSONArray();

		for (final GroupModel groupModel : groupService.getAll(new GroupService.GroupFilter() {
			@Override
			public boolean sortByDate() {
				return false;
			}

			@Override
			public boolean sortByName() {
				return false;
			}

			@Override
			public boolean sortAscending() {
				return false;
			}

			@Override
			public boolean includeDeletedGroups() {
				return true;
			}

			@Override
			public boolean includeLeftGroups() {
				return true;
			}
		})) {
			groupsArray.put(getGroup(groupService, groupModel));
		}

		return groupsArray;
	}

	private JSONArray getDistributionlistMembers(String[] distributionlistMembers) {
		JSONArray membersArray = new JSONArray();

		for (final String distributionlistMember : distributionlistMembers) {
			membersArray.put(distributionlistMember);
		}

		return membersArray;
	}

	private JSONObject getDistributionlist(DistributionListService distributionListService, DistributionListModel distributionListModel) throws JSONException {
		JSONObject distributionlist = new JSONObject();

		distributionlist.put(TAG_SAFE_DISTRIBUTIONLIST_ID, Utils.byteArrayToHexString(Utils.longToByteArrayBigEndian(distributionListModel.getId())));
		distributionlist.put(TAG_SAFE_DISTRIBUTIONLIST_NAME, distributionListModel.getName());
		if (distributionListModel.getCreatedAt() != null) {
			distributionlist.put(TAG_SAFE_DISTRIBUTIONLIST_CREATED_AT, Utils.getUnsignedTimestamp(distributionListModel.getCreatedAt()));
		} else {
			distributionlist.put(TAG_SAFE_DISTRIBUTIONLIST_CREATED_AT, 0);
		}
		distributionlist.put(TAG_SAFE_DISTRIBUTIONLIST_MEMBERS, getDistributionlistMembers(distributionListService.getDistributionListIdentities(distributionListModel)));
		distributionlist.put(TAG_SAFE_DISTRIBUTIONLIST_PRIVATE, hiddenChatsListService.has(distributionListService.getUniqueIdString(distributionListModel)));

		return distributionlist;
	}

	private JSONArray getDistributionlists() throws JSONException {
		final DistributionListService distributionListService;
		try {
			distributionListService = ThreemaApplication.getServiceManager().getDistributionListService();
		} catch (Exception e) {
			logger.error("Exception", e);
			return null;
		}

		JSONArray distributionlistsArray = new JSONArray();

		for (final DistributionListModel distributionListModel : distributionListService.getAll(new DistributionListService.DistributionListFilter() {
			@Override
			public boolean sortingByDate() {
				return false;
			}

			@Override
			public boolean sortingAscending() {
				return false;
			}

			@Override
			public boolean showHidden() {
				return false;
			}
		})) {
			distributionlistsArray.put(getDistributionlist(distributionListService, distributionListModel));
		}

		return distributionlistsArray;
	}

	private JSONObject getInfo() throws JSONException {
		JSONObject info = new JSONObject();

		info.put(TAG_SAFE_INFO_VERSION, PROTOCOL_VERSION);
		info.put(TAG_SAFE_INFO_DEVICE, ConfigUtils.getAppVersion(context) + "A/" + Locale.getDefault());

		return info;
	}

	private JSONObject getUser() throws JSONException {
		JSONObject user = new JSONObject();

		user.put(TAG_SAFE_USER_PRIVATE_KEY, Base64.encodeBytes(identityStore.getPrivateKey()));
		user.put(TAG_SAFE_USER_NICKNAME, userService.getPublicNickname());

		try {
			Bitmap image = fileService.getContactAvatar(contactService.getMe());
			if (image != null) {
				// scale image - assume profile pics are always square
				if (Math.max(image.getWidth(), image.getHeight()) > PROFILEPIC_MAX_WIDTH) {
					image = BitmapUtil.resizeBitmap(image, PROFILEPIC_MAX_WIDTH, PROFILEPIC_MAX_WIDTH);
				}
				user.put(TAG_SAFE_USER_PROFILE_PIC, Base64.encodeBytes(BitmapUtil.bitmapToByteArray(image, Bitmap.CompressFormat.JPEG, PROFILEPIC_QUALITY)));
				JSONArray profilePicRelease = new JSONArray();
				switch (preferenceService.getProfilePicRelease()) {
					case PROFILEPIC_RELEASE_EVERYONE:
						profilePicRelease.put(PROFILE_PIC_RELEASE_ALL_PLACEHOLDER);
						break;
					case PROFILEPIC_RELEASE_SOME:
						for (String id: profilePicRecipientsService.getAll()) {
							profilePicRelease.put(id);
						}
						break;
					default:
						profilePicRelease.put(null);
						break;
				}
				user.put(TAG_SAFE_USER_PROFILE_PIC_RELEASE, profilePicRelease);
			}
		} catch (Exception e) {
			// ignore missing profile pic
		}

		user.put(TAG_SAFE_USER_LINKS, getLinks());

		return user;
	}

	private JSONArray getSettingsBlockedContacts() {
		final IdListService blacklistService;
		try {
			blacklistService = ThreemaApplication.getServiceManager().getBlackListService();
		} catch (Exception e) {
			logger.error("Exception", e);
			return null;
		}

		JSONArray blockedContactsArray = new JSONArray();

		for (final String id : blacklistService.getAll()) {
			blockedContactsArray.put(id);
		}

		return blockedContactsArray;
	}

	private void setSettingsBlockedContacts(JSONArray blockedContacts) {
		if (blockedContacts == null) return;

		final IdListService blacklistService;
		try {
			blacklistService = ThreemaApplication.getServiceManager().getBlackListService();
		} catch (Exception e) {
			logger.error("Exception", e);
			return;
		}

		for (int i=0; i <blockedContacts.length(); i++) {
			try {
				blacklistService.add(blockedContacts.getString(i));
			} catch (JSONException e) {
				// ignore invalid entry
			}
		}
	}

	private JSONArray getSettingsSyncExcludedContacts() {
		final IdListService excludedSyncIdentitiesService;
		try {
			excludedSyncIdentitiesService = ThreemaApplication.getServiceManager().getExcludedSyncIdentitiesService();
		} catch (Exception e) {
			logger.error("Exception", e);
			return null;
		}

		JSONArray excludedSyncIds = new JSONArray();

		for (final String id : excludedSyncIdentitiesService.getAll()) {
			excludedSyncIds.put(id);
		}

		return excludedSyncIds;
	}

	private void setSettingsSyncExcluded(JSONArray excludedIdentities) {
		if (excludedIdentities == null) return;

		final IdListService excludedSyncIdentitiesService;
		try {
			excludedSyncIdentitiesService = ThreemaApplication.getServiceManager().getExcludedSyncIdentitiesService();
		} catch (Exception e) {
			logger.error("Exception", e);
			return;
		}

		for (int i=0; i <excludedIdentities.length(); i++) {
			try {
				excludedSyncIdentitiesService.add(excludedIdentities.getString(i));
			} catch (JSONException e) {
				// ignore invalid entry
			}
		}
	}

	private JSONArray getSettingsRecentEmojis() {
		JSONArray recentEmojis = new JSONArray();

		for (final String emoji : preferenceService.getRecentEmojis2()) {
			recentEmojis.put(emoji);
		}

		return recentEmojis;
	}

	private void setSettingsRecentEmojis(JSONArray recentEmojis) {
		if (recentEmojis == null) return;

		LinkedList<String> emojiList = new LinkedList<>();

		for (int i=0; i<recentEmojis.length(); i++) {
			try {
				emojiList.add(recentEmojis.getString(i));
			} catch (JSONException e) {
				// ignore invalid entry
			}
		}

		if (emojiList.size() > 0) {
			preferenceService.setRecentEmojis2(emojiList);
		}
	}

	private JSONObject getSettings() throws JSONException {
		JSONObject settings = new JSONObject();

		settings.put(TAG_SAFE_SETTINGS_SYNC_CONTACTS, preferenceService.isSyncContacts());
		settings.put(TAG_SAFE_SETTINGS_BLOCK_UNKNOWN, preferenceService.isBlockUnknown());
		settings.put(TAG_SAFE_SETTINGS_SEND_TYPING, preferenceService.isTypingIndicator());
		settings.put(TAG_SAFE_SETTINGS_READ_RECEIPTS, preferenceService.isReadReceipts());
		settings.put(TAG_SAFE_SETTINGS_THREEMA_CALLS, preferenceService.isVoipEnabled());
		settings.put(TAG_SAFE_SETTINGS_RELAY_THREEMA_CALLS, preferenceService.getForceTURN());
		settings.put(TAG_SAFE_SETTINGS_DISABLE_SCREENSHOTS, preferenceService.isDisableScreenshots());
		settings.put(TAG_SAFE_SETTINGS_INCOGNITO_KEAYBOARD, preferenceService.getIncognitoKeyboard());
		settings.put(TAG_SAFE_SETTINGS_BLOCKED_CONTACTS, getSettingsBlockedContacts());
		settings.put(TAG_SAFE_SETTINGS_SYNC_EXCLUDED_CONTACTS, getSettingsSyncExcludedContacts());
		settings.put(TAG_SAFE_SETTINGS_RECENT_EMOJIS, getSettingsRecentEmojis());

		return settings;
	}

	private String getJson() {
		JSONObject jsonObject = new JSONObject();
		try {
			jsonObject.put(TAG_SAFE_INFO, getInfo());
			jsonObject.put(TAG_SAFE_USER, getUser());
			jsonObject.put(TAG_SAFE_CONTACTS, getContacts());
			jsonObject.put(TAG_SAFE_GROUPS, getGroups());
			jsonObject.put(TAG_SAFE_DISTRIBUTIONLISTS, getDistributionlists());
			jsonObject.put(TAG_SAFE_SETTINGS, getSettings());
			// TODO(ANDR-2296): Store group invites in backup
			return jsonObject.toString(BuildConfig.DEBUG ? 4 : 0);

		} catch (JSONException e) {
			logger.error("Exception", e);
		}

		return null;
	}

	public static class UploadSizeExceedException extends Exception {
		UploadSizeExceedException(String e) {
			super(e);
		}
	}
}

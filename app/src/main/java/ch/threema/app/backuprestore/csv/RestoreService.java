/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2014-2023 Threema GmbH
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

package ch.threema.app.backuprestore.csv;

import static ch.threema.app.services.NotificationService.NOTIFICATION_CHANNEL_ALERT;
import static ch.threema.app.services.NotificationService.NOTIFICATION_CHANNEL_BACKUP_RESTORE_IN_PROGRESS;
import static ch.threema.app.utils.IntentDataUtil.PENDING_INTENT_FLAG_IMMUTABLE;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.StrictMode;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.io.inputstream.ZipInputStream;
import net.lingala.zip4j.model.FileHeader;

import org.apache.commons.io.IOUtils;
import org.json.JSONException;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import ch.threema.app.BuildConfig;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.activities.DummyActivity;
import ch.threema.app.activities.HomeActivity;
import ch.threema.app.asynctasks.DeleteIdentityAsyncTask;
import ch.threema.app.backuprestore.BackupRestoreDataService;
import ch.threema.app.collections.Functional;
import ch.threema.app.exceptions.RestoreCanceledException;
import ch.threema.app.managers.ServiceManager;
import ch.threema.app.notifications.NotificationBuilderWrapper;
import ch.threema.app.services.ContactService;
import ch.threema.app.services.FileService;
import ch.threema.app.services.GroupService;
import ch.threema.app.services.PreferenceService;
import ch.threema.app.services.UserService;
import ch.threema.app.utils.BackupUtils;
import ch.threema.app.utils.CSVReader;
import ch.threema.app.utils.CSVRow;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.JsonUtil;
import ch.threema.app.utils.MessageUtil;
import ch.threema.app.utils.MimeUtil;
import ch.threema.app.utils.StringConversionUtil;
import ch.threema.app.utils.TestUtil;
import ch.threema.base.ThreemaException;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.base.utils.Utils;
import ch.threema.domain.models.GroupId;
import ch.threema.domain.models.VerificationLevel;
import ch.threema.domain.protocol.csp.ProtocolDefines;
import ch.threema.domain.protocol.csp.connection.ThreemaConnection;
import ch.threema.storage.DatabaseNonceStore;
import ch.threema.storage.DatabaseServiceNew;
import ch.threema.storage.factories.ContactModelFactory;
import ch.threema.storage.models.AbstractMessageModel;
import ch.threema.storage.models.ContactModel;
import ch.threema.storage.models.DistributionListMemberModel;
import ch.threema.storage.models.DistributionListMessageModel;
import ch.threema.storage.models.DistributionListModel;
import ch.threema.storage.models.GroupMemberModel;
import ch.threema.storage.models.GroupMessageModel;
import ch.threema.storage.models.GroupModel;
import ch.threema.storage.models.MessageModel;
import ch.threema.storage.models.MessageState;
import ch.threema.storage.models.MessageType;
import ch.threema.storage.models.ballot.BallotChoiceModel;
import ch.threema.storage.models.ballot.BallotModel;
import ch.threema.storage.models.ballot.BallotVoteModel;
import ch.threema.storage.models.ballot.GroupBallotModel;
import ch.threema.storage.models.ballot.IdentityBallotModel;
import ch.threema.storage.models.ballot.LinkBallotModel;
import ch.threema.storage.models.data.MessageContentsType;
import ch.threema.storage.models.data.media.BallotDataModel;
import ch.threema.storage.models.data.media.FileDataModel;

public class RestoreService extends Service {
	private static final Logger logger = LoggingUtil.getThreemaLogger("RestoreService");

	public static final String EXTRA_RESTORE_BACKUP_FILE = "file";
	public static final String EXTRA_RESTORE_BACKUP_PASSWORD = "pwd";
	private static final int MAX_THUMBNAIL_SIZE_BYTES = 5 * 1024 * 1024; // do not restore thumbnails that are bigger than 5 MB

	private ServiceManager serviceManager;
	private ContactService contactService;
	private FileService fileService;
	private UserService userService;
	private GroupService groupService;
	private DatabaseServiceNew databaseServiceNew;
	private PreferenceService preferenceService;
	private PowerManager.WakeLock wakeLock;
	private NotificationManager notificationManager;
	private DatabaseNonceStore databaseNonceStore;

	private NotificationCompat.Builder notificationBuilder;

	private static final int RESTORE_NOTIFICATION_ID = 981772;
	public static final int RESTORE_COMPLETION_NOTIFICATION_ID = 981773;
	private static final String EXTRA_ID_CANCEL = "cnc";

	private final RestoreResultImpl restoreResult = new RestoreResultImpl();
	private final HashMap<String, String> identityIdMap = new HashMap<>();
	private final HashMap<String, Integer> groupUidMap = new HashMap<>();

	private long currentProgressStep = 0;
	private long progressSteps = 0;
	private int latestPercentStep = -1;
	private long startTime = 0;

	private static boolean restoreSuccess = false;

	private ZipFile zipFile;
	private String password;

	private static final int STEP_SIZE_PREPARE = 100;
	private static final int STEP_SIZE_IDENTITY = 100;
	private static final int STEP_SIZE_MAIN_FILES = 200;
	private static final int STEP_SIZE_MESSAGES = 1; // per message
	private static final int STEP_SIZE_GROUP_AVATARS = 50;
	private static final int STEP_SIZE_MEDIA = 25; // per media file

	private long stepSizeTotal = (long) STEP_SIZE_PREPARE + STEP_SIZE_IDENTITY + STEP_SIZE_MAIN_FILES + STEP_SIZE_GROUP_AVATARS;

	private static boolean isCanceled = false;
	private static boolean isRunning = false;

	public static boolean isRunning() {
		return isRunning;
	}

	@Nullable
	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}

	@SuppressLint("StaticFieldLeak")
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		logger.debug("onStartCommand flags = " + flags + " startId " + startId);

		startForeground(RESTORE_NOTIFICATION_ID, getPersistentNotification());

		if (intent != null) {
			logger.debug("onStartCommand intent != null");

			isCanceled = intent.getBooleanExtra(EXTRA_ID_CANCEL, false);

			if (!isCanceled) {
				File file = (File) intent.getSerializableExtra(EXTRA_RESTORE_BACKUP_FILE);
				password = intent.getStringExtra(EXTRA_RESTORE_BACKUP_PASSWORD);

				if (file == null || TextUtils.isEmpty(password)) {
					showRestoreErrorNotification("Invalid input");
					stopSelf();
					isRunning = false;

					return START_NOT_STICKY;
				}

				PowerManager powerManager = (PowerManager) getApplicationContext().getSystemService(Context.POWER_SERVICE);
				if (powerManager != null) {
					String tag = BuildConfig.APPLICATION_ID + ":restore";
					if (Build.VERSION.SDK_INT == Build.VERSION_CODES.M && Build.MANUFACTURER.equals("Huawei")) {
						// Huawei will not kill your app if your Wakelock has a well known tag
						// see https://dontkillmyapp.com/huawei
						tag = "LocationManagerService";
					}
					wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, tag);
					if (wakeLock != null) {
						wakeLock.acquire(DateUtils.DAY_IN_MILLIS);
					}
				}

				try {
					serviceManager.stopConnection();
				} catch (InterruptedException e) {
					showRestoreErrorNotification("RestoreService interrupted");
					stopSelf();
					return START_NOT_STICKY;
				}

				new AsyncTask<Void, Void, Boolean>() {
					@Override
					protected Boolean doInBackground(Void... params) {
						zipFile = new ZipFile(file, password.toCharArray());
						if (!zipFile.isValidZipFile()) {
							showRestoreErrorNotification(getString(R.string.restore_zip_invalid_file));
							isRunning = false;

							return false;
						}
						return restore();
					}

					@Override
					protected void onPostExecute(Boolean success) {
						stopSelf();
					}
				}.execute();

				if (isRunning) {
					return START_STICKY;
				}
			} else {
				Toast.makeText(this, R.string.restore_data_cancelled, Toast.LENGTH_LONG).show();
			}
		} else {
			logger.debug("onStartCommand intent == null");

			onFinished("Empty intent");
		}
		isRunning = false;

		return START_NOT_STICKY;
	}

	@Override
	public void onCreate() {
		logger.info("onCreate");

		super.onCreate();

		isRunning = true;

		serviceManager = ThreemaApplication.getServiceManager();
		if (serviceManager == null) {
			stopSelf();
			return;
		}

		try {
			fileService = serviceManager.getFileService();
			databaseServiceNew = serviceManager.getDatabaseServiceNew();
			contactService = serviceManager.getContactService();
			userService = serviceManager.getUserService();
			groupService = serviceManager.getGroupService();
			preferenceService = serviceManager.getPreferenceService();
			databaseNonceStore = new DatabaseNonceStore(this, serviceManager.getIdentityStore());
		} catch (Exception e) {
			logger.error("Could not instantiate all required services", e);
			stopSelf();
			return;
		}

		notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
	}

	@Override
	public void onDestroy() {
		logger.info("onDestroy success = {} cancelled = {}", restoreSuccess, isCanceled);

		if (isCanceled) {
			onFinished(getString(R.string.restore_data_cancelled));
		}

		super.onDestroy();
	}

	@Override
	public void onLowMemory() {
		logger.info("onLowMemory");
		super.onLowMemory();
	}

	@Override
	public void onTaskRemoved(Intent rootIntent) {
		logger.info("onTaskRemoved");

		Intent intent = new Intent(this, DummyActivity.class);
		intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		startActivity(intent);
	}

	// ---------------------------------------------------------------------------
	private static class RestoreResultImpl implements BackupRestoreDataService.RestoreResult {
		private long contactSuccess = 0;
		private long contactFailed = 0;
		private long messageSuccess = 0;
		private long messageFailed = 0;

		@Override
		public long getContactSuccess() {
			return this.contactSuccess;
		}

		@Override
		public long getContactFailed() {
			return this.contactFailed;
		}

		@Override
		public long getMessageSuccess() {
			return this.messageSuccess;
		}

		@Override
		public long getMessageFailed() {
			return this.messageFailed;
		}

		protected void incContactSuccess() {
			this.contactSuccess++;
		}
		protected void incContactFailed() {
			this.contactFailed++;
		}
		protected void incMessageSuccess() {
			this.messageSuccess++;
		}
		protected void incMessageFailed() {
			this.messageFailed++;
		}
	}

	/**
	 * CSV file processor
	 *
	 * The {@link #row(CSVRow)} method will be called for every row in the CSV file.
	 */
	private interface ProcessCsvFile {
		void row(CSVRow row) throws RestoreCanceledException;
	}

	private interface GetMessageModel {
		AbstractMessageModel get(String uid);
	}

	private RestoreSettings restoreSettings;
	private final HashMap<String, Integer> ballotIdMap = new HashMap<>();
	private final HashMap<Integer, Integer> ballotOldIdMap = new HashMap<>();
	private final HashMap<String, Integer> ballotChoiceIdMap = new HashMap<>();
	private final HashMap<String, Long> distributionListIdMap = new HashMap<>();

	private boolean writeToDb = false;

	public boolean restore() {
		logger.info("Restoring data backup");

		int mediaCount;
		int messageCount;
		String message;

		if (BuildConfig.DEBUG) {
			// zipFile.getInputStream() currently causes "Explicit termination method 'end' not called" exception
			StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder()
				.detectAll()
				.penaltyLog()
				.build());
		}

		try {
			// Ensure that the server connection is stopped before restoring the backup.
			//
			// This is important, because during the backup restore process, some outgoing
			// messages (e.g. group sync messages) might be enqueued. However, we only want to
			// send those messages if the backup restore succeeded.
			//
			// The connection will be resumed in {@link onFinished}.
			final ThreemaConnection connection = serviceManager.getConnection();
			if (connection != null && connection.isRunning()) {
				connection.stop();
			}

			// We use two passes for a restore. The first pass only scans the files in the backup, but does not write to the database. In the second pass, the files are actually written.
			for (int nTry = 0; nTry < 2; nTry++) {
				logger.info("Attempt {}", nTry + 1);
				if (nTry > 0) {
					this.writeToDb = true;
					this.initProgress(stepSizeTotal);
				}

				this.identityIdMap.clear();
				this.groupUidMap.clear();
				this.ballotIdMap.clear();
				this.ballotOldIdMap.clear();
				this.ballotChoiceIdMap.clear();
				this.distributionListIdMap.clear();

				if (this.writeToDb) {
					updateProgress(STEP_SIZE_PREPARE);

					//clear tables!!
					logger.info("Clearing current tables");
					databaseServiceNew.getMessageModelFactory().deleteAll();
					databaseServiceNew.getContactModelFactory().deleteAll();
					databaseServiceNew.getGroupMessageModelFactory().deleteAll();
					databaseServiceNew.getGroupMemberModelFactory().deleteAll();
					databaseServiceNew.getGroupModelFactory().deleteAll();
					databaseServiceNew.getDistributionListMessageModelFactory().deleteAll();
					databaseServiceNew.getDistributionListMemberModelFactory().deleteAll();
					databaseServiceNew.getDistributionListModelFactory().deleteAll();
					databaseServiceNew.getBallotModelFactory().deleteAll();
					databaseServiceNew.getBallotVoteModelFactory().deleteAll();
					databaseServiceNew.getBallotChoiceModelFactory().deleteAll();
					databaseServiceNew.getGroupMessagePendingMessageIdModelFactory().deleteAll();
					databaseServiceNew.getGroupRequestSyncLogModelFactory().deleteAll();

					// Remove all media files (don't remove recursively, tmp folder contain the restoring files
					logger.info("Deleting current media files");
					fileService.clearDirectory(fileService.getAppDataPath(), false);
				}

				List<FileHeader> fileHeaders = zipFile.getFileHeaders();

				// The restore settings file contains the data backup format version
				this.restoreSettings = getRestoreSettings(fileHeaders);

				if (restoreSettings.isUnsupportedVersion()) {
					throw new ThreemaException(getString(R.string.backup_version_mismatch));
				}

				// Restore the identity
				logger.info("Restoring identity");
				FileHeader identityHeader = Functional.select(
					fileHeaders,
					type -> TestUtil.compare(type.getFileName(), Tags.IDENTITY_FILE_NAME)
				);
				if (identityHeader != null && this.writeToDb) {
					String identityContent;
					try (InputStream inputStream = zipFile.getInputStream(identityHeader)) {
						identityContent = IOUtils.toString(inputStream);
					}

					try {
						if (!userService.restoreIdentity(identityContent, this.password)) {
							throw new ThreemaException(getString(R.string.unable_to_restore_identity_because, "n/a"));
						}
						// If the backup is older than version 19, the contact avatar file has the
						// id as suffix and is not "me". Therefore we need to include the identity
						// in the id map, so that restoring this id's avatar file works.
						if (restoreSettings.getVersion() < 19) {
							identityIdMap.put(userService.getIdentity(), userService.getIdentity());
						}
					} catch (UnknownHostException e) {
						throw e;
					} catch (Exception e) {
						throw new ThreemaException(getString(R.string.unable_to_restore_identity_because, e.getMessage()));
					}

					updateProgress(STEP_SIZE_IDENTITY);
				}

				// Restore nonces
				logger.info("Restoring nonces");
				if (!restoreNonces(fileHeaders)) {
					logger.error("Restoring nonces failed");
					//continue anyway!
				}

				//contacts, groups and distribution lists
				logger.info("Restoring main files (contacts, groups, distribution lists)");
				if(!this.restoreMainFiles(fileHeaders)) {
					logger.error("restore main files failed");
					//continue anyway!
				}

				updateProgress(STEP_SIZE_MAIN_FILES);

				logger.info("Restoring message files");
				messageCount = this.restoreMessageFiles(fileHeaders);
				if(messageCount == 0) {
					logger.error("restore message files failed");
					//continue anyway!
				}

				logger.info("Restoring group avatar files");
				if(!this.restoreGroupAvatarFiles(fileHeaders)) {
					logger.error("restore group avatar files failed");
					//continue anyway!
				}

				updateProgress(STEP_SIZE_GROUP_AVATARS);

				logger.info("Restoring message media files");
				mediaCount = this.restoreMessageMediaFiles(fileHeaders);
				if (mediaCount == 0) {
					logger.warn("No media files restored. Might be a backup without media?");
					//continue anyway!
				} else {
					logger.info("{} media files found", mediaCount);
				}

				//restore all avatars
				logger.info("Restoring avatars");
				if(!this.restoreContactAvatars(fileHeaders)) {
					logger.error("restore contact avatar files failed");
					//continue anyway!
				}

				// Reset the profile pic upload so that the own profile picture is redistributed
				preferenceService.setProfilePicUploadDate(new Date(0));
				preferenceService.setProfilePicUploadData(null);

				if (!writeToDb) {
					stepSizeTotal += (messageCount * STEP_SIZE_MESSAGES)  + ((long) mediaCount * STEP_SIZE_MEDIA);
				}
			}

			logger.info("Restore successful!");
			restoreSuccess = true;
			onFinished(null);

			return true;
		} catch (InterruptedException e) {
			logger.error("Interrupted while restoring identity", e);
			Thread.currentThread().interrupt();
			message = "Interrupted while restoring identity";
		} catch (RestoreCanceledException e) {
			logger.error("Restore cancelled", e);
			message = getString(R.string.restore_data_cancelled);
		} catch(IOException e) {
			logger.error("Exception while restoring backup", e);
			message = getString(R.string.invalid_zip_restore_failed, e.getMessage());
		} catch (Exception e) {
			// wrong password? no connection? throw
			logger.error("Exception while restoring backup", e);
			message = e.getMessage();
		}

		onFinished(message);

		return false;
	}

	@NonNull
	private RestoreSettings getRestoreSettings(List<FileHeader> fileHeaders) throws ThreemaException, IOException {
		FileHeader settingsHeader = Functional.select(fileHeaders, type -> TestUtil.compare(type.getFileName(), Tags.SETTINGS_FILE_NAME));
		if (settingsHeader == null) {
			logger.error("Settings file header is missing");
			throw new ThreemaException(getString(R.string.invalid_backup));
		}
		try (
			InputStream is = zipFile.getInputStream(settingsHeader);
			InputStreamReader inputStreamReader = new InputStreamReader(is);
			CSVReader csvReader = new CSVReader(inputStreamReader)
		) {
			RestoreSettings settings = new RestoreSettings();
			settings.parse(csvReader.readAll());
			return settings;
		}
	}

	/**
	 * restore the main files (contacts, groups, distribution lists)
	 */
	private boolean restoreMainFiles(List<FileHeader> fileHeaders) throws IOException, RestoreCanceledException {
		FileHeader ballotMain = null;
		FileHeader ballotChoice = null;
		FileHeader ballotVote = null;
		for (FileHeader fileHeader : fileHeaders) {
			String fileName = fileHeader.getFileName();

			if (fileName.endsWith(Tags.CSV_FILE_POSTFIX)) {
				if (fileName.startsWith(Tags.CONTACTS_FILE_NAME)) {
					if(!this.restoreContactFile(fileHeader)) {
						logger.error("restore contact file failed");
						return false;
					}
				}
				else if (fileName.startsWith(Tags.GROUPS_FILE_NAME)) {
					if(!this.restoreGroupFile(fileHeader)) {
						logger.error("restore group file failed");
					}
				}
				else if (fileName.startsWith(Tags.DISTRIBUTION_LISTS_FILE_NAME)) {
					if(!this.restoreDistributionListFile(fileHeader)) {
						logger.error("restore distribution list file failed");
					}
				}
				else if (fileName.startsWith(Tags.BALLOT_FILE_NAME + Tags.CSV_FILE_POSTFIX)) {
					ballotMain = fileHeader;
				}
				else if (fileName.startsWith(Tags.BALLOT_CHOICE_FILE_NAME + Tags.CSV_FILE_POSTFIX)) {
					ballotChoice = fileHeader;
				}
				else if (fileName.startsWith(Tags.BALLOT_VOTE_FILE_NAME + Tags.CSV_FILE_POSTFIX)) {
					ballotVote = fileHeader;
				}
			}
		}

		if (TestUtil.required(ballotMain, ballotChoice, ballotVote)) {
			this.restoreBallotFile(ballotMain, ballotChoice, ballotVote);
		}

		return true;
	}

	private boolean restoreNonces(List<FileHeader> fileHeaders) throws IOException {
		FileHeader nonceFileHeader = null;
		for (FileHeader fileHeader : fileHeaders) {
			String fileName = fileHeader.getFileName();
			if (fileName != null && fileName.startsWith(Tags.NONCE_FILE_NAME)) {
				nonceFileHeader = fileHeader;
				break;
			}
		}
		if (nonceFileHeader == null) {
			logger.info("Nonce file header is null");
			return false;
		}

		if (this.writeToDb) {
			try (ZipInputStream inputStream = this.zipFile.getInputStream(nonceFileHeader);
			     InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
			     CSVReader csvReader = new CSVReader(inputStreamReader, false)
			) {
				boolean success = true;
				CSVRow row;
				while ((row = csvReader.readNextRow()) != null) {
					try {
						// Note that currently there is only one nonce per row, and therefore we do
						// not need to read them as array. However, this gives us the flexibility to
						// backup several nonces in one row (as we have done in 5.1-alpha3)
						String[] nonces = row.getStrings(Tags.TAG_NONCES);
						success &= databaseNonceStore.insertHashedNonces(nonces);
					} catch (ThreemaException e) {
						logger.error("Could not insert nonces");
						return false;
					}
				}
				return success;
			}
		}

		return true;
	}

	/**
	 * restore all avatars and profile pics
	 */
	private boolean restoreContactAvatars(List<FileHeader> fileHeaders) {
		for (FileHeader fileHeader : fileHeaders) {
			String fileName = fileHeader.getFileName();
			if (fileName.startsWith(Tags.CONTACT_AVATAR_FILE_PREFIX)) {
				if(!this.restoreContactAvatarFile(fileHeader)) {
					logger.error("restore contact avatar {} file failed or skipped", fileName);
					//continue anyway
				}
			}
			else if (fileName.startsWith(Tags.CONTACT_PROFILE_PIC_FILE_PREFIX)) {
				if(!this.restoreContactPhotoFile(fileHeader)) {
					logger.error("restore contact profile pic {} file failed or skipped", fileName);
					//continue anyway
				}
			}
		}
		return true;
	}
	/**
	 * restore all message files
	 */
	private int restoreMessageFiles(List<FileHeader> fileHeaders) throws IOException, RestoreCanceledException {
		int count = 0;
		for (FileHeader fileHeader : fileHeaders) {

			String fileName = fileHeader.getFileName();

			if (!fileName.endsWith(Tags.CSV_FILE_POSTFIX)) {
				continue;
			}

			if (fileName.startsWith(Tags.MESSAGE_FILE_PREFIX)) {
				try {
					count += this.restoreContactMessageFile(fileHeader);
				} catch (ThreemaException e) {
					logger.error("restore contact message file failed");
					return 0;
				}
			}
			else if (fileName.startsWith(Tags.GROUP_MESSAGE_FILE_PREFIX)) {
				try {
					count += this.restoreGroupMessageFile(fileHeader);
				} catch (ThreemaException e) {
					logger.error("restore group message file failed");
					return 0;
				}
			}
			else if (fileName.startsWith(Tags.DISTRIBUTION_LIST_MESSAGE_FILE_PREFIX)) {
				try {
					count += this.restoreDistributionListMessageFile(fileHeader);
				} catch (ThreemaException e) {
					logger.error("restore distributionList message file failed");
					return 0;
				}
			}
		}
		return count;
	}

	/**
	 * restore all group avatars!
	 */
	private boolean restoreGroupAvatarFiles(List<FileHeader> fileHeaders) {
		boolean success = true;
		for(FileHeader fileHeader: fileHeaders) {
			String fileName = fileHeader.getFileName();

			if (!fileName.startsWith(Tags.GROUP_AVATAR_PREFIX)) {
				continue;
			}

			final String groupUid = fileName.substring(Tags.GROUP_AVATAR_PREFIX.length());
			if (!TestUtil.empty(groupUid)) {
				Integer groupId = groupUidMap.get(groupUid);
				if (groupId != null) {
					GroupModel m = databaseServiceNew.getGroupModelFactory().getById(groupId);
					if (m != null) {
						try (InputStream inputStream = zipFile.getInputStream(fileHeader)) {
							this.fileService.writeGroupAvatar(m, IOUtils.toByteArray(inputStream));
						} catch (Exception e) {
							//ignore, just the avatar :)
							success = false;
						}
						//
					}
				}
			}
		}

		return success;
	}

	/**
	 * restore all message media
	 */
	private int restoreMessageMediaFiles(List<FileHeader> fileHeaders) throws RestoreCanceledException {
		int count = 0;

		count += this.restoreMessageMediaFiles(
			fileHeaders,
			Tags.MESSAGE_MEDIA_FILE_PREFIX,
			Tags.MESSAGE_MEDIA_THUMBNAIL_FILE_PREFIX,
			uid -> databaseServiceNew.getMessageModelFactory().getByUid(uid)
		);

		count += this.restoreMessageMediaFiles(
			fileHeaders,
			Tags.GROUP_MESSAGE_MEDIA_FILE_PREFIX,
			Tags.GROUP_MESSAGE_MEDIA_THUMBNAIL_FILE_PREFIX,
			uid -> databaseServiceNew.getGroupMessageModelFactory().getByUid(uid)
		);

		return count;
	}


	/**
	 * restore all message media
	 */
	private int restoreMessageMediaFiles(List<FileHeader> fileHeaders, String filePrefix, String thumbnailPrefix, GetMessageModel getMessageModel) throws RestoreCanceledException {
		int count = 0;

		//process all thumbnails
		Map<String, FileHeader> thumbnailFileHeaders = new HashMap<>();

		for (FileHeader fileHeader : fileHeaders) {
			String fileName = fileHeader.getFileName();
			if(!TestUtil.empty(fileName)
					&& fileName.startsWith(thumbnailPrefix)) {
				thumbnailFileHeaders.put(fileName, fileHeader);
			}
		}

		for (FileHeader fileHeader : fileHeaders) {
			String fileName = fileHeader.getFileName();

			String messageUid;
			if (fileName.startsWith(filePrefix)) {
				messageUid = fileName.substring(filePrefix.length());
			} else if (fileName.startsWith(thumbnailPrefix)) {
				messageUid = fileName.substring(thumbnailPrefix.length());
			} else {
				continue;
			}

			AbstractMessageModel model = getMessageModel.get(messageUid);

			if (model != null) {
				try {
					if (fileName.startsWith(thumbnailPrefix)) {
						// restore thumbnail
						if (this.writeToDb) {
							FileHeader thumbnailFileHeader = thumbnailFileHeaders.get(thumbnailPrefix + messageUid);
							if (thumbnailFileHeader != null) {
								try (ZipInputStream inputStream = zipFile.getInputStream(thumbnailFileHeader)) {
									byte[] thumbnailBytes = IOUtils.toByteArray(inputStream);
									if (thumbnailBytes != null && thumbnailBytes.length < MAX_THUMBNAIL_SIZE_BYTES) {
										this.fileService.saveThumbnail(model, thumbnailBytes);
									}
								}
								//
							}
						}
					} else {
						if (this.writeToDb) {
							byte[] imageData;
							try (ZipInputStream inputStream = zipFile.getInputStream(fileHeader)) {
								imageData = IOUtils.toByteArray(inputStream);
								this.fileService.writeConversationMedia(model, imageData);
							} catch (OutOfMemoryError e) {
								imageData = null;
							}

							if (MessageUtil.canHaveThumbnailFile(model)) {
								//check if a thumbnail file is in backup
								FileHeader thumbnailFileHeader = thumbnailFileHeaders.get(thumbnailPrefix + messageUid);

								//if no thumbnail file exist in backup, generate one
								if (thumbnailFileHeader == null && imageData != null) {
									this.fileService.writeConversationMediaThumbnail(model, imageData);
								}
							}
						}
					}
					count++;
					updateProgress(STEP_SIZE_MEDIA);
				} catch (RestoreCanceledException e) {
					throw new RestoreCanceledException();
				} catch (Exception x) {
					logger.error("Exception", x);
					//ignore and continue
				}
			} else {
				count++;
			}
		}
		return count;
	}

	private boolean restoreContactFile(@NonNull FileHeader fileHeader) throws IOException, RestoreCanceledException {
		return this.processCsvFile(fileHeader, row -> {
			try {
				ContactModel contactModel = createContactModel(row, restoreSettings);
				if (writeToDb) {
					//set the default color
					ContactModelFactory contactModelFactory = databaseServiceNew.getContactModelFactory();
					contactModelFactory.createOrUpdate(contactModel);
					restoreResult.incContactSuccess();
				}
			} catch (Exception x) {
				logger.error("Could not restore contact", x);
				if (writeToDb) {
					//process next
					restoreResult.incContactFailed();
				}
			}
		});
	}

	private boolean restoreContactAvatarFile(@NonNull FileHeader fileHeader){
		// Look up avatar filename
		String filename = fileHeader.getFileName();
		if (TestUtil.empty(filename)) {
			return false;
		}

		// Look up contact model for this avatar
		String identityId = filename.substring(Tags.CONTACT_AVATAR_FILE_PREFIX.length());
		if (TestUtil.empty(identityId)) {
			return false;
		}

		ContactModel contactModel;
		if (Tags.CONTACT_AVATAR_FILE_SUFFIX_ME.equals(identityId)) {
			contactModel = contactService.getMe();
		} else {
			contactModel = contactService.getByIdentity(identityIdMap.get(identityId));
		}

		if (contactModel == null) {
			return false;
		}

		// Set contact avatar
		try (ZipInputStream inputStream = zipFile.getInputStream(fileHeader)) {
			return fileService.writeContactAvatar(
				contactModel,
				IOUtils.toByteArray(inputStream)
			);
		} catch (Exception e) {
			logger.error("Exception while writing contact avatar", e);
			return false;
		}
	}

	private boolean restoreContactPhotoFile(@NonNull FileHeader fileHeader){
		// Look up profile picture filename
		String filename = fileHeader.getFileName();
		if(TestUtil.empty(filename)) {
			return false;
		}

		// Look up contact model for this avatar
		String identityId = filename.substring(Tags.CONTACT_PROFILE_PIC_FILE_PREFIX.length());
		if (TestUtil.empty(identityId)) {
			return false;
		}
		ContactModel contactModel = contactService.getByIdentity(identityIdMap.get(identityId));
		if (contactModel == null) {
			return false;
		}

		// Set contact profile picture
		try (ZipInputStream inputStream = zipFile.getInputStream(fileHeader)) {
			return fileService.writeContactPhoto(
				contactModel,
				IOUtils.toByteArray(inputStream));
		} catch (Exception e) {
			logger.error("Exception while writing contact profile picture", e);
			return false;
		}
	}

	private boolean restoreGroupFile(@NonNull FileHeader fileHeader) throws IOException, RestoreCanceledException {
		return this.processCsvFile(fileHeader, row -> {
			try {
				GroupModel groupModel = createGroupModel(row, restoreSettings);

				if (writeToDb) {
					databaseServiceNew.getGroupModelFactory().create(
							groupModel
					);

					if (restoreSettings.getVersion() >= 19) {
						groupUidMap.put(row.getString(Tags.TAG_GROUP_UID), groupModel.getId());
					} else {
						groupUidMap.put(BackupUtils.buildGroupUid(groupModel), groupModel.getId());
					}

					restoreResult.incContactSuccess();
				}

				List<GroupMemberModel> groupMemberModels = createGroupMembers(row, groupModel.getId());
				if (writeToDb) {
					for (GroupMemberModel groupMemberModel : groupMemberModels) {
						databaseServiceNew.getGroupMemberModelFactory().create(groupMemberModel);
					}

					if (!groupModel.isDeleted()) {
						if (groupService.isGroupOwner(groupModel)) {
							groupService.sendSync(groupModel);
						} else {
							groupService.requestSync(groupModel.getCreatorIdentity(), new GroupId(Utils.hexStringToByteArray(groupModel.getApiGroupId().toString())));
						}
					}
				}
			} catch (Exception x) {
				logger.error("Could not restore group", x);
				if (writeToDb) {
					//process next
					restoreResult.incContactFailed();
				}
			}
		});
	}

	private boolean restoreDistributionListFile(@NonNull FileHeader fileHeader) throws IOException, RestoreCanceledException {
		return this.processCsvFile(fileHeader, row -> {
			try {
				DistributionListModel distributionListModel = createDistributionListModel(row);

				if (writeToDb) {
					databaseServiceNew.getDistributionListModelFactory().create(
							distributionListModel);
					distributionListIdMap.put(BackupUtils.buildDistributionListUid(distributionListModel), distributionListModel.getId());
					restoreResult.incContactSuccess();
				}

				List<DistributionListMemberModel> distributionListMemberModels = createDistributionListMembers(row, distributionListModel.getId());
				if (writeToDb) {
					for (DistributionListMemberModel distributionListMemberModel : distributionListMemberModels) {
						databaseServiceNew.getDistributionListMemberModelFactory().create(
								distributionListMemberModel
						);
					}
				}
			} catch (Exception x) {
				logger.error("Could not restore distribution list", x);
				if (writeToDb) {
					//process next
					restoreResult.incContactFailed();
				}
			}
		});
	}

	private void restoreBallotFile(
		@NonNull FileHeader ballotMain,
		@NonNull final FileHeader ballotChoice,
		@NonNull FileHeader ballotVote
	) throws IOException, RestoreCanceledException {
		this.processCsvFile(ballotMain, row -> {
			try {
				BallotModel ballotModel = createBallotModel(row);

				if (writeToDb) {
					databaseServiceNew.getBallotModelFactory().create(
							ballotModel
					);

					ballotIdMap.put(BackupUtils.buildBallotUid(ballotModel), ballotModel.getId());
					ballotOldIdMap.put(row.getInteger(Tags.TAG_BALLOT_ID), ballotModel.getId());
				}

				LinkBallotModel ballotLinkModel = createLinkBallotModel(row, ballotModel.getId());

				if (writeToDb) {
					if(ballotLinkModel == null) {
						//link failed
						logger.error("link failed");
					}
					if(ballotLinkModel instanceof GroupBallotModel) {
						databaseServiceNew.getGroupBallotModelFactory().create(
								(GroupBallotModel)ballotLinkModel
								);
					}
					else if(ballotLinkModel instanceof IdentityBallotModel) {
						databaseServiceNew.getIdentityBallotModelFactory().create(
								(IdentityBallotModel)ballotLinkModel
						);
					}
					else {
						logger.error("not handled link");
					}
				}

			} catch (Exception x) {
				logger.error("Could not restore ballot", x);
				if (writeToDb) {
					//process next
					restoreResult.incContactFailed();
				}
			}
		});

		this.processCsvFile(ballotChoice, row -> {
			try {
				BallotChoiceModel ballotChoiceModel = createBallotChoiceModel(row);
				if (ballotChoiceModel != null && writeToDb) {
					databaseServiceNew.getBallotChoiceModelFactory().create(
							ballotChoiceModel
					);
					ballotChoiceIdMap.put(BackupUtils.buildBallotChoiceUid(ballotChoiceModel), ballotChoiceModel.getId());
				}
			} catch (Exception x) {
				logger.error("Exception", x);
				//continue!
			}
		});

		this.processCsvFile(ballotVote, row -> {
			try {
				BallotVoteModel ballotVoteModel = createBallotVoteModel(row);
				if (ballotVoteModel != null && writeToDb) {
					databaseServiceNew.getBallotVoteModelFactory().create(
							ballotVoteModel
					);
				}
			} catch (Exception x) {
				logger.error("Exception", x);
				//continue!
			}
		});
	}

	private GroupModel createGroupModel(CSVRow row, RestoreSettings restoreSettings) throws ThreemaException {
		GroupModel groupModel = new GroupModel();
		groupModel.setApiGroupId(new GroupId(row.getString(Tags.TAG_GROUP_ID)));
		groupModel.setCreatorIdentity(row.getString(Tags.TAG_GROUP_CREATOR));
		groupModel.setName(row.getString(Tags.TAG_GROUP_NAME));
		groupModel.setCreatedAt(row.getDate(Tags.TAG_GROUP_CREATED_AT));

		if(restoreSettings.getVersion() >= 4) {
			groupModel.setDeleted(row.getBoolean(Tags.TAG_GROUP_DELETED));
		}
		else {
			groupModel.setDeleted(false);
		}
		if(restoreSettings.getVersion() >= 14) {
			groupModel.setArchived(row.getBoolean(Tags.TAG_GROUP_ARCHIVED));
		}

		if (restoreSettings.getVersion() >= 17) {
			groupModel.setGroupDesc(row.getString(Tags.TAG_GROUP_DESC));
			groupModel.setGroupDescTimestamp(row.getDate(Tags.TAG_GROUP_DESC_TIMESTAMP));
		}

		return groupModel;
	}

	private BallotModel createBallotModel(CSVRow row) throws ThreemaException {
		BallotModel ballotModel = new BallotModel();

		ballotModel.setApiBallotId(row.getString(Tags.TAG_BALLOT_API_ID));
		ballotModel.setCreatorIdentity(row.getString(Tags.TAG_BALLOT_API_CREATOR));
		ballotModel.setName(row.getString(Tags.TAG_BALLOT_NAME));

		String state = row.getString(Tags.TAG_BALLOT_STATE);
		if(TestUtil.compare(state, BallotModel.State.CLOSED.toString())) {
			ballotModel.setState(BallotModel.State.CLOSED);
		}
		else if(TestUtil.compare(state, BallotModel.State.OPEN.toString())) {
			ballotModel.setState(BallotModel.State.OPEN);
		}
		else if(TestUtil.compare(state, BallotModel.State.TEMPORARY.toString())) {
			ballotModel.setState(BallotModel.State.TEMPORARY);
		}

		String assessment = row.getString(Tags.TAG_BALLOT_ASSESSMENT);
		if(TestUtil.compare(assessment, BallotModel.Assessment.MULTIPLE_CHOICE.toString())) {
			ballotModel.setAssessment(BallotModel.Assessment.MULTIPLE_CHOICE);
		}
		else if(TestUtil.compare(assessment, BallotModel.Assessment.SINGLE_CHOICE.toString())) {
			ballotModel.setAssessment(BallotModel.Assessment.SINGLE_CHOICE);
		}

		String type = row.getString(Tags.TAG_BALLOT_TYPE);
		if(TestUtil.compare(type, BallotModel.Type.INTERMEDIATE.toString())) {
			ballotModel.setType(BallotModel.Type.INTERMEDIATE);
		}
		else if(TestUtil.compare(type, BallotModel.Type.RESULT_ON_CLOSE.toString())) {
			ballotModel.setType(BallotModel.Type.RESULT_ON_CLOSE);
		}

		String choiceType = row.getString(Tags.TAG_BALLOT_C_TYPE);
		if(TestUtil.compare(choiceType, BallotModel.ChoiceType.TEXT.toString())) {
			ballotModel.setChoiceType(BallotModel.ChoiceType.TEXT);
		}

		ballotModel.setLastViewedAt(row.getDate(Tags.TAG_BALLOT_LAST_VIEWED_AT));
		ballotModel.setCreatedAt(row.getDate(Tags.TAG_BALLOT_CREATED_AT));
		ballotModel.setModifiedAt(row.getDate(Tags.TAG_BALLOT_MODIFIED_AT));

		return ballotModel;
	}

	private LinkBallotModel createLinkBallotModel(CSVRow row, int ballotId) throws ThreemaException {
		String reference = row.getString(Tags.TAG_BALLOT_REF);
		String referenceId = row.getString(Tags.TAG_BALLOT_REF_ID);
		Integer groupId = null;
		String identity = null;

		if(reference.endsWith("GroupBallotModel")) {
			groupId = this.groupUidMap.get(referenceId);
		}
		else if(reference.endsWith("IdentityBallotModel")) {
			identity = referenceId;
		}
		else {
			//first try to get the reference as group
			groupId = this.groupUidMap.get(referenceId);
			if(groupId == null) {
				if(referenceId != null && referenceId.length() == ProtocolDefines.IDENTITY_LEN) {
					identity = referenceId;
				}
			}
		}

		if(groupId != null) {
			GroupBallotModel linkBallotModel = new GroupBallotModel();
			linkBallotModel.setBallotId(ballotId);
			linkBallotModel.setGroupId(groupId);

			return linkBallotModel;
		}
		else if(identity != null) {
			IdentityBallotModel linkBallotModel = new IdentityBallotModel();
			linkBallotModel.setBallotId(ballotId);
			linkBallotModel.setIdentity(referenceId);
			return linkBallotModel;
		}

		if(writeToDb) {
			logger.error("invalid ballot reference " + reference + " with id " + referenceId);
			return null;
		}
		//not a valid reference!
		return null;
	}

	private BallotChoiceModel createBallotChoiceModel(CSVRow row) throws ThreemaException {
		Integer ballotId = ballotIdMap.get(row.getString(Tags.TAG_BALLOT_CHOICE_BALLOT_UID));
		if(ballotId == null) {
			logger.error("invalid ballotId");
			return null;
		}

		BallotChoiceModel ballotChoiceModel = new BallotChoiceModel();
		ballotChoiceModel.setBallotId(ballotId);
		ballotChoiceModel.setApiBallotChoiceId(row.getInteger(Tags.TAG_BALLOT_CHOICE_API_ID));
		ballotChoiceModel.setApiBallotChoiceId(row.getInteger(Tags.TAG_BALLOT_CHOICE_API_ID));

		String type = row.getString(Tags.TAG_BALLOT_CHOICE_TYPE);
		if(TestUtil.compare(type, BallotChoiceModel.Type.Text.toString())) {
			ballotChoiceModel.setType(BallotChoiceModel.Type.Text);
		}

		ballotChoiceModel.setName(row.getString(Tags.TAG_BALLOT_CHOICE_NAME));
		ballotChoiceModel.setVoteCount(row.getInteger(Tags.TAG_BALLOT_CHOICE_VOTE_COUNT));
		ballotChoiceModel.setOrder(row.getInteger(Tags.TAG_BALLOT_CHOICE_ORDER));
		ballotChoiceModel.setCreatedAt(row.getDate(Tags.TAG_BALLOT_CHOICE_CREATED_AT));
		ballotChoiceModel.setModifiedAt(row.getDate(Tags.TAG_BALLOT_CHOICE_MODIFIED_AT));

		return ballotChoiceModel;
	}

	private BallotVoteModel createBallotVoteModel(CSVRow row) throws ThreemaException {
		Integer ballotId = ballotIdMap.get(row.getString(Tags.TAG_BALLOT_VOTE_BALLOT_UID));
		Integer ballotChoiceId = ballotChoiceIdMap.get(row.getString(Tags.TAG_BALLOT_VOTE_CHOICE_UID));

		if(!TestUtil.required(ballotId, ballotChoiceId)) {
			return null;
		}

		BallotVoteModel ballotVoteModel = new BallotVoteModel();
		ballotVoteModel.setBallotId(ballotId);
		ballotVoteModel.setBallotChoiceId(ballotChoiceId);
		ballotVoteModel.setVotingIdentity(row.getString(Tags.TAG_BALLOT_VOTE_IDENTITY));
		ballotVoteModel.setChoice(row.getInteger(Tags.TAG_BALLOT_VOTE_CHOICE));
		ballotVoteModel.setCreatedAt(row.getDate(Tags.TAG_BALLOT_VOTE_CREATED_AT));
		ballotVoteModel.setModifiedAt(row.getDate(Tags.TAG_BALLOT_VOTE_MODIFIED_AT));
		return ballotVoteModel;
	}

	private int restoreContactMessageFile(FileHeader fileHeader) throws IOException, ThreemaException, RestoreCanceledException {
		final int[] count = {0};

		String fileName = fileHeader.getFileName();
		if(fileName == null) {
			throw new ThreemaException(null);
		}

		final String identityId = fileName.substring(Tags.MESSAGE_FILE_PREFIX.length(), fileName.indexOf(Tags.CSV_FILE_POSTFIX));
		if (TestUtil.empty(identityId)) {
			throw new ThreemaException(null);
		}

		String identity = identityIdMap.get(identityId);

		if (!this.processCsvFile(fileHeader, row -> {
			try {
				MessageModel messageModel = createMessageModel(row, restoreSettings);
				messageModel.setIdentity(identity);
				count[0]++;

				if (writeToDb) {
					updateProgress(STEP_SIZE_MESSAGES);

					//faster, do not make a createORupdate to safe queries
					databaseServiceNew.getMessageModelFactory().create(
							messageModel
					);
					restoreResult.incMessageSuccess();
				}
			} catch (RestoreCanceledException e) {
				throw new RestoreCanceledException();
			} catch (Exception x) {
				if (writeToDb) {
					restoreResult.incMessageFailed();
				}
			}
		})) {
			throw new ThreemaException(null);
		}
		return count[0];
	}

	private int restoreGroupMessageFile(FileHeader fileHeader)  throws IOException, ThreemaException, RestoreCanceledException {
		final int[] count = {0};

		String fileName = fileHeader.getFileName();
		if(fileName == null) {
			throw new ThreemaException(null);
		}

		final String groupUid = fileName.substring(Tags.GROUP_MESSAGE_FILE_PREFIX.length(), fileName.indexOf(Tags.CSV_FILE_POSTFIX));
		if (TestUtil.empty(groupUid)) {
			throw new ThreemaException(null);
		}

		if (!this.processCsvFile(fileHeader, row -> {
			try {
				GroupMessageModel groupMessageModel = createGroupMessageModel(row, restoreSettings);
				count[0]++;

				if (writeToDb) {
					updateProgress(STEP_SIZE_MESSAGES);
					Integer groupId = groupUidMap.get(groupUid);
					if (groupId != null) {
						groupMessageModel.setGroupId(groupId);
						databaseServiceNew.getGroupMessageModelFactory().create(
								groupMessageModel
						);
					}
					restoreResult.incMessageSuccess();
				}
			} catch (RestoreCanceledException e) {
				throw new RestoreCanceledException();
			} catch (Exception x) {
				if (writeToDb) {
					restoreResult.incMessageFailed();
				}
			}
		})) {
			throw new ThreemaException(null);
		}
		return count[0];
	}

	private int restoreDistributionListMessageFile(FileHeader fileHeader) throws IOException, ThreemaException, RestoreCanceledException {
		final int[] count = {0};

		String fileName = fileHeader.getFileName();
		if(fileName == null) {
			throw new ThreemaException(null);
		}

		String[] pieces = fileName.substring(Tags.DISTRIBUTION_LIST_MESSAGE_FILE_PREFIX.length(), fileName.indexOf(Tags.CSV_FILE_POSTFIX)).split("-");

		if(pieces.length != 1) {
			throw new ThreemaException(null);
		}

		final String apiId = pieces[0];

		if (TestUtil.empty(apiId)) {
			throw new ThreemaException(null);
		}

		if (!this.processCsvFile(fileHeader, row -> {
			try {
				DistributionListMessageModel distributionListMessageModel = createDistributionListMessageModel(row, restoreSettings);
				count[0]++;

				if (writeToDb) {
					updateProgress(STEP_SIZE_MESSAGES);

					Long distributionListId = null;

					if (distributionListIdMap.containsKey(apiId)) {
						distributionListId = distributionListIdMap.get(apiId);
					}

					if (distributionListId != null) {
						distributionListMessageModel.setDistributionListId(distributionListId);
						databaseServiceNew.getDistributionListMessageModelFactory().createOrUpdate(
								distributionListMessageModel
						);
					}
					restoreResult.incContactSuccess();
				}
			} catch (RestoreCanceledException e) {
				throw new RestoreCanceledException();
			} catch (Exception x) {
				if (writeToDb) {
					restoreResult.incMessageFailed();
				}
			}
		})) {
			throw new ThreemaException(null);
		}
		return count[0];
	}

	private DistributionListModel createDistributionListModel(CSVRow row) throws ThreemaException {
		DistributionListModel distributionListModel = new DistributionListModel();
		distributionListModel.setName(row.getString(Tags.TAG_DISTRIBUTION_LIST_NAME));
		distributionListModel.setCreatedAt(row.getDate(Tags.TAG_DISTRIBUTION_CREATED_AT));
		if(restoreSettings.getVersion() >= 14) {
			distributionListModel.setArchived(row.getBoolean(Tags.TAG_DISTRIBUTION_LIST_ARCHIVED));
		}
		return distributionListModel;
	}

	private List<GroupMemberModel> createGroupMembers(CSVRow row, int groupId) throws ThreemaException {
		List<GroupMemberModel> res = new ArrayList<>();
		for(String identity: row.getStrings(Tags.TAG_GROUP_MEMBERS)) {
			if(!TestUtil.empty(identity)) {
				GroupMemberModel m = new GroupMemberModel();
				m.setGroupId(groupId);
				m.setIdentity(identity);
				m.setActive(true);
				res.add(m);
			}
		}
		return res;
	}

	private List<DistributionListMemberModel> createDistributionListMembers(CSVRow row, long distributionListId) throws ThreemaException {
		List<DistributionListMemberModel> res = new ArrayList<>();
		for(String identity: row.getStrings(Tags.TAG_DISTRIBUTION_MEMBERS)) {
			if(!TestUtil.empty(identity)) {
				DistributionListMemberModel m = new DistributionListMemberModel();
				m.setDistributionListId(distributionListId);
				m.setIdentity(identity);
				m.setActive(true);
				res.add(m);
			}
		}
		return res;
	}

	private ContactModel createContactModel(CSVRow row, RestoreSettings restoreSettings) throws ThreemaException {

		ContactModel contactModel = new ContactModel(
				row.getString(Tags.TAG_CONTACT_IDENTITY),
				Utils.hexStringToByteArray(row.getString(Tags.TAG_CONTACT_PUBLIC_KEY)));

		String verificationString = row.getString(Tags.TAG_CONTACT_VERIFICATION_LEVEL);
		VerificationLevel verification = VerificationLevel.UNVERIFIED;

		if (verificationString.equals(VerificationLevel.SERVER_VERIFIED.name())) {
			verification = VerificationLevel.SERVER_VERIFIED;
		} else if (verificationString.equals(VerificationLevel.FULLY_VERIFIED.name())) {
			verification = VerificationLevel.FULLY_VERIFIED;
		}
		contactModel.setVerificationLevel(verification);
		contactModel.setFirstName(row.getString(Tags.TAG_CONTACT_FIRST_NAME));
		contactModel.setLastName(row.getString(Tags.TAG_CONTACT_LAST_NAME));

		if(restoreSettings.getVersion() >= 3) {
			contactModel.setPublicNickName(row.getString(Tags.TAG_CONTACT_NICK_NAME));
		}
		if(restoreSettings.getVersion() >= 13) {
			contactModel.setIsHidden(row.getBoolean(Tags.TAG_CONTACT_HIDDEN));
		}
		if(restoreSettings.getVersion() >= 14) {
			contactModel.setArchived(row.getBoolean(Tags.TAG_CONTACT_ARCHIVED));
		}
		if (restoreSettings.getVersion() >= 19) {
			identityIdMap.put(row.getString(Tags.TAG_CONTACT_IDENTITY_ID), contactModel.getIdentity());
		} else {
			identityIdMap.put(contactModel.getIdentity(), contactModel.getIdentity());
		}
		contactModel.setIsRestored(true);

		return contactModel;
	}

	private void fillMessageModel(AbstractMessageModel messageModel, CSVRow row, RestoreSettings restoreSettings) throws ThreemaException {
		messageModel.setApiMessageId(row.getString(Tags.TAG_MESSAGE_API_MESSAGE_ID));
		messageModel.setOutbox(row.getBoolean(Tags.TAG_MESSAGE_IS_OUTBOX));
		messageModel.setRead(row.getBoolean(Tags.TAG_MESSAGE_IS_READ));
		messageModel.setSaved(row.getBoolean(Tags.TAG_MESSAGE_IS_SAVED));

		String messageState = row.getString(Tags.TAG_MESSAGE_MESSAGE_STATE);
		MessageState state = null;
		if (messageState.equals(MessageState.PENDING.name())) {
			state = MessageState.PENDING;
		} else if (messageState.equals(MessageState.SENDFAILED.name())) {
			state = MessageState.SENDFAILED;
		} else if (messageState.equals(MessageState.USERACK.name())) {
			state = MessageState.USERACK;
		} else if (messageState.equals(MessageState.USERDEC.name())) {
			state = MessageState.USERDEC;
		} else if (messageState.equals(MessageState.DELIVERED.name())) {
			state = MessageState.DELIVERED;
		} else if (messageState.equals(MessageState.READ.name())) {
			state = MessageState.READ;
		} else if (messageState.equals(MessageState.SENDING.name())) {
			state = MessageState.SENDING;
		} else if (messageState.equals(MessageState.SENT.name())) {
			state = MessageState.SENT;
		} else if (messageState.equals(MessageState.CONSUMED.name())) {
			state = MessageState.CONSUMED;
		} else if (messageState.equals(MessageState.FS_KEY_MISMATCH.name())) {
			state = MessageState.FS_KEY_MISMATCH;
		}

		messageModel.setState(state);
		MessageType messageType = MessageType.TEXT;
		@MessageContentsType int messageContentsType = MessageContentsType.UNDEFINED;
		String typeAsString = row.getString(Tags.TAG_MESSAGE_TYPE);

		if (typeAsString.equals(MessageType.VIDEO.name())) {
			messageType = MessageType.VIDEO;
			messageContentsType = MessageContentsType.VIDEO;
		} else if (typeAsString.equals(MessageType.VOICEMESSAGE.name())) {
			messageType = MessageType.VOICEMESSAGE;
			messageContentsType = MessageContentsType.VOICE_MESSAGE;
		} else if (typeAsString.equals(MessageType.LOCATION.name())) {
			messageType = MessageType.LOCATION;
			messageContentsType = MessageContentsType.LOCATION;
		} else if (typeAsString.equals(MessageType.IMAGE.name())) {
			messageType = MessageType.IMAGE;
			messageContentsType = MessageContentsType.IMAGE;
		} else if (typeAsString.equals(MessageType.CONTACT.name())) {
			messageType = MessageType.CONTACT;
			messageContentsType = MessageContentsType.CONTACT;
		} else if (typeAsString.equals(MessageType.BALLOT.name())) {
			messageType = MessageType.BALLOT;
			messageContentsType = MessageContentsType.BALLOT;
		} else if (typeAsString.equals(MessageType.FILE.name())) {
			messageType = MessageType.FILE;
			// get mime type from body
			String body = row.getString(Tags.TAG_MESSAGE_BODY);
			if (!TestUtil.empty(body)) {
				FileDataModel fileDataModel = FileDataModel.create(body);
				messageContentsType = MimeUtil.getContentTypeFromFileData(fileDataModel);
			} else {
				messageContentsType = MessageContentsType.FILE;
			}
		} else if (typeAsString.equals(MessageType.VOIP_STATUS.name())) {
			messageType = MessageType.VOIP_STATUS;
			messageContentsType = MessageContentsType.VOIP_STATUS;
		} else if (typeAsString.equals(MessageType.GROUP_CALL_STATUS.name())) {
			messageType = MessageType.GROUP_CALL_STATUS;
			messageContentsType = MessageContentsType.GROUP_CALL_STATUS;
		}
		messageModel.setType(messageType);
		messageModel.setMessageContentsType(messageContentsType);
		messageModel.setBody(row.getString(Tags.TAG_MESSAGE_BODY));

		if(messageModel.getType() == MessageType.BALLOT) {
			//try to update to new ballot id
			BallotDataModel ballotData = messageModel.getBallotData();
			if(this.ballotOldIdMap.containsKey(ballotData.getBallotId())) {
				BallotDataModel newBallotData = new BallotDataModel(ballotData.getType(), this.ballotOldIdMap.get(ballotData.getBallotId()));
				messageModel.setBallotData(newBallotData);
			}
		}
		if(restoreSettings.getVersion() >= 2) {
			messageModel.setIsStatusMessage(row.getBoolean(Tags.TAG_MESSAGE_IS_STATUS_MESSAGE));
		}

		if(restoreSettings.getVersion() >= 10) {
			messageModel.setCaption(row.getString(Tags.TAG_MESSAGE_CAPTION));
		}

		if(restoreSettings.getVersion() >= 15) {
			String quotedMessageId = row.getString(Tags.TAG_MESSAGE_QUOTED_MESSAGE_ID);
			if (!TestUtil.empty(quotedMessageId)) {
				messageModel.setQuotedMessageId(quotedMessageId);
			}
		}
	}
	private MessageModel createMessageModel(CSVRow row, RestoreSettings restoreSettings) throws ThreemaException {
		MessageModel messageModel = new MessageModel();
		this.fillMessageModel(messageModel, row, restoreSettings);

		messageModel.setPostedAt(row.getDate(Tags.TAG_MESSAGE_POSTED_AT));
		messageModel.setCreatedAt(row.getDate(Tags.TAG_MESSAGE_CREATED_AT));
		if(restoreSettings.getVersion() >= 5) {
			messageModel.setModifiedAt(row.getDate(Tags.TAG_MESSAGE_MODIFIED_AT));
		}
		messageModel.setUid(row.getString(Tags.TAG_MESSAGE_UID));

		if(restoreSettings.getVersion() >= 9) {
			messageModel.setIsQueued(row.getBoolean(Tags.TAG_MESSAGE_IS_QUEUED));
		}
		else {
			messageModel.setIsQueued(true);
		}
		if (restoreSettings.getVersion() >= 16) {
			messageModel.setDeliveredAt(row.getDate(Tags.TAG_MESSAGE_DELIVERED_AT));
			messageModel.setReadAt(row.getDate(Tags.TAG_MESSAGE_READ_AT));
		}
		return messageModel;
	}

	private GroupMessageModel createGroupMessageModel(CSVRow row, RestoreSettings restoreSettings) throws ThreemaException {
		GroupMessageModel messageModel = new GroupMessageModel();
		this.fillMessageModel(messageModel, row, restoreSettings);

		messageModel.setIdentity(row.getString(Tags.TAG_MESSAGE_IDENTITY));
		messageModel.setPostedAt(row.getDate(Tags.TAG_MESSAGE_POSTED_AT));
		messageModel.setCreatedAt(row.getDate(Tags.TAG_MESSAGE_CREATED_AT));
		if(restoreSettings.getVersion() >= 5) {
			messageModel.setModifiedAt(row.getDate(Tags.TAG_MESSAGE_MODIFIED_AT));
		}
		if(restoreSettings.getVersion() >= 9) {
			messageModel.setIsQueued(row.getBoolean(Tags.TAG_MESSAGE_IS_QUEUED));
		}
		else {
			messageModel.setIsQueued(true);
		}
		messageModel.setUid(row.getString(Tags.TAG_MESSAGE_UID));
		if (restoreSettings.getVersion() >= 16) {
			messageModel.setDeliveredAt(row.getDate(Tags.TAG_MESSAGE_DELIVERED_AT));
			messageModel.setReadAt(row.getDate(Tags.TAG_MESSAGE_READ_AT));
		}
		if (restoreSettings.getVersion() >= 17) {
			String messageStatesJson = row.getString(Tags.TAG_GROUP_MESSAGE_STATES);
			if (!TestUtil.empty(messageStatesJson)) {
				try {
					Map<String, Object> messageStatesMap = JsonUtil.convertObject(messageStatesJson);
					messageModel.setGroupMessageStates(messageStatesMap);
				} catch (JSONException ignored) {
					// map may not be available, empty or invalid
				}
			}
		}
		return messageModel;
	}

	private DistributionListMessageModel createDistributionListMessageModel(CSVRow row, RestoreSettings restoreSettings) throws ThreemaException {
		DistributionListMessageModel messageModel = new DistributionListMessageModel();
		this.fillMessageModel(messageModel, row, restoreSettings);

		messageModel.setIdentity(row.getString(Tags.TAG_MESSAGE_IDENTITY));
		messageModel.setPostedAt(row.getDate(Tags.TAG_MESSAGE_POSTED_AT));
		messageModel.setCreatedAt(row.getDate(Tags.TAG_MESSAGE_CREATED_AT));
		if(restoreSettings.getVersion() >= 5) {
			messageModel.setModifiedAt(row.getDate(Tags.TAG_MESSAGE_MODIFIED_AT));
		}
		if(restoreSettings.getVersion() >= 9) {
			messageModel.setIsQueued(row.getBoolean(Tags.TAG_MESSAGE_IS_QUEUED));
		}
		else {
			messageModel.setIsQueued(true);
		}
		messageModel.setUid(row.getString(Tags.TAG_MESSAGE_UID));
		if (restoreSettings.getVersion() >= 16) {
			messageModel.setDeliveredAt(row.getDate(Tags.TAG_MESSAGE_DELIVERED_AT));
			messageModel.setReadAt(row.getDate(Tags.TAG_MESSAGE_READ_AT));
		}
		return messageModel;
	}

	private boolean processCsvFile(
		@NonNull FileHeader fileHeader,
		@NonNull ProcessCsvFile processCsvFile
	) throws IOException, RestoreCanceledException {
		try (ZipInputStream inputStream = this.zipFile.getInputStream(fileHeader);
		     InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
		     CSVReader csvReader = new CSVReader(inputStreamReader, true)) {
			CSVRow row;
			while ((row = csvReader.readNextRow()) != null) {
				processCsvFile.row(row);
			}
		}
		return true;
	}

	private void initProgress(long steps) {
		this.currentProgressStep = 0;
		this.progressSteps = steps;
		this.latestPercentStep = 0;
		this.startTime = System.currentTimeMillis();

		this.handleProgress();
	}

	private void updateProgress(long increment) throws RestoreCanceledException {
		if (isCanceled) {
			throw new RestoreCanceledException();
		}

		if (writeToDb) {
			this.currentProgressStep += increment;
			handleProgress();
		}
	}

	/**
	 * only call progress on 100 steps
	 */
	private void handleProgress() {
		int p = (int) (100d / (double) this.progressSteps * (double) this.currentProgressStep);
		if (p > this.latestPercentStep) {
			this.latestPercentStep = p;
			updatePersistentNotification(latestPercentStep, 100, false);
		}
	}

	public void onFinished(String message) {
		logger.info("onFinished success = {}", restoreSuccess);

		cancelPersistentNotification();

		if (restoreSuccess && userService.hasIdentity()) {
			preferenceService.setWizardRunning(true);

			showRestoreSuccessNotification();

			//try to reopen connection
			try {
				if (!serviceManager.getConnection().isRunning()) {
					serviceManager.startConnection();
				}
			} catch (Exception e) {
				logger.error("Exception", e);
			}

			if (wakeLock != null && wakeLock.isHeld()) {
				logger.debug("releasing wakelock");
				wakeLock.release();
			}

			stopForeground(true);

			isRunning = false;

			if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
				ConfigUtils.scheduleAppRestart(getApplicationContext(), 2 * (int) DateUtils.SECOND_IN_MILLIS, getApplicationContext().getResources().getString(R.string.ipv6_restart_now));
			}
			stopSelf();
		} else {
			showRestoreErrorNotification(message);

			new DeleteIdentityAsyncTask(null, () -> {
				isRunning = false;

				System.exit(0);
			}).execute();
		}
	}

	private Notification getPersistentNotification() {
		logger.debug("getPersistentNotification");

		Intent cancelIntent = new Intent(this, RestoreService.class);
		cancelIntent.putExtra(EXTRA_ID_CANCEL, true);
		PendingIntent cancelPendingIntent;
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			cancelPendingIntent = PendingIntent.getForegroundService(this, (int) System.currentTimeMillis(), cancelIntent, PendingIntent.FLAG_UPDATE_CURRENT | PENDING_INTENT_FLAG_IMMUTABLE);
		} else {
			cancelPendingIntent = PendingIntent.getService(this, (int) System.currentTimeMillis(), cancelIntent, PendingIntent.FLAG_UPDATE_CURRENT | PENDING_INTENT_FLAG_IMMUTABLE);
		}

		notificationBuilder = new NotificationBuilderWrapper(this, NOTIFICATION_CHANNEL_BACKUP_RESTORE_IN_PROGRESS, null)
			.setContentTitle(getString(R.string.restoring_backup))
			.setContentText(getString(R.string.please_wait))
			.setOngoing(true)
			.setSmallIcon(R.drawable.ic_notification_small)
			.setPriority(NotificationCompat.PRIORITY_DEFAULT)
			.addAction(R.drawable.ic_close_white_24dp, getString(R.string.cancel), cancelPendingIntent);

		return notificationBuilder.build();
	}

	private void updatePersistentNotification(int currentStep, int steps, boolean indeterminate) {
		logger.debug("updatePersistentNoti {} of {}", currentStep, steps);

		if (currentStep != 0) {
			final long millisPassed = System.currentTimeMillis() - startTime;
			final long millisRemaining = millisPassed * steps / currentStep - millisPassed;
			String timeRemaining = StringConversionUtil.secondsToString(millisRemaining / DateUtils.SECOND_IN_MILLIS, false);
			notificationBuilder.setContentText(String.format(getString(R.string.time_remaining), timeRemaining));
		}

		notificationBuilder.setProgress(steps, currentStep, indeterminate);
		notificationManager.notify(RESTORE_NOTIFICATION_ID, notificationBuilder.build());
	}

	private void cancelPersistentNotification() {
		notificationManager.cancel(RESTORE_NOTIFICATION_ID);
	}

	private void showRestoreErrorNotification(String message) {
		String contentText;

		if (!TestUtil.empty(message)) {
			contentText = message;
		} else {
			contentText = getString(R.string.restore_error_body);
		}

		NotificationCompat.Builder builder =
			new NotificationBuilderWrapper(this, NOTIFICATION_CHANNEL_ALERT, null)
				.setSmallIcon(R.drawable.ic_notification_small)
				.setTicker(getString(R.string.restore_error_body))
				.setContentTitle(getString(R.string.restoring_backup))
				.setContentText(contentText)
				.setDefaults(Notification.DEFAULT_LIGHTS|Notification.DEFAULT_SOUND|Notification.DEFAULT_VIBRATE)
				.setColor(getResources().getColor(R.color.material_red))
				.setPriority(NotificationCompat.PRIORITY_MAX)
				.setStyle(new NotificationCompat.BigTextStyle().bigText(contentText))
				.setAutoCancel(false);

		notificationManager.notify(RESTORE_COMPLETION_NOTIFICATION_ID, builder.build());
	}


	private void showRestoreSuccessNotification() {
		String text;

		NotificationCompat.Builder builder =
			new NotificationBuilderWrapper(this, NOTIFICATION_CHANNEL_ALERT, null)
				.setSmallIcon(R.drawable.ic_notification_small)
				.setTicker(getString(R.string.restore_success_body))
				.setContentTitle(getString(R.string.restoring_backup))
				.setDefaults(Notification.DEFAULT_LIGHTS|Notification.DEFAULT_SOUND|Notification.DEFAULT_VIBRATE)
				.setColor(getResources().getColor(R.color.material_green))
				.setPriority(NotificationCompat.PRIORITY_MAX)
				.setAutoCancel(true);

		if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
			// Android Q does not allow restart in the background
			Intent backupIntent = new Intent(this, HomeActivity.class);
			PendingIntent pendingIntent = PendingIntent.getActivity(this, (int)System.currentTimeMillis(), backupIntent, PendingIntent.FLAG_UPDATE_CURRENT | PENDING_INTENT_FLAG_IMMUTABLE);

			builder.setContentIntent(pendingIntent);

			text = getString(R.string.restore_success_body) + "\n" + getString(R.string.tap_to_start, getString(R.string.app_name));
		} else {
			text = getString(R.string.restore_success_body);
		}

		builder.setContentText(text);
		builder.setStyle(new NotificationCompat.BigTextStyle().bigText(text));

		notificationManager.notify(RESTORE_COMPLETION_NOTIFICATION_ID, builder.build());
	}
}

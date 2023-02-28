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

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.SystemClock;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.documentfile.provider.DocumentFile;

import net.lingala.zip4j.io.outputstream.ZipOutputStream;

import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.ByteArrayOutputStream;
import org.json.JSONObject;
import org.slf4j.Logger;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import ch.threema.app.BuildConfig;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.activities.DummyActivity;
import ch.threema.app.activities.HomeActivity;
import ch.threema.app.backuprestore.BackupRestoreDataConfig;
import ch.threema.app.backuprestore.RandomUtil;
import ch.threema.app.collections.Functional;
import ch.threema.app.collections.IPredicateNonNull;
import ch.threema.app.managers.ServiceManager;
import ch.threema.app.messagereceiver.MessageReceiver;
import ch.threema.app.notifications.NotificationBuilderWrapper;
import ch.threema.app.services.ContactService;
import ch.threema.app.services.DistributionListService;
import ch.threema.app.services.FileService;
import ch.threema.app.services.GroupService;
import ch.threema.app.services.PreferenceService;
import ch.threema.app.services.UserService;
import ch.threema.app.services.ballot.BallotService;
import ch.threema.app.utils.BackupUtils;
import ch.threema.app.utils.CSVWriter;
import ch.threema.app.utils.MessageUtil;
import ch.threema.app.utils.MimeUtil;
import ch.threema.app.utils.RuntimeUtil;
import ch.threema.app.utils.StringConversionUtil;
import ch.threema.app.utils.TestUtil;
import ch.threema.app.utils.ZipUtil;
import ch.threema.base.ThreemaException;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.base.utils.Utils;
import ch.threema.domain.identitybackup.IdentityBackupGenerator;
import ch.threema.storage.DatabaseServiceNew;
import ch.threema.storage.models.AbstractMessageModel;
import ch.threema.storage.models.ContactModel;
import ch.threema.storage.models.DistributionListMessageModel;
import ch.threema.storage.models.DistributionListModel;
import ch.threema.storage.models.GroupMessageModel;
import ch.threema.storage.models.GroupModel;
import ch.threema.storage.models.MessageModel;
import ch.threema.storage.models.MessageType;
import ch.threema.storage.models.ballot.BallotChoiceModel;
import ch.threema.storage.models.ballot.BallotModel;
import ch.threema.storage.models.ballot.BallotVoteModel;
import ch.threema.storage.models.ballot.GroupBallotModel;
import ch.threema.storage.models.ballot.IdentityBallotModel;
import ch.threema.storage.models.ballot.LinkBallotModel;
import ch.threema.storage.models.data.media.AudioDataModel;
import ch.threema.storage.models.data.media.FileDataModel;
import ch.threema.storage.models.data.media.VideoDataModel;

import static ch.threema.app.services.NotificationService.NOTIFICATION_CHANNEL_ALERT;
import static ch.threema.app.services.NotificationService.NOTIFICATION_CHANNEL_BACKUP_RESTORE_IN_PROGRESS;
import static ch.threema.app.utils.IntentDataUtil.PENDING_INTENT_FLAG_IMMUTABLE;

public class BackupService extends Service {
	private static final Logger logger = LoggingUtil.getThreemaLogger("BackupService");

	private static final int MEDIA_STEP_FACTOR = 9;
	private static final int MEDIA_STEP_FACTOR_VIDEOS_AND_FILES = 12;
	private static final int MEDIA_STEP_FACTOR_THUMBNAILS = 3;

	private static final String EXTRA_ID_CANCEL = "cnc";
	public static final String EXTRA_BACKUP_RESTORE_DATA_CONFIG = "ebrdc";

	private static final int BACKUP_NOTIFICATION_ID = 991772;
	private static final int BACKUP_COMPLETION_NOTIFICATION_ID = 991773;
	private static final long FILE_SETTLE_DELAY = 5000;

	private static final String INCOMPLETE_BACKUP_FILENAME_PREFIX = "INCOMPLETE-";

	private int currentProgressStep = 0;
	private long processSteps = 0;

	private static boolean backupSuccess = false;
	private static boolean isCanceled = false;
	private static boolean isRunning = false;

	private ServiceManager serviceManager;
	private ContactService contactService;
	private FileService fileService;
	private UserService userService;
	private GroupService groupService;
	private BallotService ballotService;
	private DistributionListService distributionListService;
	private DatabaseServiceNew databaseServiceNew;
	private PreferenceService preferenceService;
	private PowerManager.WakeLock wakeLock;
	private NotificationManager notificationManager;

	private NotificationCompat.Builder notificationBuilder;

	private int latestPercentStep = -1;
	private long startTime = 0;

	private static DocumentFile backupFile = null;
	private BackupRestoreDataConfig config = null;
	private final HashMap<Integer, String> groupUidMap = new HashMap<>();
	private final Iterator<Integer> randomIterator = RandomUtil.getDistinctRandomIterator();

	public static boolean isRunning() {
		return isRunning;
	}

	@Nullable
	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		if (intent != null) {
			isCanceled = intent.getBooleanExtra(EXTRA_ID_CANCEL, false);

			if (!isCanceled) {
				config = (BackupRestoreDataConfig) intent.getSerializableExtra(EXTRA_BACKUP_RESTORE_DATA_CONFIG);

				if (config == null || userService.getIdentity() == null || userService.getIdentity().length() == 0) {
					safeStopSelf();
					return START_NOT_STICKY;
				}

				// acquire wake locks
				logger.debug("Acquiring wakelock");
				PowerManager powerManager = (PowerManager) getApplicationContext().getSystemService(Context.POWER_SERVICE);
				if (powerManager != null) {
					String tag = BuildConfig.APPLICATION_ID + ":backup";
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

				boolean success = false;
				Date now = new Date();
				DocumentFile zipFile = null;
				Uri backupUri = this.fileService.getBackupUri();

				if (backupUri == null) {
					showBackupErrorNotification("Destination directory has not been selected yet");
					safeStopSelf();
					return START_NOT_STICKY;
				}

				String filename = "threema-backup_" + now.getTime() + "_1";

				if (ContentResolver.SCHEME_FILE.equalsIgnoreCase(backupUri.getScheme())) {
					zipFile = DocumentFile.fromFile(new File(backupUri.getPath(), INCOMPLETE_BACKUP_FILENAME_PREFIX + filename + ".zip"));
					success = true;
				} else {
					DocumentFile directory = DocumentFile.fromTreeUri(getApplicationContext(), backupUri);
					if (directory != null && directory.exists()) {
						try {
							zipFile = directory.createFile(MimeUtil.MIME_TYPE_ZIP, INCOMPLETE_BACKUP_FILENAME_PREFIX + filename);
							if (zipFile != null && zipFile.canWrite()) {
								success = true;
							}
						} catch (Exception e) {
							logger.debug("Exception", e);
						}
					}
				}

				if (zipFile == null || !success) {
					showBackupErrorNotification(getString(R.string.backup_data_no_permission));
					safeStopSelf();
					return START_NOT_STICKY;
				}

				backupFile = zipFile;

				showPersistentNotification();

				// close connection
				try {
					serviceManager.stopConnection();
				} catch (InterruptedException e) {
					showBackupErrorNotification("BackupService interrupted");
					stopSelf();
					return START_NOT_STICKY;
				}

				new AsyncTask<Void, Void, Boolean>() {
					@Override
					protected Boolean doInBackground(Void... params) {
						return backup();
					}

					@Override
					protected void onPostExecute(Boolean success) {
						stopSelf();
					}
				}.execute();

				return START_STICKY;
			} else {
				Toast.makeText(this, R.string.backup_data_cancelled, Toast.LENGTH_LONG).show();
			}
		} else {
			logger.debug("onStartCommand intent == null");

			onFinished(null);
		}
		return START_NOT_STICKY;
	}

	@Override
	public void onCreate() {
		logger.info("onCreate");

		super.onCreate();

		isRunning = true;

		serviceManager = ThreemaApplication.getServiceManager();
		if (serviceManager == null) {
			safeStopSelf();
			return;
		}

		try {
			fileService = serviceManager.getFileService();
			databaseServiceNew = serviceManager.getDatabaseServiceNew();
			contactService = serviceManager.getContactService();
			groupService = serviceManager.getGroupService();
			distributionListService = serviceManager.getDistributionListService();
			userService = serviceManager.getUserService();
			ballotService = serviceManager.getBallotService();
			preferenceService = serviceManager.getPreferenceService();
		} catch (Exception e) {
			logger.error("Exception", e);
			safeStopSelf();
			return;
		}

		notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

	}

	@Override
	public void onDestroy() {
		logger.info("onDestroy success={} canceled={}", backupSuccess, isCanceled);

		if (isCanceled) {
			onFinished(getString(R.string.backup_data_cancelled));
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
		logger.debug("onTaskRemoved");

		Intent intent = new Intent(this, DummyActivity.class);
		intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		startActivity(intent);
	}

	private int getStepFactor() {
		return this.config.backupVideoAndFiles() ?
				MEDIA_STEP_FACTOR_VIDEOS_AND_FILES : (this.config.backupMedia() ? MEDIA_STEP_FACTOR :
				(this.config.backupThumbnails() ? MEDIA_STEP_FACTOR_THUMBNAILS : 1));
	}

	private boolean backup() {
		String identity = userService.getIdentity();
		try(final ZipOutputStream zipOutputStream = ZipUtil.initializeZipOutputStream(getContentResolver(), backupFile.getUri(), config.getPassword())) {
			logger.debug("Creating zip file {}", backupFile.getUri());

			//save settings
			RestoreSettings settings = new RestoreSettings(RestoreSettings.CURRENT_VERSION);
			ByteArrayOutputStream settingsBuffer = null;
			try {
				settingsBuffer = new ByteArrayOutputStream();
				CSVWriter settingsCsv = new CSVWriter(new OutputStreamWriter(settingsBuffer));
				settingsCsv.writeAll(settings.toList());
				settingsCsv.close();
			}
			finally {
				if (settingsBuffer != null) {
					try {
						settingsBuffer.close();
					} catch (IOException e) { /**/ }
				}
			}

			long progressContactsAndMessages = this.databaseServiceNew.getContactModelFactory().count()
					+ this.databaseServiceNew.getMessageModelFactory().count()
					+ this.databaseServiceNew.getGroupModelFactory().count()
					+ this.databaseServiceNew.getGroupMessageModelFactory().count();

			long progressDistributionLists = this.databaseServiceNew.getDistributionListModelFactory().count()
					+ this.databaseServiceNew.getDistributionListMessageModelFactory().count();

			long progressBallots = this.databaseServiceNew.getBallotModelFactory().count();

			long progress = (this.config.backupIdentity() ? 1 : 0)
					+ (this.config.backupContactAndMessages() ?
					progressContactsAndMessages : 0)
					+ (this.config.backupDistributionLists() ?
					progressDistributionLists : 0)
					+ (this.config.backupBallots() ?
					progressBallots : 0);

			if (this.config.backupMedia() || this.config.backupThumbnails()) {
				try {
					Set<MessageType> fileTypes = this.config.backupVideoAndFiles() ? MessageUtil.getFileTypes() : MessageUtil.getLowProfileMessageModelTypes();
					MessageType[] fileTypesArray = fileTypes.toArray(new MessageType[fileTypes.size()]);

					long mediaProgress = this.databaseServiceNew.getMessageModelFactory().countByTypes(fileTypesArray);
					mediaProgress += this.databaseServiceNew.getGroupMessageModelFactory().countByTypes(fileTypesArray);

					if (this.config.backupDistributionLists()) {
						mediaProgress += this.databaseServiceNew.getDistributionListMessageModelFactory().countByTypes(fileTypesArray);
					}

					progress += (mediaProgress * getStepFactor());
				} catch (Exception x) {
					logger.error("Exception", x);
				}
			}
			logger.debug("Calculated steps " + progress);
			this.initProgress(progress);

			ZipUtil.addZipStream(zipOutputStream, new ByteArrayInputStream(settingsBuffer.toByteArray()), Tags.SETTINGS_FILE_NAME, true);

			if (this.config.backupIdentity()) {
				if (!this.next("backup identity")) {
					return this.cancelBackup(backupFile);
				}

				byte[] privateKey = this.userService.getPrivateKey();
				IdentityBackupGenerator identityBackupGenerator = new IdentityBackupGenerator(identity, privateKey);
				String backupData = identityBackupGenerator.generateBackup(this.config.getPassword());

				ZipUtil.addZipStream(zipOutputStream, IOUtils.toInputStream(backupData), Tags.IDENTITY_FILE_NAME, false);
			}

			//backup contacts and messages
			if (this.config.backupContactAndMessages()) {
				if (!this.backupContactsAndMessages(config, zipOutputStream)) {
					return this.cancelBackup(backupFile);
				}
			}

			//backup groups and messages
			if (this.config.backupGroupsAndMessages()) {
				if (!this.backupGroupsAndMessages(config, zipOutputStream)) {
					return this.cancelBackup(backupFile);
				}
			}

			//backup distribution lists and messages
			if (this.config.backupDistributionLists()) {
				if (!this.backupDistributionListsAndMessages(config, zipOutputStream)) {
					return this.cancelBackup(backupFile);
				}
			}

			if (this.config.backupBallots()) {
				if (!this.backupBallots(config, zipOutputStream)) {
					return this.cancelBackup(backupFile);
				}
			}

			backupSuccess = true;
			onFinished("");
		} catch (final Exception e) {
			removeBackupFile(backupFile);

			backupSuccess = false;
			onFinished("Error: " + e.getMessage());

			logger.error("Exception", e);
		}
		return backupSuccess;
	}

	private boolean next(String subject) {
		return this.next(subject, 1);
	}

	private boolean next(String subject, int factor) {
		this.currentProgressStep += (this.currentProgressStep < this.processSteps ? factor : 0);
		this.handleProgress();
		return !isCanceled;
	}

	/**
	 * only call progress on 100 steps
	 */
	private void handleProgress() {
		int p = (int) (100d / (double) this.processSteps * (double) this.currentProgressStep);
		if (p > this.latestPercentStep) {
			this.latestPercentStep = p;
			updatePersistentNotification(latestPercentStep, 100);
		}
	}

	private void removeBackupFile(DocumentFile zipFile) {
		//remove zip file
		if (zipFile != null && zipFile.exists()) {
			logger.debug( "remove " + zipFile.getUri());
			zipFile.delete();
		}
	}

	private boolean cancelBackup(DocumentFile zipFile) {
		removeBackupFile(zipFile);
		backupSuccess = false;
		onFinished(null);

		return false;
	}

	private void initProgress(long steps) {
		this.currentProgressStep = 0;
		this.processSteps = steps;
		this.latestPercentStep = 0;
		this.startTime = System.currentTimeMillis();
		this.handleProgress();
	}

	/**
	 * Create a Backup of all contacts and messages.
	 * Backup media if configured.
	 */
	private boolean backupContactsAndMessages(
		@NonNull BackupRestoreDataConfig config,
		@NonNull ZipOutputStream zipOutputStream
	) throws ThreemaException, IOException {
		// first, save my own profile pic
		if (this.config.backupAvatars()) {
			try {
				ZipUtil.addZipStream(
					zipOutputStream,
					this.fileService.getContactAvatarStream(contactService.getMe()),
					Tags.CONTACT_AVATAR_FILE_PREFIX + Tags.CONTACT_AVATAR_FILE_SUFFIX_ME,
					false
				);
			} catch (IOException e) {
				logger.warn("Could not back up own avatar: {}", e.getMessage());
			}
		}

		final String[] contactCsvHeader = {
			Tags.TAG_CONTACT_IDENTITY,
			Tags.TAG_CONTACT_PUBLIC_KEY,
			Tags.TAG_CONTACT_VERIFICATION_LEVEL,
			Tags.TAG_CONTACT_ANDROID_CONTACT_ID,
			Tags.TAG_CONTACT_THREEMA_ANDROID_CONTACT_ID,
			Tags.TAG_CONTACT_FIRST_NAME,
			Tags.TAG_CONTACT_LAST_NAME,
			Tags.TAG_CONTACT_NICK_NAME,
			Tags.TAG_CONTACT_HIDDEN,
			Tags.TAG_CONTACT_ARCHIVED,
			Tags.TAG_CONTACT_FORWARD_SECURITY,
			Tags.TAG_CONTACT_IDENTITY_ID
		};
		final String[] messageCsvHeader = {
			Tags.TAG_MESSAGE_API_MESSAGE_ID,
			Tags.TAG_MESSAGE_UID,
			Tags.TAG_MESSAGE_IS_OUTBOX,
			Tags.TAG_MESSAGE_IS_READ,
			Tags.TAG_MESSAGE_IS_SAVED,
			Tags.TAG_MESSAGE_MESSAGE_STATE,
			Tags.TAG_MESSAGE_POSTED_AT,
			Tags.TAG_MESSAGE_CREATED_AT,
			Tags.TAG_MESSAGE_MODIFIED_AT,
			Tags.TAG_MESSAGE_TYPE,
			Tags.TAG_MESSAGE_BODY,
			Tags.TAG_MESSAGE_IS_STATUS_MESSAGE,
			Tags.TAG_MESSAGE_IS_QUEUED,
			Tags.TAG_MESSAGE_CAPTION,
			Tags.TAG_MESSAGE_QUOTED_MESSAGE_ID,
			Tags.TAG_MESSAGE_DELIVERED_AT,
			Tags.TAG_MESSAGE_READ_AT,
			Tags.TAG_GROUP_MESSAGE_STATES
		};

		// Iterate over all contacts. Then backup every contact with the corresponding messages.
		try (final ByteArrayOutputStream contactBuffer = new ByteArrayOutputStream()) {
			try (final CSVWriter contactCsv = new CSVWriter(new OutputStreamWriter(contactBuffer), contactCsvHeader)) {
				for (final ContactModel contactModel : contactService.find(null)) {
					if (!this.next("backup contact " + contactModel.getIdentity())) {
						return false;
					}

					String identityId = getFormattedUniqueId();

					// Write contact
					contactCsv.createRow()
						.write(Tags.TAG_CONTACT_IDENTITY, contactModel.getIdentity())
						.write(Tags.TAG_CONTACT_PUBLIC_KEY, Utils.byteArrayToHexString(contactModel.getPublicKey()))
						.write(Tags.TAG_CONTACT_VERIFICATION_LEVEL, contactModel.getVerificationLevel().toString())
						.write(Tags.TAG_CONTACT_ANDROID_CONTACT_ID, contactModel.getAndroidContactLookupKey())
						.write(Tags.TAG_CONTACT_THREEMA_ANDROID_CONTACT_ID, contactModel.getThreemaAndroidContactId())
						.write(Tags.TAG_CONTACT_FIRST_NAME, contactModel.getFirstName())
						.write(Tags.TAG_CONTACT_LAST_NAME, contactModel.getLastName())
						.write(Tags.TAG_CONTACT_NICK_NAME, contactModel.getPublicNickName())
						.write(Tags.TAG_CONTACT_HIDDEN, contactModel.isHidden())
						.write(Tags.TAG_CONTACT_ARCHIVED, contactModel.isArchived())
						.write(Tags.TAG_CONTACT_FORWARD_SECURITY, contactModel.isForwardSecurityEnabled())
						.write(Tags.TAG_CONTACT_IDENTITY_ID, identityId)
						.write();

					// Back up contact profile pictures
					if (this.config.backupAvatars()) {
						try {
							if (!userService.getIdentity().equals(contactModel.getIdentity())) {
								ZipUtil.addZipStream(
									zipOutputStream,
									this.fileService.getContactAvatarStream(contactModel),
									Tags.CONTACT_AVATAR_FILE_PREFIX + identityId,
									false
								);
							}
						} catch (IOException e) {
							// avatars are not THAT important, so we don't care if adding them fails
							logger.warn("Could not back up avatar for contact {}: {}", contactModel.getIdentity(), e.getMessage());
						}

						try {
							ZipUtil.addZipStream(
								zipOutputStream,
								this.fileService.getContactPhotoStream(contactModel),
								Tags.CONTACT_PROFILE_PIC_FILE_PREFIX + identityId,
								false
							);
						} catch (IOException e) {
							// profile pics are not THAT important, so we don't care if adding them fails
							logger.warn("Could not back up profile pic for contact {}: {}", contactModel.getIdentity(), e.getMessage());
						}
					}

					// Back up conversations
					try (final ByteArrayOutputStream messageBuffer = new ByteArrayOutputStream()) {
						try (final CSVWriter messageCsv = new CSVWriter(new OutputStreamWriter(messageBuffer), messageCsvHeader)) {

							List<MessageModel> messageModels = this.databaseServiceNew
								.getMessageModelFactory()
								.getByIdentityUnsorted(contactModel.getIdentity());

							for (MessageModel messageModel : messageModels) {
								if (!this.next("backup message " + messageModel.getId())) {
									return false;
								}

								String apiMessageId = messageModel.getApiMessageId();

								if ((apiMessageId != null && apiMessageId.length() > 0) || messageModel.getType() == MessageType.VOIP_STATUS) {
									messageCsv.createRow()
										.write(Tags.TAG_MESSAGE_API_MESSAGE_ID, messageModel.getApiMessageId())
										.write(Tags.TAG_MESSAGE_UID, messageModel.getUid())
										.write(Tags.TAG_MESSAGE_IS_OUTBOX, messageModel.isOutbox())
										.write(Tags.TAG_MESSAGE_IS_READ, messageModel.isRead())
										.write(Tags.TAG_MESSAGE_IS_SAVED, messageModel.isSaved())
										.write(Tags.TAG_MESSAGE_MESSAGE_STATE, messageModel.getState())
										.write(Tags.TAG_MESSAGE_POSTED_AT, messageModel.getPostedAt())
										.write(Tags.TAG_MESSAGE_CREATED_AT, messageModel.getCreatedAt())
										.write(Tags.TAG_MESSAGE_MODIFIED_AT, messageModel.getModifiedAt())
										.write(Tags.TAG_MESSAGE_TYPE, messageModel.getType().toString())
										.write(Tags.TAG_MESSAGE_BODY, messageModel.getBody())
										.write(Tags.TAG_MESSAGE_IS_STATUS_MESSAGE, messageModel.isStatusMessage())
										.write(Tags.TAG_MESSAGE_IS_QUEUED, messageModel.isQueued())
										.write(Tags.TAG_MESSAGE_CAPTION, messageModel.getCaption())
										.write(Tags.TAG_MESSAGE_QUOTED_MESSAGE_ID, messageModel.getQuotedMessageId())
										.write(Tags.TAG_MESSAGE_DELIVERED_AT, messageModel.getDeliveredAt())
										.write(Tags.TAG_MESSAGE_READ_AT, messageModel.getReadAt())
										.write();
								}

								if (MessageUtil.hasDataFile(messageModel)) {
									this.backupMediaFile(
										config,
										zipOutputStream,
										Tags.MESSAGE_MEDIA_FILE_PREFIX,
										Tags.MESSAGE_MEDIA_THUMBNAIL_FILE_PREFIX,
										messageModel);
								}
							}
						}

						ZipUtil.addZipStream(
							zipOutputStream,
							new ByteArrayInputStream(messageBuffer.toByteArray()),
							Tags.MESSAGE_FILE_PREFIX + identityId + Tags.CSV_FILE_POSTFIX,
							true
						);
					}
				}
			}

			ZipUtil.addZipStream(
				zipOutputStream,
				new ByteArrayInputStream(contactBuffer.toByteArray()),
				Tags.CONTACTS_FILE_NAME + Tags.CSV_FILE_POSTFIX,
				true
			);
		}

		return true;
	}

	/**
	 * Backup all groups with messages and media (if configured).
	 */
	private boolean backupGroupsAndMessages(
		@NonNull BackupRestoreDataConfig config,
		@NonNull ZipOutputStream zipOutputStream
	) throws ThreemaException, IOException {
		final String[] groupCsvHeader = {
			Tags.TAG_GROUP_ID,
			Tags.TAG_GROUP_CREATOR,
			Tags.TAG_GROUP_NAME,
			Tags.TAG_GROUP_CREATED_AT,
			Tags.TAG_GROUP_MEMBERS,
			Tags.TAG_GROUP_DELETED,
			Tags.TAG_GROUP_ARCHIVED,
			Tags.TAG_GROUP_DESC,
			Tags.TAG_GROUP_DESC_TIMESTAMP,
			Tags.TAG_GROUP_UID,
		};
		final String[] groupMessageCsvHeader = {
			Tags.TAG_MESSAGE_API_MESSAGE_ID,
			Tags.TAG_MESSAGE_UID,
			Tags.TAG_MESSAGE_IDENTITY,
			Tags.TAG_MESSAGE_IS_OUTBOX,
			Tags.TAG_MESSAGE_IS_READ,
			Tags.TAG_MESSAGE_IS_SAVED,
			Tags.TAG_MESSAGE_MESSAGE_STATE,
			Tags.TAG_MESSAGE_POSTED_AT,
			Tags.TAG_MESSAGE_CREATED_AT,
			Tags.TAG_MESSAGE_MODIFIED_AT,
			Tags.TAG_MESSAGE_TYPE,
			Tags.TAG_MESSAGE_BODY,
			Tags.TAG_MESSAGE_IS_STATUS_MESSAGE,
			Tags.TAG_MESSAGE_IS_QUEUED,
			Tags.TAG_MESSAGE_CAPTION,
			Tags.TAG_MESSAGE_QUOTED_MESSAGE_ID,
			Tags.TAG_MESSAGE_DELIVERED_AT,
			Tags.TAG_MESSAGE_READ_AT,
			Tags.TAG_GROUP_MESSAGE_STATES,
		};

		final GroupService.GroupFilter groupFilter = new GroupService.GroupFilter() {
			@Override
			public boolean sortingByDate() {
				return false;
			}

			@Override
			public boolean sortingByName() {
				return false;
			}

			@Override
			public boolean sortingAscending() {
				return false;
			}

			@Override
			public boolean withDeleted() {
				return true;
			}

			@Override
			public boolean withDeserted() {
				return true;
			}
		};

		// Iterate over all groups
		try (final ByteArrayOutputStream groupBuffer = new ByteArrayOutputStream()) {
			try (final CSVWriter groupCsv = new CSVWriter(new OutputStreamWriter(groupBuffer), groupCsvHeader)) {
				for (final GroupModel groupModel : this.groupService.getAll(groupFilter)) {
					String groupUid = getFormattedUniqueId();
					groupUidMap.put(groupModel.getId(), groupUid);

					if (!this.next("backup group " + groupModel.getApiGroupId())) {
						return false;
					}

					groupCsv.createRow()
						.write(Tags.TAG_GROUP_ID, groupModel.getApiGroupId())
						.write(Tags.TAG_GROUP_CREATOR, groupModel.getCreatorIdentity())
						.write(Tags.TAG_GROUP_NAME, groupModel.getName())
						.write(Tags.TAG_GROUP_CREATED_AT, groupModel.getCreatedAt())
						.write(Tags.TAG_GROUP_MEMBERS, this.groupService.getGroupIdentities(groupModel))
						.write(Tags.TAG_GROUP_DELETED, groupModel.isDeleted())
						.write(Tags.TAG_GROUP_ARCHIVED, groupModel.isArchived())
						.write(Tags.TAG_GROUP_DESC, groupModel.getGroupDesc())
						.write(Tags.TAG_GROUP_DESC_TIMESTAMP, groupModel.getGroupDescTimestamp())
						.write(Tags.TAG_GROUP_UID, groupUid)
						.write();

					//check if the group have a photo
					if (this.config.backupAvatars()) {
						try {
							ZipUtil.addZipStream(zipOutputStream, this.fileService.getGroupAvatarStream(groupModel), Tags.GROUP_AVATAR_PREFIX + groupUid, false);
						} catch (Exception e) {
							logger.warn("Could not back up group avatar: {}", e.getMessage());
						}
					}

					// Back up group messages
					try (final ByteArrayOutputStream groupMessageBuffer = new ByteArrayOutputStream()) {
						try (final CSVWriter groupMessageCsv = new CSVWriter(new OutputStreamWriter(groupMessageBuffer), groupMessageCsvHeader)) {
							List<GroupMessageModel> groupMessageModels = this.databaseServiceNew
								.getGroupMessageModelFactory()
								.getByGroupIdUnsorted(groupModel.getId());

							for (GroupMessageModel groupMessageModel : groupMessageModels) {
								if (!this.next("backup group message " + groupMessageModel.getUid())) {
									return false;
								}

								String groupMessageStates = "";
								if (groupMessageModel.getGroupMessageStates() != null) {
									groupMessageStates = new JSONObject(groupMessageModel.getGroupMessageStates()).toString();
								}

								groupMessageCsv.createRow()
									.write(Tags.TAG_MESSAGE_API_MESSAGE_ID, groupMessageModel.getApiMessageId())
									.write(Tags.TAG_MESSAGE_UID, groupMessageModel.getUid())
									.write(Tags.TAG_MESSAGE_IDENTITY, groupMessageModel.getIdentity())
									.write(Tags.TAG_MESSAGE_IS_OUTBOX, groupMessageModel.isOutbox())
									.write(Tags.TAG_MESSAGE_IS_READ, groupMessageModel.isRead())
									.write(Tags.TAG_MESSAGE_IS_SAVED, groupMessageModel.isSaved())
									.write(Tags.TAG_MESSAGE_MESSAGE_STATE, groupMessageModel.getState())
									.write(Tags.TAG_MESSAGE_POSTED_AT, groupMessageModel.getPostedAt())
									.write(Tags.TAG_MESSAGE_CREATED_AT, groupMessageModel.getCreatedAt())
									.write(Tags.TAG_MESSAGE_MODIFIED_AT, groupMessageModel.getModifiedAt())
									.write(Tags.TAG_MESSAGE_TYPE, groupMessageModel.getType())
									.write(Tags.TAG_MESSAGE_BODY, groupMessageModel.getBody())
									.write(Tags.TAG_MESSAGE_IS_STATUS_MESSAGE, groupMessageModel.isStatusMessage())
									.write(Tags.TAG_MESSAGE_IS_QUEUED, groupMessageModel.isQueued())
									.write(Tags.TAG_MESSAGE_CAPTION, groupMessageModel.getCaption())
									.write(Tags.TAG_MESSAGE_QUOTED_MESSAGE_ID, groupMessageModel.getQuotedMessageId())
									.write(Tags.TAG_MESSAGE_DELIVERED_AT, groupMessageModel.getDeliveredAt())
									.write(Tags.TAG_MESSAGE_READ_AT, groupMessageModel.getReadAt())
									.write(Tags.TAG_GROUP_MESSAGE_STATES, groupMessageStates)
									.write();

								if (MessageUtil.hasDataFile(groupMessageModel)) {
									this.backupMediaFile(
										config,
										zipOutputStream,
										Tags.GROUP_MESSAGE_MEDIA_FILE_PREFIX,
										Tags.GROUP_MESSAGE_MEDIA_THUMBNAIL_FILE_PREFIX,
										groupMessageModel
									);
								}
							}
						}

						ZipUtil.addZipStream(
							zipOutputStream,
							new ByteArrayInputStream(groupMessageBuffer.toByteArray()),
							Tags.GROUP_MESSAGE_FILE_PREFIX + groupUid + Tags.CSV_FILE_POSTFIX,
							true
						);
					}
				}
			}

			ZipUtil.addZipStream(zipOutputStream, new ByteArrayInputStream(
				groupBuffer.toByteArray()),
				Tags.GROUPS_FILE_NAME + Tags.CSV_FILE_POSTFIX,
				true
			);
		}

		return true;
	}

	/**
	 * backup all ballots with votes and choices!
	 */
	private boolean backupBallots(
		@NonNull BackupRestoreDataConfig config,
		@NonNull ZipOutputStream zipOutputStream
	) throws ThreemaException, IOException {

		final String[] ballotCsvHeader = {
			Tags.TAG_BALLOT_ID,
			Tags.TAG_BALLOT_API_ID,
			Tags.TAG_BALLOT_API_CREATOR,
			Tags.TAG_BALLOT_REF,
			Tags.TAG_BALLOT_REF_ID,
			Tags.TAG_BALLOT_NAME,
			Tags.TAG_BALLOT_STATE,
			Tags.TAG_BALLOT_ASSESSMENT,
			Tags.TAG_BALLOT_TYPE,
			Tags.TAG_BALLOT_C_TYPE,
			Tags.TAG_BALLOT_LAST_VIEWED_AT,
			Tags.TAG_BALLOT_CREATED_AT,
			Tags.TAG_BALLOT_MODIFIED_AT
		};
		final String[] ballotChoiceCsvHeader = {
			Tags.TAG_BALLOT_CHOICE_ID,
			Tags.TAG_BALLOT_CHOICE_BALLOT_UID,
			Tags.TAG_BALLOT_CHOICE_API_ID,
			Tags.TAG_BALLOT_CHOICE_TYPE,
			Tags.TAG_BALLOT_CHOICE_NAME,
			Tags.TAG_BALLOT_CHOICE_VOTE_COUNT,
			Tags.TAG_BALLOT_CHOICE_ORDER,
			Tags.TAG_BALLOT_CHOICE_CREATED_AT,
			Tags.TAG_BALLOT_CHOICE_MODIFIED_AT,
		};
		final String[] ballotVoteCsvHeader = {
			Tags.TAG_BALLOT_VOTE_ID,
			Tags.TAG_BALLOT_VOTE_BALLOT_UID,
			Tags.TAG_BALLOT_VOTE_CHOICE_UID,
			Tags.TAG_BALLOT_VOTE_IDENTITY,
			Tags.TAG_BALLOT_VOTE_CHOICE,
			Tags.TAG_BALLOT_VOTE_CREATED_AT,
			Tags.TAG_BALLOT_VOTE_MODIFIED_AT,
		};

		try (
			final ByteArrayOutputStream ballotCsvBuffer = new ByteArrayOutputStream();
			final ByteArrayOutputStream ballotChoiceCsvBuffer = new ByteArrayOutputStream();
			final ByteArrayOutputStream ballotVoteCsvBuffer = new ByteArrayOutputStream()
		) {
			try (
				final OutputStreamWriter ballotOsw = new OutputStreamWriter(ballotCsvBuffer);
				final OutputStreamWriter ballotChoiceOsw = new OutputStreamWriter(ballotChoiceCsvBuffer);
				final OutputStreamWriter ballotVoteOsw = new OutputStreamWriter(ballotVoteCsvBuffer);
				final CSVWriter ballotCsv = new CSVWriter(ballotOsw, ballotCsvHeader);
				final CSVWriter ballotChoiceCsv = new CSVWriter(ballotChoiceOsw, ballotChoiceCsvHeader);
				final CSVWriter ballotVoteCsv = new CSVWriter(ballotVoteOsw, ballotVoteCsvHeader)
			) {

				List<BallotModel> ballots = ballotService.getBallots(new BallotService.BallotFilter() {
					@Override
					public MessageReceiver getReceiver() {
						return null;
					}

					@Override
					public BallotModel.State[] getStates() {
						return new BallotModel.State[]{BallotModel.State.OPEN, BallotModel.State.CLOSED};
					}

					@Override
					public boolean filter(BallotModel ballotModel) {
						return true;
					}
				});

				if (ballots != null) {
					for (BallotModel ballotModel : ballots) {
						if (!this.next("ballot " + ballotModel.getId())) {
							return false;
						}

						LinkBallotModel link = ballotService.getLinkedBallotModel(ballotModel);
						if (link == null) {
							continue;
						}

						String ref;
						String refId;
						if (link instanceof GroupBallotModel) {
							GroupModel groupModel = groupService
								.getById(((GroupBallotModel) link).getGroupId());

							if (groupModel == null) {
								logger.error("invalid group for a ballot");
								continue;
							}

							ref = "GroupBallotModel";
							refId = groupUidMap.get(groupModel.getId());
						} else if (link instanceof IdentityBallotModel) {
							ref = "IdentityBallotModel";
							refId = ((IdentityBallotModel) link).getIdentity();
						} else {
							continue;
						}

						ballotCsv.createRow()
							.write(Tags.TAG_BALLOT_ID, ballotModel.getId())
							.write(Tags.TAG_BALLOT_API_ID, ballotModel.getApiBallotId())
							.write(Tags.TAG_BALLOT_API_CREATOR, ballotModel.getCreatorIdentity())
							.write(Tags.TAG_BALLOT_REF, ref)
							.write(Tags.TAG_BALLOT_REF_ID, refId)
							.write(Tags.TAG_BALLOT_NAME, ballotModel.getName())
							.write(Tags.TAG_BALLOT_STATE, ballotModel.getState())
							.write(Tags.TAG_BALLOT_ASSESSMENT, ballotModel.getAssessment())
							.write(Tags.TAG_BALLOT_TYPE, ballotModel.getType())
							.write(Tags.TAG_BALLOT_C_TYPE, ballotModel.getChoiceType())
							.write(Tags.TAG_BALLOT_LAST_VIEWED_AT, ballotModel.getLastViewedAt())
							.write(Tags.TAG_BALLOT_CREATED_AT, ballotModel.getCreatedAt())
							.write(Tags.TAG_BALLOT_MODIFIED_AT, ballotModel.getModifiedAt())
							.write();


						final List<BallotChoiceModel> ballotChoiceModels = this.databaseServiceNew
							.getBallotChoiceModelFactory()
							.getByBallotId(ballotModel.getId());
						for (BallotChoiceModel ballotChoiceModel : ballotChoiceModels) {
							ballotChoiceCsv.createRow()
								.write(Tags.TAG_BALLOT_CHOICE_ID, ballotChoiceModel.getId())
								.write(Tags.TAG_BALLOT_CHOICE_BALLOT_UID, BackupUtils.buildBallotUid(ballotModel))
								.write(Tags.TAG_BALLOT_CHOICE_API_ID, ballotChoiceModel.getApiBallotChoiceId())
								.write(Tags.TAG_BALLOT_CHOICE_TYPE, ballotChoiceModel.getType())
								.write(Tags.TAG_BALLOT_CHOICE_NAME, ballotChoiceModel.getName())
								.write(Tags.TAG_BALLOT_CHOICE_VOTE_COUNT, ballotChoiceModel.getVoteCount())
								.write(Tags.TAG_BALLOT_CHOICE_ORDER, ballotChoiceModel.getOrder())
								.write(Tags.TAG_BALLOT_CHOICE_CREATED_AT, ballotChoiceModel.getCreatedAt())
								.write(Tags.TAG_BALLOT_CHOICE_MODIFIED_AT, ballotChoiceModel.getModifiedAt())
								.write();

						}

						final List<BallotVoteModel> ballotVoteModels = this.databaseServiceNew
							.getBallotVoteModelFactory()
							.getByBallotId(ballotModel.getId());
						for (final BallotVoteModel ballotVoteModel : ballotVoteModels) {
							BallotChoiceModel ballotChoiceModel = Functional.select(ballotChoiceModels, new IPredicateNonNull<BallotChoiceModel>() {
								@Override
								public boolean apply(@NonNull BallotChoiceModel type) {
									return type.getId() == ballotVoteModel.getBallotChoiceId();
								}
							});

							if (ballotChoiceModel == null) {
								continue;
							}

							ballotVoteCsv.createRow()
								.write(Tags.TAG_BALLOT_VOTE_ID, ballotVoteModel.getId())
								.write(Tags.TAG_BALLOT_VOTE_BALLOT_UID, BackupUtils.buildBallotUid(ballotModel))
								.write(Tags.TAG_BALLOT_VOTE_CHOICE_UID, BackupUtils.buildBallotChoiceUid(ballotChoiceModel))
								.write(Tags.TAG_BALLOT_VOTE_IDENTITY, ballotVoteModel.getVotingIdentity())
								.write(Tags.TAG_BALLOT_VOTE_CHOICE, ballotVoteModel.getChoice())
								.write(Tags.TAG_BALLOT_VOTE_CREATED_AT, ballotVoteModel.getCreatedAt())
								.write(Tags.TAG_BALLOT_VOTE_MODIFIED_AT, ballotVoteModel.getModifiedAt())
								.write();

						}
					}
				}
			}

			ZipUtil.addZipStream(
				zipOutputStream,
				new ByteArrayInputStream(ballotCsvBuffer.toByteArray()),
				Tags.BALLOT_FILE_NAME + Tags.CSV_FILE_POSTFIX,
				true
			);
			ZipUtil.addZipStream(
				zipOutputStream,
				new ByteArrayInputStream(ballotChoiceCsvBuffer.toByteArray()),
				Tags.BALLOT_CHOICE_FILE_NAME + Tags.CSV_FILE_POSTFIX,
				true
			);
			ZipUtil.addZipStream(
				zipOutputStream,
				new ByteArrayInputStream(ballotVoteCsvBuffer.toByteArray()),
				Tags.BALLOT_VOTE_FILE_NAME + Tags.CSV_FILE_POSTFIX,
				true
			);

        }

		return true;
	}

	/**
	 * Create the distribution list zip file.
	 */
	private boolean backupDistributionListsAndMessages(
		@NonNull BackupRestoreDataConfig config,
		@NonNull ZipOutputStream zipOutputStream
	) throws ThreemaException, IOException {
		final String[] distributionListCsvHeader = {
			Tags.TAG_DISTRIBUTION_LIST_ID,
			Tags.TAG_DISTRIBUTION_LIST_NAME,
			Tags.TAG_DISTRIBUTION_CREATED_AT,
			Tags.TAG_DISTRIBUTION_MEMBERS,
			Tags.TAG_DISTRIBUTION_LIST_ARCHIVED
		};
		final String[] distributionListMessageCsvHeader = {
			Tags.TAG_MESSAGE_API_MESSAGE_ID,
			Tags.TAG_MESSAGE_UID,
			Tags.TAG_MESSAGE_IDENTITY,
			Tags.TAG_MESSAGE_IS_OUTBOX,
			Tags.TAG_MESSAGE_IS_READ,
			Tags.TAG_MESSAGE_IS_SAVED,
			Tags.TAG_MESSAGE_MESSAGE_STATE,
			Tags.TAG_MESSAGE_POSTED_AT,
			Tags.TAG_MESSAGE_CREATED_AT,
			Tags.TAG_MESSAGE_MODIFIED_AT,
			Tags.TAG_MESSAGE_TYPE,
			Tags.TAG_MESSAGE_BODY,
			Tags.TAG_MESSAGE_IS_STATUS_MESSAGE,
			Tags.TAG_MESSAGE_IS_QUEUED,
			Tags.TAG_MESSAGE_CAPTION,
			Tags.TAG_MESSAGE_QUOTED_MESSAGE_ID,
			Tags.TAG_MESSAGE_DELIVERED_AT,
			Tags.TAG_MESSAGE_READ_AT
		};

		try (final ByteArrayOutputStream distributionListBuffer = new ByteArrayOutputStream()) {
			try (final CSVWriter distributionListCsv = new CSVWriter(new OutputStreamWriter(distributionListBuffer), distributionListCsvHeader)) {

				for (DistributionListModel distributionListModel : distributionListService.getAll()) {
					if (!this.next("distribution list " + distributionListModel.getId())) {
						return false;
					}
					distributionListCsv.createRow()
						.write(Tags.TAG_DISTRIBUTION_LIST_ID, distributionListModel.getId())
						.write(Tags.TAG_DISTRIBUTION_LIST_NAME, distributionListModel.getName())
						.write(Tags.TAG_DISTRIBUTION_CREATED_AT, distributionListModel.getCreatedAt())
						.write(Tags.TAG_DISTRIBUTION_MEMBERS, distributionListService.getDistributionListIdentities(distributionListModel))
						.write(Tags.TAG_DISTRIBUTION_LIST_ARCHIVED, distributionListModel.isArchived())
						.write();

					try (final ByteArrayOutputStream messageBuffer = new ByteArrayOutputStream()) {
						try (final CSVWriter distributionListMessageCsv = new CSVWriter(new OutputStreamWriter(messageBuffer), distributionListMessageCsvHeader)) {

							final List<DistributionListMessageModel> distributionListMessageModels = this.databaseServiceNew
								.getDistributionListMessageModelFactory()
								.getByDistributionListIdUnsorted(distributionListModel.getId());
							for (DistributionListMessageModel distributionListMessageModel : distributionListMessageModels) {
								String apiMessageId = distributionListMessageModel.getApiMessageId();
								if (!this.next("distribution list message " + distributionListMessageModel.getId())) {
									return false;
								}

								if (apiMessageId != null && apiMessageId.length() > 0) {
									distributionListMessageCsv.createRow()
										.write(Tags.TAG_MESSAGE_API_MESSAGE_ID, distributionListMessageModel.getApiMessageId())
										.write(Tags.TAG_MESSAGE_UID, distributionListMessageModel.getUid())
										.write(Tags.TAG_MESSAGE_IDENTITY, distributionListMessageModel.getIdentity())
										.write(Tags.TAG_MESSAGE_IS_OUTBOX, distributionListMessageModel.isOutbox())
										.write(Tags.TAG_MESSAGE_IS_READ, distributionListMessageModel.isRead())
										.write(Tags.TAG_MESSAGE_IS_SAVED, distributionListMessageModel.isSaved())
										.write(Tags.TAG_MESSAGE_MESSAGE_STATE, distributionListMessageModel.getState())
										.write(Tags.TAG_MESSAGE_POSTED_AT, distributionListMessageModel.getPostedAt())
										.write(Tags.TAG_MESSAGE_CREATED_AT, distributionListMessageModel.getCreatedAt())
										.write(Tags.TAG_MESSAGE_MODIFIED_AT, distributionListMessageModel.getModifiedAt())
										.write(Tags.TAG_MESSAGE_TYPE, distributionListMessageModel.getType())
										.write(Tags.TAG_MESSAGE_BODY, distributionListMessageModel.getBody())
										.write(Tags.TAG_MESSAGE_IS_STATUS_MESSAGE, distributionListMessageModel.isStatusMessage())
										.write(Tags.TAG_MESSAGE_IS_QUEUED, distributionListMessageModel.isQueued())
										.write(Tags.TAG_MESSAGE_CAPTION, distributionListMessageModel.getCaption())
										.write(Tags.TAG_MESSAGE_QUOTED_MESSAGE_ID, distributionListMessageModel.getQuotedMessageId())
										.write(Tags.TAG_MESSAGE_DELIVERED_AT, distributionListMessageModel.getDeliveredAt())
										.write(Tags.TAG_MESSAGE_READ_AT, distributionListMessageModel.getReadAt())
										.write();
								}

								switch (distributionListMessageModel.getType()) {
									case VIDEO:
									case VOICEMESSAGE:
									case IMAGE:
										this.backupMediaFile(
											config,
											zipOutputStream,
											Tags.DISTRIBUTION_LIST_MESSAGE_MEDIA_FILE_PREFIX,
											Tags.DISTRIBUTION_LIST_MESSAGE_MEDIA_THUMBNAIL_FILE_PREFIX,
											distributionListMessageModel
										);
								}
							}
						}

						ZipUtil.addZipStream(
							zipOutputStream,
							new ByteArrayInputStream(messageBuffer.toByteArray()),
							Tags.DISTRIBUTION_LIST_MESSAGE_FILE_PREFIX + distributionListModel.getId() + Tags.CSV_FILE_POSTFIX,
							true
						);
					}
				}
			}

			ZipUtil.addZipStream(
				zipOutputStream,
				new ByteArrayInputStream(distributionListBuffer.toByteArray()),
				Tags.DISTRIBUTION_LISTS_FILE_NAME + Tags.CSV_FILE_POSTFIX,
				true
			);
		}

		return true;
	}


	/**
	 * Backup all media files of the given AbstractMessageModel
	 */
	private boolean backupMediaFile(
		@NonNull BackupRestoreDataConfig config,
	    ZipOutputStream zipOutputStream,
	    String filePrefix,
	    String thumbnailFilePrefix,
	    AbstractMessageModel messageModel
	) {
		if (messageModel == null || !MessageUtil.hasDataFile(messageModel)) {
			//its not a message model or a media message model
			return false;
		}

		if (!this.next("media " + messageModel.getId(), getStepFactor())) {
			return false;
		}

		try {
			boolean saveMedia = false;
			boolean saveThumbnail = true;

			switch (messageModel.getType()) {
				case IMAGE:
					saveMedia = config.backupMedia();
					// image thumbnails will be generated again on restore - no need to save
					saveThumbnail = !saveMedia;
					break;
				case VIDEO:
					if (config.backupVideoAndFiles()) {
						VideoDataModel videoDataModel = messageModel.getVideoData();
						saveMedia = videoDataModel != null && videoDataModel.isDownloaded();
					}
					break;
				case VOICEMESSAGE:
					if (config.backupMedia()) {
						AudioDataModel audioDataModel = messageModel.getAudioData();
						saveMedia = audioDataModel != null && audioDataModel.isDownloaded();
					}
					break;
				case FILE:
					if (config.backupVideoAndFiles()) {
						FileDataModel fileDataModel = messageModel.getFileData();
						saveMedia = fileDataModel != null && fileDataModel.isDownloaded();
					}
					break;
				default:
					return false;
			}

			if (saveMedia) {
				InputStream is = this.fileService.getDecryptedMessageStream(messageModel);
				if (is != null) {
					ZipUtil.addZipStream(zipOutputStream, is, filePrefix + messageModel.getUid(), false);
				} else {
					logger.debug( "Can't add media for message " + messageModel.getUid() + " (" + messageModel.getPostedAt().toString() + "): missing file");
					// try to save thumbnail if media is missing
					saveThumbnail = true;
				}
			}

			if (config.backupThumbnails() && saveThumbnail) {
				//save thumbnail every time (if a thumbnail exists)
				InputStream is = this.fileService.getDecryptedMessageThumbnailStream(messageModel);
				if (is != null) {
					ZipUtil.addZipStream(zipOutputStream, is, thumbnailFilePrefix + messageModel.getUid(), false);
				}
			}

			return true;
		} catch (Exception x) {
			//do not abort, its only a media :-)
			logger.debug( "Can't add media for message " + messageModel.getUid() + " (" + messageModel.getPostedAt().toString() + "): " + x.getMessage());
			return false;
		}
	}

	public void onFinished(@Nullable String message) {
		if (TextUtils.isEmpty(message)) {
			logger.debug("onFinished (success={})", backupSuccess);
		} else {
			logger.debug("onFinished (success={}): {}", backupSuccess, message);
		}

		cancelPersistentNotification();

		if (backupSuccess) {
			// hacky, hacky: delay success notification for a few seconds to allow file system to settle.
			SystemClock.sleep(FILE_SETTLE_DELAY);

			if (backupFile != null) {
				// Rename to reflect that the backup has been completed successfully
				final String filename = backupFile.getName();
				if (filename != null && backupFile.renameTo(filename.replace(INCOMPLETE_BACKUP_FILENAME_PREFIX, ""))) {
					// make sure media scanner sees this file
					logger.debug("Sending media scanner broadcast");
					sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, backupFile.getUri()));

					// Completed successfully!
					preferenceService.setLastDataBackupDate(new Date());
					showBackupSuccessNotification();
				} else {
					logger.error("Backup failed: File could not be renamed");
					showBackupErrorNotification(null);
				}
			} else {
				logger.error("Backup failed: File does not exist");
				showBackupErrorNotification(null);
			}
		} else {
			logger.error("Backup failed: {}", message);
			showBackupErrorNotification(message);
		}

		//try to reopen connection
		try {
			if (serviceManager != null) {
				serviceManager.startConnection();
			}
		} catch (Exception e) {
			logger.error("Exception", e);
		}

		if (wakeLock != null && wakeLock.isHeld()) {
			logger.debug("Releasing wakelock");
			wakeLock.release();
		}

		stopForeground(true);

		isRunning = false;

		//ConfigUtils.scheduleAppRestart(getApplicationContext(), getApplicationContext().getResources().getString(R.string.ipv6_restart_now));
		stopSelf();
	}

	private void showPersistentNotification() {
		logger.debug( "showPersistentNotification");

		Intent cancelIntent = new Intent(this, BackupService.class);
		cancelIntent.putExtra(EXTRA_ID_CANCEL, true);
		PendingIntent cancelPendingIntent;
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			cancelPendingIntent = PendingIntent.getForegroundService(this, (int) System.currentTimeMillis(), cancelIntent, PendingIntent.FLAG_UPDATE_CURRENT | PENDING_INTENT_FLAG_IMMUTABLE);
		} else {
			cancelPendingIntent = PendingIntent.getService(this, (int) System.currentTimeMillis(), cancelIntent, PendingIntent.FLAG_UPDATE_CURRENT | PENDING_INTENT_FLAG_IMMUTABLE);
		}

		notificationBuilder = new NotificationBuilderWrapper(this, NOTIFICATION_CHANNEL_BACKUP_RESTORE_IN_PROGRESS, null)
					.setContentTitle(getString(R.string.backup_in_progress))
					.setContentText(getString(R.string.please_wait))
					.setOngoing(true)
					.setSmallIcon(R.drawable.ic_notification_small)
					.setPriority(NotificationCompat.PRIORITY_DEFAULT)
					.addAction(R.drawable.ic_close_white_24dp, getString(R.string.cancel), cancelPendingIntent);

		Notification notification = notificationBuilder.build();

		startForeground(BACKUP_NOTIFICATION_ID, notification);
	}

	private void updatePersistentNotification(int currentStep, int steps) {
		logger.debug( "updatePersistentNoti " + currentStep + " of " + steps);

		if (currentStep != 0) {
			final long millisPassed = System.currentTimeMillis() - startTime;
			final long millisRemaining = millisPassed * steps / currentStep - millisPassed + FILE_SETTLE_DELAY;
			String timeRemaining = StringConversionUtil.secondsToString(millisRemaining / DateUtils.SECOND_IN_MILLIS, false);
			notificationBuilder.setContentText(String.format(getString(R.string.time_remaining), timeRemaining));
		}

		notificationBuilder.setProgress(steps, currentStep, false);

		if (notificationManager != null) {
			notificationManager.notify(BACKUP_NOTIFICATION_ID, notificationBuilder.build());
		}
	}

	private void cancelPersistentNotification() {
		if (notificationManager != null) {
			notificationManager.cancel(BACKUP_NOTIFICATION_ID);
		}
	}

	private void showBackupErrorNotification(String message) {
		String contentText;

		if (!TestUtil.empty(message)) {
			contentText = message;
		} else {
			contentText = getString(R.string.backup_or_restore_error_body);
		}

		Intent backupIntent = new Intent(this, HomeActivity.class);
		PendingIntent pendingIntent = PendingIntent.getActivity(this, (int)System.currentTimeMillis(), backupIntent, PendingIntent.FLAG_UPDATE_CURRENT | PENDING_INTENT_FLAG_IMMUTABLE);

		NotificationCompat.Builder builder =
				new NotificationBuilderWrapper(this, NOTIFICATION_CHANNEL_ALERT, null)
						.setSmallIcon(R.drawable.ic_notification_small)
						.setTicker(getString(R.string.backup_or_restore_error_body))
						.setContentTitle(getString(R.string.backup_or_restore_error))
						.setContentText(contentText)
						.setContentIntent(pendingIntent)
						.setDefaults(Notification.DEFAULT_LIGHTS|Notification.DEFAULT_SOUND|Notification.DEFAULT_VIBRATE)
						.setColor(getResources().getColor(R.color.material_red))
						.setPriority(NotificationCompat.PRIORITY_MAX)
						.setStyle(new NotificationCompat.BigTextStyle().bigText(contentText))
						.setAutoCancel(false);

		if (notificationManager != null) {
			notificationManager.notify(BACKUP_COMPLETION_NOTIFICATION_ID, builder.build());
		} else {
			RuntimeUtil.runOnUiThread(
				() -> Toast.makeText(getApplicationContext(), R.string.backup_or_restore_error_body, Toast.LENGTH_LONG).show()
			);
		}
	}

	private void showBackupSuccessNotification() {
		logger.debug( "showBackupSuccess");

		String text;

		Intent backupIntent = new Intent(this, HomeActivity.class);
		PendingIntent pendingIntent = PendingIntent.getActivity(this, (int)System.currentTimeMillis(), backupIntent, PendingIntent.FLAG_UPDATE_CURRENT | PENDING_INTENT_FLAG_IMMUTABLE);

		NotificationCompat.Builder builder =
				new NotificationBuilderWrapper(this, NOTIFICATION_CHANNEL_ALERT, null)
						.setSmallIcon(R.drawable.ic_notification_small)
						.setTicker(getString(R.string.backup_or_restore_success_body))
						.setContentTitle(getString(R.string.app_name))
						.setContentIntent(pendingIntent)
						.setDefaults(Notification.DEFAULT_LIGHTS|Notification.DEFAULT_SOUND|Notification.DEFAULT_VIBRATE)
						.setColor(getResources().getColor(R.color.material_green))
						.setPriority(NotificationCompat.PRIORITY_MAX)
						.setAutoCancel(true);

		if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
			// Android Q does not allow restart in the background
			text = getString(R.string.backup_or_restore_success_body) + "\n" + getString(R.string.tap_to_start, getString(R.string.app_name));
		} else {
			text = getString(R.string.backup_or_restore_success_body);
		}

		builder.setContentText(text);
		builder.setStyle(new NotificationCompat.BigTextStyle().bigText(text));

		if (notificationManager == null) {
			notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
		}

		if (notificationManager != null) {
			notificationManager.notify(BACKUP_COMPLETION_NOTIFICATION_ID, builder.build());
		} else {
			RuntimeUtil.runOnUiThread(new Runnable() {
				@Override
				public void run() {
					Toast.makeText(getApplicationContext(), R.string.backup_or_restore_success_body, Toast.LENGTH_LONG).show();
				}
			});
		}
	}

	/**
	 * Show a fake notification before stopping service in order to prevent Context.startForegroundService() did not then call Service.startForeground() crash
	 */
	private void safeStopSelf() {
		Notification notification = new NotificationBuilderWrapper(this, NOTIFICATION_CHANNEL_BACKUP_RESTORE_IN_PROGRESS, null)
			.setContentTitle("")
			.setContentText("").
				build();

		startForeground(BACKUP_NOTIFICATION_ID, notification);
		stopForeground(true);
		isRunning = false;
		stopSelf();
	}

	/**
	 * Return a string representation of the next value in randomIterator
	 * @return a 10 character string
	 */
	@NonNull
	private String getFormattedUniqueId() {
		return String.format(Locale.US, "%010d", randomIterator.next());
	}
}



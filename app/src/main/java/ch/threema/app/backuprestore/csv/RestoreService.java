/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2014-2025 Threema GmbH
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

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentCallbacks2;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.StrictMode;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.text.format.Formatter;
import android.widget.Toast;

import net.lingala.zip4j.model.FileHeader;

import org.apache.commons.io.IOUtils;
import org.koin.java.KoinJavaComponent;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.stream.Collectors;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.app.ServiceCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import ch.threema.app.BuildConfig;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.activities.DummyActivity;
import ch.threema.app.home.HomeActivity;
import ch.threema.app.asynctasks.DeleteIdentityAsyncTask;
import ch.threema.app.backuprestore.MessageIdCache;
import ch.threema.app.backuprestore.ZipFileWrapper;
import ch.threema.app.collections.Functional;
import ch.threema.app.emojis.EmojiUtil;
import ch.threema.app.exceptions.RestoreCanceledException;
import ch.threema.app.managers.ServiceManager;
import ch.threema.app.notifications.NotificationChannels;
import ch.threema.app.services.ContactService;
import ch.threema.app.services.ConversationService;
import ch.threema.app.services.FileService;
import ch.threema.app.services.NotificationPreferenceService;
import ch.threema.app.preference.service.PreferenceService;
import ch.threema.app.services.UserService;
import ch.threema.app.utils.BackupUtils;
import ch.threema.app.utils.CSVReader;
import ch.threema.app.utils.CSVRow;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.Counter;
import ch.threema.app.utils.JsonUtil;
import ch.threema.app.utils.MessageUtil;
import ch.threema.app.utils.MimeUtil;
import ch.threema.app.utils.ResettableInputStream;
import ch.threema.app.utils.ElapsedTimeFormatter;
import ch.threema.app.utils.TestUtil;
import ch.threema.app.utils.ThrowingConsumer;
import ch.threema.base.ThreemaException;
import ch.threema.base.crypto.NonceFactory;
import ch.threema.base.crypto.NonceScope;
import static ch.threema.base.utils.LoggingKt.getThreemaLogger;
import ch.threema.base.utils.Utils;
import ch.threema.data.repositories.EmojiReactionsRepository;
import ch.threema.data.repositories.GroupModelRepository;
import ch.threema.data.repositories.ModelRepositories;
import ch.threema.data.storage.DbEmojiReaction;
import ch.threema.domain.models.GroupId;
import ch.threema.domain.models.VerificationLevel;
import ch.threema.domain.protocol.connection.ServerConnection;
import ch.threema.domain.protocol.csp.ProtocolDefines;
import ch.threema.storage.DatabaseService;
import ch.threema.storage.factories.ContactModelFactory;
import ch.threema.storage.factories.GroupMessageModelFactory;
import ch.threema.storage.factories.GroupModelFactory;
import ch.threema.storage.factories.MessageModelFactory;
import ch.threema.storage.models.AbstractMessageModel;
import ch.threema.storage.models.ContactModel;
import ch.threema.storage.models.ContactModel.AcquaintanceLevel;
import ch.threema.storage.models.DistributionListMemberModel;
import ch.threema.storage.models.DistributionListMessageModel;
import ch.threema.storage.models.DistributionListModel;
import ch.threema.storage.models.GroupMemberModel;
import ch.threema.storage.models.GroupMessageModel;
import ch.threema.storage.models.GroupModel;
import ch.threema.storage.models.GroupModel.UserState;
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

import static android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC;
import static ch.threema.storage.models.GroupModel.UserState.KICKED;
import static ch.threema.storage.models.GroupModel.UserState.LEFT;
import static ch.threema.storage.models.GroupModel.UserState.MEMBER;

public class RestoreService extends Service implements ComponentCallbacks2 {
    private static final Logger logger = getThreemaLogger("RestoreService");

    public static final String RESTORE_PROGRESS_INTENT = "restore_progress_intent";
    public static final String RESTORE_PROGRESS = "restore_progress";
    public static final String RESTORE_PROGRESS_STEPS = "restore_progress_steps";
    public static final String RESTORE_PROGRESS_MESSAGE = "restore_progress_message";
    public static final String RESTORE_PROGRESS_ERROR_MESSAGE = "restore_progress_error_message";

    public static final String EXTRA_RESTORE_BACKUP_FILE = "file";
    public static final String EXTRA_RESTORE_BACKUP_PASSWORD = "pwd";
    private static final int MAX_THUMBNAIL_SIZE_BYTES = 5 * 1024 * 1024; // do not restore thumbnails that are bigger than 5 MB

    private static final int FG_SERVICE_TYPE = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE ? FOREGROUND_SERVICE_TYPE_DATA_SYNC : 0;

    private ServiceManager serviceManager;
    private ContactService contactService;
    private ConversationService conversationService;
    private FileService fileService;
    private UserService userService;
    private DatabaseService databaseService;
    private ModelRepositories modelRepositories;
    private PreferenceService preferenceService;
    private NotificationPreferenceService notificationPreferenceService;
    private PowerManager.WakeLock wakeLock;
    private NotificationManagerCompat notificationManagerCompat;
    private ActivityManager activityManager;
    private NonceFactory nonceFactory;
    private @NonNull GroupModelRepository groupModelRepository;

    private NotificationCompat.Builder notificationBuilder;

    private static final int RESTORE_NOTIFICATION_ID = 981772;
    public static final int RESTORE_COMPLETION_NOTIFICATION_ID = 981773;
    private static final String EXTRA_ID_CANCEL = "cnc";

    private final HashMap<String, String> identityIdMap = new HashMap<>();
    private final HashSet<String> identitiesSet = new HashSet<>();
    private final HashMap<String, Integer> groupUidMap = new HashMap<>();

    private long currentProgressStep = 0;
    private long progressSteps = 0;
    private int latestPercentStep = -1;
    private long startTime = 0;

    private static boolean restoreSuccess = false;

    private ZipFileWrapper zipFileWrapper;
    private String password;

    private static final int STEP_SIZE_PREPARE = 100;
    private static final int STEP_SIZE_IDENTITY = 100;
    private static final int STEP_SIZE_MAIN_FILES = 200;
    private static final int STEP_SIZE_MESSAGES = 1; // per message
    private static final int STEP_SIZE_GROUP_AVATARS = 50;
    private static final int STEP_SIZE_MEDIA = 25; // per media file
    private static final int NONCES_PER_STEP = 50;
    private static final int NONCES_CHUNK_SIZE = 10_000;
    private static final int REACTIONS_PER_STEP = 25;
    private static final int REACTIONS_STEP_THRESHOLD = 250;

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
        logger.debug("onStartCommand flags = {} startId {}", flags, startId);
        ServiceCompat.startForeground(
            this,
            RESTORE_NOTIFICATION_ID,
            getPersistentNotification(),
            FG_SERVICE_TYPE
        );

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
                        logger.info("Size of backup file: {}", formatFileSize(file.length()));
                        zipFileWrapper = new ZipFileWrapper(file, password);
                        if (!zipFileWrapper.isValidZipFile()) {
                            logger.warn("ZIP file might be invalid, restore might fail");
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
            logger.warn("onStartCommand intent == null");
            // The term "empty-intent" isn't meaningful to the user, but it is so infamous
            // that it is known internally (to support and devs), which is why we keep it
            // in the error message, allowing us to more easily identify it in bug reports.
            onFinished(getString(R.string.restore_error_body) + " (error code: empty-intent)");
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
            databaseService = serviceManager.getDatabaseService();
            modelRepositories = serviceManager.getModelRepositories();
            contactService = serviceManager.getContactService();
            conversationService = serviceManager.getConversationService();
            userService = serviceManager.getUserService();
            preferenceService = serviceManager.getPreferenceService();
            notificationPreferenceService = KoinJavaComponent.get(NotificationPreferenceService.class);
            nonceFactory = serviceManager.getNonceFactory();
            groupModelRepository = serviceManager.getModelRepositories().getGroups();
        } catch (Exception e) {
            logger.error("Could not instantiate all required services", e);
            stopSelf();
            return;
        }

        notificationManagerCompat = NotificationManagerCompat.from(this);
        activityManager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
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
        logger.warn("onLowMemory");
    }

    @Override
    public void onTrimMemory(int level) {
        logger.warn("onTrimMemory({})", level);
        logMemoryStatus();
    }

    private void logMemoryStatus() {
        if (activityManager != null && logger.isInfoEnabled()) {
            ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
            activityManager.getMemoryInfo(memoryInfo);
            logger.info(
                "Memory status: available={}, total={}, threshold={}, low={}",
                formatFileSize(memoryInfo.availMem),
                formatFileSize(memoryInfo.totalMem),
                formatFileSize(memoryInfo.threshold),
                memoryInfo.lowMemory
            );
        }
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        logger.info("onTaskRemoved");

        Intent intent = new Intent(this, DummyActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    /**
     * CSV file processor
     * <p>
     * The {@link #row(CSVRow)} method will be called for every row in the CSV file.
     */
    private interface ProcessCsvFile {
        void row(@NonNull CSVRow row) throws RestoreCanceledException;
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
            final ServerConnection connection = serviceManager.getConnection();
            if (connection.isRunning()) {
                connection.stop();
            }

            // We use two passes for a restore. The first pass only scans the files in the backup,
            // but does not write to the database. In the second pass, the files are actually written.
            for (int nTry = 0; nTry < 2; nTry++) {
                logger.info("Attempt {}", nTry + 1);
                logMemoryStatus();
                if (nTry > 0) {
                    this.writeToDb = true;
                    this.initProgress(stepSizeTotal);
                }

                this.identityIdMap.clear();
                this.identitiesSet.clear();
                this.groupUidMap.clear();
                this.ballotIdMap.clear();
                this.ballotOldIdMap.clear();
                this.ballotChoiceIdMap.clear();
                this.distributionListIdMap.clear();

                if (this.writeToDb) {
                    updateProgress(STEP_SIZE_PREPARE);

                    // clear tables!!
                    logger.info("Clearing current tables");
                    databaseService.getMessageModelFactory().deleteAll();
                    databaseService.getContactModelFactory().deleteAll();
                    databaseService.getGroupMessageModelFactory().deleteAll();
                    databaseService.getGroupMemberModelFactory().deleteAll();
                    databaseService.getGroupModelFactory().deleteAll();
                    databaseService.getDistributionListMessageModelFactory().deleteAll();
                    databaseService.getDistributionListMemberModelFactory().deleteAll();
                    databaseService.getDistributionListModelFactory().deleteAll();
                    databaseService.getBallotModelFactory().deleteAll();
                    databaseService.getBallotVoteModelFactory().deleteAll();
                    databaseService.getBallotChoiceModelFactory().deleteAll();
                    databaseService.getOutgoingGroupSyncRequestLogModelFactory().deleteAll();
                    databaseService.getIncomingGroupSyncRequestLogModelFactory().deleteAll();

                    modelRepositories.getEmojiReaction().deleteAllReactions();
                    // TODO(ANDR-3207): delete all edit history entries

                    logger.info("Deleting current media files");
                    fileService.deleteMediaFiles();
                }

                List<FileHeader> fileHeaders = zipFileWrapper.getFileHeaders();

                // The restore settings file contains the data backup format version
                this.restoreSettings = getRestoreSettings(fileHeaders);

                if (restoreSettings.isUnsupportedVersion()) {
                    logger.error(
                        "Backup version {} is higher than supported version {}",
                        restoreSettings.getVersion(),
                        RestoreSettings.CURRENT_VERSION
                    );
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
                    try (InputStream inputStream = getZipFileInputStream(identityHeader)) {
                        identityContent = IOUtils.toString(inputStream, Charset.defaultCharset());
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
                int nonceCount = restoreNonces(fileHeaders);

                // contacts, groups and distribution lists
                logger.info("Restoring main files (contacts, groups, distribution lists)");
                if (!this.restoreMainFiles(fileHeaders)) {
                    logger.error("restore main files failed");
                    // continue anyway!
                }

                updateProgress(STEP_SIZE_MAIN_FILES);

                logger.info("Restoring message files");
                long messageCount = this.restoreMessageFiles(fileHeaders);
                if (messageCount == 0) {
                    logger.error("restore message files failed");
                    //continue anyway!
                }

                logger.info("Restoring group avatar files");
                if (!this.restoreGroupAvatarFiles(fileHeaders)) {
                    logger.error("restore group avatar files failed");
                    // continue anyway!
                }

                updateProgress(STEP_SIZE_GROUP_AVATARS);

                // The media files are the most memory hungry, so we log the memory status before and after
                logMemoryStatus();
                logger.info("Restoring message media files");
                long mediaCount = this.restoreMessageMediaFiles(fileHeaders);
                if (mediaCount == 0) {
                    logger.warn("No media files restored. Might be a backup without media?");
                    //continue anyway!
                } else {
                    logger.info("{} media files found", mediaCount);
                }
                if (writeToDb) {
                    logMemoryStatus();
                }

                // restore all avatars
                logger.info("Restoring avatars");
                if (!this.restoreContactAvatars(fileHeaders)) {
                    logger.error("restore contact avatar files failed");
                    // continue anyway!
                }

                // Reset the profile pic upload so that the own profile picture is redistributed
                preferenceService.setProfilePicUploadData(null);

                // If we're restoring a backup that does not yet contain lastUpdate (version <22),
                // calculate lastUpdate ourselves based on restored data.
                if (restoreSettings.getVersion() < 22) {
                    this.conversationService.calculateLastUpdateForAllConversations();
                }

                long stepsRestoreReactions = this.restoreReactions(fileHeaders);

                if (!writeToDb) {
                    stepSizeTotal += (messageCount * STEP_SIZE_MESSAGES);
                    stepSizeTotal += (mediaCount * STEP_SIZE_MEDIA);
                    stepSizeTotal += (long) Math.ceil((double) nonceCount / NONCES_PER_STEP);
                    stepSizeTotal += stepsRestoreReactions;
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
        } catch (IOException e) {
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
            InputStream is = getZipFileInputStream(settingsHeader);
            InputStreamReader inputStreamReader = new InputStreamReader(is);
            CSVReader csvReader = new CSVReader(inputStreamReader, false)
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
                final String fileNameWithoutExtension = fileName.substring(0, fileName.length() - Tags.CSV_FILE_POSTFIX.length());
                switch (fileNameWithoutExtension) {
                    case Tags.CONTACTS_FILE_NAME:
                        if (!this.restoreContactFile(fileHeader)) {
                            logger.error("restore contact file failed");
                            return false;
                        }
                        break;
                    case Tags.GROUPS_FILE_NAME:
                        if (!this.restoreGroupFile(fileHeader)) {
                            logger.error("restore group file failed");
                        }
                        break;
                    case Tags.DISTRIBUTION_LISTS_FILE_NAME:
                        if (!this.restoreDistributionListFile(fileHeader)) {
                            logger.error("restore distribution list file failed");
                        }
                        break;
                    case Tags.BALLOT_FILE_NAME:
                        ballotMain = fileHeader;
                        break;
                    case Tags.BALLOT_CHOICE_FILE_NAME:
                        ballotChoice = fileHeader;
                        break;
                    case Tags.BALLOT_VOTE_FILE_NAME:
                        ballotVote = fileHeader;
                        break;
                }
            }
        }

        if (TestUtil.required(ballotMain, ballotChoice, ballotVote)) {
            this.restoreBallotFile(ballotMain, ballotChoice, ballotVote);
        }

        return true;
    }

    /**
     * Attempt to restore the nonces. If restoring of nonces fails for some reason 0 is returned.
     * Since we continue anyway, there is no need to distinguish between zero restored nonces and
     * a failure.
     */
    private int restoreNonces(List<FileHeader> fileHeaders) throws IOException, RestoreCanceledException {
        if (!writeToDb) {
            // If not writing to the database only the count of nonces is required.
            // Try to read optional nonces count file if present in backup.
            logger.info("Get nonce counts");
            int nonceCount = readNonceCounts(fileHeaders);
            if (nonceCount >= 0) {
                // If the nonce count is available return it and skip reading the whole nonces file.
                logger.info("{} nonces in backup", nonceCount);
                return nonceCount;
            } else {
                logger.info("Count nonces in backup.");
            }
        }

        int nonceCountCsp = restoreNonces(
            NonceScope.CSP,
            Tags.NONCE_FILE_NAME_CSP + Tags.CSV_FILE_POSTFIX,
            fileHeaders
        );

        int nonceCountD2d = restoreNonces(
            NonceScope.D2D,
            Tags.NONCE_FILE_NAME_D2D + Tags.CSV_FILE_POSTFIX,
            fileHeaders
        );

        int remainingCsp = BackupUtils.calcRemainingNoncesProgress(NONCES_CHUNK_SIZE, NONCES_PER_STEP, nonceCountCsp);
        int remainingD2d = BackupUtils.calcRemainingNoncesProgress(NONCES_CHUNK_SIZE, NONCES_PER_STEP, nonceCountD2d);
        int remainingNonceProgress = remainingCsp + remainingD2d;
        logger.debug("Remaining nonce progress: {}", remainingNonceProgress);
        updateProgress((long) Math.ceil((double) remainingNonceProgress / NONCES_PER_STEP));

        return nonceCountCsp + nonceCountD2d;
    }

    /**
     * Read the counts from the nonce counts file if available.
     *
     * @return the count, or -1 if the count could not be read from some reason.
     */
    private int readNonceCounts(List<FileHeader> fileHeaders) throws IOException {
        FileHeader nonceCountFileHeader = getFileHeader(Tags.NONCE_COUNTS_FILE + Tags.CSV_FILE_POSTFIX, fileHeaders);
        if (nonceCountFileHeader == null) {
            logger.info("No nonce count file available in backup");
            return -1;
        }
        try (InputStream inputStream = getZipFileInputStream(nonceCountFileHeader);
             InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
             CSVReader csvReader = new CSVReader(inputStreamReader, true)
        ) {
            CSVRow row = csvReader.readNextRow();
            if (row == null) {
                logger.warn("Could not read nonce count. File is empty.");
                return -1;
            }
            return row.getInteger(Tags.TAG_NONCE_COUNT_CSP) + row.getInteger(Tags.TAG_NONCE_COUNT_D2D);
        } catch (ThreemaException | NumberFormatException e) {
            logger.warn("Could not read nonce count", e);
            return -1;
        }
    }

    /**
     * Get the file header where the file name matches the provided exactFileName.
     *
     * @param exactFileName The file name that is matched against
     * @param fileHeaders   The file headers that are scanned
     * @return The first matching file header or null if none matches
     */
    @Nullable
    private FileHeader getFileHeader(@NonNull String exactFileName, List<FileHeader> fileHeaders) {
        for (FileHeader fileHeader : fileHeaders) {
            if (exactFileName.equals(fileHeader.getFileName())) {
                return fileHeader;
            }
        }
        logger.info("No file header for '{}' found", exactFileName);
        return null;
    }

    private int restoreNonces(
        @NonNull NonceScope scope,
        @NonNull String nonceBackupFile,
        @NonNull List<FileHeader> fileHeaders
    ) throws IOException, RestoreCanceledException {
        logger.info("Restore {} nonces", scope);
        final FileHeader nonceFileHeader = getFileHeader(nonceBackupFile, fileHeaders);
        if (nonceFileHeader == null) {
            logger.info("Nonce file header is null");
            return 0;
        }

        try (InputStream inputStream = getZipFileInputStream(nonceFileHeader);
             InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
             CSVReader csvReader = new CSVReader(inputStreamReader, true)
        ) {
            int nonceCount = 0;
            boolean success = true;
            CSVRow row;
            List<byte[]> nonceBytes = new ArrayList<>(NONCES_CHUNK_SIZE);
            while ((row = csvReader.readNextRow()) != null) {
                try {
                    // Note that currently there is only one nonce per row, and therefore we do
                    // not need to read them as array. However, this gives us the flexibility to
                    // backup several nonces in one row (as we have done in 5.1-alpha3)
                    String[] nonces = row.getStrings(Tags.TAG_NONCES);
                    nonceCount += nonces.length;
                    if (writeToDb) {
                        for (String nonce : nonces) {
                            nonceBytes.add(Utils.hexStringToByteArray(nonce));
                            if (nonceBytes.size() >= NONCES_CHUNK_SIZE) {
                                success &= insertNonces(scope, nonceBytes);
                                nonceBytes.clear();
                            }
                        }
                    }
                } catch (ThreemaException|NoSuchAlgorithmException|InvalidKeyException e) {
                    logger.error("Could not insert nonces", e);
                    return 0;
                }
            }
            if (!nonceBytes.isEmpty()) {
                try {
                    success &= insertNonces(scope, nonceBytes);
                } catch (NoSuchAlgorithmException|InvalidKeyException e) {
                    logger.error("Could not insert remaining nonces", e);
                    success = false;
                }
            }
            if (success) {
                logger.info("Restored {} {} nonces", nonceCount, scope);
                return nonceCount;
            } else {
                logger.warn("Restoring {} nonces was not successfull", scope);
                return 0;
            }
        }
    }

    private boolean insertNonces(
        @NonNull NonceScope scope,
        @NonNull List<byte[]> nonces
    ) throws RestoreCanceledException, NoSuchAlgorithmException, InvalidKeyException {
        logger.debug("Write {} nonces to database", nonces.size());
        boolean success = nonceFactory.insertHashedNoncesJava(scope, nonces, userService.getIdentity());
        updateProgress(nonces.size() / NONCES_PER_STEP);
        return success;
    }

    private long restoreReactions(@NonNull List<FileHeader> fileHeaders) throws Exception {
        logger.info("Restore reactions");
        FileHeader reactionCountFileHeader = getFileHeader(Tags.REACTION_COUNTS_FILE + Tags.CSV_FILE_POSTFIX, fileHeaders);
        long restoreReactionsSteps = reactionCountFileHeader != null
            ? getRestoreReactionsSteps(reactionCountFileHeader)
            : 0;

        if (writeToDb) {
            FileHeader contactReactionsFileHeader = getFileHeader(Tags.CONTACT_REACTIONS_FILE_NAME + Tags.CSV_FILE_POSTFIX, fileHeaders);
            if (contactReactionsFileHeader != null) {
                restoreContactReactions(contactReactionsFileHeader);
            }

            FileHeader groupReactionsFileHeader = getFileHeader(Tags.GROUP_REACTIONS_FILE_NAME + Tags.CSV_FILE_POSTFIX, fileHeaders);
            if (groupReactionsFileHeader != null) {
                restoreGroupReactions(groupReactionsFileHeader);
            }
        }

        return restoreReactionsSteps;
    }

    /**
     * The reaction count is only read an logged.
     * At the moment this is not used, but can later be used to improve the
     * progress calculation of the restore process.
     */
    private long getRestoreReactionsSteps(@NonNull FileHeader reactionCountFileHeader) throws IOException {
        try (InputStream inputStream = getZipFileInputStream(reactionCountFileHeader);
             InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
             CSVReader csvReader = new CSVReader(inputStreamReader, true)
        ) {
            CSVRow row = csvReader.readNextRow();
            if (row == null) {
                logger.warn("Could not read reaction counts. File is empty");
                return 0;
            }
            long contactReactionCount = row.getLong(Tags.TAG_REACTION_COUNT_CONTACTS);
            long groupReactionCount = row.getLong(Tags.TAG_REACTION_COUNT_GROUPS);
            logger.info(
                "Reactions: (contactReactionCount={}, groupReactionCount={})",
                contactReactionCount,
                groupReactionCount
            );
            return (contactReactionCount / REACTIONS_PER_STEP) + (groupReactionCount / REACTIONS_PER_STEP);
        } catch (ThreemaException | NumberFormatException e) {
            logger.warn("Could not read reaction count", e);
            return 0;
        }
    }

    private MessageIdCache<MessageIdCache.ContactMessageKey> createContactMessageIdCache() {
        MessageModelFactory messageModelFactory = databaseService.getMessageModelFactory();
        return new MessageIdCache<>(key ->
            messageModelFactory
                .getByApiMessageIdAndIdentity(key.getMessageId(), key.getContactIdentity())
                .getId()
        );
    }

    private MessageIdCache<MessageIdCache.GroupMessageKey> createGroupMessageIdCache() {
        GroupModelFactory groupModelFactory = databaseService.getGroupModelFactory();
        GroupMessageModelFactory groupMessageModelFactory = databaseService.getGroupMessageModelFactory();

        return new MessageIdCache<>(key -> {
            @Nullable GroupModel groupModel = groupModelFactory
                .getByApiGroupIdAndCreator(key.getApiGroupId(), key.getGroupCreatorIdentity());
            if (groupModel == null) {
                throw new NoSuchElementException();
            }
            return groupMessageModelFactory.getByApiMessageIdAndGroupId(
                key.getMessageId(),
                groupModel.getId()
            ).getId();
        });
    }

    private void iterateRows(@NonNull FileHeader fileHeader, ThrowingConsumer<CSVRow> rowConsumer) throws Exception {
        try (InputStream inputStream = getZipFileInputStream(fileHeader);
             InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
             CSVReader csvReader = new CSVReader(inputStreamReader, true)
        ) {
            CSVRow row;
            while ((row = csvReader.readNextRow()) != null) {
                rowConsumer.accept(row);
            }
        }
    }

    private void restoreContactReactions(@NonNull FileHeader contactReactionsFileHeader) throws Exception {
        logger.info("Restore contact reactions");
        final MessageIdCache<MessageIdCache.ContactMessageKey> messageIdCache = createContactMessageIdCache();
        EmojiReactionsRepository reactionsRepository = modelRepositories.getEmojiReaction();
        reactionsRepository.restoreContactReactions(insertHandle -> {
            final Counter restoredReactionsCounter = new Counter(REACTIONS_PER_STEP);
            iterateRows(contactReactionsFileHeader, row -> {
                try {
                    String contactIdentity = row.getString(Tags.TAG_REACTION_CONTACT_IDENTITY);
                    String apiMessageId = row.getString(Tags.TAG_REACTION_API_MESSAGE_ID);
                    String senderIdentity = row.getString(Tags.TAG_REACTION_SENDER_IDENTITY);
                    String sequence = row.getString(Tags.TAG_REACTION_EMOJI_SEQUENCE);
                    long reactedAt = row.getLong(Tags.TAG_REACTION_REACTED_AT);

                    MessageIdCache.ContactMessageKey key = new MessageIdCache.ContactMessageKey(
                        contactIdentity,
                        apiMessageId
                    );

                    int id = messageIdCache.get(key);
                    insertHandle.insert(new DbEmojiReaction(
                        id,
                        senderIdentity,
                        sequence,
                        new Date(reactedAt)
                    ));
                    restoredReactionsCounter.count();
                    long steps = restoredReactionsCounter.getAndResetSteps(REACTIONS_STEP_THRESHOLD);
                    if (steps > 0) {
                        updateProgress(steps);
                    }
                } catch (NoSuchElementException exception) {
                    logger.info("Could not get message id for reaction. Skip contact reaction.");
                } catch (NumberFormatException exception) {
                    logger.info("Could not read reacted at date from backup. Skip contact reaction.", exception);
                }
            });
            updateProgress(restoredReactionsCounter.getSteps());
            logger.info("Restored {} contact reactions", restoredReactionsCounter);
        });
    }

    private void restoreGroupReactions(@NonNull FileHeader groupReactionsFileHeader) throws Exception {
        logger.info("Restore group reactions");
        final MessageIdCache<MessageIdCache.GroupMessageKey> messageIdCache = createGroupMessageIdCache();
        EmojiReactionsRepository reactionsRepository = modelRepositories.getEmojiReaction();
        reactionsRepository.restoreGroupReactions(insertHandle -> {
            final Counter restoredReactionsCounter = new Counter(REACTIONS_PER_STEP);
            iterateRows(groupReactionsFileHeader, row -> {
                try {
                    String apiGroupId = row.getString(Tags.TAG_REACTION_API_GROUP_ID);
                    String groupCreatorIdentity = row.getString(Tags.TAG_REACTION_GROUP_CREATOR_IDENTITY);
                    String apiMessageId = row.getString(Tags.TAG_REACTION_API_MESSAGE_ID);
                    String senderIdentity = row.getString(Tags.TAG_REACTION_SENDER_IDENTITY);
                    String sequence = row.getString(Tags.TAG_REACTION_EMOJI_SEQUENCE);
                    long reactedAt = row.getLong(Tags.TAG_REACTION_REACTED_AT);

                    MessageIdCache.GroupMessageKey key = new MessageIdCache.GroupMessageKey(
                        apiGroupId,
                        groupCreatorIdentity,
                        apiMessageId
                    );

                    int id = messageIdCache.get(key);
                    insertHandle.insert(new DbEmojiReaction(
                        id,
                        senderIdentity,
                        sequence,
                        new Date(reactedAt)
                    ));
                    restoredReactionsCounter.count();
                    long steps = restoredReactionsCounter.getAndResetSteps(REACTIONS_STEP_THRESHOLD);
                    if (steps > 0) {
                        updateProgress(steps);
                    }
                } catch (NoSuchElementException exception) {
                    logger.info("Could not get message id for reaction. Skip group reaction");
                } catch (NumberFormatException exception) {
                    logger.info("Could not read reacted at date from backup. Skip group reaction", exception);
                }
            });
            updateProgress(restoredReactionsCounter.getSteps());
            logger.info("Restored {} group reactions", restoredReactionsCounter);
        });
    }

    /**
     * restore all avatars and profile pics
     */
    private boolean restoreContactAvatars(List<FileHeader> fileHeaders) {
        for (FileHeader fileHeader : fileHeaders) {
            String fileName = fileHeader.getFileName();
            if (fileName.startsWith(Tags.CONTACT_AVATAR_FILE_PREFIX)) {
                if (!this.restoreContactAvatarFile(fileHeader)) {
                    logger.error("restore contact avatar {} file failed or skipped", fileName);
                    //continue anyway
                }
            } else if (fileName.startsWith(Tags.CONTACT_PROFILE_PIC_FILE_PREFIX)) {
                if (!this.restoreContactPhotoFile(fileHeader)) {
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
    private long restoreMessageFiles(List<FileHeader> fileHeaders) throws IOException, RestoreCanceledException {
        long count = 0;
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
            } else if (fileName.startsWith(Tags.GROUP_MESSAGE_FILE_PREFIX)) {
                try {
                    count += this.restoreGroupMessageFile(fileHeader);
                } catch (ThreemaException e) {
                    logger.error("restore group message file failed");
                    return 0;
                }
            } else if (fileName.startsWith(Tags.DISTRIBUTION_LIST_MESSAGE_FILE_PREFIX)) {
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
        for (FileHeader fileHeader : fileHeaders) {
            String fileName = fileHeader.getFileName();

            if (!fileName.startsWith(Tags.GROUP_AVATAR_PREFIX)) {
                continue;
            }

            final String groupUid = fileName.substring(Tags.GROUP_AVATAR_PREFIX.length());
            if (!TestUtil.isEmptyOrNull(groupUid)) {
                Integer groupId = groupUidMap.get(groupUid);
                if (groupId != null) {
                    ch.threema.data.models.GroupModel m = groupModelRepository.getByLocalGroupDbId(groupId);
                    if (m != null) {
                        try (InputStream inputStream = getZipFileInputStream(fileHeader)) {
                            this.fileService.writeGroupProfilePicture(m, inputStream);
                        } catch (Exception e) {
                            //ignore, just the avatar :)
                            success = false;
                        }
                    }
                }
            }
        }

        return success;
    }

    /**
     * restore all message media
     */
    private long restoreMessageMediaFiles(List<FileHeader> fileHeaders) throws RestoreCanceledException {
        long count = 0;

        count += this.restoreMessageMediaFiles(
            fileHeaders,
            Tags.MESSAGE_MEDIA_FILE_PREFIX,
            Tags.MESSAGE_MEDIA_THUMBNAIL_FILE_PREFIX,
            uid -> databaseService.getMessageModelFactory().getByUid(uid)
        );

        count += this.restoreMessageMediaFiles(
            fileHeaders,
            Tags.GROUP_MESSAGE_MEDIA_FILE_PREFIX,
            Tags.GROUP_MESSAGE_MEDIA_THUMBNAIL_FILE_PREFIX,
            uid -> databaseService.getGroupMessageModelFactory().getByUid(uid)
        );

        count += this.restoreMessageMediaFiles(
            fileHeaders,
            Tags.DISTRIBUTION_LIST_MESSAGE_MEDIA_FILE_PREFIX,
            Tags.DISTRIBUTION_LIST_MESSAGE_MEDIA_THUMBNAIL_FILE_PREFIX,
            uid -> databaseService.getDistributionListMessageModelFactory().getByUid(uid)
        );

        return count;
    }


    /**
     * restore all message media
     */
    private long restoreMessageMediaFiles(
        @NonNull List<FileHeader> fileHeaders,
        @NonNull String filePrefix,
        @NonNull String thumbnailPrefix,
        @NonNull GetMessageModel getMessageModel
    ) throws RestoreCanceledException {
        long count = 0;

        // process all thumbnails
        Map<String, FileHeader> thumbnailFileHeaders = new HashMap<>();
        if (writeToDb) {
            for (FileHeader fileHeader : fileHeaders) {
                String fileName = fileHeader.getFileName();
                if (!TestUtil.isEmptyOrNull(fileName) && fileName.startsWith(thumbnailPrefix)) {
                    thumbnailFileHeaders.put(fileName, fileHeader);
                }
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
                    if (this.writeToDb) {
                        if (fileName.startsWith(thumbnailPrefix)) {
                            // restore thumbnail
                            FileHeader thumbnailFileHeader = thumbnailFileHeaders.get(thumbnailPrefix + messageUid);
                            if (thumbnailFileHeader != null) {
                                long fileSize = thumbnailFileHeader.getUncompressedSize();
                                if (logger.isInfoEnabled()) {
                                    logger.info("Restoring thumbnail from file ({})", formatFileSize(fileSize));
                                }
                                if (fileSize < MAX_THUMBNAIL_SIZE_BYTES) {
                                    try (InputStream inputStream = getZipFileInputStream(thumbnailFileHeader)) {
                                        this.fileService.saveThumbnail(model, inputStream);
                                    }
                                }
                            }
                        } else {
                            if (logger.isInfoEnabled()) {
                                logger.info(
                                    "Restoring media from file, with message contents type = {}, {}",
                                    model.getMessageContentsType(),
                                    formatFileSize(fileHeader.getUncompressedSize())
                                );
                            }
                            try (InputStream inputStream = getZipFileInputStream(fileHeader)) {
                                this.fileService.writeConversationMedia(model, inputStream);
                            }

                            // TODO(ANDR-3737): The following check is insufficient and leads to false positives and false negatives,
                            //  e.g. video files are not handled properly
                            if (MessageUtil.canHaveThumbnailFile(model) && model.getMessageContentsType() == MessageContentsType.IMAGE) {
                                    // check if a thumbnail file is in backup
                                FileHeader thumbnailFileHeader = thumbnailFileHeaders.get(thumbnailPrefix + messageUid);

                                // if no thumbnail file exist in backup, generate one
                                if (thumbnailFileHeader == null) {
                                    generateThumbnailForMediaFile(fileHeader, model);
                                }
                            }
                        }
                    }
                    count++;
                    updateProgress(STEP_SIZE_MEDIA);
                } catch (RestoreCanceledException e) {
                    throw new RestoreCanceledException();
                } catch (Exception e) {
                    logger.error("Failed to restore media file", e);
                    // ignore and continue
                }
            } else {
                count++;
            }
        }
        return count;
    }

    private void generateThumbnailForMediaFile(
        @NonNull FileHeader fileHeader,
        @NonNull AbstractMessageModel model
    ) {
        logger.info("Generating thumbnail for media file");
        try (ResettableInputStream inputStream = new ResettableInputStream(() -> getZipFileInputStream(fileHeader))) {
            fileService.writeConversationMediaThumbnail(model, inputStream);
        } catch (Exception e) {
            logger.warn("Failed to generate thumbnail for media file, skipping", e);
        }
    }

    private boolean restoreContactFile(@NonNull FileHeader fileHeader) throws IOException, RestoreCanceledException {
        return this.processCsvFile(fileHeader, row -> {
            try {
                ContactModel contactModel = createContactModel(row, restoreSettings);
                if (writeToDb) {
                    //set the default color
                    ContactModelFactory contactModelFactory = databaseService.getContactModelFactory();
                    contactModelFactory.createOrUpdate(contactModel);
                }
            } catch (Exception x) {
                logger.error("Could not restore contact", x);
            }
        });
    }

    private boolean restoreContactAvatarFile(@NonNull FileHeader fileHeader) {
        // Look up avatar filename
        String filename = fileHeader.getFileName();
        if (TestUtil.isEmptyOrNull(filename)) {
            return false;
        }

        // Look up contact model for this avatar
        String identityId = filename.substring(Tags.CONTACT_AVATAR_FILE_PREFIX.length());
        if (TestUtil.isEmptyOrNull(identityId)) {
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
        try (InputStream inputStream = getZipFileInputStream(fileHeader)) {
            fileService.writeUserDefinedProfilePicture(contactModel.getIdentity(), inputStream);
            return true;
        } catch (Exception e) {
            logger.error("Exception while writing contact avatar", e);
            return false;
        }
    }

    private boolean restoreContactPhotoFile(@NonNull FileHeader fileHeader) {
        // Look up profile picture filename
        String filename = fileHeader.getFileName();
        if (TestUtil.isEmptyOrNull(filename)) {
            return false;
        }

        // Look up contact model for this avatar
        String identityId = filename.substring(Tags.CONTACT_PROFILE_PIC_FILE_PREFIX.length());
        if (TestUtil.isEmptyOrNull(identityId)) {
            return false;
        }
        ContactModel contactModel = contactService.getByIdentity(identityIdMap.get(identityId));
        if (contactModel == null) {
            return false;
        }

        // Set contact profile picture
        try (InputStream inputStream = getZipFileInputStream(fileHeader)) {
            fileService.writeContactDefinedProfilePicture(contactModel.getIdentity(), inputStream);
            return true;
        } catch (Exception e) {
            logger.error("Exception while writing contact profile picture", e);
            return false;
        }
    }

    private boolean restoreGroupFile(@NonNull FileHeader fileHeader) throws IOException, RestoreCanceledException {
        return this.processCsvFile(fileHeader, row -> {
            try {
                GroupModel groupModel = createGroupModel(row, restoreSettings);

                if (groupModel != null && writeToDb) {
                    databaseService.getGroupModelFactory().create(
                        groupModel
                    );

                    if (restoreSettings.getVersion() >= 19) {
                        groupUidMap.put(row.getString(Tags.TAG_GROUP_UID), groupModel.getId());
                    } else {
                        groupUidMap.put(BackupUtils.buildGroupUid(groupModel), groupModel.getId());
                    }

                    String myIdentity = userService.getIdentity();
                    boolean userIsInMemberList = false;
                    boolean creatorIsInMemberList = false;

                    List<GroupMemberModel> groupMemberModels = createGroupMembers(row, groupModel.getId());

                    for (GroupMemberModel groupMemberModel : groupMemberModels) {
                        String memberIdentity = groupMemberModel.getIdentity();
                        if (myIdentity.equals(memberIdentity)) {
                            // Don't save the user in member list. Just set the flag for setting
                            // the user state later.
                            userIsInMemberList = true;
                        } else if (contactService.getByIdentity(memberIdentity) != null) {
                            // In case the contact exists, add the contact as a member
                            databaseService.getGroupMemberModelFactory().create(groupMemberModel);
                            if (memberIdentity != null && memberIdentity.equals(groupModel.getCreatorIdentity())) {
                                creatorIsInMemberList = true;
                            }
                        } else {
                            // The contact does not exist. This can happen when the data backup is
                            // corrupt or the contact hasn't been added due to an invalid public
                            // key.
                            logger.warn("Could not add member {} to the group because it is no valid contact", memberIdentity);
                        }
                    }
                    if (restoreSettings.getVersion() < 25) {
                        // In this case the group user state is not included in the backup and we
                        // need to determine the state based on the group member list.
                        groupModel.setUserState(userIsInMemberList ? MEMBER : LEFT);
                        databaseService.getGroupModelFactory().update(groupModel);
                    }

                    if (groupModel.getUserState() == MEMBER && !myIdentity.equals(groupModel.getCreatorIdentity()) && !creatorIsInMemberList) {
                        // If the creator is not a member, the group is considered to be 'orphaned'. This is not allowed, so instead we treat
                        // the group as if we had been kicked from it.
                        groupModel.setUserState(KICKED);
                        databaseService.getGroupModelFactory().update(groupModel);
                    }
                }
            } catch (Exception x) {
                logger.error("Could not restore group", x);
            }
        });
    }

    private boolean restoreDistributionListFile(@NonNull FileHeader fileHeader) throws IOException, RestoreCanceledException {
        return this.processCsvFile(fileHeader, row -> {
            try {
                DistributionListModel distributionListModel = createDistributionListModel(row);

                if (writeToDb) {
                    databaseService.getDistributionListModelFactory().create(distributionListModel);
                    distributionListIdMap.put(BackupUtils.buildDistributionListUid(distributionListModel), distributionListModel.getId());
                }

                List<DistributionListMemberModel> distributionListMemberModels = createDistributionListMembers(row, distributionListModel.getId());
                if (writeToDb) {
                    for (DistributionListMemberModel distributionListMemberModel : distributionListMemberModels) {
                        String memberIdentity = distributionListMemberModel.getIdentity();
                        if (contactService.getByIdentity(memberIdentity) != null) {
                            databaseService.getDistributionListMemberModelFactory().create(distributionListMemberModel);
                        } else {
                            // The contact does not exist. This can happen when the data backup is
                            // corrupt or the contact hasn't been added due to an invalid public
                            // key.
                            logger.warn("Could not add member {} to distribution list because it is no valid contact", memberIdentity);
                        }
                    }
                }
            } catch (Exception x) {
                logger.error("Could not restore distribution list", x);
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
                    databaseService.getBallotModelFactory().create(ballotModel);

                    ballotIdMap.put(BackupUtils.buildBallotUid(ballotModel), ballotModel.getId());
                    ballotOldIdMap.put(row.getInteger(Tags.TAG_BALLOT_ID), ballotModel.getId());
                }

                LinkBallotModel ballotLinkModel = createLinkBallotModel(row, ballotModel.getId());

                if (writeToDb) {
                    if (ballotLinkModel == null) {
                        //link failed
                        logger.error("link failed");
                    }
                    if (ballotLinkModel instanceof GroupBallotModel) {
                        databaseService.getGroupBallotModelFactory().create(
                            (GroupBallotModel) ballotLinkModel
                        );
                    } else if (ballotLinkModel instanceof IdentityBallotModel) {
                        databaseService.getIdentityBallotModelFactory().create(
                            (IdentityBallotModel) ballotLinkModel
                        );
                    } else {
                        logger.error("not handled link");
                    }
                }

            } catch (Exception x) {
                logger.error("Could not restore ballot", x);
            }
        });

        this.processCsvFile(ballotChoice, row -> {
            try {
                BallotChoiceModel ballotChoiceModel = createBallotChoiceModel(row);
                if (ballotChoiceModel != null && writeToDb) {
                    databaseService.getBallotChoiceModelFactory().create(ballotChoiceModel);
                    ballotChoiceIdMap.put(BackupUtils.buildBallotChoiceUid(ballotChoiceModel), ballotChoiceModel.getId());
                }
            } catch (Exception e) {
                logger.error("Failed to restore ballot choice file", e);
                // continue!
            }
        });

        this.processCsvFile(ballotVote, row -> {
            try {
                BallotVoteModel ballotVoteModel = createBallotVoteModel(row);
                if (ballotVoteModel != null && writeToDb) {
                    databaseService.getBallotVoteModelFactory().create(ballotVoteModel);
                }
            } catch (Exception x) {
                logger.error("Failed to restore ballot vote file", x);
                // continue!
            }
        });
    }

    @Nullable
    private GroupModel createGroupModel(CSVRow row, RestoreSettings restoreSettings) throws ThreemaException {
        GroupModel groupModel = new GroupModel();
        groupModel.setApiGroupId(new GroupId(row.getString(Tags.TAG_GROUP_ID)));
        groupModel.setCreatorIdentity(row.getString(Tags.TAG_GROUP_CREATOR));
        groupModel.setName(row.getString(Tags.TAG_GROUP_NAME));
        groupModel.setCreatedAt(row.getDate(Tags.TAG_GROUP_CREATED_AT));

        if (restoreSettings.getVersion() >= 4 && row.hasField(Tags.TAG_GROUP_DELETED) && row.getBoolean(Tags.TAG_GROUP_DELETED)) {
            return null;
        }
        if (restoreSettings.getVersion() >= 14) {
            groupModel.setArchived(row.getBoolean(Tags.TAG_GROUP_ARCHIVED));
        }

        if (restoreSettings.getVersion() >= 17) {
            groupModel.setGroupDesc(row.getString(Tags.TAG_GROUP_DESC));
            groupModel.setGroupDescTimestamp(row.getDate(Tags.TAG_GROUP_DESC_TIMESTAMP));
        }

        if (restoreSettings.getVersion() >= 22) {
            groupModel.setLastUpdate(row.getDate(Tags.TAG_GROUP_LAST_UPDATE));
        }

        if (restoreSettings.getVersion() >= 25) {
            groupModel.setUserState(UserState.valueOf(row.getInteger(Tags.TAG_GROUP_USER_STATE)));
        }

        return groupModel;
    }

    private BallotModel createBallotModel(CSVRow row) throws ThreemaException {
        BallotModel ballotModel = new BallotModel();

        ballotModel.setApiBallotId(row.getString(Tags.TAG_BALLOT_API_ID));
        ballotModel.setCreatorIdentity(row.getString(Tags.TAG_BALLOT_API_CREATOR));
        ballotModel.setName(row.getString(Tags.TAG_BALLOT_NAME));

        String state = row.getString(Tags.TAG_BALLOT_STATE);
        if (TestUtil.compare(state, BallotModel.State.CLOSED.toString())) {
            ballotModel.setState(BallotModel.State.CLOSED);
        } else if (TestUtil.compare(state, BallotModel.State.OPEN.toString())) {
            ballotModel.setState(BallotModel.State.OPEN);
        } else if (TestUtil.compare(state, BallotModel.State.TEMPORARY.toString())) {
            ballotModel.setState(BallotModel.State.TEMPORARY);
        }

        String assessment = row.getString(Tags.TAG_BALLOT_ASSESSMENT);
        if (TestUtil.compare(assessment, BallotModel.Assessment.MULTIPLE_CHOICE.toString())) {
            ballotModel.setAssessment(BallotModel.Assessment.MULTIPLE_CHOICE);
        } else if (TestUtil.compare(assessment, BallotModel.Assessment.SINGLE_CHOICE.toString())) {
            ballotModel.setAssessment(BallotModel.Assessment.SINGLE_CHOICE);
        }

        String type = row.getString(Tags.TAG_BALLOT_TYPE);
        if (TestUtil.compare(type, BallotModel.Type.INTERMEDIATE.toString())) {
            ballotModel.setType(BallotModel.Type.INTERMEDIATE);
        } else if (TestUtil.compare(type, BallotModel.Type.RESULT_ON_CLOSE.toString())) {
            ballotModel.setType(BallotModel.Type.RESULT_ON_CLOSE);
        }

        String choiceType = row.getString(Tags.TAG_BALLOT_C_TYPE);
        if (TestUtil.compare(choiceType, BallotModel.ChoiceType.TEXT.toString())) {
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

        if (reference.endsWith("GroupBallotModel")) {
            groupId = this.groupUidMap.get(referenceId);
        } else if (reference.endsWith("IdentityBallotModel")) {
            identity = referenceId;
        } else {
            //first try to get the reference as group
            groupId = this.groupUidMap.get(referenceId);
            if (groupId == null) {
                if (referenceId != null && referenceId.length() == ProtocolDefines.IDENTITY_LEN) {
                    identity = referenceId;
                }
            }
        }

        if (groupId != null) {
            GroupBallotModel linkBallotModel = new GroupBallotModel();
            linkBallotModel.setBallotId(ballotId);
            linkBallotModel.setGroupId(groupId);

            return linkBallotModel;
        } else if (identity != null) {
            if (!identitiesSet.contains(identity)) {
                logger.error("invalid ballot reference {} with id {}, contact {} does not exist", reference, referenceId, identity);
                return null;
            }
            IdentityBallotModel linkBallotModel = new IdentityBallotModel();
            linkBallotModel.setBallotId(ballotId);
            linkBallotModel.setIdentity(referenceId);
            return linkBallotModel;
        }

        if (writeToDb) {
            logger.error("invalid ballot reference {} with id {}", reference, referenceId);
            return null;
        }
        // not a valid reference!
        return null;
    }

    private BallotChoiceModel createBallotChoiceModel(CSVRow row) throws ThreemaException {
        Integer ballotId = ballotIdMap.get(row.getString(Tags.TAG_BALLOT_CHOICE_BALLOT_UID));
        if (ballotId == null) {
            logger.error("invalid ballotId");
            return null;
        }

        BallotChoiceModel ballotChoiceModel = new BallotChoiceModel();
        ballotChoiceModel.setBallotId(ballotId);
        ballotChoiceModel.setApiBallotChoiceId(row.getInteger(Tags.TAG_BALLOT_CHOICE_API_ID));
        ballotChoiceModel.setApiBallotChoiceId(row.getInteger(Tags.TAG_BALLOT_CHOICE_API_ID));

        String type = row.getString(Tags.TAG_BALLOT_CHOICE_TYPE);
        if (TestUtil.compare(type, BallotChoiceModel.Type.Text.toString())) {
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

        if (ballotId == null || ballotChoiceId == null) {
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

    private long restoreContactMessageFile(FileHeader fileHeader) throws IOException, ThreemaException, RestoreCanceledException {
        final Counter counter = new Counter();

        String fileName = fileHeader.getFileName();
        if (fileName == null) {
            throw new ThreemaException(null);
        }

        final String identityId = fileName.substring(Tags.MESSAGE_FILE_PREFIX.length(), fileName.indexOf(Tags.CSV_FILE_POSTFIX));
        if (TestUtil.isEmptyOrNull(identityId)) {
            throw new ThreemaException(null);
        }

        String identity = identityIdMap.get(identityId);

        if (!this.processCsvFile(fileHeader, row -> {
            try {
                counter.count();

                if (writeToDb) {
                    MessageModel messageModel = createMessageModel(row, restoreSettings);
                    messageModel.setIdentity(identity);

                    // faster, do not make a createOrUpdate to safe queries
                    boolean success = databaseService.getMessageModelFactory().create(messageModel);
                    if (success) {
                        tryMapContactAckDecToReaction(row, messageModel);
                    }

                    updateProgress(STEP_SIZE_MESSAGES);
                }
            } catch (RestoreCanceledException e) {
                throw new RestoreCanceledException();
            } catch (Exception e) {
                logger.error("Could not restore contact message file", e);
            }
        })) {
            throw new ThreemaException(null);
        }
        return counter.getCount();
    }

    /**
     * If the backup entry has State USERACK or USERDEC a corresponding
     * reaction is created for this message.
     * If the reaction cannot be created this will be logged but ignored.
     * If the backup entry has a state other than USERACK or USERDEC, this method
     * has no effect.
     * <p>
     * Not that this will not alter the state of {@code messageModel}
     * (also see {@link #setMessageState})
     */
    private void tryMapContactAckDecToReaction(@NonNull CSVRow row, @NonNull MessageModel messageModel) {
        try {
            String backupMessageStateName = row.getString(Tags.TAG_MESSAGE_MESSAGE_STATE);

            if (backupMessageStateName != null) {
                createContactReactionForMessage(backupMessageStateName, messageModel);
            }
        } catch (Exception e) {
            logger.error("Exception while trying to map ACK/DEC message state to a reaction", e);
        }
    }

    private void createContactReactionForMessage(
        @NonNull String backupMessageStateName,
        @NonNull MessageModel messageModel
    ) throws Exception {
        String senderIdentity = messageModel.isOutbox()
            ? messageModel.getIdentity()
            : userService.getIdentity();
        DbEmojiReaction reaction = createReactionForStateName(backupMessageStateName, senderIdentity, messageModel);
        if (reaction != null) {
            logger.debug(
                "Create contact reaction for message {} (id={}) with state {}",
                messageModel.getApiMessageId(),
                messageModel.getId(),
                backupMessageStateName
            );
            modelRepositories.getEmojiReaction().restoreContactReactions(insertScope ->
                insertScope.insert(reaction)
            );
        }

    }

    private long restoreGroupMessageFile(FileHeader fileHeader) throws IOException, ThreemaException, RestoreCanceledException {
        final Counter counter = new Counter();

        String fileName = fileHeader.getFileName();
        if (fileName == null) {
            throw new ThreemaException(null);
        }

        final String groupUid = fileName.substring(Tags.GROUP_MESSAGE_FILE_PREFIX.length(), fileName.indexOf(Tags.CSV_FILE_POSTFIX));
        if (TestUtil.isEmptyOrNull(groupUid)) {
            throw new ThreemaException("Group uid could not be extracted");
        }

        if (!this.processCsvFile(fileHeader, row -> {
            try {
                counter.count();

                if (writeToDb) {
                    GroupMessageModel groupMessageModel = createGroupMessageModel(row, restoreSettings);
                    Integer groupId = groupUidMap.get(groupUid);
                    if (groupId != null) {
                        groupMessageModel.setGroupId(groupId);
                        boolean success = databaseService.getGroupMessageModelFactory().create(groupMessageModel);
                        if (success) {
                            tryMapGroupAckDecToReactions(row, groupMessageModel);
                        }
                    }
                    updateProgress(STEP_SIZE_MESSAGES);
                }
            } catch (RestoreCanceledException e) {
                throw new RestoreCanceledException();
            } catch (Exception e) {
                logger.error("Could not restore group message file", e);
            }
        })) {
            throw new ThreemaException(null);
        }
        return counter.getCount();
    }

    private void tryMapGroupAckDecToReactions(@NonNull CSVRow row, @NonNull GroupMessageModel messageModel) {
        if (restoreSettings.getVersion() >= 17) {
            try {
                String messageStatesJson = row.getString(Tags.TAG_GROUP_MESSAGE_STATES);
                if (!TestUtil.isEmptyOrNull(messageStatesJson)) {
                    createGroupReactionsForMessage(messageStatesJson, messageModel);
                }
            } catch (Exception e) {
                logger.error("Exception while trying to map group ACK/DEC to reactions", e);
            }
        }
    }

    private void createGroupReactionsForMessage(
        @NonNull String messageStatesJson,
        @NonNull GroupMessageModel messageModel
    ) throws Exception {
        logger.debug(
            "Create group reactions for message {} (id={}) with states {}",
            messageModel.getApiMessageId(),
            messageModel.getId(),
            messageStatesJson
        );
        Map<String, Object> messageStatesMap = JsonUtil.convertObject(messageStatesJson);
        List<DbEmojiReaction> reactions = messageStatesMap.entrySet().stream()
            .filter(entry -> entry != null && entry.getKey() != null && entry.getValue() instanceof String)
            .map(entry -> createReactionForStateName(
                (String) entry.getValue(),
                entry.getKey(),
                messageModel
            ))
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
        if (!reactions.isEmpty()) {
            modelRepositories.getEmojiReaction()
                .restoreGroupReactions(insertScope -> reactions.forEach(insertScope::insert));
        }
    }

    @Nullable
    private DbEmojiReaction createReactionForStateName(
        @NonNull String stateName,
        @NonNull String senderIdentity,
        @NonNull AbstractMessageModel messageModel
    ) {
        String reaction = mapMessageStateNameToReactionSequence(stateName);
        if (reaction == null) {
            return null;
        }

        // We do not exactly now, when this reaction was actually created
        // therefore we make a best guess.
        Date reactedAt;
        if (messageModel.getModifiedAt() != null) {
            // This is the closest we get
            reactedAt = messageModel.getModifiedAt();
        } else if (messageModel.getCreatedAt() != null) {
            // Use creation date of message if modified at is not available
            reactedAt = messageModel.getCreatedAt();
        } else {
            // Fallback to current date if no other dates are present
            reactedAt = new Date();
        }

        return new DbEmojiReaction(
            messageModel.getId(),
            senderIdentity,
            reaction,
            reactedAt
        );
    }

    @Nullable
    private String mapMessageStateNameToReactionSequence(@NonNull String stateName) {
        if (MessageState.USERACK.name().equals(stateName)) {
            return EmojiUtil.THUMBS_UP_SEQUENCE;
        } else if (MessageState.USERDEC.name().equals(stateName)) {
            return EmojiUtil.THUMBS_DOWN_SEQUENCE;
        } else {
            return null;
        }
    }

    private long restoreDistributionListMessageFile(FileHeader fileHeader) throws IOException, ThreemaException, RestoreCanceledException {
        final Counter counter = new Counter();

        String fileName = fileHeader.getFileName();
        if (fileName == null) {
            throw new ThreemaException(null);
        }

        String[] pieces = fileName.substring(Tags.DISTRIBUTION_LIST_MESSAGE_FILE_PREFIX.length(), fileName.indexOf(Tags.CSV_FILE_POSTFIX)).split("-");

        if (pieces.length != 1) {
            throw new ThreemaException(null);
        }

        final String distributionListBackupUid = pieces[0];

        if (TestUtil.isEmptyOrNull(distributionListBackupUid)) {
            throw new ThreemaException(null);
        }

        if (!this.processCsvFile(fileHeader, row -> {
            try {
                DistributionListMessageModel distributionListMessageModel = createDistributionListMessageModel(row, restoreSettings);
                counter.count();

                if (writeToDb) {
                    updateProgress(STEP_SIZE_MESSAGES);

                    final Long distributionListId = distributionListIdMap.get(distributionListBackupUid);
                    if (distributionListId != null) {
                        distributionListMessageModel.setDistributionListId(distributionListId);
                        databaseService.getDistributionListMessageModelFactory().createOrUpdate(
                            distributionListMessageModel
                        );
                    }
                }
            } catch (RestoreCanceledException e) {
                throw new RestoreCanceledException();
            } catch (Exception e) {
                logger.error("Could not restore distribution list message file", e);
            }
        })) {
            throw new ThreemaException(null);
        }
        return counter.getCount();
    }

    private DistributionListModel createDistributionListModel(CSVRow row) throws ThreemaException {
        DistributionListModel distributionListModel = new DistributionListModel();
        distributionListModel.setId(row.getLong(Tags.TAG_DISTRIBUTION_LIST_ID));
        distributionListModel.setName(row.getString(Tags.TAG_DISTRIBUTION_LIST_NAME));
        distributionListModel.setCreatedAt(row.getDate(Tags.TAG_DISTRIBUTION_CREATED_AT));
        if (restoreSettings.getVersion() >= 14) {
            distributionListModel.setArchived(row.getBoolean(Tags.TAG_DISTRIBUTION_LIST_ARCHIVED));
        }
        if (restoreSettings.getVersion() >= 22) {
            distributionListModel.setLastUpdate(row.getDate(Tags.TAG_DISTRIBUTION_LAST_UPDATE));
        }
        return distributionListModel;
    }

    private List<GroupMemberModel> createGroupMembers(CSVRow row, int groupId) throws ThreemaException {
        List<GroupMemberModel> res = new ArrayList<>();
        for (String identity : row.getStrings(Tags.TAG_GROUP_MEMBERS)) {
            if (!TestUtil.isEmptyOrNull(identity)) {
                GroupMemberModel m = new GroupMemberModel();
                m.setGroupId(groupId);
                m.setIdentity(identity);
                res.add(m);
            }
        }
        return res;
    }

    private List<DistributionListMemberModel> createDistributionListMembers(CSVRow row, long distributionListId) throws ThreemaException {
        List<DistributionListMemberModel> res = new ArrayList<>();
        for (String identity : row.getStrings(Tags.TAG_DISTRIBUTION_MEMBERS)) {
            if (!TestUtil.isEmptyOrNull(identity)) {
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
        ContactModel contactModel = ContactModel.create(
            row.getString(Tags.TAG_CONTACT_IDENTITY),
            Utils.hexStringToByteArray(row.getString(Tags.TAG_CONTACT_PUBLIC_KEY))
        );

        String verificationString = row.getString(Tags.TAG_CONTACT_VERIFICATION_LEVEL);
        VerificationLevel verification = VerificationLevel.UNVERIFIED;

        if (verificationString.equals(VerificationLevel.SERVER_VERIFIED.name())) {
            verification = VerificationLevel.SERVER_VERIFIED;
        } else if (verificationString.equals(VerificationLevel.FULLY_VERIFIED.name())) {
            verification = VerificationLevel.FULLY_VERIFIED;
        }
        contactModel.verificationLevel = verification;
        contactModel.setFirstName(row.getString(Tags.TAG_CONTACT_FIRST_NAME));
        contactModel.setLastName(row.getString(Tags.TAG_CONTACT_LAST_NAME));

        if (restoreSettings.getVersion() >= 3) {
            contactModel.setPublicNickName(row.getString(Tags.TAG_CONTACT_NICK_NAME));
        }
        if (restoreSettings.getVersion() >= 13) {
            final boolean isHidden = row.getBoolean(Tags.TAG_CONTACT_HIDDEN);
            // Contacts are marked as hidden if their acquaintance level is GROUP
            contactModel.setAcquaintanceLevel(isHidden ? AcquaintanceLevel.GROUP : AcquaintanceLevel.DIRECT);
        }
        if (restoreSettings.getVersion() >= 14) {
            contactModel.setArchived(row.getBoolean(Tags.TAG_CONTACT_ARCHIVED));
        }
        if (restoreSettings.getVersion() >= 19) {
            identityIdMap.put(row.getString(Tags.TAG_CONTACT_IDENTITY_ID), contactModel.getIdentity());
        } else {
            identityIdMap.put(contactModel.getIdentity(), contactModel.getIdentity());
        }
        identitiesSet.add(contactModel.getIdentity());
        if (restoreSettings.getVersion() >= 22) {
            contactModel.setLastUpdate(row.getDate(Tags.TAG_CONTACT_LAST_UPDATE));
        }
        contactModel.setIsRestored(true);

        return contactModel;
    }

    private void fillMessageModel(
        @NonNull AbstractMessageModel messageModel,
        @NonNull CSVRow row,
        @NonNull RestoreSettings restoreSettings
    ) throws ThreemaException {
        messageModel.setApiMessageId(row.getString(Tags.TAG_MESSAGE_API_MESSAGE_ID));
        messageModel.setOutbox(row.getBoolean(Tags.TAG_MESSAGE_IS_OUTBOX));
        messageModel.setRead(row.getBoolean(Tags.TAG_MESSAGE_IS_READ));
        messageModel.setSaved(row.getBoolean(Tags.TAG_MESSAGE_IS_SAVED));

        setCommonTimestamps(messageModel, row);

        setMessageState(messageModel, row);

        setMessageContent(messageModel, row);

        tryUpdatingToNewBallotId(messageModel);

        messageModel.setUid(row.getString(Tags.TAG_MESSAGE_UID));

        if (restoreSettings.getVersion() >= 2) {
            messageModel.setStatusMessage(row.getBoolean(Tags.TAG_MESSAGE_IS_STATUS_MESSAGE));
        }

        if (restoreSettings.getVersion() >= 10) {
            messageModel.setCaption(row.getString(Tags.TAG_MESSAGE_CAPTION));
        }

        if (restoreSettings.getVersion() >= 15) {
            String quotedMessageId = row.getString(Tags.TAG_MESSAGE_QUOTED_MESSAGE_ID);
            if (!TestUtil.isEmptyOrNull(quotedMessageId)) {
                messageModel.setQuotedMessageId(quotedMessageId);
            }
        }

        if (restoreSettings.getVersion() >= 20) {
            if (!(messageModel instanceof DistributionListMessageModel)) {
                Integer displayTags = row.getInteger(Tags.TAG_MESSAGE_DISPLAY_TAGS);
                messageModel.setDisplayTags(displayTags);
            }
        }
    }

    private void setCommonTimestamps(
        @NonNull AbstractMessageModel messageModel,
        @NonNull CSVRow row
    ) throws ThreemaException {
        messageModel.setPostedAt(row.getDate(Tags.TAG_MESSAGE_POSTED_AT));
        messageModel.setCreatedAt(row.getDate(Tags.TAG_MESSAGE_CREATED_AT));

        if (restoreSettings.getVersion() >= 5) {
            messageModel.setModifiedAt(row.getDate(Tags.TAG_MESSAGE_MODIFIED_AT));
        }

        if (restoreSettings.getVersion() >= 16) {
            messageModel.setDeliveredAt(row.getDate(Tags.TAG_MESSAGE_DELIVERED_AT));
            messageModel.setReadAt(row.getDate(Tags.TAG_MESSAGE_READ_AT));
        }

        // Edit/delete is only available for contact and group messages
        if (messageModel instanceof MessageModel || messageModel instanceof GroupMessageModel) {
            if (restoreSettings.getVersion() >= 23) {
                messageModel.setEditedAt(row.getDate(Tags.TAG_MESSAGE_EDITED_AT));
            }
            if (restoreSettings.getVersion() >= 24) {
                messageModel.setDeletedAt(row.getDate(Tags.TAG_MESSAGE_DELETED_AT));
            }
        }
    }

    /**
     * Set the state for this message. If the message state is ACK/DEC
     * the correct state will be derived from the available timestamps.
     * Therefore this only leads to correct results if the timestamps on this message
     * are already set.
     * <p>
     * Note that no reaction is created in case of ACK/DEC. This has to be taken
     * care of separately see {@link #tryMapContactAckDecToReaction}.
     */
    private void setMessageState(
        @NonNull AbstractMessageModel messageModel,
        @NonNull CSVRow row
    ) throws ThreemaException {
        String messageState = row.getString(Tags.TAG_MESSAGE_MESSAGE_STATE);
        MessageState state = null;
        if (messageState.equals(MessageState.PENDING.name())) {
            state = MessageState.PENDING;
        } else if (messageState.equals(MessageState.SENDFAILED.name())) {
            state = MessageState.SENDFAILED;
        } else if (messageState.equals(MessageState.USERACK.name()) || messageState.equals(MessageState.USERDEC.name())) {
            state = messageModel.getReadAt() != null
                ? MessageState.READ
                : MessageState.DELIVERED;
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
    }

    private void setMessageContent(@NonNull AbstractMessageModel messageModel, @NonNull CSVRow row) throws ThreemaException {
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
            if (!TestUtil.isEmptyOrNull(body)) {
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
        } else if (typeAsString.equals(MessageType.GROUP_STATUS.name())) {
            messageType = MessageType.GROUP_STATUS;
            messageContentsType = MessageContentsType.GROUP_STATUS;
        }
        messageModel.setType(messageType);
        messageModel.setMessageContentsType(messageContentsType);
        messageModel.setBody(row.getString(Tags.TAG_MESSAGE_BODY));
    }

    private void tryUpdatingToNewBallotId(@NonNull AbstractMessageModel messageModel) {
        if (messageModel.getType() == MessageType.BALLOT) {
            // try to update to new ballot id
            BallotDataModel ballotData = messageModel.getBallotData();
            Integer ballotId = this.ballotOldIdMap.get(ballotData.getBallotId());
            if (ballotId != null) {
                BallotDataModel newBallotData = new BallotDataModel(ballotData.getType(), ballotId);
                messageModel.setBallotData(newBallotData);
            }
        }
    }

    private MessageModel createMessageModel(
        @NonNull CSVRow row,
        @NonNull RestoreSettings restoreSettings
    ) throws ThreemaException {
        MessageModel messageModel = new MessageModel();
        this.fillMessageModel(messageModel, row, restoreSettings);

        return messageModel;
    }

    private GroupMessageModel createGroupMessageModel(CSVRow row, RestoreSettings restoreSettings) throws ThreemaException {
        GroupMessageModel messageModel = new GroupMessageModel();
        this.fillMessageModel(messageModel, row, restoreSettings);
        messageModel.setIdentity(row.getString(Tags.TAG_MESSAGE_IDENTITY));
        return messageModel;
    }

    private DistributionListMessageModel createDistributionListMessageModel(CSVRow row, RestoreSettings restoreSettings) throws ThreemaException {
        DistributionListMessageModel messageModel = new DistributionListMessageModel();
        this.fillMessageModel(messageModel, row, restoreSettings);
        messageModel.setIdentity(row.getString(Tags.TAG_MESSAGE_IDENTITY));
        return messageModel;
    }

    private boolean processCsvFile(
        @NonNull FileHeader fileHeader,
        @NonNull ProcessCsvFile processCsvFile
    ) throws IOException, RestoreCanceledException {
        try (InputStream inputStream = getZipFileInputStream(fileHeader);
             InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
             CSVReader csvReader = new CSVReader(inputStreamReader, true)) {
            CSVRow row;
            while ((row = csvReader.readNextRow()) != null) {
                processCsvFile.row(row);
            }
        }
        return true;
    }

    private InputStream getZipFileInputStream(FileHeader fileHeader) {
        return zipFileWrapper.getInputStream(fileHeader);
    }

    private String formatFileSize(long sizeBytes) {
        return Formatter.formatFileSize(this, sizeBytes);
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
            String remainingTimeText = getRemainingTimeText(latestPercentStep, 100);
            updatePersistentNotification(latestPercentStep, 100, false, remainingTimeText);
            LocalBroadcastManager.getInstance(this)
                .sendBroadcast(new Intent(RESTORE_PROGRESS_INTENT)
                    .putExtra(RESTORE_PROGRESS, latestPercentStep)
                    .putExtra(RESTORE_PROGRESS_STEPS, 100)
                    .putExtra(RESTORE_PROGRESS_MESSAGE, remainingTimeText)
                );
        }
    }

    public void onFinished(String message) {
        logger.info("onFinished success = {}", restoreSuccess);

        cancelPersistentNotification();

        if (restoreSuccess && userService.hasIdentity()) {
            notificationPreferenceService.setWizardRunning(true);

            showRestoreSuccessNotification();

            // try to reopen connection
            try {
                if (!serviceManager.getConnection().isRunning()) {
                    serviceManager.startConnection();
                }
            } catch (Exception e) {
                logger.error("Failed to reopen connection after restore", e);
            }

            if (wakeLock != null && wakeLock.isHeld()) {
                logger.debug("releasing wakelock");
                wakeLock.release();
            }

            stopForeground(true);

            isRunning = false;

            // Send broadcast after isRunning has been set to false to indicate that there is no
            // backup being restored anymore
            LocalBroadcastManager.getInstance(this)
                .sendBroadcast(new Intent(RESTORE_PROGRESS_INTENT)
                    .putExtra(RESTORE_PROGRESS, 100)
                    .putExtra(RESTORE_PROGRESS_STEPS, 100)
                );

            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                ConfigUtils.scheduleAppRestart(getApplicationContext(), 2 * (int) DateUtils.SECOND_IN_MILLIS, getApplicationContext().getResources().getString(R.string.ipv6_restart_now));
            }
            stopSelf();
        } else {
            showRestoreErrorNotification(message);

            // Send broadcast so that the BackupRestoreProgressActivity can display the message
            LocalBroadcastManager.getInstance(this).sendBroadcast(
                new Intent(RESTORE_PROGRESS_INTENT).putExtra(RESTORE_PROGRESS_ERROR_MESSAGE, message)
            );

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
            cancelPendingIntent = PendingIntent.getForegroundService(this, (int) System.currentTimeMillis(), cancelIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        } else {
            cancelPendingIntent = PendingIntent.getService(this, (int) System.currentTimeMillis(), cancelIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        }

        notificationBuilder = new NotificationCompat.Builder(this, NotificationChannels.NOTIFICATION_CHANNEL_BACKUP_RESTORE_IN_PROGRESS)
            .setContentTitle(getString(R.string.restoring_backup))
            .setContentText(getString(R.string.please_wait))
            .setOngoing(true)
            .setSmallIcon(R.drawable.ic_notification_small)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .addAction(R.drawable.ic_close_white_24dp, getString(R.string.cancel), cancelPendingIntent);

        return notificationBuilder.build();
    }

    @SuppressLint("MissingPermission")
    private void updatePersistentNotification(int currentStep, int steps, boolean indeterminate, @Nullable final String remainingTimeText) {
        logger.debug("updatePersistentNotification {} of {}", currentStep, steps);

        if (remainingTimeText != null) {
            notificationBuilder.setContentText(remainingTimeText);
        }

        notificationBuilder.setProgress(steps, currentStep, indeterminate);
        notificationManagerCompat.notify(RESTORE_NOTIFICATION_ID, notificationBuilder.build());
    }

    private String getRemainingTimeText(int currentStep, int steps) {
        final long millisPassed = System.currentTimeMillis() - startTime;
        final long millisRemaining = millisPassed * steps / currentStep - millisPassed;
        String timeRemaining = ElapsedTimeFormatter.millisecondsToString(millisRemaining);
        return String.format(getString(R.string.time_remaining), timeRemaining);
    }


    private void cancelPersistentNotification() {
        notificationManagerCompat.cancel(RESTORE_NOTIFICATION_ID);
    }

    @SuppressLint("MissingPermission")
    private void showRestoreErrorNotification(String message) {
        String contentText;

        if (!TestUtil.isEmptyOrNull(message)) {
            contentText = message;
        } else {
            contentText = getString(R.string.restore_error_body);
        }

        NotificationCompat.Builder builder =
            new NotificationCompat.Builder(this, NotificationChannels.NOTIFICATION_CHANNEL_ALERT)
                .setSmallIcon(R.drawable.ic_notification_small)
                .setTicker(getString(R.string.restore_error_body))
                .setContentTitle(getString(R.string.restoring_backup))
                .setContentText(contentText)
                .setDefaults(Notification.DEFAULT_LIGHTS | Notification.DEFAULT_SOUND | Notification.DEFAULT_VIBRATE)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(contentText))
                .setAutoCancel(false);

        notificationManagerCompat.notify(RESTORE_COMPLETION_NOTIFICATION_ID, builder.build());
    }


    @SuppressLint("MissingPermission")
    private void showRestoreSuccessNotification() {
        String text;

        NotificationCompat.Builder builder =
            new NotificationCompat.Builder(this, NotificationChannels.NOTIFICATION_CHANNEL_ALERT)
                .setSmallIcon(R.drawable.ic_notification_small)
                .setTicker(getString(R.string.restore_success_body))
                .setContentTitle(getString(R.string.restoring_backup))
                .setDefaults(Notification.DEFAULT_LIGHTS | Notification.DEFAULT_SOUND | Notification.DEFAULT_VIBRATE)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setAutoCancel(true);

        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
            // Android Q does not allow restart in the background
            Intent backupIntent = new Intent(this, HomeActivity.class);
            PendingIntent pendingIntent = PendingIntent.getActivity(this, (int) System.currentTimeMillis(), backupIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

            builder.setContentIntent(pendingIntent);

            text = getString(R.string.restore_success_body) + "\n" + getString(R.string.tap_to_start, getString(R.string.app_name));
        } else {
            text = getString(R.string.restore_success_body);
        }

        builder.setContentText(text);
        builder.setStyle(new NotificationCompat.BigTextStyle().bigText(text));

        notificationManagerCompat.notify(RESTORE_COMPLETION_NOTIFICATION_ID, builder.build());
    }
}

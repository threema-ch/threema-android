package ch.threema.app.preference.service

import android.net.Uri
import androidx.annotation.IntDef
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.Immutable
import ch.threema.app.backupcenter.models.LastLocalDataBackupInfo
import ch.threema.app.backupcenter.models.LocalDataBackupPassword
import ch.threema.app.files.AppLogoFileHandleProvider
import ch.threema.app.services.ContactService.ProfilePictureUploadData
import ch.threema.app.threemasafe.ThreemaSafeServerInfo
import ch.threema.data.datatypes.AvailabilityStatus
import ch.threema.data.datatypes.ContactNameFormat
import ch.threema.data.datatypes.ConversationIdObfuscated
import ch.threema.domain.protocol.api.work.WorkDirectoryCategory
import ch.threema.domain.protocol.api.work.WorkOrganization
import java.time.Instant
import kotlin.time.Duration
import kotlinx.coroutines.flow.Flow

interface PreferenceService {
    @Retention(AnnotationRetention.SOURCE)
    @IntDef(
        IMAGE_SCALE_DEFAULT,
        IMAGE_SCALE_SMALL,
        IMAGE_SCALE_MEDIUM,
        IMAGE_SCALE_LARGE,
        IMAGE_SCALE_XLARGE,
        IMAGE_SCALE_ORIGINAL,
        IMAGE_SCALE_SEND_AS_FILE,
    )
    annotation class ImageScale

    @IntDef(
        VIDEO_SIZE_DEFAULT,
        VIDEO_SIZE_SMALL,
        VIDEO_SIZE_MEDIUM,
        VIDEO_SIZE_ORIGINAL,
        VIDEO_SIZE_SEND_AS_FILE,
    )
    annotation class VideoSize

    @IntDef(
        STARRED_MESSAGES_SORT_ORDER_DATE_DESCENDING,
        STARRED_MESSAGES_SORT_ORDER_DATE_ASCENDING,
    )
    annotation class StarredMessagesSortOrder

    enum class ContactSortOrder {
        FIRST_NAME,
        LAST_NAME,
    }

    @Immutable
    enum class EmojiStyle(val serialized: String) {
        DEFAULT("0"),
        ANDROID("1"),
        ;

        companion object {
            fun deserialize(serialized: String): EmojiStyle? =
                entries.find { it.serialized == serialized }
        }
    }

    enum class ErrorReportingState {
        ALWAYS_ASK,
        ALWAYS_SEND,
        NEVER_SEND,
    }

    enum class LockMechanism(val serialized: String) {
        NONE("none"),
        PIN("pin"),
        SYSTEM("system"),
        BIOMETRIC("biometric"),
        ;

        companion object {
            fun deserialize(serialized: String): LockMechanism? =
                entries.find { it.serialized == serialized }
        }
    }

    enum class Theme(val serialized: String, @AppCompatDelegate.NightMode val mode: Int) {
        LIGHT("0", AppCompatDelegate.MODE_NIGHT_NO),
        DARK("1", AppCompatDelegate.MODE_NIGHT_YES),
        FOLLOW_SYSTEM("2", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM),
        ;

        companion object {
            fun deserialize(serialized: String): Theme? =
                entries.find { it.serialized == serialized }
        }
    }

    enum class AutoDownloadType(val serialized: String) {
        IMAGE("1"),
        VOICE_MESSAGE("3"),
        VIDEO("2"),
        FILE("8"),
        ;

        companion object {
            fun deserialize(serialized: String): AutoDownloadType? =
                AutoDownloadType.entries.find { it.serialized == serialized }
        }
    }

    fun getTheme(): Theme

    fun setTheme(theme: Theme)

    fun isEnterToSend(): Boolean

    fun isInAppSounds(): Boolean

    fun isInAppVibrate(): Boolean

    @ImageScale
    fun getImageScale(): Int

    fun getVideoSize(): Int

    fun getSerialNumber(): String?

    fun setSerialNumber(serialNumber: String?)

    /**
     * @return the license username, guaranteed to be non-empty, or null
     */
    fun getLicenseUsername(): String?

    fun setLicenseUsername(username: String?)

    /**
     * @return the license password, guaranteed to be non-empty, or null
     */
    fun getLicensePassword(): String?

    fun setLicensePassword(password: String?)

    fun getOppfUrl(): String?

    fun setOppfUrl(oppfUrl: String?)

    fun getEmojiStyle(): EmojiStyle

    fun useDefaultEmojiStyle(): Boolean =
        getEmojiStyle() == EmojiStyle.DEFAULT

    fun setEmojiStyle(emojiStyle: EmojiStyle)

    fun isBiggerSingleEmojis(): Boolean

    fun setBiggerSingleEmojis(biggerSingleEmoji: Boolean)

    fun getRecentEmojis(): List<String>

    fun setRecentEmojis(list: List<String>)

    fun getEmojiSearchIndexVersion(): Int

    fun setEmojiSearchIndexVersion(version: Int)

    fun mustUseThreemaPush(): Boolean

    fun setMustUseThreemaPush(value: Boolean)

    fun watchMustUseThreemaPush(): Flow<Boolean>

    fun isSaveMedia(): Boolean

    fun isPinSet(): Boolean

    fun setPin(newCode: String?): Boolean

    fun isPinCodeCorrect(code: String): Boolean

    fun getAppLockGraceTime(): Duration?

    fun getIDBackupCount(): Int

    fun incrementIDBackupCount()

    fun resetIDBackupCount()

    fun setLastIDBackupReminderTimestamp(lastIDBackupReminderTimestamp: Instant?)

    fun getContactSortOrder(): ContactSortOrder

    fun getContactNameFormat(): ContactNameFormat

    fun isDefaultContactPictureColored(): Boolean

    fun getFontStyle(): Int

    fun getLastIDBackupReminderTimestamp(): Instant?

    fun getTransmittedFeatureMask(): Long

    fun setTransmittedFeatureMask(transmittedFeatureMask: Long)

    fun getLastFeatureMaskTransmission(): Instant?

    fun setLastFeatureMaskTransmission(timestamp: Instant?)

    fun getEncryptedList(listName: String): Array<String>

    fun setEncryptedList(listName: String, elements: Array<String>)

    fun showInactiveContacts(): Boolean

    fun getLastOnlineStatus(): Boolean

    fun setLastOnlineStatus(online: Boolean)

    fun isLatestVersion(): Boolean

    fun getLatestVersion(): Int

    fun setLatestVersion()

    /**
     * Check whether the app has been updated since the last check. Note that this returns true for
     * every app update. For the what's new dialog, we use [getLatestVersion].
     * Note: This method can only be used once as it returns true only once per update. Currently,
     * it is used in [ch.threema.app.home.HomeActivity] and must not be used anywhere else.
     */
    fun checkForAppUpdate(): Boolean

    fun getFileSendInfoShown(): Boolean

    fun setFileSendInfoShown(shown: Boolean)

    fun setLockoutDeadline(deadline: Instant?)

    fun getLockoutDeadline(): Instant?

    fun setLockoutTimeoutIndex(index: Int)

    fun getLockoutTimeoutIndex(): Int

    fun setLockoutAttempts(numWrongConfirmAttempts: Int)

    fun getLockoutAttempts(): Int

    fun isAnimationAutoplay(): Boolean

    fun isUseProximitySensor(): Boolean

    fun setAppLogoExpiresAt(expiresAt: Instant?, theme: AppLogoFileHandleProvider.Theme)

    fun getAppLogoExpiresAt(theme: AppLogoFileHandleProvider.Theme): Instant?

    fun arePrivateChatsHidden(): Boolean

    fun setArePrivateChatsHidden(hidden: Boolean)

    fun watchArePrivateChatsHidden(): Flow<Boolean>

    fun isContactSyncEnabled(): Boolean

    fun watchIsContactSyncEnabled(): Flow<Boolean>

    fun getLockMechanism(): LockMechanism

    fun hasLockMechanism(): Boolean

    fun setLockMechanism(lockMechanism: LockMechanism)

    /**
     * Check if app UI lock is enabled
     *
     * @return true if UI lock is enabled, false otherwise
     */
    fun isAppLockEnabled(): Boolean

    fun setAppLockEnabled(enabled: Boolean)

    fun isShowImageAttachPreviewsEnabled(): Boolean

    fun isDirectShare(): Boolean

    fun setMessageDrafts(messageDrafts: Map<ConversationIdObfuscated, String?>?)

    fun getMessageDrafts(): Map<ConversationIdObfuscated, String?>?

    fun setQuoteDrafts(quoteDrafts: Map<ConversationIdObfuscated, String?>?)

    fun getQuoteDrafts(): Map<ConversationIdObfuscated, String?>?

    fun setCustomSupportUrl(supportUrl: String?)

    fun getCustomSupportUrl(): String?

    fun getDiverseEmojiPrefs(): Map<String, String?>

    fun setDiverseEmojiPrefs(diverseEmojis: Map<String, String?>)

    fun isWebClientEnabled(): Boolean

    fun setWebClientEnabled(enabled: Boolean)

    fun setPushToken(fcmToken: String?)

    fun getPushToken(): String?

    fun getProfilePicRelease(): Int

    fun setProfilePicRelease(value: Int)

    fun watchProfilePicRelease(): Flow<Int>

    fun getProfilePicUploadTimestamp(): Instant?

    fun setProfilePicUploadData(result: ProfilePictureUploadData?)

    /**
     * Get the stored profile picture upload data. Note that the returned data does not include the
     * bitmap array of the profile picture.
     *
     * @return the stored profile picture upload data or null if there is no stored data or an error
     * occurred while reading the data
     */
    fun getProfilePicUploadData(): ProfilePictureUploadData?

    fun getProfilePicReceive(): Boolean

    fun getAECMode(): String

    fun getVideoCodec(): String

    /**
     * If true, then mobile POTS calls should be rejected while a Threema call is active.
     */
    /**
     * Set whether or not a mobile POTS calls should be rejected while a Threema call is active.
     *
     *
     * Note that this requires the "manage phone call" permission.
     */
    fun isRejectMobileCalls(): Boolean

    fun setRejectMobileCalls(value: Boolean)

    /**
     * If true, a running 1:1 call will be aborted when the bluetooth headset is disconnected.
     */
    fun shouldAbortCallOnBluetoothDisconnect(): Boolean

    /**
     * This preference corresponds to the troubleshooting setting "IPv6 for messages"
     *
     * @return true if ipv6 is enabled for messages, false otherwise
     */
    fun isIpv6Preferred(): Boolean

    fun allowWebrtcIpv6(): Boolean

    fun getMobileAutoDownload(): Set<AutoDownloadType>

    fun setMobileAutoDownload(types: Set<AutoDownloadType>)

    fun getWifiAutoDownload(): Set<AutoDownloadType>

    fun setWifiAutoDownload(types: Set<AutoDownloadType>)

    fun setRatingReference(reference: String?)

    fun getRatingReference(): String?

    fun setRatingReviewText(review: String?)

    fun getRatingReviewText(): String?

    fun setPrivacyPolicyAccepted(timestamp: Instant?, source: Int)

    fun getPrivacyPolicyAccepted(): Instant?

    fun getIsGroupCallsTooltipShown(): Boolean

    fun setGroupCallsTooltipShown(shown: Boolean)

    fun getIsWorkHintTooltipShown(): Boolean

    fun setIsWorkHintTooltipShown(shown: Boolean)

    fun getIsFaceBlurTooltipShown(): Boolean

    fun setFaceBlurTooltipShown(shown: Boolean)

    fun isMultipleRecipientsTooltipShown(): Boolean

    fun setMultipleRecipientsTooltipShown(shown: Boolean)

    fun isOneTimeDialogShown(dialogTag: String): Boolean

    fun setOneTimeDialogShown(dialogTag: String, shown: Boolean)

    fun isTooltipPopupDismissed(@StringRes key: Int): Boolean

    fun setTooltipPopupDismissed(@StringRes key: Int, dismissed: Boolean)

    fun setThreemaSafeEnabled(value: Boolean)

    fun getThreemaSafeEnabled(): Boolean

    fun watchThreemaSafeEnabled(): Flow<Boolean>

    fun setThreemaSafeMasterKey(masterKey: ByteArray?)

    fun getThreemaSafeMasterKey(): ByteArray?

    fun setThreemaSafeServerInfo(serverInfo: ThreemaSafeServerInfo?)

    fun getThreemaSafeServerInfo(): ThreemaSafeServerInfo

    fun setThreemaSafeUploadTimestamp(timestamp: Instant?)

    fun getThreemaSafeUploadTimestamp(): Instant?

    fun getShowUnreadBadge(): Boolean

    fun setThreemaSafeErrorCode(code: Int)

    fun getThreemaSafeErrorCode(): Int

    /**
     * Set the earliest timestamp where the threema safe backup failed. Only set this if there are
     * changes for the backup available. Don't update the timestamp when there is already a timestamp set as
     * this is the first occurrence of a failed backup. Override this timestamp with null, when a safe
     * backup has been created successfully.
     *
     * @param timestamp the timestamp when the safe backup first failed
     */
    fun setThreemaSafeErrorTimestamp(timestamp: Instant?)

    /**
     * Get the first timestamp where the safe backup failed. If this is null, then the last safe backup
     * was successful.
     *
     * @return the timestamp of the first failed safe backup
     */
    fun getThreemaSafeErrorTimestamp(): Instant?

    fun setThreemaSafeServerMaxUploadSize(maxBackupBytes: Long)

    fun getThreemaSafeServerMaxUploadSize(): Long

    fun setThreemaSafeServerRetention(days: Int)

    fun getThreemaSafeServerRetention(): Int

    fun setThreemaSafeBackupSize(size: Int)

    fun getThreemaSafeBackupSize(): Int

    fun setThreemaSafeHashString(hashString: String?)

    fun getThreemaSafeHashString(): String?

    fun setThreemaSafeBackupTimestamp(timestamp: Instant?)

    fun getThreemaSafeBackupTimestamp(): Instant?

    fun setWorkSyncCheckInterval(checkInterval: Duration)

    fun getWorkSyncCheckInterval(): Duration

    fun setIdentityStateSyncInterval(syncInterval: Duration)

    fun getIdentityStateSyncInterval(): Duration

    fun getIsExportIdTooltipShown(): Boolean

    fun setThreemaSafeMDMConfig(mdmConfigHash: String?)

    fun getThreemaSafeMDMConfig(): String?

    fun setWorkDirectoryEnabled(enabled: Boolean)

    fun getWorkDirectoryEnabled(): Boolean

    fun setWorkDirectoryCategories(categories: List<WorkDirectoryCategory>)

    fun getWorkDirectoryCategories(): List<WorkDirectoryCategory>

    fun setWorkOrganization(organization: WorkOrganization?)

    fun getWorkOrganization(): WorkOrganization?

    fun setLicensedStatus(licensed: Boolean)

    fun getLicensedStatus(): Boolean

    fun setShowDeveloperMenu(show: Boolean)

    fun showDeveloperMenu(): Boolean

    fun getLegacyDataBackupUri(): Uri?

    fun setLegacyDataBackupUri(newUri: Uri?)

    fun getLegacyDataBackupPickerLaunched(): Instant?

    fun setLegacyDataBackupPickerLaunched(timestamp: Instant?)

    fun getLastLegacyDataBackupTimestamp(): Instant?

    fun setLastLegacyDataBackupTimestamp(timestamp: Instant?)

    fun getLocalDataBackupPassword(): LocalDataBackupPassword?

    fun setLocalDataBackupPassword(localDataBackupPassword: LocalDataBackupPassword?)

    fun getLastLocalDataBackupInfo(): LastLocalDataBackupInfo?

    fun setLastLocalDataBackupInfo(info: LastLocalDataBackupInfo?)

    fun watchLastLocalDataBackupInfo(): Flow<LastLocalDataBackupInfo?>

    fun getLocalDataBackupLocation(): Uri?

    fun setLocalDataBackupLocation(location: Uri?)

    fun getMatchToken(): String?

    fun setMatchToken(matchToken: String?)

    fun isAfterWorkDNDEnabled(): Boolean

    fun setAfterWorkDNDEnabled(enabled: Boolean)

    fun setCameraFlashMode(flashMode: Int)

    fun getCameraFlashMode(): Int

    fun setPipPosition(pipPosition: Int)

    fun getPipPosition(): Int

    fun getVideoCallsProfile(): String

    fun setPollOverviewHidden(hidden: Boolean)

    fun getPollOverviewHidden(): Boolean

    fun isVideoCallToggleTooltipShown(): Boolean

    fun setVideoCallToggleTooltipShown(shown: Boolean)

    fun getCameraPermissionRequestShown(): Boolean

    fun setCameraPermissionRequestShown(shown: Boolean)

    fun setVoiceRecorderBluetoothDisabled(disabled: Boolean)

    fun getVoiceRecorderBluetoothDisabled(): Boolean

    fun setAudioPlaybackSpeed(newSpeed: Float)

    fun getAudioPlaybackSpeed(): Float

    fun isGroupCallSendInitEnabled(): Boolean

    fun skipGroupCallCreateDelay(): Boolean

    fun isBackupWarningDismissed(): Boolean

    fun setBackupWarningDismissed(isDismissed: Boolean = true)

    @StarredMessagesSortOrder
    fun getStarredMessagesSortOrder(): Int

    fun setStarredMessagesSortOrder(@StarredMessagesSortOrder order: Int)

    fun setAutoDeleteDays(days: Int)

    fun getAutoDeleteDays(): Int

    fun getMediaGalleryContentTypes(): BooleanArray

    fun setMediaGalleryContentTypes(contentTypes: BooleanArray)

    fun getEmailSyncHashCode(): Int

    fun getPhoneNumberSyncHashCode(): Int

    fun setEmailSyncHashCode(emailsHash: Int)

    fun setPhoneNumberSyncHashCode(phoneNumbersHash: Int)

    fun setTimeOfLastContactSync(timestamp: Instant?)

    fun getTimeOfLastContactSync(): Instant?

    fun showMessageDebugInfo(): Boolean

    fun getLastShortcutUpdateTimestamp(): Instant?

    fun setLastShortcutUpdateTimestamp(timestamp: Instant?)

    fun hasRequestedNotificationPermission(): Boolean

    fun setHasRequestedNotificationPermission(requested: Boolean)

    fun getLastMultiDeviceGroupCheckTimestamp(): Instant?

    fun setLastMultiDeviceGroupCheckTimestamp(timestamp: Instant)

    fun getErrorReportingState(): ErrorReportingState

    fun setErrorReportingState(state: ErrorReportingState)

    fun getProblemDismissed(problemKey: String): Instant?

    fun setProblemDismissed(problemKey: String, timestamp: Instant?)

    fun isDebugLogEnabled(): Boolean

    fun setDebugLogEnabledTimestamp(timestamp: Instant?)

    fun getDebugLogEnabledTimestamp(): Instant?

    fun watchDebugLogEnabledTimestamp(): Flow<Instant?>

    /**
     *  Persist the users [AvailabilityStatus]. If the current build does not support this feature, this is a no-op.
     */
    fun setAvailabilityStatus(availabilityStatus: AvailabilityStatus)

    /**
     *  Read the currently stored [AvailabilityStatus] of the user **if** the current build supports the feature.
     *
     *  In case no status was ever saved or the deserialization fails, `null` will be returned.
     */
    fun getAvailabilityStatus(): AvailabilityStatus?

    /**
     *  Creates a *cold* flow of the latest [AvailabilityStatus] of the user.
     *
     *  If the current build does not support this feature, a single value flow of `null` is returned.
     *
     *  In case the deserialization fails, `null` will be emitted.
     */
    fun watchAvailabilityStatus(): Flow<AvailabilityStatus?>

    fun shouldShowEmojiReactionNotificationsInContactConversations(): Boolean

    fun shouldShowEmojiReactionNotificationsInGroupConversations(): Boolean

    companion object {
        const val IMAGE_SCALE_DEFAULT = -1
        const val IMAGE_SCALE_SMALL = 0
        const val IMAGE_SCALE_MEDIUM = 1
        const val IMAGE_SCALE_LARGE = 2
        const val IMAGE_SCALE_XLARGE = 3
        const val IMAGE_SCALE_ORIGINAL = 4
        const val IMAGE_SCALE_SEND_AS_FILE = 5

        const val VIDEO_SIZE_DEFAULT = -1
        const val VIDEO_SIZE_SMALL = 0
        const val VIDEO_SIZE_MEDIUM = 1
        const val VIDEO_SIZE_ORIGINAL = 2
        const val VIDEO_SIZE_SEND_AS_FILE = 3

        const val STARRED_MESSAGES_SORT_ORDER_DATE_DESCENDING = 0
        const val STARRED_MESSAGES_SORT_ORDER_DATE_ASCENDING = 1

        const val PROFILEPIC_RELEASE_NOBODY = 0
        const val PROFILEPIC_RELEASE_EVERYONE = 1
        const val PROFILEPIC_RELEASE_ALLOW_LIST = 2

        const val PRIVACY_POLICY_ACCEPT_NONE = 0
        const val PRIVACY_POLICY_ACCEPT_EXPLICIT = 1
        const val PRIVACY_POLICY_ACCEPT_IMPLICIT = 2
        const val PRIVACY_POLICY_ACCEPT_UPDATE = 3

        const val VIDEO_CODEC_HW = "hw"
        const val VIDEO_CODEC_NO_VP8 = "no-vp8"
        const val VIDEO_CODEC_NO_H264HIP = "no-h264hip"
        const val VIDEO_CODEC_SW = "sw"
    }
}

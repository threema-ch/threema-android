package ch.threema.app.preference.service

import android.content.Context
import android.net.Uri
import androidx.annotation.IntDef
import androidx.annotation.StringRes
import ch.threema.app.services.ContactService.ProfilePictureUploadData
import ch.threema.app.threemasafe.ThreemaSafeServerInfo
import ch.threema.app.utils.ConfigUtils.AppThemeSetting
import ch.threema.data.datatypes.AvailabilityStatus
import ch.threema.data.datatypes.ContactNameFormat
import ch.threema.domain.protocol.api.work.WorkDirectoryCategory
import ch.threema.domain.protocol.api.work.WorkOrganization
import java.time.Instant
import java.util.LinkedList
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

    @IntDef(
        EMOJI_STYLE_DEFAULT,
        EMOJI_STYLE_ANDROID,
    )
    annotation class EmojiStyle

    enum class ErrorReportingState {
        ALWAYS_ASK,
        ALWAYS_SEND,
        NEVER_SEND,
    }

    fun isCustomWallpaperEnabled(): Boolean

    fun setCustomWallpaperEnabled(enabled: Boolean)

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

    @Deprecated("Superseded by getRecentEmojis2")
    fun getRecentEmojis(): LinkedList<Int>

    fun getRecentEmojis2(): LinkedList<String>

    @Deprecated("Superseded by setRecentEmojis2")
    fun setRecentEmojis(list: LinkedList<Int>?)

    fun setRecentEmojis2(list: LinkedList<String>)

    fun getEmojiSearchIndexVersion(): Int

    fun setEmojiSearchIndexVersion(version: Int)

    /**
     * Whether to use Threema Push instead of another push service.
     */
    fun useThreemaPush(): Boolean

    fun setUseThreemaPush(value: Boolean)

    fun isSaveMedia(): Boolean

    fun isPinSet(): Boolean

    fun setPin(newCode: String?): Boolean

    fun isPinCodeCorrect(code: String): Boolean

    /**
     * value in seconds, or -1 if not set
     */
    fun getPinLockGraceTime(): Int

    fun getIDBackupCount(): Int

    fun incrementIDBackupCount()

    fun resetIDBackupCount()

    fun setLastIDBackupReminderTimestamp(lastIDBackupReminderTimestamp: Instant?)

    fun getContactListSorting(): String

    fun isContactListSortingFirstName(): Boolean

    fun getContactNameFormat(): ContactNameFormat

    fun isDefaultContactPictureColored(): Boolean

    fun getFontStyle(): Int

    fun getLastIDBackupReminderTimestamp(): Instant?

    fun getTransmittedFeatureMask(): Long
    fun setTransmittedFeatureMask(transmittedFeatureMask: Long)

    fun getLastFeatureMaskTransmission(): Instant?

    fun setLastFeatureMaskTransmission(timestamp: Instant?)

    fun getEncryptedList(listName: String): Array<String>

    fun getList(listName: String): Array<String>

    fun setList(listName: String, elements: Array<String>)

    /**
     * save list to preferences without triggering a listener
     */
    fun setEncryptedListQuietly(listName: String, elements: Array<String>)

    /**
     * save list to preferences without triggering a listener
     */
    fun setListQuietly(listName: String, elements: Array<String>)

    fun getStringMap(listName: String): Map<String, String?>

    fun setStringMap(listName: String, map: Map<String, String?>)

    fun showInactiveContacts(): Boolean

    fun getLastOnlineStatus(): Boolean

    fun setLastOnlineStatus(online: Boolean)

    fun isLatestVersion(context: Context): Boolean

    fun getLatestVersion(): Int

    fun setLatestVersion(context: Context)

    /**
     * Check whether the app has been updated since the last check. Note that this returns true for
     * every app update. For the what's new dialog, we use [getLatestVersion].
     * Note: This method can only be used once as it returns true only once per update. Currently,
     * it is used in [ch.threema.app.home.HomeActivity] and must not be used anywhere else.
     */
    fun checkForAppUpdate(context: Context): Boolean

    fun getFileSendInfoShown(): Boolean

    fun setFileSendInfoShown(shown: Boolean)

    @EmojiStyle
    fun getEmojiStyle(): Int

    fun setLockoutDeadline(deadline: Instant?)

    fun getLockoutDeadline(): Instant?

    fun setLockoutAttempts(numWrongConfirmAttempts: Int)

    fun getLockoutAttempts(): Int

    fun isAnimationAutoplay(): Boolean

    fun isUseProximitySensor(): Boolean

    fun setAppLogoExpiresAt(expiresAt: Instant?, @AppThemeSetting theme: String)

    fun getAppLogoExpiresAt(@AppThemeSetting theme: String): Instant?

    fun arePrivateChatsHidden(): Boolean

    fun setArePrivateChatsHidden(hidden: Boolean)

    fun watchArePrivateChatsHidden(): Flow<Boolean>

    fun getLockMechanism(): String

    /**
     * Check if app UI lock is enabled
     *
     * @return true if UI lock is enabled, false otherwise
     */
    fun isAppLockEnabled(): Boolean

    fun setAppLockEnabled(enabled: Boolean)

    fun setLockMechanism(lockingMech: String?)

    fun isShowImageAttachPreviewsEnabled(): Boolean

    fun isDirectShare(): Boolean

    fun setMessageDrafts(messageDrafts: Map<String, String?>?)

    fun getMessageDrafts(): Map<String, String?>?

    fun setQuoteDrafts(quoteDrafts: Map<String, String?>?)

    fun getQuoteDrafts(): Map<String, String?>?

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

    fun getMobileAutoDownload(): Set<String>

    fun getWifiAutoDownload(): Set<String>

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

    fun setWorkSyncCheckInterval(checkInterval: Int)

    fun getWorkSyncCheckInterval(): Int

    /**
     * Store the interval for the identity state sync in seconds.
     * @param syncIntervalS The sync interval in seconds
     */
    fun setIdentityStateSyncInterval(syncIntervalS: Int)

    /**
     * @return The identity state sync interval in seconds
     */
    fun getIdentityStateSyncIntervalS(): Int

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

    fun getDataBackupUri(): Uri?

    fun setDataBackupUri(newUri: Uri?)

    fun getDataBackupPickerLaunched(): Instant?

    fun setDataBackupPickerLaunched(timestamp: Instant?)

    fun getLastDataBackupTimestamp(): Instant?

    fun setLastDataBackupTimestamp(timestamp: Instant?)

    fun getMatchToken(): String?

    fun setMatchToken(matchToken: String?)

    fun isAfterWorkDNDEnabled(): Boolean

    fun setAfterWorkDNDEnabled(enabled: Boolean)

    fun setCameraFlashMode(flashMode: Int)

    fun getCameraFlashMode(): Int

    fun setPipPosition(pipPosition: Int)

    fun getPipPosition(): Int

    fun getVideoCallsProfile(): String?

    fun setBallotOverviewHidden(hidden: Boolean)

    fun getBallotOverviewHidden(): Boolean

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

    fun getBackupWarningDismissedTime(): Instant?

    fun setBackupWarningDismissedTime(timestamp: Instant?)

    @StarredMessagesSortOrder
    fun getStarredMessagesSortOrder(): Int

    fun setStarredMessagesSortOrder(@StarredMessagesSortOrder order: Int)

    fun setAutoDeleteDays(days: Int)

    fun getAutoDeleteDays(): Int

    // TODO(ANDR-2816): Remove
    fun removeLastNotificationRationaleShown()

    fun getMediaGalleryContentTypes(): BooleanArray

    fun setMediaGalleryContentTypes(contentTypes: BooleanArray?)

    fun getEmailSyncHashCode(): Int

    fun getPhoneNumberSyncHashCode(): Int

    fun setEmailSyncHashCode(emailsHash: Int)

    fun setPhoneNumberSyncHashCode(phoneNumbersHash: Int)

    fun setTimeOfLastContactSync(timestamp: Instant?)

    fun getTimeOfLastContactSync(): Instant?

    fun showMessageDebugInfo(): Boolean

    fun getLastShortcutUpdateTimestamp(): Instant?

    fun setLastShortcutUpdateTimestamp(timestamp: Instant?)

    /**
     * Set the last timestamp when the notification permission has been requested.
     */
    fun setLastNotificationPermissionRequestTimestamp(timestamp: Instant?)

    /**
     * Get the last timestamp when the notification permission has been requested. If the
     * notification permission has not yet been requested, null is returned.
     */
    fun getLastNotificationPermissionRequestTimestamp(): Instant?

    fun getLastMultiDeviceGroupCheckTimestamp(): Instant?

    fun setLastMultiDeviceGroupCheckTimestamp(timestamp: Instant)

    fun getErrorReportingState(): ErrorReportingState

    fun setErrorReportingState(state: ErrorReportingState)

    fun getProblemDismissed(problemKey: String): Instant?

    fun setProblemDismissed(problemKey: String, timestamp: Instant?)

    fun isDebugLogEnabled(): Boolean

    fun setDebugLogEnabledTimestamp(timestamp: Instant?)

    fun getDebugLogEnabledTimestamp(): Instant?

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

    fun clear()

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

        const val EMOJI_STYLE_DEFAULT = 0
        const val EMOJI_STYLE_ANDROID = 1

        const val LOCKING_MECH_NONE = "none"
        const val LOCKING_MECH_PIN = "pin"
        const val LOCKING_MECH_SYSTEM = "system"
        const val LOCKING_MECH_BIOMETRIC = "biometric"

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

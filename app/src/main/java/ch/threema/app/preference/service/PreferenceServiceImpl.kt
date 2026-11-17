package ch.threema.app.preference.service

import android.content.Context
import android.net.Uri
import androidx.annotation.StringRes
import androidx.core.net.toUri
import androidx.core.text.isDigitsOnly
import ch.threema.app.AppConstants
import ch.threema.app.BuildConfig
import ch.threema.app.BuildFlavor
import ch.threema.app.R
import ch.threema.app.backupcenter.models.LastLocalDataBackupInfo
import ch.threema.app.backupcenter.models.LocalDataBackupPassword
import ch.threema.app.files.AppLogoFileHandleProvider
import ch.threema.app.mediagallery.MediaGalleryViewModel.Companion.SELECTABLE_CONTENT_TYPES
import ch.threema.app.preference.service.PreferenceService.EmojiStyle
import ch.threema.app.preference.service.PreferenceService.ErrorReportingState
import ch.threema.app.preference.service.PreferenceService.ImageScale
import ch.threema.app.preference.service.PreferenceService.LockMechanism
import ch.threema.app.preference.service.PreferenceService.StarredMessagesSortOrder
import ch.threema.app.services.ContactService.ProfilePictureUploadData
import ch.threema.app.stores.EncryptedPreferenceStore
import ch.threema.app.stores.PreferenceStore
import ch.threema.app.threemasafe.ThreemaSafeMDMConfig
import ch.threema.app.threemasafe.ThreemaSafeServerInfo
import ch.threema.app.utils.AutoDeleteUtil
import ch.threema.app.utils.ConfigUtils
import ch.threema.base.utils.getThreemaLogger
import ch.threema.common.Base64
import ch.threema.common.DispatcherProvider
import ch.threema.common.TimeProvider
import ch.threema.common.booleanOrNull
import ch.threema.common.decodeFromStringOrNull
import ch.threema.common.secureContentEquals
import ch.threema.common.stateFlowOf
import ch.threema.common.takeUnlessEmpty
import ch.threema.data.datatypes.AvailabilityStatus
import ch.threema.data.datatypes.ContactNameFormat
import ch.threema.data.datatypes.ConversationIdObfuscated
import ch.threema.domain.protocol.api.work.WorkDirectoryCategory
import ch.threema.domain.protocol.api.work.WorkOrganization
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray

private val logger = getThreemaLogger("PreferenceServiceImpl")

class PreferenceServiceImpl(
    private val appContext: Context,
    private val preferenceStore: PreferenceStore,
    private val encryptedPreferenceStore: EncryptedPreferenceStore,
    private val dispatcherProvider: DispatcherProvider,
    private val timeProvider: TimeProvider,
) : PreferenceService {
    override fun getTheme(): PreferenceService.Theme =
        preferenceStore.getString(getKeyName(R.string.preferences__theme))
            ?.let(PreferenceService.Theme::deserialize)
            ?: PreferenceService.Theme.FOLLOW_SYSTEM

    override fun setTheme(theme: PreferenceService.Theme) {
        preferenceStore.save(getKeyName(R.string.preferences__theme), theme.serialized)
    }

    override fun isEnterToSend(): Boolean =
        preferenceStore.getBoolean(getKeyName(R.string.preferences__enter_to_send))

    override fun isInAppSounds(): Boolean =
        preferenceStore.getBoolean(getKeyName(R.string.preferences__inapp_sounds), defaultValue = true)

    override fun isInAppVibrate(): Boolean =
        preferenceStore.getBoolean(getKeyName(R.string.preferences__inapp_vibrate), defaultValue = true)

    @ImageScale
    override fun getImageScale(): Int {
        val imageScale = preferenceStore.getString(getKeyName(R.string.preferences__image_size))
        return when (imageScale?.toIntOrNull()) {
            0 -> PreferenceService.IMAGE_SCALE_SMALL
            1 -> PreferenceService.IMAGE_SCALE_MEDIUM
            3 -> PreferenceService.IMAGE_SCALE_XLARGE
            4 -> PreferenceService.IMAGE_SCALE_ORIGINAL
            else -> PreferenceService.IMAGE_SCALE_LARGE
        }
    }

    override fun getVideoSize(): Int {
        val videoSize = preferenceStore.getString(getKeyName(R.string.preferences__video_size))
        return when (videoSize?.toIntOrNull()) {
            0 -> PreferenceService.VIDEO_SIZE_SMALL
            2 -> PreferenceService.VIDEO_SIZE_ORIGINAL
            else -> PreferenceService.VIDEO_SIZE_MEDIUM
        }
    }

    override fun getSerialNumber(): String? =
        preferenceStore.getString(getKeyName(R.string.preferences__serial_number))

    override fun setSerialNumber(serialNumber: String?) {
        preferenceStore.save(getKeyName(R.string.preferences__serial_number), serialNumber)
    }

    @Synchronized
    override fun getLicenseUsername(): String? =
        encryptedPreferenceStore.getString(getKeyName(R.string.preferences__license_username))
            ?.takeUnlessEmpty()

    override fun setLicenseUsername(username: String?) {
        encryptedPreferenceStore.save(getKeyName(R.string.preferences__license_username), username)
    }

    @Synchronized
    override fun getLicensePassword(): String? =
        encryptedPreferenceStore.getString(getKeyName(R.string.preferences__license_password))
            ?.takeUnlessEmpty()

    override fun setLicensePassword(password: String?) {
        encryptedPreferenceStore.save(getKeyName(R.string.preferences__license_password), password)
    }

    @Synchronized
    override fun getOppfUrl(): String? =
        preferenceStore.getString(getKeyName(R.string.preferences__oppf_url))

    override fun setOppfUrl(oppfUrl: String?) {
        preferenceStore.save(getKeyName(R.string.preferences__oppf_url), oppfUrl)
    }

    override fun getEmojiStyle(): EmojiStyle =
        preferenceStore.getString(getKeyName(R.string.preferences__emoji_style))
            ?.let(EmojiStyle::deserialize)
            ?: EmojiStyle.DEFAULT

    override fun setEmojiStyle(emojiStyle: EmojiStyle) {
        preferenceStore.save(getKeyName(R.string.preferences__emoji_style), emojiStyle.serialized)
    }

    override fun isBiggerSingleEmojis(): Boolean =
        preferenceStore.getBoolean(getKeyName(R.string.preferences__bigger_single_emojis), defaultValue = true)

    override fun setBiggerSingleEmojis(biggerSingleEmoji: Boolean) {
        preferenceStore.save(getKeyName(R.string.preferences__bigger_single_emojis), biggerSingleEmoji)
    }

    override fun getRecentEmojis(): List<String> =
        preferenceStore.getStringArray(getKeyName(R.string.preferences__recent_emojis))
            ?.toList()
            ?: emptyList()

    override fun setRecentEmojis(list: List<String>) {
        preferenceStore.save(getKeyName(R.string.preferences__recent_emojis), list.toTypedArray<String>())
    }

    override fun getEmojiSearchIndexVersion(): Int =
        preferenceStore.getInt(getKeyName(R.string.preferences__emoji_search_index_version), defaultValue = -1)

    override fun setEmojiSearchIndexVersion(version: Int) {
        preferenceStore.save(getKeyName(R.string.preferences__emoji_search_index_version), version)
    }

    override fun mustUseThreemaPush(): Boolean =
        BuildFlavor.FORCE_THREEMA_PUSH || preferenceStore.getBoolean(getKeyName(R.string.preferences__threema_push_switch))

    override fun setMustUseThreemaPush(value: Boolean) {
        preferenceStore.save(getKeyName(R.string.preferences__threema_push_switch), value)
    }

    override fun watchMustUseThreemaPush(): Flow<Boolean> =
        if (BuildFlavor.FORCE_THREEMA_PUSH) {
            stateFlowOf(true)
        } else {
            preferenceStore.watchBoolean(getKeyName(R.string.preferences__threema_push_switch))
        }

    override fun isSaveMedia(): Boolean =
        preferenceStore.getBoolean(getKeyName(R.string.preferences__save_media))

    override fun isPinSet(): Boolean =
        isPinCodeValid(encryptedPreferenceStore.getString(getKeyName(R.string.preferences__pin_lock_code)))

    override fun setPin(newCode: String?): Boolean {
        if (isPinCodeValid(newCode)) {
            encryptedPreferenceStore.save(getKeyName(R.string.preferences__pin_lock_code), newCode)
            return true
        } else {
            preferenceStore.remove(getKeyName(R.string.preferences__pin_lock_code))
            return false
        }
    }

    override fun isPinCodeCorrect(code: String): Boolean {
        val storedCode = encryptedPreferenceStore.getString(getKeyName(R.string.preferences__pin_lock_code))
            ?: ""
        return storedCode.toByteArray().secureContentEquals(code.toByteArray())
    }

    override fun getAppLockGraceTime(): Duration? =
        preferenceStore.getString(getKeyName(R.string.preferences__pin_lock_grace_time))
            ?.toIntOrNull()
            ?.takeIf { it >= 30 }
            ?.seconds

    override fun getIDBackupCount(): Int =
        preferenceStore.getInt(getKeyName(R.string.preferences__id_backup_count))

    override fun incrementIDBackupCount() {
        preferenceStore.save(
            getKeyName(R.string.preferences__id_backup_count),
            getIDBackupCount() + 1,
        )
    }

    override fun resetIDBackupCount() {
        preferenceStore.save(getKeyName(R.string.preferences__id_backup_count), 0)
    }

    override fun setLastIDBackupReminderTimestamp(lastIDBackupReminderTimestamp: Instant?) {
        preferenceStore.save(
            getKeyName(R.string.preferences__last_id_backup_date),
            lastIDBackupReminderTimestamp,
        )
    }

    override fun getContactSortOrder(): PreferenceService.ContactSortOrder =
        when (preferenceStore.getString(getKeyName(R.string.preferences__contact_sorting))) {
            appContext.getString(R.string.contact_sorting__first_name) -> PreferenceService.ContactSortOrder.FIRST_NAME
            else -> PreferenceService.ContactSortOrder.LAST_NAME
        }

    override fun getContactNameFormat(): ContactNameFormat =
        preferenceStore.getString(getKeyName(R.string.preferences__contact_format))
            ?.takeUnlessEmpty()
            ?.let { formatValueStored ->
                ContactNameFormat.fromValue(formatValueStored, appContext)
            }
            ?: ContactNameFormat.DEFAULT

    override fun isDefaultContactPictureColored(): Boolean =
        preferenceStore.getBoolean(getKeyName(R.string.preferences__default_contact_picture_colored), defaultValue = true)

    override fun getFontStyle(): Int {
        val fontStyle = preferenceStore.getString(getKeyName(R.string.preferences__fontstyle))
        return when (fontStyle?.toIntOrNull()) {
            1 -> R.style.FontStyle_Large
            2 -> R.style.FontStyle_XLarge
            else -> R.style.FontStyle_Normal
        }
    }

    override fun getLastIDBackupReminderTimestamp(): Instant? =
        preferenceStore.getInstant(getKeyName(R.string.preferences__last_id_backup_date))

    override fun getTransmittedFeatureMask(): Long {
        // TODO(ANDR-2703): Remove this migration code
        // Delete old feature level (int) and move it to the feature mask value (long)
        val featureLevelKey = getKeyName(R.string.preferences__transmitted_feature_level)
        if (preferenceStore.containsKey(featureLevelKey)) {
            // Store feature mask as long
            setTransmittedFeatureMask(preferenceStore.getInt(featureLevelKey).toLong())

            // Remove transmitted feature level
            preferenceStore.remove(featureLevelKey)
        }

        return preferenceStore.getLong(getKeyName(R.string.preferences__transmitted_feature_mask))
    }

    override fun setTransmittedFeatureMask(transmittedFeatureMask: Long) {
        preferenceStore.save(getKeyName(R.string.preferences__transmitted_feature_mask), transmittedFeatureMask)
    }

    override fun getLastFeatureMaskTransmission(): Instant? =
        preferenceStore.getInstant(getKeyName(R.string.preferences__last_feature_mask_transmission))

    override fun setLastFeatureMaskTransmission(timestamp: Instant?) {
        preferenceStore.save(getKeyName(R.string.preferences__last_feature_mask_transmission), timestamp)
    }

    override fun getEncryptedList(listName: String): Array<String> =
        encryptedPreferenceStore.getStringArray(listName) ?: emptyArray()

    override fun setEncryptedList(listName: String, elements: Array<String>) {
        encryptedPreferenceStore.save(listName, elements)
    }

    override fun showInactiveContacts(): Boolean =
        preferenceStore.getBoolean(getKeyName(R.string.preferences__show_inactive_contacts))

    override fun getLastOnlineStatus(): Boolean =
        preferenceStore.getBoolean(getKeyName(R.string.preferences__last_online_status))

    override fun setLastOnlineStatus(online: Boolean) {
        preferenceStore.save(getKeyName(R.string.preferences__last_online_status), online)
    }

    override fun isLatestVersion(): Boolean {
        val buildNumber = ConfigUtils.getBuildNumber(appContext)
        if (buildNumber != 0) {
            return preferenceStore.getInt(getKeyName(R.string.preferences__latest_version)) >= buildNumber
        }
        return false
    }

    override fun getLatestVersion(): Int =
        preferenceStore.getInt(getKeyName(R.string.preferences__latest_version))

    override fun setLatestVersion() {
        val buildNumber = ConfigUtils.getBuildNumber(appContext)
        if (buildNumber != 0) {
            preferenceStore.save(getKeyName(R.string.preferences__latest_version), buildNumber)
        }
    }

    override fun checkForAppUpdate(): Boolean {
        val buildNumber = ConfigUtils.getBuildNumber(appContext)
        if (buildNumber == 0) {
            logger.error("Could not check for app update because build number is 0")
            return false
        }

        val lastCheckedBuildNumber = preferenceStore.getInt(getKeyName(R.string.preferences__build_version))

        if (lastCheckedBuildNumber < buildNumber) {
            preferenceStore.save(getKeyName(R.string.preferences__build_version), buildNumber)
            return true
        }

        return false
    }

    override fun getFileSendInfoShown(): Boolean =
        preferenceStore.getBoolean(getKeyName(R.string.preferences__filesend_info_shown))

    override fun setFileSendInfoShown(shown: Boolean) {
        preferenceStore.save(getKeyName(R.string.preferences__filesend_info_shown), shown)
    }

    override fun setLockoutDeadline(deadline: Instant?) {
        preferenceStore.save(getKeyName(R.string.preferences__lockout_deadline), deadline)
    }

    override fun getLockoutDeadline(): Instant? =
        preferenceStore.getInstant(getKeyName(R.string.preferences__lockout_deadline))

    override fun setLockoutTimeoutIndex(index: Int) {
        preferenceStore.save(getKeyName(R.string.preferences__lockout_timeout_index), index)
    }

    override fun getLockoutTimeoutIndex(): Int =
        preferenceStore.getInt(getKeyName(R.string.preferences__lockout_timeout_index), 0)

    override fun setLockoutAttempts(numWrongConfirmAttempts: Int) {
        preferenceStore.save(getKeyName(R.string.preferences__lockout_attempts), numWrongConfirmAttempts)
    }

    override fun getLockoutAttempts(): Int =
        preferenceStore.getInt(getKeyName(R.string.preferences__lockout_attempts))

    override fun isAnimationAutoplay(): Boolean =
        preferenceStore.getBoolean(getKeyName(R.string.preferences__gif_autoplay), defaultValue = true)

    override fun isUseProximitySensor(): Boolean =
        preferenceStore.getBoolean(getKeyName(R.string.preferences__proximity_sensor), defaultValue = true)

    override fun setAppLogoExpiresAt(expiresAt: Instant?, theme: AppLogoFileHandleProvider.Theme) {
        preferenceStore.save(
            getKeyName(
                if (theme == AppLogoFileHandleProvider.Theme.DARK) {
                    R.string.preferences__app_logo_dark_expires_at
                } else {
                    R.string.preferences__app_logo_light_expires_at
                },
            ),
            expiresAt,
        )
    }

    override fun getAppLogoExpiresAt(theme: AppLogoFileHandleProvider.Theme): Instant? =
        preferenceStore.getInstant(
            getKeyName(
                if (theme == AppLogoFileHandleProvider.Theme.DARK) {
                    R.string.preferences__app_logo_dark_expires_at
                } else {
                    R.string.preferences__app_logo_light_expires_at
                },
            ),
        )

    override fun arePrivateChatsHidden(): Boolean =
        preferenceStore.getBoolean(getKeyName(R.string.preferences__chats_hidden))

    override fun setArePrivateChatsHidden(hidden: Boolean) {
        logger.info("Set private chats hidden = {}", hidden)
        preferenceStore.save(getKeyName(R.string.preferences__chats_hidden), hidden)
    }

    override fun watchArePrivateChatsHidden(): Flow<Boolean> =
        preferenceStore.watchBoolean(getKeyName(R.string.preferences__chats_hidden))

    override fun isContactSyncEnabled(): Boolean =
        preferenceStore.getBoolean(getKeyName(R.string.preferences__sync_contacts))

    override fun watchIsContactSyncEnabled(): Flow<Boolean> =
        preferenceStore.watchBoolean(getKeyName(R.string.preferences__sync_contacts))

    override fun getLockMechanism(): LockMechanism =
        preferenceStore.getString(getKeyName(R.string.preferences__lock_mechanism))
            ?.let(LockMechanism::deserialize)
            ?: LockMechanism.NONE

    override fun hasLockMechanism(): Boolean =
        getLockMechanism() != LockMechanism.NONE

    override fun setLockMechanism(lockMechanism: LockMechanism) {
        preferenceStore.save(getKeyName(R.string.preferences__lock_mechanism), lockMechanism.serialized)
    }

    override fun isAppLockEnabled(): Boolean =
        preferenceStore.getBoolean(getKeyName(R.string.preferences__app_lock_enabled)) && hasLockMechanism()

    override fun setAppLockEnabled(enabled: Boolean) {
        preferenceStore.save(
            getKeyName(R.string.preferences__app_lock_enabled),
            enabled && hasLockMechanism(),
        )
    }

    override fun isShowImageAttachPreviewsEnabled(): Boolean =
        preferenceStore.getBoolean(getKeyName(R.string.preferences__image_attach_previews), defaultValue = true)

    override fun isDirectShare(): Boolean =
        preferenceStore.getBoolean(getKeyName(R.string.preferences__direct_share), defaultValue = true)

    override fun setMessageDrafts(messageDrafts: Map<ConversationIdObfuscated, String?>?) {
        encryptedPreferenceStore
            .save(
                getKeyName(R.string.preferences__message_drafts),
                messageDrafts?.mapKeys { entry ->
                    entry.key.value
                },
            )
    }

    override fun getMessageDrafts(): Map<ConversationIdObfuscated, String?>? =
        encryptedPreferenceStore
            .getMap(getKeyName(R.string.preferences__message_drafts))
            ?.mapKeys { entry ->
                ConversationIdObfuscated(entry.key)
            }

    override fun setQuoteDrafts(quoteDrafts: Map<ConversationIdObfuscated, String?>?) {
        encryptedPreferenceStore
            .save(
                getKeyName(R.string.preferences__quote_drafts),
                quoteDrafts?.mapKeys { (conversationIdObfuscated, _) ->
                    conversationIdObfuscated.value
                },
            )
    }

    override fun getQuoteDrafts(): Map<ConversationIdObfuscated, String?>? =
        encryptedPreferenceStore
            .getMap(getKeyName(R.string.preferences__quote_drafts))
            ?.mapKeys { entry ->
                ConversationIdObfuscated(entry.key)
            }

    override fun setCustomSupportUrl(supportUrl: String?) {
        encryptedPreferenceStore.save(getKeyName(R.string.preferences__custom_support_url), supportUrl)
    }

    override fun getCustomSupportUrl(): String? =
        encryptedPreferenceStore.getString(getKeyName(R.string.preferences__custom_support_url))
            ?.takeUnlessEmpty()

    override fun getDiverseEmojiPrefs(): Map<String, String?> =
        preferenceStore.getMap(getKeyName(R.string.preferences__diverse_emojis))

    override fun setDiverseEmojiPrefs(diverseEmojis: Map<String, String?>) {
        preferenceStore.save(getKeyName(R.string.preferences__diverse_emojis), diverseEmojis)
    }

    override fun isWebClientEnabled(): Boolean =
        preferenceStore.getBoolean(getKeyName(R.string.preferences__web_client_enabled))

    override fun setWebClientEnabled(enabled: Boolean) =
        preferenceStore.save(getKeyName(R.string.preferences__web_client_enabled), enabled)

    override fun setPushToken(fcmToken: String?) {
        encryptedPreferenceStore.save(getKeyName(R.string.preferences__push_token), fcmToken)
    }

    override fun getPushToken(): String? =
        encryptedPreferenceStore.getString(getKeyName(R.string.preferences__push_token))

    override fun getProfilePicRelease(): Int =
        preferenceStore.getInt(getKeyName(R.string.preferences__profile_pic_release))

    override fun setProfilePicRelease(value: Int) {
        preferenceStore.save(getKeyName(R.string.preferences__profile_pic_release), value)
    }

    override fun watchProfilePicRelease(): Flow<Int> =
        preferenceStore.watchInt(getKeyName(R.string.preferences__profile_pic_release))

    override fun getProfilePicUploadTimestamp(): Instant? =
        preferenceStore.getInstant(getKeyName(R.string.preferences__profile_pic_upload_date))

    @Serializable
    private data class SerializableProfilePictureUploadData(
        val id: String,
        val key: String,
        val size: Int,
    )

    override fun setProfilePicUploadData(result: ProfilePictureUploadData?) {
        if (result != null) {
            val data = SerializableProfilePictureUploadData(
                id = Base64.encode(result.blobId),
                key = Base64.encode(result.encryptionKey),
                size = result.size,
            )

            encryptedPreferenceStore.save(getKeyName(R.string.preferences__profile_pic_upload_data), Json.encodeToString(data))
        }

        val uploadDate = if (result != null) Instant.ofEpochMilli(result.uploadedAt) else null
        preferenceStore.save(getKeyName(R.string.preferences__profile_pic_upload_date), uploadDate)
    }

    override fun getProfilePicUploadData(): ProfilePictureUploadData? {
        val fromStore = encryptedPreferenceStore.getString(getKeyName(R.string.preferences__profile_pic_upload_data))
        if (fromStore != null) {
            try {
                val data = Json.decodeFromString<SerializableProfilePictureUploadData>(fromStore)
                return ProfilePictureUploadData()
                    .apply {
                        this.blobId = Base64.decode(data.id)
                        this.encryptionKey = Base64.decode(data.key)
                        this.size = data.size
                    }
            } catch (e: Exception) {
                logger.error("Failed to decode profile picture upload data", e)
                return null
            }
        }
        return null
    }

    override fun getProfilePicReceive(): Boolean =
        preferenceStore.getBoolean(getKeyName(R.string.preferences__receive_profilepics), defaultValue = true)

    override fun getAECMode(): String =
        when (preferenceStore.getString(getKeyName(R.string.preferences__voip_echocancel))) {
            "sw" -> "sw"
            else -> "hw"
        }

    override fun getVideoCodec(): String =
        preferenceStore.getString(getKeyName(R.string.preferences__voip_video_codec))
            ?: PreferenceService.VIDEO_CODEC_HW

    override fun isRejectMobileCalls(): Boolean =
        preferenceStore.getBoolean(getKeyName(R.string.preferences__voip_reject_mobile_calls))

    override fun setRejectMobileCalls(value: Boolean) {
        preferenceStore.save(getKeyName(R.string.preferences__voip_reject_mobile_calls), value)
    }

    override fun shouldAbortCallOnBluetoothDisconnect(): Boolean =
        preferenceStore.getBoolean(getKeyName(R.string.preferences__voip_abort_on_bluetooth_disconnect), true)

    override fun isIpv6Preferred(): Boolean =
        preferenceStore.getBoolean(getKeyName(R.string.preferences__ipv6_preferred))

    override fun allowWebrtcIpv6(): Boolean =
        preferenceStore.getBoolean(getKeyName(R.string.preferences__ipv6_webrtc_allowed), defaultValue = true)

    override fun getMobileAutoDownload(): Set<PreferenceService.AutoDownloadType> =
        preferenceStore.getStringSet(getKeyName(R.string.preferences__auto_download_mobile))
            ?.mapNotNull(PreferenceService.AutoDownloadType::deserialize)
            ?.toSet()
            ?: setOf(PreferenceService.AutoDownloadType.IMAGE)

    override fun setMobileAutoDownload(types: Set<PreferenceService.AutoDownloadType>) {
        preferenceStore.save(
            getKeyName(R.string.preferences__auto_download_mobile),
            types.map(PreferenceService.AutoDownloadType::serialized).toSet(),
        )
    }

    override fun getWifiAutoDownload(): Set<PreferenceService.AutoDownloadType> =
        preferenceStore.getStringSet(getKeyName(R.string.preferences__auto_download_wifi))
            ?.mapNotNull(PreferenceService.AutoDownloadType::deserialize)
            ?.toSet()
            ?: setOf(
                PreferenceService.AutoDownloadType.IMAGE,
                PreferenceService.AutoDownloadType.VOICE_MESSAGE,
                PreferenceService.AutoDownloadType.VIDEO,
                PreferenceService.AutoDownloadType.FILE,
            )

    override fun setWifiAutoDownload(types: Set<PreferenceService.AutoDownloadType>) {
        preferenceStore.save(
            getKeyName(R.string.preferences__auto_download_wifi),
            types.map(PreferenceService.AutoDownloadType::serialized).toSet(),
        )
    }

    override fun setRatingReference(reference: String?) {
        encryptedPreferenceStore.save(getKeyName(R.string.preferences__rate_ref), reference)
    }

    override fun getRatingReference(): String? =
        encryptedPreferenceStore.getString(getKeyName(R.string.preferences__rate_ref))

    override fun setRatingReviewText(review: String?) {
        encryptedPreferenceStore.save(getKeyName(R.string.preferences__rate_text), review)
    }

    override fun getRatingReviewText(): String? =
        encryptedPreferenceStore.getString(getKeyName(R.string.preferences__rate_text))

    override fun setPrivacyPolicyAccepted(timestamp: Instant?, source: Int) {
        preferenceStore.save(getKeyName(R.string.preferences__privacy_policy_accept_date), timestamp)
        preferenceStore.save(getKeyName(R.string.preferences__privacy_policy_accept_source), source)
    }

    override fun getPrivacyPolicyAccepted(): Instant? {
        val acceptSource = preferenceStore.getInt(getKeyName(R.string.preferences__privacy_policy_accept_source))
        if (acceptSource != PreferenceService.PRIVACY_POLICY_ACCEPT_NONE) {
            return preferenceStore.getInstant(getKeyName(R.string.preferences__privacy_policy_accept_date))
        }
        return null
    }

    override fun getIsGroupCallsTooltipShown(): Boolean =
        preferenceStore.getBoolean(getKeyName(R.string.preferences__group_calls_tooltip_shown))

    override fun setGroupCallsTooltipShown(shown: Boolean) {
        preferenceStore.save(getKeyName(R.string.preferences__group_calls_tooltip_shown), shown)
    }

    override fun getIsWorkHintTooltipShown(): Boolean =
        preferenceStore.getBoolean(getKeyName(R.string.preferences__tooltip_work_hint_shown))

    override fun setIsWorkHintTooltipShown(shown: Boolean) {
        preferenceStore.save(getKeyName(R.string.preferences__tooltip_work_hint_shown), shown)
    }

    override fun getIsFaceBlurTooltipShown(): Boolean =
        preferenceStore.getBoolean(getKeyName(R.string.preferences__tooltip_face_blur_shown))

    override fun setFaceBlurTooltipShown(shown: Boolean) {
        preferenceStore.save(getKeyName(R.string.preferences__tooltip_face_blur_shown), shown)
    }

    override fun isMultipleRecipientsTooltipShown() =
        preferenceStore.getInt(getKeyName(R.string.preferences__tooltip_multi_recipients)) > 0

    override fun setMultipleRecipientsTooltipShown(shown: Boolean) {
        preferenceStore.save(getKeyName(R.string.preferences__tooltip_multi_recipients), if (shown) 1 else 0)
    }

    override fun isOneTimeDialogShown(dialogTag: String): Boolean =
        preferenceStore.getBoolean(ONE_TIME_DIALOG_PREFIX + dialogTag)

    override fun setOneTimeDialogShown(dialogTag: String, shown: Boolean) {
        preferenceStore.save(ONE_TIME_DIALOG_PREFIX + dialogTag, shown)
    }

    override fun isTooltipPopupDismissed(key: Int): Boolean =
        preferenceStore.getBoolean(getKeyName(key))

    override fun setTooltipPopupDismissed(key: Int, dismissed: Boolean) {
        preferenceStore.save(getKeyName(key), dismissed)
        if (key == R.string.preferences__tooltip_emoji_reactions_shown && !dismissed) {
            preferenceStore.save(getKeyName(R.string.preferences__tooltip_emoji_reactions_shown_counter), 0)
        }
    }

    override fun setThreemaSafeEnabled(value: Boolean) {
        preferenceStore.save(getKeyName(R.string.preferences__threema_safe_enabled), value)
    }

    override fun getThreemaSafeEnabled(): Boolean =
        preferenceStore.getBoolean(getKeyName(R.string.preferences__threema_safe_enabled))

    override fun watchThreemaSafeEnabled(): Flow<Boolean> =
        preferenceStore.watchBoolean(getKeyName(R.string.preferences__threema_safe_enabled))

    override fun setThreemaSafeMasterKey(masterKey: ByteArray?) {
        encryptedPreferenceStore.save(getKeyName(R.string.preferences__threema_safe_masterkey), masterKey)
        ThreemaSafeMDMConfig.getInstance().saveConfig(this)
    }

    override fun getThreemaSafeMasterKey(): ByteArray? =
        encryptedPreferenceStore.getBytes(getKeyName(R.string.preferences__threema_safe_masterkey))

    override fun setThreemaSafeServerInfo(serverInfo: ThreemaSafeServerInfo?) {
        encryptedPreferenceStore.save(getKeyName(R.string.preferences__threema_safe_server_name), serverInfo?.customServerName)
        encryptedPreferenceStore.save(getKeyName(R.string.preferences__threema_safe_server_username), serverInfo?.serverUsername)
        encryptedPreferenceStore.save(getKeyName(R.string.preferences__threema_safe_server_password), serverInfo?.serverPassword)
    }

    override fun getThreemaSafeServerInfo(): ThreemaSafeServerInfo =
        ThreemaSafeServerInfo(
            encryptedPreferenceStore.getString(getKeyName(R.string.preferences__threema_safe_server_name)),
            encryptedPreferenceStore.getString(getKeyName(R.string.preferences__threema_safe_server_username)),
            encryptedPreferenceStore.getString(getKeyName(R.string.preferences__threema_safe_server_password)),
        )

    override fun setThreemaSafeUploadTimestamp(timestamp: Instant?) {
        preferenceStore.save(getKeyName(R.string.preferences__threema_safe_backup_date), timestamp)
    }

    override fun getThreemaSafeUploadTimestamp(): Instant? =
        preferenceStore.getInstant(getKeyName(R.string.preferences__threema_safe_backup_date))

    override fun getShowUnreadBadge(): Boolean =
        preferenceStore.getBoolean(getKeyName(R.string.preferences__show_unread_badge), defaultValue = true)

    override fun setThreemaSafeErrorCode(code: Int) {
        preferenceStore.save(getKeyName(R.string.preferences__threema_safe_error_code), code)
    }

    override fun getThreemaSafeErrorCode(): Int =
        preferenceStore.getInt(getKeyName(R.string.preferences__threema_safe_error_code))

    override fun setThreemaSafeErrorTimestamp(timestamp: Instant?) {
        preferenceStore.save(getKeyName(R.string.preferences__threema_safe_create_error_date), timestamp)
    }

    override fun getThreemaSafeErrorTimestamp(): Instant? =
        preferenceStore.getInstant(getKeyName(R.string.preferences__threema_safe_create_error_date))

    override fun setThreemaSafeServerMaxUploadSize(maxBackupBytes: Long) {
        preferenceStore.save(getKeyName(R.string.preferences__threema_safe_server_upload_size), maxBackupBytes)
    }

    override fun getThreemaSafeServerMaxUploadSize(): Long =
        preferenceStore.getLong(getKeyName(R.string.preferences__threema_safe_server_upload_size))

    override fun setThreemaSafeServerRetention(days: Int) {
        preferenceStore.save(getKeyName(R.string.preferences__threema_safe_server_retention), days)
    }

    override fun getThreemaSafeServerRetention(): Int =
        preferenceStore.getInt(getKeyName(R.string.preferences__threema_safe_server_retention))

    override fun setThreemaSafeBackupSize(size: Int) {
        preferenceStore.save(getKeyName(R.string.preferences__threema_safe_upload_size), size)
    }

    override fun getThreemaSafeBackupSize(): Int =
        preferenceStore.getInt(getKeyName(R.string.preferences__threema_safe_upload_size))

    override fun setThreemaSafeHashString(hashString: String?) {
        preferenceStore.save(getKeyName(R.string.preferences__threema_safe_hash_string), hashString)
    }

    override fun getThreemaSafeHashString(): String? =
        preferenceStore.getString(getKeyName(R.string.preferences__threema_safe_hash_string))

    override fun setThreemaSafeBackupTimestamp(timestamp: Instant?) {
        preferenceStore.save(getKeyName(R.string.preferences__threema_safe_backup_date), timestamp)
    }

    override fun getThreemaSafeBackupTimestamp(): Instant? =
        preferenceStore.getInstant(getKeyName(R.string.preferences__threema_safe_backup_date))

    override fun setWorkSyncCheckInterval(checkInterval: Duration) {
        preferenceStore.save(getKeyName(R.string.preferences__work_sync_check_interval), checkInterval.inWholeSeconds.toInt())
    }

    override fun getWorkSyncCheckInterval(): Duration =
        preferenceStore.getInt(getKeyName(R.string.preferences__work_sync_check_interval))
            .takeUnless { it <= 0 }
            ?.seconds
            ?: 1.days

    override fun setIdentityStateSyncInterval(syncInterval: Duration) {
        preferenceStore.save(getKeyName(R.string.preferences__identity_states_check_interval), syncInterval.inWholeSeconds.toInt())
    }

    override fun getIdentityStateSyncInterval(): Duration =
        preferenceStore.getInt(getKeyName(R.string.preferences__identity_states_check_interval))
            .takeUnless { it <= 0 }
            ?.seconds
            ?: 1.days

    override fun getIsExportIdTooltipShown(): Boolean =
        preferenceStore.getBoolean(getKeyName(R.string.preferences__tooltip_export_id_shown))

    override fun setThreemaSafeMDMConfig(mdmConfigHash: String?) {
        encryptedPreferenceStore.save(getKeyName(R.string.preferences__work_safe_mdm_config), mdmConfigHash)
    }

    override fun getThreemaSafeMDMConfig(): String? =
        encryptedPreferenceStore.getString(getKeyName(R.string.preferences__work_safe_mdm_config))

    override fun setWorkDirectoryEnabled(enabled: Boolean) {
        preferenceStore.save(getKeyName(R.string.preferences__work_directory_enabled), enabled)
    }

    override fun getWorkDirectoryEnabled(): Boolean =
        preferenceStore.getBoolean(getKeyName(R.string.preferences__work_directory_enabled))

    override fun setWorkDirectoryCategories(categories: List<WorkDirectoryCategory>) {
        encryptedPreferenceStore.save(
            getKeyName(R.string.preferences__work_directory_categories),
            Json.encodeToString(categories),
        )
    }

    override fun getWorkDirectoryCategories(): List<WorkDirectoryCategory> =
        encryptedPreferenceStore.getString(getKeyName(R.string.preferences__work_directory_categories))
            ?.let(Json::decodeFromStringOrNull)
            ?: emptyList()

    override fun setWorkOrganization(organization: WorkOrganization?) {
        encryptedPreferenceStore.save(
            getKeyName(R.string.preferences__work_directory_organization),
            organization?.let(Json::encodeToString),
        )
    }

    override fun getWorkOrganization(): WorkOrganization? =
        encryptedPreferenceStore.getString(getKeyName(R.string.preferences__work_directory_organization))
            ?.let(Json::decodeFromStringOrNull)

    override fun setLicensedStatus(licensed: Boolean) {
        preferenceStore.save(getKeyName(R.string.preferences__license_status), licensed)
    }

    override fun getLicensedStatus(): Boolean =
        preferenceStore.getBoolean(getKeyName(R.string.preferences__license_status), false)

    override fun setShowDeveloperMenu(show: Boolean) {
        preferenceStore.save(getKeyName(R.string.preferences__developer_menu), show)
    }

    override fun showDeveloperMenu(): Boolean =
        preferenceStore.getBoolean(getKeyName(R.string.preferences__developer_menu), BuildConfig.DEBUG)

    override fun getLegacyDataBackupUri(): Uri? =
        preferenceStore.getString(getKeyName(R.string.preferences__data_backup_uri))
            ?.takeUnlessEmpty()
            ?.toUri()

    override fun setLegacyDataBackupUri(newUri: Uri?) {
        preferenceStore.save(getKeyName(R.string.preferences__data_backup_uri), newUri?.toString())
    }

    override fun getLegacyDataBackupPickerLaunched(): Instant? =
        preferenceStore.getInstant(getKeyName(R.string.preferences__data_backup_picker_launched))

    override fun setLegacyDataBackupPickerLaunched(timestamp: Instant?) {
        preferenceStore.save(getKeyName(R.string.preferences__data_backup_picker_launched), timestamp)
    }

    override fun getLastLegacyDataBackupTimestamp(): Instant? =
        preferenceStore.getInstant(getKeyName(R.string.preferences__last_data_backup_date))

    override fun setLastLegacyDataBackupTimestamp(timestamp: Instant?) {
        preferenceStore.save(getKeyName(R.string.preferences__last_data_backup_date), timestamp)
    }

    override fun getLocalDataBackupPassword(): LocalDataBackupPassword? =
        encryptedPreferenceStore.getString(getKeyName(R.string.preferences__local_backup_password))
            ?.let(::LocalDataBackupPassword)

    override fun setLocalDataBackupPassword(localDataBackupPassword: LocalDataBackupPassword?) {
        encryptedPreferenceStore.save(getKeyName(R.string.preferences__local_backup_password), localDataBackupPassword?.passwordString)
    }

    override fun getLastLocalDataBackupInfo(): LastLocalDataBackupInfo? =
        preferenceStore.getString(getKeyName(R.string.preferences__last_local_backup_info))
            ?.let(Json::decodeFromStringOrNull)

    override fun setLastLocalDataBackupInfo(info: LastLocalDataBackupInfo?) {
        preferenceStore.save(getKeyName(R.string.preferences__last_local_backup_info), info?.let(Json::encodeToString))
    }

    override fun watchLastLocalDataBackupInfo(): Flow<LastLocalDataBackupInfo?> =
        preferenceStore.watchString(getKeyName(R.string.preferences__last_local_backup_info))
            .map { jsonString ->
                jsonString?.let {
                    Json.decodeFromStringOrNull<LastLocalDataBackupInfo>(jsonString)
                }
            }
            .flowOn(dispatcherProvider.worker)

    override fun getLocalDataBackupLocation(): Uri? =
        preferenceStore.getString(getKeyName(R.string.preferences__local_backup_location))
            ?.toUri()

    override fun setLocalDataBackupLocation(location: Uri?) {
        preferenceStore.save(getKeyName(R.string.preferences__local_backup_location), location?.toString())
    }

    override fun getMatchToken(): String? =
        preferenceStore.getString(getKeyName(R.string.preferences__match_token))

    override fun setMatchToken(matchToken: String?) {
        preferenceStore.save(getKeyName(R.string.preferences__match_token), matchToken)
    }

    override fun isAfterWorkDNDEnabled(): Boolean =
        preferenceStore.getBoolean(getKeyName(R.string.preferences__working_days_enable))

    override fun setAfterWorkDNDEnabled(enabled: Boolean) {
        preferenceStore.save(getKeyName(R.string.preferences__working_days_enable), enabled)
    }

    override fun setCameraFlashMode(flashMode: Int) {
        preferenceStore.save(getKeyName(R.string.preferences__camera_flash_mode), flashMode)
    }

    override fun getCameraFlashMode(): Int =
        preferenceStore.getInt(getKeyName(R.string.preferences__camera_flash_mode))

    override fun setPipPosition(pipPosition: Int) {
        preferenceStore.save(getKeyName(R.string.preferences__pip_position), pipPosition)
    }

    override fun getPipPosition(): Int =
        preferenceStore.getInt(getKeyName(R.string.preferences__pip_position))

    override fun getVideoCallsProfile(): String =
        preferenceStore.getString(getKeyName(R.string.preferences__voip_video_profile)) ?: "0"

    override fun setPollOverviewHidden(hidden: Boolean) {
        preferenceStore.save(getKeyName(R.string.preferences__poll_overview_hidden), hidden)
    }

    override fun getPollOverviewHidden(): Boolean =
        preferenceStore.getBoolean(getKeyName(R.string.preferences__poll_overview_hidden))

    override fun isVideoCallToggleTooltipShown(): Boolean =
        preferenceStore.getInt(getKeyName(R.string.preferences__tooltip_video_toggle)) > 0

    override fun setVideoCallToggleTooltipShown(shown: Boolean) {
        preferenceStore.save(getKeyName(R.string.preferences__tooltip_video_toggle), if (shown) 1 else 0)
    }

    override fun getCameraPermissionRequestShown(): Boolean =
        preferenceStore.getBoolean(getKeyName(R.string.preferences__camera_permission_request_shown), false)

    override fun setCameraPermissionRequestShown(shown: Boolean) {
        preferenceStore.save(getKeyName(R.string.preferences__camera_permission_request_shown), shown)
    }

    override fun setVoiceRecorderBluetoothDisabled(disabled: Boolean) {
        preferenceStore.save(getKeyName(R.string.preferences__voicerecorder_bluetooth_disabled), disabled)
    }

    override fun getVoiceRecorderBluetoothDisabled(): Boolean =
        preferenceStore.getBoolean(getKeyName(R.string.preferences__voicerecorder_bluetooth_disabled), true)

    override fun setAudioPlaybackSpeed(newSpeed: Float) {
        preferenceStore.save(getKeyName(R.string.preferences__audio_playback_speed), newSpeed)
    }

    override fun getAudioPlaybackSpeed(): Float =
        preferenceStore.getFloat(getKeyName(R.string.preferences__audio_playback_speed), 1f)

    override fun isGroupCallSendInitEnabled(): Boolean =
        preferenceStore.getBoolean(getKeyName(R.string.preferences__group_call_send_init))

    override fun skipGroupCallCreateDelay(): Boolean =
        preferenceStore.getBoolean(getKeyName(R.string.preferences__group_call_skip_delay))

    override fun isBackupWarningDismissed(): Boolean =
        preferenceStore.getInstant(getKeyName(R.string.preferences__backup_warning_dismissed_time)) != null

    override fun setBackupWarningDismissed(isDismissed: Boolean) {
        preferenceStore.save(getKeyName(R.string.preferences__backup_warning_dismissed_time), if (isDismissed) timeProvider.get() else null)
    }

    @StarredMessagesSortOrder
    override fun getStarredMessagesSortOrder(): Int =
        preferenceStore.getInt(getKeyName(R.string.preferences__starred_messages_sort_order))

    override fun setStarredMessagesSortOrder(@StarredMessagesSortOrder order: Int) {
        preferenceStore.save(getKeyName(R.string.preferences__starred_messages_sort_order), order)
    }

    override fun setAutoDeleteDays(days: Int) {
        preferenceStore.save(getKeyName(R.string.preferences__auto_delete_days), days)
    }

    override fun getAutoDeleteDays(): Int {
        val autoDeleteDays = preferenceStore.getInt(getKeyName(R.string.preferences__auto_delete_days))
        return AutoDeleteUtil.validateKeepMessageDays(autoDeleteDays)
    }

    override fun getMediaGalleryContentTypes(): BooleanArray {
        val contentTypes = BooleanArray(SELECTABLE_CONTENT_TYPES.size) { true }
        preferenceStore.getString(getKeyName(R.string.preferences__media_gallery_content_types))
            ?.let { jsonString ->
                Json.decodeFromStringOrNull<JsonArray>(jsonString)
            }
            ?.let { jsonArray ->
                jsonArray
                    .take(SELECTABLE_CONTENT_TYPES.size)
                    .forEachIndexed { index, jsonElement ->
                        jsonElement.booleanOrNull
                            ?.let { value ->
                                contentTypes[index] = value
                            }
                    }
            }
        return contentTypes
    }

    override fun setMediaGalleryContentTypes(contentTypes: BooleanArray) {
        val jsonArray = buildJsonArray {
            contentTypes.forEach { contentType ->
                add(contentType)
            }
        }
        preferenceStore.save(getKeyName(R.string.preferences__media_gallery_content_types), jsonArray.toString())
    }

    override fun getEmailSyncHashCode(): Int =
        preferenceStore.getInt(getKeyName(R.string.preferences__email_sync_hash))

    override fun getPhoneNumberSyncHashCode(): Int =
        preferenceStore.getInt(getKeyName(R.string.preferences__phone_number_sync_hash))

    override fun setEmailSyncHashCode(emailsHash: Int) {
        preferenceStore.save(getKeyName(R.string.preferences__email_sync_hash), emailsHash)
    }

    override fun setPhoneNumberSyncHashCode(phoneNumbersHash: Int) {
        preferenceStore.save(getKeyName(R.string.preferences__phone_number_sync_hash), phoneNumbersHash)
    }

    override fun setTimeOfLastContactSync(timestamp: Instant?) {
        preferenceStore.save(getKeyName(R.string.preferences__contact_sync_time), timestamp)
    }

    override fun getTimeOfLastContactSync(): Instant? =
        preferenceStore.getInstant(getKeyName(R.string.preferences__contact_sync_time))

    override fun showMessageDebugInfo(): Boolean =
        preferenceStore.getBoolean(getKeyName(R.string.preferences__message_debug_info), false)

    override fun getLastShortcutUpdateTimestamp(): Instant? =
        preferenceStore.getInstant(getKeyName(R.string.preferences__last_shortcut_update_date))

    override fun setLastShortcutUpdateTimestamp(timestamp: Instant?) {
        preferenceStore.save(getKeyName(R.string.preferences__last_shortcut_update_date), timestamp)
    }

    override fun hasRequestedNotificationPermission(): Boolean =
        preferenceStore.getInstant(getKeyName(R.string.preferences__last_notification_request_timestamp)) != null

    override fun setHasRequestedNotificationPermission(requested: Boolean) {
        preferenceStore.save(getKeyName(R.string.preferences__last_notification_request_timestamp), if (requested) timeProvider.get() else null)
    }

    override fun getLastMultiDeviceGroupCheckTimestamp(): Instant? =
        preferenceStore.getInstant(getKeyName(R.string.preferences__last_multi_device_group_check_timestamp))

    override fun setLastMultiDeviceGroupCheckTimestamp(timestamp: Instant) {
        preferenceStore.save(getKeyName(R.string.preferences__last_multi_device_group_check_timestamp), timestamp)
    }

    override fun getErrorReportingState(): ErrorReportingState =
        when (preferenceStore.getString(getKeyName(R.string.preferences__error_reporting))) {
            appContext.getString(R.string.error_reporting_value_always_send) -> ErrorReportingState.ALWAYS_SEND
            appContext.getString(R.string.error_reporting_value_never_send) -> ErrorReportingState.NEVER_SEND
            appContext.getString(R.string.error_reporting_value_always_ask) -> ErrorReportingState.ALWAYS_ASK
            else -> if (BuildConfig.DEBUG) {
                ErrorReportingState.NEVER_SEND
            } else if (BuildFlavor.IS_SANDBOX) {
                ErrorReportingState.ALWAYS_SEND
            } else {
                ErrorReportingState.ALWAYS_ASK
            }
        }

    override fun setErrorReportingState(state: ErrorReportingState) {
        preferenceStore.save(
            getKeyName(R.string.preferences__error_reporting),
            when (state) {
                ErrorReportingState.ALWAYS_SEND -> appContext.getString(R.string.error_reporting_value_always_send)
                ErrorReportingState.NEVER_SEND -> appContext.getString(R.string.error_reporting_value_never_send)
                else -> appContext.getString(R.string.error_reporting_value_always_ask)
            },
        )
    }

    override fun getProblemDismissed(problemKey: String): Instant? =
        preferenceStore.getInstant(getKeyName(R.string.preferences__problem_dismissed_prefix, problemKey))

    override fun setProblemDismissed(problemKey: String, timestamp: Instant?) {
        preferenceStore.save(getKeyName(R.string.preferences__problem_dismissed_prefix, problemKey), timestamp)
    }

    override fun isDebugLogEnabled(): Boolean =
        preferenceStore.containsKey(getKeyName(R.string.preferences__debug_log_enable_time))

    override fun setDebugLogEnabledTimestamp(timestamp: Instant?) {
        preferenceStore.save(getKeyName(R.string.preferences__debug_log_enable_time), timestamp)
    }

    override fun getDebugLogEnabledTimestamp(): Instant? =
        preferenceStore.getInstant(getKeyName(R.string.preferences__debug_log_enable_time))

    override fun watchDebugLogEnabledTimestamp(): Flow<Instant?> =
        preferenceStore.watchInstant(getKeyName(R.string.preferences__debug_log_enable_time))

    override fun setAvailabilityStatus(availabilityStatus: AvailabilityStatus) {
        if (!BuildConfig.AVAILABILITY_STATUS_ENABLED) {
            logger.error("The current build does not support this feature")
            return
        }
        preferenceStore.save(
            getKeyName(R.string.preferences__availability_status),
            availabilityStatus.toJson(),
        )
    }

    override fun getAvailabilityStatus(): AvailabilityStatus? {
        if (!BuildConfig.AVAILABILITY_STATUS_ENABLED) {
            return null
        }
        return preferenceStore
            .getString(getKeyName(R.string.preferences__availability_status))
            ?.let(AvailabilityStatus::fromJson)
    }

    override fun watchAvailabilityStatus(): Flow<AvailabilityStatus?> {
        if (!BuildConfig.AVAILABILITY_STATUS_ENABLED) {
            return flowOf(null)
        }
        return preferenceStore
            .watchString(getKeyName(R.string.preferences__availability_status))
            .map { availabilityStatusJson: String? ->
                availabilityStatusJson?.let(AvailabilityStatus::fromJson)
            }
            .flowOn(dispatcherProvider.worker)
    }

    override fun shouldShowEmojiReactionNotificationsInContactConversations(): Boolean =
        preferenceStore.getBoolean(
            key = getKeyName(R.string.preferences__emoji_reaction_contact_notifications),
            defaultValue = true,
        )

    override fun shouldShowEmojiReactionNotificationsInGroupConversations(): Boolean =
        preferenceStore.getBoolean(
            key = getKeyName(R.string.preferences__emoji_reaction_group_notifications),
            defaultValue = true,
        )

    private fun getKeyName(@StringRes resourceId: Int) = appContext.getString(resourceId)

    @Suppress("SameParameterValue")
    private fun getKeyName(@StringRes resourceId: Int, suffix: String) = appContext.getString(resourceId) + suffix

    companion object {
        private const val ONE_TIME_DIALOG_PREFIX = "dialog_"

        private fun isPinCodeValid(code: String?): Boolean =
            !code.isNullOrEmpty() &&
                code.length >= AppConstants.MIN_PIN_LENGTH &&
                code.length <= AppConstants.MAX_PIN_LENGTH &&
                code.isDigitsOnly()
    }
}

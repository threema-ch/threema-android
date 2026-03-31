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
import ch.threema.app.listeners.PreferenceListener
import ch.threema.app.managers.ListenerManager
import ch.threema.app.mediagallery.MediaGalleryViewModel.Companion.SELECTABLE_CONTENT_TYPES
import ch.threema.app.preference.service.PreferenceService.EmojiStyle
import ch.threema.app.preference.service.PreferenceService.ErrorReportingState
import ch.threema.app.preference.service.PreferenceService.ImageScale
import ch.threema.app.preference.service.PreferenceService.StarredMessagesSortOrder
import ch.threema.app.services.ContactService.ProfilePictureUploadData
import ch.threema.app.stores.EncryptedPreferenceStore
import ch.threema.app.stores.PreferenceStore
import ch.threema.app.threemasafe.ThreemaSafeMDMConfig
import ch.threema.app.threemasafe.ThreemaSafeServerInfo
import ch.threema.app.utils.AutoDeleteUtil
import ch.threema.app.utils.ConfigUtils
import ch.threema.app.utils.ConfigUtils.AppThemeSetting
import ch.threema.base.utils.Base64
import ch.threema.base.utils.getThreemaLogger
import ch.threema.common.secureContentEquals
import ch.threema.common.takeUnlessEmpty
import ch.threema.data.datatypes.ContactNameFormat
import ch.threema.domain.protocol.api.work.WorkDirectoryCategory
import ch.threema.domain.protocol.api.work.WorkOrganization
import java.time.Instant
import java.util.Arrays
import java.util.LinkedList
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

private val logger = getThreemaLogger("PreferenceServiceImpl")

class PreferenceServiceImpl(
    private val appContext: Context,
    private val preferenceStore: PreferenceStore,
    private val encryptedPreferenceStore: EncryptedPreferenceStore,
) : PreferenceService {
    override fun isCustomWallpaperEnabled(): Boolean =
        preferenceStore.getBoolean(getKeyName(R.string.preferences__wallpaper_switch))

    override fun setCustomWallpaperEnabled(enabled: Boolean) {
        preferenceStore.save(getKeyName(R.string.preferences__wallpaper_switch), enabled)
    }

    override fun isEnterToSend(): Boolean =
        preferenceStore.getBoolean(getKeyName(R.string.preferences__enter_to_send))

    override fun isInAppSounds(): Boolean =
        preferenceStore.getBoolean(getKeyName(R.string.preferences__inapp_sounds))

    override fun isInAppVibrate(): Boolean =
        preferenceStore.getBoolean(getKeyName(R.string.preferences__inapp_vibrate))

    @ImageScale
    override fun getImageScale(): Int {
        val imageScale = preferenceStore.getString(getKeyName(R.string.preferences__image_size))
        return when (imageScale?.toIntOrNull()) {
            0 -> PreferenceService.IMAGE_SCALE_SMALL
            2 -> PreferenceService.IMAGE_SCALE_LARGE
            3 -> PreferenceService.IMAGE_SCALE_XLARGE
            4 -> PreferenceService.IMAGE_SCALE_ORIGINAL
            else -> PreferenceService.IMAGE_SCALE_MEDIUM
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

    @Deprecated("")
    override fun getRecentEmojis(): LinkedList<Int> {
        val list = LinkedList<Int>()
        val array = preferenceStore.getJSONArray(getKeyName(R.string.preferences__recent_emojis))
        for (i in 0..<array.length()) {
            try {
                list.add(array.getInt(i))
            } catch (e: JSONException) {
                logger.error("Failed to parse recent emoji", e)
            }
        }
        return list
    }

    override fun getRecentEmojis2(): LinkedList<String> =
        LinkedList(
            preferenceStore.getStringArray(getKeyName(R.string.preferences__recent_emojis2))
                ?.toList()
                ?: emptyList(),
        )

    @Deprecated("")
    override fun setRecentEmojis(list: LinkedList<Int>?) {
        preferenceStore.save(getKeyName(R.string.preferences__recent_emojis), JSONArray(list))
    }

    override fun setRecentEmojis2(list: LinkedList<String>) {
        preferenceStore.save(getKeyName(R.string.preferences__recent_emojis2), list.toTypedArray<String>())
    }

    override fun getEmojiSearchIndexVersion(): Int =
        preferenceStore.getInt(getKeyName(R.string.preferences__emoji_search_index_version), defaultValue = -1)

    override fun setEmojiSearchIndexVersion(version: Int) {
        preferenceStore.save(getKeyName(R.string.preferences__emoji_search_index_version), version)
    }

    override fun useThreemaPush(): Boolean =
        preferenceStore.getBoolean(getKeyName(R.string.preferences__threema_push_switch))

    override fun setUseThreemaPush(value: Boolean) {
        preferenceStore.save(getKeyName(R.string.preferences__threema_push_switch), value)
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

    override fun getPinLockGraceTime(): Int =
        preferenceStore.getString(getKeyName(R.string.preferences__pin_lock_grace_time))
            ?.toIntOrNull()
            ?.takeIf { it >= 30 || it == -1 }
            ?: -1

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

    override fun getContactListSorting(): String {
        val sorting = preferenceStore.getString(getKeyName(R.string.preferences__contact_sorting))
            ?.takeUnlessEmpty()
        if (sorting == null) {
            val sorting = appContext.getString(R.string.contact_sorting__last_name)
            preferenceStore.save(getKeyName(R.string.preferences__contact_sorting), sorting)
            return sorting
        }
        return sorting
    }

    override fun isContactListSortingFirstName(): Boolean =
        getContactListSorting() == appContext.getString(R.string.contact_sorting__first_name)

    override fun getContactNameFormat(): ContactNameFormat {
        val prefKeyName = getKeyName(R.string.preferences__contact_format)
        var formatValueStored = preferenceStore.getString(prefKeyName)
            ?.takeUnlessEmpty()
        if (formatValueStored == null) {
            formatValueStored = appContext.getString(ContactNameFormat.DEFAULT.valueRes)
            preferenceStore.save(prefKeyName, formatValueStored)
        }

        return ContactNameFormat.fromValue(formatValueStored, appContext)
            ?: ContactNameFormat.DEFAULT
    }

    override fun isDefaultContactPictureColored(): Boolean =
        preferenceStore.getBoolean(getKeyName(R.string.preferences__default_contact_picture_colored))

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

    override fun getEncryptedList(listName: String): Array<String> {
        var res = encryptedPreferenceStore.getStringArray(listName)
        if (res == null) {
            // check if we have an old unencrypted identity list - migrate if necessary and return its values
            if (preferenceStore.containsKey(listName)) {
                res = preferenceStore.getStringArray(listName)
                preferenceStore.remove(listName)
                if (res != null) {
                    encryptedPreferenceStore.save(listName, res)
                }
            }
        }
        return res ?: emptyArray()
    }

    override fun getList(listName: String): Array<String> =
        preferenceStore.getStringArray(listName)
            ?: emptyArray()

    override fun setList(listName: String, elements: Array<String>) {
        encryptedPreferenceStore.save(listName, elements)
    }

    override fun setEncryptedListQuietly(listName: String, elements: Array<String>) {
        encryptedPreferenceStore.saveQuietly(listName, elements)
    }

    override fun setListQuietly(listName: String, elements: Array<String>) {
        preferenceStore.saveQuietly(listName, elements)
    }

    override fun getStringMap(listName: String): Map<String, String?> =
        preferenceStore.getMap(listName)

    override fun setStringMap(listName: String, map: Map<String, String?>) {
        preferenceStore.save(listName, map)
    }

    override fun showInactiveContacts(): Boolean =
        preferenceStore.getBoolean(getKeyName(R.string.preferences__show_inactive_contacts))

    override fun getLastOnlineStatus(): Boolean =
        preferenceStore.getBoolean(getKeyName(R.string.preferences__last_online_status))

    override fun setLastOnlineStatus(online: Boolean) {
        preferenceStore.save(getKeyName(R.string.preferences__last_online_status), online)
    }

    override fun isLatestVersion(context: Context): Boolean {
        val buildNumber = ConfigUtils.getBuildNumber(context)
        if (buildNumber != 0) {
            return preferenceStore.getInt(getKeyName(R.string.preferences__latest_version)) >= buildNumber
        }
        return false
    }

    override fun getLatestVersion(): Int =
        preferenceStore.getInt(getKeyName(R.string.preferences__latest_version))

    override fun setLatestVersion(context: Context) {
        val buildNumber = ConfigUtils.getBuildNumber(context)
        if (buildNumber != 0) {
            preferenceStore.save(getKeyName(R.string.preferences__latest_version), buildNumber)
        }
    }

    override fun checkForAppUpdate(context: Context): Boolean {
        val buildNumber = ConfigUtils.getBuildNumber(context)
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

    @EmojiStyle
    override fun getEmojiStyle(): Int {
        val emojiStyle = preferenceStore.getString(getKeyName(R.string.preferences__emoji_style))
            ?.takeUnlessEmpty()
        return when (emojiStyle?.toIntOrNull()) {
            1 -> PreferenceService.EMOJI_STYLE_ANDROID
            else -> PreferenceService.EMOJI_STYLE_DEFAULT
        }
    }

    override fun setLockoutDeadline(deadline: Instant?) {
        preferenceStore.save(getKeyName(R.string.preferences__lockout_deadline), deadline)
    }

    override fun getLockoutDeadline(): Instant? =
        preferenceStore.getInstant(getKeyName(R.string.preferences__lockout_deadline))

    override fun setLockoutAttempts(numWrongConfirmAttempts: Int) {
        preferenceStore.save(getKeyName(R.string.preferences__lockout_attempts), numWrongConfirmAttempts)
    }

    override fun getLockoutAttempts(): Int =
        preferenceStore.getInt(getKeyName(R.string.preferences__lockout_attempts))

    override fun isAnimationAutoplay(): Boolean =
        preferenceStore.getBoolean(getKeyName(R.string.preferences__gif_autoplay))

    override fun isUseProximitySensor(): Boolean =
        preferenceStore.getBoolean(getKeyName(R.string.preferences__proximity_sensor))

    override fun setAppLogoExpiresAt(expiresAt: Instant?, @AppThemeSetting theme: String) {
        preferenceStore.save(
            getKeyName(
                if (theme == ConfigUtils.THEME_DARK) {
                    R.string.preferences__app_logo_dark_expires_at
                } else {
                    R.string.preferences__app_logo_light_expires_at
                },
            ),
            expiresAt,
        )
    }

    override fun getAppLogoExpiresAt(@AppThemeSetting theme: String): Instant? =
        preferenceStore.getInstant(
            getKeyName(
                if (theme == ConfigUtils.THEME_DARK) {
                    R.string.preferences__app_logo_dark_expires_at
                } else {
                    R.string.preferences__app_logo_light_expires_at
                },
            ),
        )

    override fun arePrivateChatsHidden(): Boolean =
        preferenceStore.getBoolean(getKeyName(R.string.preferences__chats_hidden))

    override fun setArePrivateChatsHidden(hidden: Boolean) {
        preferenceStore.save(getKeyName(R.string.preferences__chats_hidden), hidden)
    }

    override fun watchArePrivateChatsHidden(): Flow<Boolean> =
        preferenceStore.watchBoolean(getKeyName(R.string.preferences__chats_hidden), false)

    override fun getLockMechanism(): String =
        preferenceStore.getString(getKeyName(R.string.preferences__lock_mechanism))
            ?: PreferenceService.LOCKING_MECH_NONE

    override fun isAppLockEnabled(): Boolean =
        preferenceStore.getBoolean(getKeyName(R.string.preferences__app_lock_enabled)) &&
            getLockMechanism() != PreferenceService.LOCKING_MECH_NONE

    override fun setAppLockEnabled(enabled: Boolean) {
        preferenceStore.save(
            getKeyName(R.string.preferences__app_lock_enabled),
            enabled && getLockMechanism() != PreferenceService.LOCKING_MECH_NONE,
        )
    }

    override fun setLockMechanism(lockingMech: String?) {
        preferenceStore.save(getKeyName(R.string.preferences__lock_mechanism), lockingMech)
    }

    override fun isShowImageAttachPreviewsEnabled(): Boolean =
        preferenceStore.getBoolean(getKeyName(R.string.preferences__image_attach_previews))

    override fun isDirectShare(): Boolean =
        preferenceStore.getBoolean(getKeyName(R.string.preferences__direct_share))

    override fun setMessageDrafts(messageDrafts: Map<String, String?>?) {
        encryptedPreferenceStore.save(getKeyName(R.string.preferences__message_drafts), messageDrafts)
    }

    override fun getMessageDrafts(): Map<String, String?>? =
        encryptedPreferenceStore.getMap(getKeyName(R.string.preferences__message_drafts))

    override fun setQuoteDrafts(quoteDrafts: Map<String, String?>?) {
        encryptedPreferenceStore.save(getKeyName(R.string.preferences__quote_drafts), quoteDrafts)
    }

    override fun getQuoteDrafts(): Map<String, String?>? =
        encryptedPreferenceStore.getMap(getKeyName(R.string.preferences__quote_drafts))

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

    override fun getProfilePicUploadTimestamp(): Instant? =
        preferenceStore.getInstant(getKeyName(R.string.preferences__profile_pic_upload_date))

    override fun setProfilePicUploadData(result: ProfilePictureUploadData?) {
        var toStore: JSONObject? = null

        if (result != null) {
            toStore = JSONObject()
            try {
                toStore.put(CONTACT_PHOTO_BLOB_ID, Base64.encodeBytes(result.blobId))
                toStore.put(CONTACT_PHOTO_ENCRYPTION_KEY, Base64.encodeBytes(result.encryptionKey))
                toStore.put(CONTACT_PHOTO_SIZE, result.size)
            } catch (e: Exception) {
                logger.error("Exception", e)
            }
        }

        if (toStore != null) {
            encryptedPreferenceStore.save(getKeyName(R.string.preferences__profile_pic_upload_data), toStore)
        } else {
            // calling the listeners here might not be needed anymore, but we do it to be on the safe side
            ListenerManager.preferenceListeners.handle { listener: PreferenceListener ->
                listener.onChanged(
                    getKeyName(R.string.preferences__profile_pic_upload_data),
                    null,
                )
            }
        }

        val uploadDate = if (result != null) Instant.ofEpochMilli(result.uploadedAt) else null
        preferenceStore.save(getKeyName(R.string.preferences__profile_pic_upload_date), uploadDate)
    }

    override fun getProfilePicUploadData(): ProfilePictureUploadData? {
        val fromStore = encryptedPreferenceStore.getJSONObject(getKeyName(R.string.preferences__profile_pic_upload_data))
        if (fromStore != null) {
            try {
                val data = ProfilePictureUploadData()
                data.blobId = Base64.decode(fromStore.getString(CONTACT_PHOTO_BLOB_ID))
                data.encryptionKey = Base64.decode(fromStore.getString(CONTACT_PHOTO_ENCRYPTION_KEY))
                data.size = fromStore.getInt(CONTACT_PHOTO_SIZE)
                return data
            } catch (e: Exception) {
                logger.error("Exception", e)
                return null
            }
        }
        return null
    }

    override fun getProfilePicReceive(): Boolean =
        preferenceStore.getBoolean(getKeyName(R.string.preferences__receive_profilepics))

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
        preferenceStore.getBoolean(getKeyName(R.string.preferences__ipv6_webrtc_allowed))

    override fun getMobileAutoDownload(): Set<String> =
        preferenceStore.getStringSet(getKeyName(R.string.preferences__auto_download_mobile))
            ?: appContext.resources.getStringArray(R.array.list_auto_download_mobile_default).toSet()

    override fun getWifiAutoDownload(): Set<String> =
        preferenceStore.getStringSet(getKeyName(R.string.preferences__auto_download_wifi))
            ?: appContext.resources.getStringArray(R.array.list_auto_download_wifi_default).toSet()

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
    }

    override fun setThreemaSafeEnabled(value: Boolean) {
        preferenceStore.save(getKeyName(R.string.preferences__threema_safe_enabled), value)
    }

    override fun getThreemaSafeEnabled(): Boolean =
        preferenceStore.getBoolean(getKeyName(R.string.preferences__threema_safe_enabled))

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
        preferenceStore.getBoolean(getKeyName(R.string.preferences__show_unread_badge))

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

    override fun setWorkSyncCheckInterval(checkInterval: Int) {
        preferenceStore.save(getKeyName(R.string.preferences__work_sync_check_interval), checkInterval)
    }

    override fun getWorkSyncCheckInterval(): Int =
        preferenceStore.getInt(getKeyName(R.string.preferences__work_sync_check_interval))

    override fun setIdentityStateSyncInterval(syncIntervalS: Int) {
        preferenceStore.save(getKeyName(R.string.preferences__identity_states_check_interval), syncIntervalS)
    }

    override fun getIdentityStateSyncIntervalS(): Int =
        preferenceStore.getInt(getKeyName(R.string.preferences__identity_states_check_interval))

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
        val array = JSONArray()
        for (category in categories) {
            val categoryObjectString = category.toJSON()
            if (!categoryObjectString.isNullOrEmpty()) {
                try {
                    array.put(JSONObject(categoryObjectString))
                } catch (e: JSONException) {
                    logger.error("Failed to encode work directory category", e)
                }
            }
        }
        encryptedPreferenceStore.save(getKeyName(R.string.preferences__work_directory_categories), array)
    }

    override fun getWorkDirectoryCategories(): List<WorkDirectoryCategory> {
        val array = encryptedPreferenceStore.getJSONArray(getKeyName(R.string.preferences__work_directory_categories))
            ?: return emptyList()
        val categories = mutableListOf<WorkDirectoryCategory>()
        for (i in 0..<array.length()) {
            try {
                val jsonObject = array.optJSONObject(i)
                if (jsonObject != null) {
                    categories.add(WorkDirectoryCategory(jsonObject))
                }
            } catch (e: Exception) {
                logger.error("Failed to parse work directory category", e)
            }
        }
        return categories
    }

    override fun setWorkOrganization(organization: WorkOrganization?) {
        encryptedPreferenceStore.save(getKeyName(R.string.preferences__work_directory_organization), organization?.toJSON())
    }

    override fun getWorkOrganization(): WorkOrganization? =
        encryptedPreferenceStore.getJSONObject(getKeyName(R.string.preferences__work_directory_organization))
            ?.let(::WorkOrganization)

    override fun setLicensedStatus(licensed: Boolean) {
        preferenceStore.save(getKeyName(R.string.preferences__license_status), licensed)
    }

    override fun getLicensedStatus(): Boolean =
        preferenceStore.getBoolean(getKeyName(R.string.preferences__license_status), true)

    override fun setShowDeveloperMenu(show: Boolean) {
        preferenceStore.save(getKeyName(R.string.preferences__developer_menu), show)
    }

    override fun showDeveloperMenu(): Boolean =
        preferenceStore.getBoolean(getKeyName(R.string.preferences__developer_menu), BuildConfig.DEBUG)

    override fun getDataBackupUri(): Uri? =
        preferenceStore.getString(getKeyName(R.string.preferences__data_backup_uri))
            ?.takeUnlessEmpty()
            ?.toUri()

    override fun setDataBackupUri(newUri: Uri?) {
        preferenceStore.save(getKeyName(R.string.preferences__data_backup_uri), newUri?.toString())
    }

    override fun getDataBackupPickerLaunched(): Instant? =
        preferenceStore.getInstant(getKeyName(R.string.preferences__data_backup_picker_launched))

    override fun setDataBackupPickerLaunched(timestamp: Instant?) {
        preferenceStore.save(getKeyName(R.string.preferences__data_backup_picker_launched), timestamp)
    }

    override fun getLastDataBackupTimestamp(): Instant? =
        preferenceStore.getInstant(getKeyName(R.string.preferences__last_data_backup_date))

    override fun setLastDataBackupTimestamp(timestamp: Instant?) {
        preferenceStore.save(getKeyName(R.string.preferences__last_data_backup_date), timestamp)
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

    override fun getVideoCallsProfile(): String? =
        preferenceStore.getString(getKeyName(R.string.preferences__voip_video_profile))

    override fun setBallotOverviewHidden(hidden: Boolean) {
        preferenceStore.save(getKeyName(R.string.preferences__ballot_overview_hidden), hidden)
    }

    override fun getBallotOverviewHidden(): Boolean =
        preferenceStore.getBoolean(getKeyName(R.string.preferences__ballot_overview_hidden))

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

    override fun getBackupWarningDismissedTime(): Instant? =
        preferenceStore.getInstant(getKeyName(R.string.preferences__backup_warning_dismissed_time))

    override fun setBackupWarningDismissedTime(timestamp: Instant?) {
        preferenceStore.save(getKeyName(R.string.preferences__backup_warning_dismissed_time), timestamp)
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

    override fun removeLastNotificationRationaleShown() {
        val key = getKeyName(R.string.preferences__last_notification_rationale_shown)
        if (preferenceStore.containsKey(key)) {
            preferenceStore.remove(key)
        }
    }

    override fun getMediaGalleryContentTypes(): BooleanArray {
        val contentTypes = BooleanArray(SELECTABLE_CONTENT_TYPES.size)
        Arrays.fill(contentTypes, true)
        val array = preferenceStore.getJSONArray(getKeyName(R.string.preferences__media_gallery_content_types))
        if (array.length() == contentTypes.size) {
            for (i in 0..<array.length()) {
                try {
                    val value = array.getBoolean(i)
                    contentTypes[i] = value
                } catch (e: JSONException) {
                    logger.error("JSON error", e)
                }
            }
        }
        return contentTypes
    }

    override fun setMediaGalleryContentTypes(contentTypes: BooleanArray?) {
        try {
            preferenceStore.save(getKeyName(R.string.preferences__media_gallery_content_types), JSONArray(contentTypes))
        } catch (e: JSONException) {
            logger.error("JSON error", e)
        }
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

    override fun setLastNotificationPermissionRequestTimestamp(timestamp: Instant?) {
        preferenceStore.save(getKeyName(R.string.preferences__last_notification_request_timestamp), timestamp)
    }

    override fun getLastNotificationPermissionRequestTimestamp(): Instant? =
        preferenceStore.getInstant(getKeyName(R.string.preferences__last_notification_request_timestamp))

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
            } else if (BuildFlavor.current == BuildFlavor.Blue) {
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

    override fun clear() {
        preferenceStore.clear()
        encryptedPreferenceStore.clear()
    }

    private fun getKeyName(@StringRes resourceId: Int) = appContext.getString(resourceId)

    @Suppress("SameParameterValue")
    private fun getKeyName(@StringRes resourceId: Int, suffix: String) = appContext.getString(resourceId) + suffix

    companion object {
        private const val CONTACT_PHOTO_BLOB_ID = "id"
        private const val CONTACT_PHOTO_ENCRYPTION_KEY = "key"
        private const val CONTACT_PHOTO_SIZE = "size"

        private const val ONE_TIME_DIALOG_PREFIX = "dialog_"

        private fun isPinCodeValid(code: String?): Boolean =
            !code.isNullOrEmpty() &&
                code.length >= AppConstants.MIN_PIN_LENGTH &&
                code.length <= AppConstants.MAX_PIN_LENGTH &&
                code.isDigitsOnly()
    }
}

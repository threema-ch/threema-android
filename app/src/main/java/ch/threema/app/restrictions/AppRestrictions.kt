package ch.threema.app.restrictions

import android.content.Context
import androidx.annotation.StringRes
import ch.threema.app.R
import ch.threema.app.utils.AutoDeleteUtil
import ch.threema.common.takeUnlessEmpty
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

class AppRestrictions(
    private val appContext: Context,
    private val restrictionProvider: AppRestrictionProvider,
) {
    fun isSkipWizard(): Boolean =
        getBooleanRestriction(R.string.restriction__skip_wizard) == true

    fun isBackupsDisabled(): Boolean =
        getBooleanRestriction(R.string.restriction__disable_backups) == true

    fun isIdBackupsDisabled(): Boolean =
        getBooleanRestriction(R.string.restriction__disable_id_export) == true

    fun isReadOnlyProfile(): Boolean =
        getBooleanRestriction(R.string.restriction__readonly_profile) == true

    fun isReadOnlyProfileOrNull(): Boolean? =
        getBooleanRestriction(R.string.restriction__readonly_profile)

    fun isDisabledProfilePicReleaseSettings(): Boolean =
        getBooleanRestriction(R.string.restriction__disable_send_profile_picture) == true

    fun isDisabledProfilePicReleaseSettingsOrNull(): Boolean? =
        getBooleanRestriction(R.string.restriction__disable_send_profile_picture)

    fun isExportDisabled(): Boolean =
        getBooleanRestriction(R.string.restriction__disable_export) == true

    fun isDataBackupsDisabled(): Boolean =
        getBooleanRestriction(R.string.restriction__disable_data_backups) == true

    fun isCallsDisabled(): Boolean =
        getBooleanRestriction(R.string.restriction__disable_calls) == true

    fun isCallsDisabledOrNull(): Boolean? =
        getBooleanRestriction(R.string.restriction__disable_calls)

    fun isVideoCallsDisabledOrNull(): Boolean? =
        getBooleanRestriction(R.string.restriction__disable_video_calls)

    fun isGroupCallsDisabledOrNull(): Boolean? =
        getBooleanRestriction(R.string.restriction__disable_group_calls)

    fun isHideInactiveIdsOrNull(): Boolean? =
        getBooleanRestriction(R.string.restriction__hide_inactive_ids)

    fun isSaveToGalleryDisabledOrNull(): Boolean? =
        getBooleanRestriction(R.string.restriction__disable_save_to_gallery)

    fun isMessagePreviewDisabledOrNull(): Boolean? =
        getBooleanRestriction(R.string.restriction__disable_message_preview)

    fun isBlockUnknownOrNull(): Boolean? =
        getBooleanRestriction(R.string.restriction__block_unknown)

    fun isScreenshotsDisabledOrNull(): Boolean? =
        getBooleanRestriction(R.string.restriction__disable_screenshots)

    fun isContactSyncEnabled(): Boolean =
        getBooleanRestriction(R.string.restriction__contact_sync) == true

    fun isContactSyncEnabledOrNull(): Boolean? =
        getBooleanRestriction(R.string.restriction__contact_sync)

    fun isWebDisabled(): Boolean =
        getBooleanRestriction(R.string.restriction__disable_web) == true

    fun isRemoteSecretEnabled(): Boolean =
        getBooleanRestriction(R.string.restriction__enable_remote_secret) == true

    fun isRemoteSecretEnabledOrNull(): Boolean? =
        getBooleanRestriction(R.string.restriction__enable_remote_secret)

    fun isAddContactDisabled(): Boolean =
        getBooleanRestriction(R.string.restriction__disable_add_contact) == true

    fun isCreateGroupDisabled(): Boolean =
        getBooleanRestriction(R.string.restriction__disable_create_group) == true

    fun isSaveToGalleryDisabled(): Boolean =
        getBooleanRestriction(R.string.restriction__disable_save_to_gallery) == true

    fun isMessagePreviewDisabled(): Boolean =
        getBooleanRestriction(R.string.restriction__disable_message_preview) == true

    fun isMultiDeviceDisabledOrNull(): Boolean? =
        getBooleanRestriction(R.string.restriction__disable_multidevice)

    fun isShareMediaDisabled(): Boolean =
        getBooleanRestriction(R.string.restriction__disable_share_media) == true

    fun isVideoCallsDisabled(): Boolean =
        getBooleanRestriction(R.string.restriction__disable_video_calls) == true

    fun isWorkDirectoryDisabled(): Boolean =
        getBooleanRestriction(R.string.restriction__disable_work_directory) == true

    fun isGroupCallsDisabled(): Boolean =
        getBooleanRestriction(R.string.restriction__disable_group_calls) == true

    fun getNickname(): String? =
        getStringRestriction(R.string.restriction__nickname)

    fun getLicenseUsername(): String? =
        getStringRestriction(R.string.restriction__license_username)

    fun getLicensePassword(): String? =
        getStringRestriction(R.string.restriction__license_password)

    fun getOnPremServer(): String? =
        getStringRestriction(R.string.restriction__onprem_server)

    fun getFirstName(): String? =
        getStringRestriction(R.string.restriction__firstname)

    fun getLastName(): String? =
        getStringRestriction(R.string.restriction__lastname)

    fun getJobTitle(): String? =
        getStringRestriction(R.string.restriction__job_title)

    fun getDepartment(): String? =
        getStringRestriction(R.string.restriction__department)

    fun getCsi(): String? =
        getStringRestriction(R.string.restriction__csi)

    fun getCategory(): String? =
        getStringRestriction(R.string.restriction__category)

    fun getLinkedEmail(): String? =
        getStringRestriction(R.string.restriction__linked_email)

    fun getLinkedPhone(): String? =
        getStringRestriction(R.string.restriction__linked_phone)

    fun getIdBackup(): String? =
        getStringRestriction(R.string.restriction__id_backup)

    fun getIdBackupPassword(): String? =
        getStringRestriction(R.string.restriction__id_backup_password)

    fun isSafeEnabledOrNull(): Boolean? =
        getBooleanRestriction(R.string.restriction__safe_enable)

    fun isSafeRestoreEnabled(): Boolean =
        getBooleanRestriction(R.string.restriction__safe_restore_enable) != false

    fun getSafeRestoreId(): String? =
        getStringRestriction(R.string.restriction__safe_restore_id)

    fun getSafePassword(): String? =
        getStringRestriction(R.string.restriction__safe_password)

    fun getSafeServerUrl(): String? =
        getStringRestriction(R.string.restriction__safe_server_url)

    fun getSafeServerUsername(): String? =
        getStringRestriction(R.string.restriction__safe_server_username)

    fun getSafeServerPassword(): String? =
        getStringRestriction(R.string.restriction__safe_server_password)

    fun getSafePasswordPattern(): Pattern? =
        try {
            getStringRestriction(R.string.restriction__safe_password_pattern)?.toPattern()
        } catch (_: PatternSyntaxException) {
            null
        }

    fun getSafePasswordMessage(): String =
        getStringRestriction(R.string.restriction__safe_password_message)
            ?: appContext.getString(R.string.password_does_not_comply)

    /**
     * Return a list of allowed signaling hosts for Threema Web (or null if no restrictions apply).
     */
    fun getAllowedWebHosts(): List<String>? =
        getStringRestriction(R.string.restriction__web_hosts)
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.takeUnlessEmpty()

    /**
     * Return true in one of the following three cases:
     * <p>
     * - Threema Web signaling host whitelist is empty
     * - Hostname is contained in whitelist
     * - A suffix pattern in the whitelist matches the hostname
     */
    fun isWebHostAllowed(hostname: String): Boolean =
        getAllowedWebHosts()
            ?.any { pattern ->
                hostname == pattern || (pattern.startsWith('*') && hostname.endsWith(pattern.drop(1)))
            }
            ?: true

    /**
     * Get MDM configuration for how long messages are to be kept before they are deleted
     *
     * @return Number of days or 0 if messages should be kept forever. null if the restriction is not set.
     */
    fun getKeepMessagesDays(): Int? =
        getIntRestriction(R.string.restriction__keep_messages_days)
            ?.let { days ->
                AutoDeleteUtil.validateKeepMessageDays(days)
            }

    private fun getBooleanRestriction(@StringRes restriction: Int): Boolean? =
        restrictionProvider.getBooleanRestriction(appContext.getString(restriction))

    private fun getStringRestriction(@StringRes restriction: Int): String? =
        restrictionProvider.getStringRestriction(appContext.getString(restriction))

    private fun getIntRestriction(@StringRes restriction: Int): Int? =
        restrictionProvider.getIntRestriction(appContext.getString(restriction))
}

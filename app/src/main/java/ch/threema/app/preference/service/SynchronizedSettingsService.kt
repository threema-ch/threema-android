package ch.threema.app.preference.service

import ch.threema.base.SessionScoped

/**
 * This service handles all the settings, which need to be synchronized in the context of Multi-Device.
 */
@SessionScoped
interface SynchronizedSettingsService {

    fun isSyncContacts(): Boolean

    fun getContactSyncPolicySetting(): ContactSyncPolicySetting

    fun isBlockUnknown(): Boolean

    fun getUnknownContactPolicySetting(): UnknownContactPolicySetting

    fun areReadReceiptsEnabled(): Boolean

    fun getReadReceiptPolicySetting(): ReadReceiptPolicySetting

    fun isTypingIndicatorEnabled(): Boolean

    fun getTypingIndicatorPolicySetting(): TypingIndicatorPolicySetting

    fun isVoipEnabled(): Boolean

    fun getO2oCallPolicySetting(): O2oCallPolicySetting

    fun isForceTURN(): Boolean

    fun getO2oCallConnectionPolicySetting(): O2oCallConnectionPolicySetting

    fun areVideoCallsEnabled(): Boolean

    fun getO2oCallVideoPolicySetting(): O2oCallVideoPolicySetting

    fun areGroupCallsEnabled(): Boolean

    fun getGroupCallPolicySetting(): GroupCallPolicySetting

    fun areScreenshotsDisabled(): Boolean

    fun getScreenshotPolicySetting(): ScreenshotPolicySetting

    fun isIncognitoKeyboardRequested(): Boolean

    fun getKeyboardDataCollectionPolicySetting(): KeyboardDataCollectionPolicySetting

    /**
     * Get the synchronized Boolean setting based on the provided key. If there is no synchronized
     * setting with this key, null is returned.
     */
    fun getSynchronizedBooleanSettingByKey(key: String): SynchronizedBooleanSetting?

    /**
     * Reload the synchronized Boolean settings from the preference store.
     * TODO(PRD-152): Note that this method may be removed once the logic regarding mdm handling is
     *  refactored.
     */
    fun reloadSynchronizedBooleanSettings()
}

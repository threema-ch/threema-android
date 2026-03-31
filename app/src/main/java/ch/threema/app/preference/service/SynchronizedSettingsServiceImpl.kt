package ch.threema.app.preference.service

import android.content.Context
import ch.threema.app.multidevice.MultiDeviceManager
import ch.threema.app.stores.PreferenceStore
import ch.threema.domain.taskmanager.TaskManager

class SynchronizedSettingsServiceImpl(
    appContext: Context,
    preferenceStore: PreferenceStore,
    taskManager: TaskManager,
    multiDeviceManager: MultiDeviceManager,
) : SynchronizedSettingsService {
    private val contactSyncPolicySetting = ContactSyncPolicySetting(
        multiDeviceManager,
        taskManager,
        preferenceStore,
        appContext,
    )
    private val unknownContactPolicySetting = UnknownContactPolicySetting(
        multiDeviceManager,
        taskManager,
        preferenceStore,
        appContext,
    )
    private val readReceiptPolicySetting = ReadReceiptPolicySetting(
        multiDeviceManager,
        taskManager,
        preferenceStore,
        appContext,
    )
    private val typingIndicatorPolicySetting = TypingIndicatorPolicySetting(
        multiDeviceManager,
        taskManager,
        preferenceStore,
        appContext,
    )
    private val o2oCallPolicySetting = O2oCallPolicySetting(
        multiDeviceManager,
        taskManager,
        preferenceStore,
        appContext,
    )
    private val o2oCallConnectionPolicySetting = O2oCallConnectionPolicySetting(
        multiDeviceManager,
        taskManager,
        preferenceStore,
        appContext,
    )
    private val o2oCallVideoPolicySetting = O2oCallVideoPolicySetting(
        multiDeviceManager,
        taskManager,
        preferenceStore,
        appContext,
    )
    private val groupCallPolicySetting = GroupCallPolicySetting(
        multiDeviceManager,
        taskManager,
        preferenceStore,
        appContext,
    )
    private val screenshotPolicySetting = ScreenshotPolicySetting(
        multiDeviceManager,
        taskManager,
        preferenceStore,
        appContext,
    )
    private val keyboardDataCollectionPolicySetting = KeyboardDataCollectionPolicySetting(
        multiDeviceManager,
        taskManager,
        preferenceStore,
        appContext,
    )
    private val booleanSettingsMap = mapOf(
        contactSyncPolicySetting.preferenceKey to contactSyncPolicySetting,
        unknownContactPolicySetting.preferenceKey to unknownContactPolicySetting,
        readReceiptPolicySetting.preferenceKey to readReceiptPolicySetting,
        typingIndicatorPolicySetting.preferenceKey to typingIndicatorPolicySetting,
        o2oCallPolicySetting.preferenceKey to o2oCallPolicySetting,
        o2oCallConnectionPolicySetting.preferenceKey to o2oCallConnectionPolicySetting,
        o2oCallVideoPolicySetting.preferenceKey to o2oCallVideoPolicySetting,
        groupCallPolicySetting.preferenceKey to groupCallPolicySetting,
        screenshotPolicySetting.preferenceKey to screenshotPolicySetting,
        keyboardDataCollectionPolicySetting.preferenceKey to keyboardDataCollectionPolicySetting,
    )

    override fun isSyncContacts(): Boolean = contactSyncPolicySetting.get()

    override fun getContactSyncPolicySetting(): ContactSyncPolicySetting = contactSyncPolicySetting

    override fun isBlockUnknown(): Boolean = unknownContactPolicySetting.get()

    override fun getUnknownContactPolicySetting(): UnknownContactPolicySetting = unknownContactPolicySetting

    override fun areReadReceiptsEnabled(): Boolean = readReceiptPolicySetting.get()

    override fun getReadReceiptPolicySetting(): ReadReceiptPolicySetting = readReceiptPolicySetting

    override fun isTypingIndicatorEnabled(): Boolean = typingIndicatorPolicySetting.get()

    override fun getTypingIndicatorPolicySetting(): TypingIndicatorPolicySetting = typingIndicatorPolicySetting

    override fun isVoipEnabled(): Boolean = o2oCallPolicySetting.get()

    override fun getO2oCallPolicySetting(): O2oCallPolicySetting = o2oCallPolicySetting

    override fun isForceTURN(): Boolean = o2oCallConnectionPolicySetting.get()

    override fun getO2oCallConnectionPolicySetting(): O2oCallConnectionPolicySetting = o2oCallConnectionPolicySetting

    override fun areVideoCallsEnabled(): Boolean = o2oCallVideoPolicySetting.get()

    override fun getO2oCallVideoPolicySetting(): O2oCallVideoPolicySetting = o2oCallVideoPolicySetting

    override fun areGroupCallsEnabled(): Boolean = groupCallPolicySetting.get()

    override fun getGroupCallPolicySetting(): GroupCallPolicySetting = groupCallPolicySetting

    override fun areScreenshotsDisabled(): Boolean = screenshotPolicySetting.get()

    override fun getScreenshotPolicySetting(): ScreenshotPolicySetting = screenshotPolicySetting

    override fun isIncognitoKeyboardRequested(): Boolean = keyboardDataCollectionPolicySetting.get()

    override fun getKeyboardDataCollectionPolicySetting(): KeyboardDataCollectionPolicySetting = keyboardDataCollectionPolicySetting

    override fun getSynchronizedBooleanSettingByKey(key: String): SynchronizedBooleanSetting? = booleanSettingsMap[key]

    override fun reloadSynchronizedBooleanSettings() {
        booleanSettingsMap.values.forEach { setting ->
            setting.reload()
        }
    }
}

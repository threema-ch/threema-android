package ch.threema.app.processors.reflectedd2dsync

import ch.threema.app.preference.service.SynchronizedSettingsService
import ch.threema.app.services.BlockedIdentitiesService
import ch.threema.app.services.ExcludedSyncIdentitiesService
import ch.threema.base.utils.getThreemaLogger
import ch.threema.domain.taskmanager.TriggerSource
import ch.threema.protobuf.d2d.MdD2D.SettingsSync
import ch.threema.protobuf.d2d.sync.MdD2DSync.Settings

private val logger = getThreemaLogger("ReflectedSettingsSyncTask")

class ReflectedSettingsSyncTask(
    private val settingsSync: SettingsSync,
    private val synchronizedSettingsService: SynchronizedSettingsService,
    private val blockedIdentitiesService: BlockedIdentitiesService,
    private val excludedSyncIdentitiesService: ExcludedSyncIdentitiesService,
) {

    fun run() {
        when (settingsSync.actionCase) {
            SettingsSync.ActionCase.UPDATE -> handleSettingsSyncUpdate(settingsSync.update)
            SettingsSync.ActionCase.ACTION_NOT_SET -> logger.warn("No action set for settings sync")
            null -> logger.warn("Action is null for settings sync")
        }
    }

    private fun handleSettingsSyncUpdate(settingsSyncUpdate: SettingsSync.Update) {
        if (!settingsSyncUpdate.hasSettings()) {
            logger.warn("No settings contained in update")
            return
        }

        val settings = settingsSyncUpdate.settings

        applyContactSyncPolicy(settings)
        applyUnknownContactPolicy(settings)
        applyReadReceiptPolicy(settings)
        applyTypingIndicatorPolicy(settings)
        applyO2oCallPolicy(settings)
        applyO2oCallConnectionPolicy(settings)
        applyO2oCallVideoPolicy(settings)
        applyGroupCallPolicy(settings)
        applyScreenshotPolicy(settings)
        applyKeyboardDataCollectionPolicy(settings)
        applyBlockedIdentities(settings)
        applyExcludeFromSyncIdentities(settings)
    }

    private fun applyContactSyncPolicy(settings: Settings) {
        if (settings.hasContactSyncPolicy()) {
            logger.info("Applying contact sync policy")
            // Note that this only enables the setting. The actual contact synchronisation may not be activated by this.
            synchronizedSettingsService.getContactSyncPolicySetting().setFromSync(settings.contactSyncPolicy)
        }
    }

    private fun applyUnknownContactPolicy(settings: Settings) {
        if (settings.hasUnknownContactPolicy()) {
            logger.info("Applying updated unknown contact policy")
            synchronizedSettingsService.getUnknownContactPolicySetting().setFromSync(settings.unknownContactPolicy)
        }
    }

    private fun applyReadReceiptPolicy(settings: Settings) {
        if (settings.hasReadReceiptPolicy()) {
            logger.info("Applying read receipt policy")
            synchronizedSettingsService.getReadReceiptPolicySetting().setFromSync(settings.readReceiptPolicy)
        }
    }

    private fun applyTypingIndicatorPolicy(settings: Settings) {
        if (settings.hasTypingIndicatorPolicy()) {
            logger.info("Applying typing indicator policy")
            synchronizedSettingsService.getTypingIndicatorPolicySetting().setFromSync(settings.typingIndicatorPolicy)
        }
    }

    private fun applyO2oCallPolicy(settings: Settings) {
        if (settings.hasO2OCallPolicy()) {
            logger.info("Applying 1:1 call policy")
            // Note that this only persists the preference. This does not stop a currently running call.
            synchronizedSettingsService.getO2oCallPolicySetting().setFromSync(settings.o2OCallPolicy)
        }
    }

    private fun applyO2oCallConnectionPolicy(settings: Settings) {
        if (settings.hasO2OCallConnectionPolicy()) {
            logger.info("Applying 1:1 call connection policy")
            synchronizedSettingsService.getO2oCallConnectionPolicySetting().setFromSync(settings.o2OCallConnectionPolicy)
        }
    }

    private fun applyO2oCallVideoPolicy(settings: Settings) {
        if (settings.hasO2OCallVideoPolicy()) {
            logger.info("Applying 1:1 call video policy")
            synchronizedSettingsService.getO2oCallVideoPolicySetting().setFromSync(settings.o2OCallVideoPolicy)
        }
    }

    private fun applyGroupCallPolicy(settings: Settings) {
        if (settings.hasGroupCallPolicy()) {
            logger.info("Applying group call policy")
            // Note that this only persists the preference. This does not stop a currently running group call.
            synchronizedSettingsService.getGroupCallPolicySetting().setFromSync(settings.groupCallPolicy)
        }
    }

    private fun applyScreenshotPolicy(settings: Settings) {
        if (settings.hasScreenshotPolicy()) {
            logger.info("Applying screenshot policy")
            synchronizedSettingsService.getScreenshotPolicySetting().setFromSync(settings.screenshotPolicy)
        }
    }

    private fun applyKeyboardDataCollectionPolicy(settings: Settings) {
        if (settings.hasKeyboardDataCollectionPolicy()) {
            logger.info("Applying keyboard data collection policy")
            synchronizedSettingsService.getKeyboardDataCollectionPolicySetting().setFromSync(settings.keyboardDataCollectionPolicy)
        }
    }

    private fun applyBlockedIdentities(settings: Settings) {
        if (settings.hasBlockedIdentities()) {
            logger.info("Applying blocked identities")
            blockedIdentitiesService.persistBlockedIdentities(settings.blockedIdentities.identitiesList.toSet())
        }
    }

    private fun applyExcludeFromSyncIdentities(settings: Settings) {
        if (settings.hasExcludeFromSyncIdentities()) {
            logger.info("Applying exclude from sync identities")
            excludedSyncIdentitiesService.setExcludedIdentities(settings.excludeFromSyncIdentities.identitiesList.toSet(), TriggerSource.SYNC)
        }
    }
}

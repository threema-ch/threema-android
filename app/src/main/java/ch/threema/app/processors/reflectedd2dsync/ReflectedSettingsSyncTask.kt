/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2025 Threema GmbH
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

package ch.threema.app.processors.reflectedd2dsync

import ch.threema.app.preference.service.PreferenceService
import ch.threema.app.services.BlockedIdentitiesService
import ch.threema.app.services.ExcludedSyncIdentitiesService
import ch.threema.base.utils.LoggingUtil
import ch.threema.domain.taskmanager.TriggerSource
import ch.threema.protobuf.d2d.MdD2D.SettingsSync
import ch.threema.protobuf.d2d.sync.MdD2DSync.Settings

private val logger = LoggingUtil.getThreemaLogger("ReflectedSettingsSyncTask")

class ReflectedSettingsSyncTask(
    private val settingsSync: SettingsSync,
    private val preferenceService: PreferenceService,
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
            preferenceService.contactSyncPolicySetting.setFromSync(settings.contactSyncPolicy)
        }
    }

    private fun applyUnknownContactPolicy(settings: Settings) {
        if (settings.hasUnknownContactPolicy()) {
            logger.info("Applying updated unknown contact policy")
            preferenceService.unknownContactPolicySetting.setFromSync(settings.unknownContactPolicy)
        }
    }

    private fun applyReadReceiptPolicy(settings: Settings) {
        if (settings.hasReadReceiptPolicy()) {
            logger.info("Applying read receipt policy")
            preferenceService.readReceiptPolicySetting.setFromSync(settings.readReceiptPolicy)
        }
    }

    private fun applyTypingIndicatorPolicy(settings: Settings) {
        if (settings.hasTypingIndicatorPolicy()) {
            logger.info("Applying typing indicator policy")
            preferenceService.typingIndicatorPolicySetting.setFromSync(settings.typingIndicatorPolicy)
        }
    }

    private fun applyO2oCallPolicy(settings: Settings) {
        if (settings.hasO2OCallPolicy()) {
            logger.info("Applying 1:1 call policy")
            // Note that this only persists the preference. This does not stop a currently running call.
            preferenceService.o2oCallPolicySetting.setFromSync(settings.o2OCallPolicy)
        }
    }

    private fun applyO2oCallConnectionPolicy(settings: Settings) {
        if (settings.hasO2OCallConnectionPolicy()) {
            logger.info("Applying 1:1 call connection policy")
            preferenceService.o2oCallConnectionPolicySetting.setFromSync(settings.o2OCallConnectionPolicy)
        }
    }

    private fun applyO2oCallVideoPolicy(settings: Settings) {
        if (settings.hasO2OCallVideoPolicy()) {
            logger.info("Applying 1:1 call video policy")
            preferenceService.o2oCallVideoPolicySetting.setFromSync(settings.o2OCallVideoPolicy)
        }
    }

    private fun applyGroupCallPolicy(settings: Settings) {
        if (settings.hasGroupCallPolicy()) {
            logger.info("Applying group call policy")
            // Note that this only persists the preference. This does not stop a currently running group call.
            preferenceService.groupCallPolicySetting.setFromSync(settings.groupCallPolicy)
        }
    }

    private fun applyScreenshotPolicy(settings: Settings) {
        if (settings.hasScreenshotPolicy()) {
            logger.info("Applying screenshot policy")
            preferenceService.screenshotPolicySetting.setFromSync(settings.screenshotPolicy)
        }
    }

    private fun applyKeyboardDataCollectionPolicy(settings: Settings) {
        if (settings.hasKeyboardDataCollectionPolicy()) {
            logger.info("Applying keyboard data collection policy")
            preferenceService.keyboardDataCollectionPolicySetting.setFromSync(settings.keyboardDataCollectionPolicy)
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

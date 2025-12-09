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

package ch.threema.app.di.modules

import ch.threema.app.activities.referral.referralFeatureModule
import ch.threema.app.applock.appLockFeatureModule
import ch.threema.app.apptaskexecutor.appTaskExecutorFeatureModule
import ch.threema.app.archive.archiveFeatureModule
import ch.threema.app.camera.cameraFeatureModule
import ch.threema.app.compose.edithistory.editHistoryFeatureModule
import ch.threema.app.contactdetails.contactDetailsFeatureModule
import ch.threema.app.crashreporting.crashReportingModule
import ch.threema.app.dialogs.loadingtimeout.LoadingWithTimeoutDialogViewModel
import ch.threema.app.drafts.draftsFeatureModule
import ch.threema.app.emojireactions.emojiReactionsFeatureModule
import ch.threema.app.files.filesFeatureModule
import ch.threema.app.globalsearch.globalSearchFeatureModule
import ch.threema.app.home.homeFeatureModule
import ch.threema.app.location.locationFeatureModule
import ch.threema.app.logging.loggingFeatureModule
import ch.threema.app.mediaattacher.mediaAttacherFeatureModule
import ch.threema.app.mediagallery.mediaGalleryFeatureModule
import ch.threema.app.messagedetails.messageDetailsFeatureModule
import ch.threema.app.multidevice.multiDeviceFeatureModule
import ch.threema.app.onprem.onPremFeatureModule
import ch.threema.app.passphrase.passphraseFeatureModule
import ch.threema.app.preference.preferenceFeatureModule
import ch.threema.app.problemsolving.problemSolvingFeatureModule
import ch.threema.app.qrcodes.qrCodesFeatureModule
import ch.threema.app.startup.startupFeatureModule
import ch.threema.app.ui.AvatarEditViewModel
import ch.threema.app.ui.GroupDetailViewModel
import ch.threema.app.ui.ServerMessageViewModel
import ch.threema.app.utils.ConfigUtils
import ch.threema.app.voip.voipFeatureModule
import ch.threema.app.webclient.webclientFeatureModule
import ch.threema.app.widget.widgetFeatureModule
import ch.threema.localcrypto.localCryptoFeatureModule
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/**
 * Provides feature specific components, such as view models and use cases.
 */
val featuresModule = module {
    includes(
        appLockFeatureModule,
        appTaskExecutorFeatureModule,
        archiveFeatureModule,
        cameraFeatureModule,
        contactDetailsFeatureModule,
        crashReportingModule,
        draftsFeatureModule,
        editHistoryFeatureModule,
        emojiReactionsFeatureModule,
        filesFeatureModule,
        globalSearchFeatureModule,
        homeFeatureModule,
        loggingFeatureModule,
        localCryptoFeatureModule,
        locationFeatureModule,
        mediaAttacherFeatureModule,
        mediaGalleryFeatureModule,
        messageDetailsFeatureModule,
        multiDeviceFeatureModule,
        passphraseFeatureModule,
        preferenceFeatureModule,
        problemSolvingFeatureModule,
        qrCodesFeatureModule,
        startupFeatureModule,
        voipFeatureModule,
        webclientFeatureModule,
        widgetFeatureModule,
        referralFeatureModule,
    )

    if (ConfigUtils.isOnPremBuild()) {
        includes(onPremFeatureModule)
    }

    // Everything below this line is placed here temporarily, as there's no associated feature package yet. It should be moved into one eventually.
    viewModelOf(::AvatarEditViewModel)
    viewModelOf(::GroupDetailViewModel)
    viewModelOf(::LoadingWithTimeoutDialogViewModel)
    viewModelOf(::ServerMessageViewModel)
}

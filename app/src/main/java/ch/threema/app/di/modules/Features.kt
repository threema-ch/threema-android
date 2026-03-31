package ch.threema.app.di.modules

import ch.threema.app.activities.referral.referralFeatureModule
import ch.threema.app.activities.starred.starredFeatureModule
import ch.threema.app.androidcontactsync.androidContactFeatureModule
import ch.threema.app.applock.appLockFeatureModule
import ch.threema.app.apptaskexecutor.appTaskExecutorFeatureModule
import ch.threema.app.archive.archiveFeatureModule
import ch.threema.app.camera.cameraFeatureModule
import ch.threema.app.compose.edithistory.editHistoryFeatureModule
import ch.threema.app.contactdetails.contactDetailsFeatureModule
import ch.threema.app.dev.devFeatureModule
import ch.threema.app.dialogs.loadingtimeout.LoadingWithTimeoutDialogViewModel
import ch.threema.app.drafts.draftsFeatureModule
import ch.threema.app.emojireactions.emojiReactionsFeatureModule
import ch.threema.app.errorreporting.errorReportingModule
import ch.threema.app.files.filesFeatureModule
import ch.threema.app.fragments.composemessage.composeMessageFeatureModule
import ch.threema.app.fragments.conversations.conversationsFeatureModule
import ch.threema.app.globalsearch.globalSearchFeatureModule
import ch.threema.app.home.homeFeatureModule
import ch.threema.app.location.locationFeatureModule
import ch.threema.app.logging.loggingFeatureModule
import ch.threema.app.mediaattacher.mediaAttacherFeatureModule
import ch.threema.app.mediagallery.mediaGalleryFeatureModule
import ch.threema.app.messagedetails.messageDetailsFeatureModule
import ch.threema.app.multidevice.multiDeviceFeatureModule
import ch.threema.app.notifications.notificationModule
import ch.threema.app.onprem.onPremFeatureModule
import ch.threema.app.passphrase.passphraseFeatureModule
import ch.threema.app.pinlock.pinLockFeatureModule
import ch.threema.app.preference.preferenceFeatureModule
import ch.threema.app.problemsolving.problemSolvingFeatureModule
import ch.threema.app.qrcodes.qrCodesFeatureModule
import ch.threema.app.reset.resetFeatureModule
import ch.threema.app.restrictions.appRestrictionsFeatureModule
import ch.threema.app.startup.startupFeatureModule
import ch.threema.app.storagemanagement.storageManagementFeatureModule
import ch.threema.app.threemasafe.usecases.threemaSafeFeatureModule
import ch.threema.app.ui.AvatarEditViewModel
import ch.threema.app.ui.GroupDetailViewModel
import ch.threema.app.ui.ServerMessageViewModel
import ch.threema.app.utils.ConfigUtils
import ch.threema.app.voicemessage.voiceMessageFeatureModule
import ch.threema.app.voip.voipFeatureModule
import ch.threema.app.webclient.webclientFeatureModule
import ch.threema.app.widget.widgetFeatureModule
import ch.threema.app.workers.workersFeatureModule
import ch.threema.localcrypto.localCryptoFeatureModule
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/**
 * Provides feature specific components, such as view models and use cases.
 */
val featuresModule = module {
    includes(
        androidContactFeatureModule,
        appLockFeatureModule,
        appRestrictionsFeatureModule,
        appTaskExecutorFeatureModule,
        conversationsFeatureModule,
        archiveFeatureModule,
        cameraFeatureModule,
        composeMessageFeatureModule,
        contactDetailsFeatureModule,
        devFeatureModule,
        draftsFeatureModule,
        editHistoryFeatureModule,
        emojiReactionsFeatureModule,
        errorReportingModule,
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
        notificationModule,
        passphraseFeatureModule,
        pinLockFeatureModule,
        preferenceFeatureModule,
        problemSolvingFeatureModule,
        qrCodesFeatureModule,
        referralFeatureModule,
        resetFeatureModule,
        starredFeatureModule,
        startupFeatureModule,
        storageManagementFeatureModule,
        threemaSafeFeatureModule,
        voiceMessageFeatureModule,
        voipFeatureModule,
        webclientFeatureModule,
        widgetFeatureModule,
        workersFeatureModule,
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

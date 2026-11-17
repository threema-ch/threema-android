package ch.threema.app.usecases

import android.app.Activity
import android.content.Context
import android.content.ReceiverCallNotAllowedException
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import ch.threema.app.BuildFlavor
import ch.threema.app.licensing.StoreLicenseCheck
import ch.threema.app.services.UserService
import ch.threema.app.services.license.LicenseService
import ch.threema.app.services.license.LicenseServiceThreema
import ch.threema.app.utils.IntentDataUtil
import ch.threema.app.work.LegacyWorkPropertiesUpdater
import ch.threema.base.utils.getThreemaLogger
import ch.threema.common.DispatcherProvider
import kotlinx.coroutines.withContext

private val logger = getThreemaLogger("CheckLicenseUseCase")

/**
 * This use case checks whether the license is valid or not. It also checks if an update is available as this is part of the response of the license
 * check in case the build flavor is able to self update. Additionally, it updates the work info if it is a work build and the license check is
 * successful.
 */
class CheckLicenseUseCase(
    private val licenseService: LicenseService<*>,
    private val userService: UserService,
    private val legacyWorkPropertiesUpdater: LegacyWorkPropertiesUpdater,
    private val dispatcherProvider: DispatcherProvider,
    private val appContext: Context,
) {
    /**
     * Checks the license.
     *
     * Warning: The HMS license check might open an activity. It is important that this method is only called if we are certain that
     * no other activity will be started immediately after. The later activity could cover up HMS license check's activity, which leaves it
     * in an invalid state from which it can not recover.
     */
    suspend fun call(activity: Activity) {
        logger.info("Running license check")
        when (BuildFlavor.licenseType) {
            BuildFlavor.LicenseType.GOOGLE, BuildFlavor.LicenseType.HMS ->
                StoreLicenseCheck.checkLicense(activity, userService)

            BuildFlavor.LicenseType.SERIAL, BuildFlavor.LicenseType.WORK ->
                checkSerial()

            BuildFlavor.LicenseType.NONE -> Unit
        }
    }

    private suspend fun checkSerial() = withContext(dispatcherProvider.io) {
        logger.info("Checking serial license")

        val error: String? = licenseService.validate(true)
        if (error != null) {
            sendInvalidLicenseBroadcast(error)
        } else {
            userService.setCredentials(licenseService.loadCredentials())

            @Suppress("SimplifyBooleanWithConstants")
            if (licenseService is LicenseServiceThreema<*> && BuildFlavor.MAY_SELF_UPDATE) {
                if (licenseService.updateMessage != null && !licenseService.isUpdateMessageShown) {
                    try {
                        LocalBroadcastManager.getInstance(appContext).sendBroadcast(
                            IntentDataUtil.createActionIntentUpdateAvailable(
                                licenseService.updateMessage,
                                licenseService.updateUrl,
                            ),
                        )
                        licenseService.isUpdateMessageShown = true
                    } catch (e: ReceiverCallNotAllowedException) {
                        logger.error("Failed to send app update available broadcast", e)
                    }
                }
            }

            // Update work properties (legacy). Note that this is a no-op on private builds.
            legacyWorkPropertiesUpdater.run()
        }
    }

    private fun sendInvalidLicenseBroadcast(message: String?) {
        try {
            LocalBroadcastManager.getInstance(appContext).sendBroadcast(IntentDataUtil.createActionIntentLicenseNotAllowed(message))
        } catch (e: ReceiverCallNotAllowedException) {
            logger.error("Could not send license not allowed broadcast", e)
        }
    }
}

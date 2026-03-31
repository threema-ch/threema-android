package ch.threema.app.home.usecases

import android.content.Context
import ch.threema.app.services.UserService
import ch.threema.app.utils.ConfigUtils

class ShouldShowWorkIntroScreenUseCase(
    private val appContext: Context,
    private val userService: UserService,
) {
    /**
     * Check whether the work intro screen should be shown. The work intro screen contains a notice
     * to direct users to the private version of the app in case it is possible that they installed
     * the work or onprem app by mistake. In certain cases we can rule out an oversight and skip the
     * screen.
     *
     * @return true if the screen should be shown
     */
    fun call(): Boolean {
        // Don't show the screen if the app is already set up with an identity.
        if (userService.hasIdentity()) {
            return false
        }

        // If it is no work (including onprem) build, we should not show the screen.
        if (!ConfigUtils.isWorkBuild()) {
            return false
        }

        // If the app is restricted, then we should skip the screen as it is very likely intended to
        // use the work or onprem app.
        if (ConfigUtils.isWorkRestricted()) {
            return false
        }

        // If the app is not installed from a store, the chance that the private app should be used
        // instead is smaller and therefore we should skip the screen.
        if (!ConfigUtils.isInstalledFromStore(appContext)) {
            return false
        }

        // On whitelabel onprem build it doesn't make sense to show the work intro screen.
        return !ConfigUtils.isWhitelabelOnPremBuild(appContext)
    }
}

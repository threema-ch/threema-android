package ch.threema.app.home.usecases

import ch.threema.app.restrictions.AppRestrictions
import ch.threema.app.threemasafe.ThreemaSafeMDMConfig

class CheckBackupsFeatureEnabledUseCase(
    private val appRestrictions: AppRestrictions,
) {
    fun call(): Boolean {
        if (appRestrictions.isBackupsDisabled()) {
            return false
        }
        if (appRestrictions.isDataBackupsDisabled() && ThreemaSafeMDMConfig.getInstance().isBackupDisabled) {
            return false
        }
        return true
    }
}

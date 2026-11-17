package ch.threema.app.activities.wizard

import androidx.lifecycle.lifecycleScope
import ch.threema.app.usecases.CheckLicenseUseCase
import kotlinx.coroutines.launch
import org.koin.mp.KoinPlatform

object WizardIntroHelper {
    @JvmStatic
    fun checkLicense(activity: WizardIntroActivity) {
        activity.lifecycleScope.launch {
            KoinPlatform.getKoin().get<CheckLicenseUseCase>().call(activity)
        }
    }
}

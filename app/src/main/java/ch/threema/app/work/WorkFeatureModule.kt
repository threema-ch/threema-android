package ch.threema.app.work

import android.os.Build
import ch.threema.app.BuildFlavor
import ch.threema.app.utils.ConfigUtils
import ch.threema.app.work.workproperties.workPropertiesFeatureModule
import ch.threema.domain.models.WorkClientInfo
import java.util.Locale
import org.koin.dsl.module

val workFeatureModule = module {
    factory<WorkClientInfo> { getWorkClientInfo() }
    includes(workPropertiesFeatureModule)
}

private fun getWorkClientInfo(): WorkClientInfo =
    WorkClientInfo(
        appVersion = ConfigUtils.getAppVersion(),
        appLocale = Locale.getDefault().toString(),
        deviceModel = Build.MODEL,
        osVersion = Build.VERSION.RELEASE,
        workFlavor = when {
            BuildFlavor.current.isOnPrem -> WorkClientInfo.WorkFlavor.ON_PREM
            BuildFlavor.current.isWork -> WorkClientInfo.WorkFlavor.WORK
            else -> error("Not a work build")
        },
    )

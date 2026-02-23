package ch.threema.app.utils

import android.annotation.SuppressLint
import android.os.Build
import ch.threema.domain.models.AppVersion
import java.util.Locale

object AppVersionProvider {
    @SuppressLint("ConstantLocale")
    @JvmStatic
    val appVersion = AppVersion(
        /* appVersionNumber = */
        ConfigUtils.getAppVersion(),
        /* appPlatformCode = */
        "A",
        /* appLanguage = */
        Locale.getDefault().language,
        /* appCountry = */
        Locale.getDefault().country,
        /* appSystemModel = */
        Build.MODEL,
        /* appSystemVersion = */
        Build.VERSION.RELEASE,
    )
}

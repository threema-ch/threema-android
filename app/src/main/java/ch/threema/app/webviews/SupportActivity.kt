package ch.threema.app.webviews

import androidx.core.net.toUri
import ch.threema.app.R
import ch.threema.app.preference.service.PreferenceService
import ch.threema.app.services.UserService
import ch.threema.app.utils.ConfigUtils
import ch.threema.app.utils.LocaleUtil
import ch.threema.app.utils.logScreenVisibility
import ch.threema.base.utils.getThreemaLogger
import org.koin.android.ext.android.inject

private val logger = getThreemaLogger("SupportActivity")

class SupportActivity : SimpleWebViewActivity() {
    init {
        logScreenVisibility(logger)
    }

    private val preferenceService: PreferenceService by inject()
    private val userService: UserService by inject()

    override fun requiresConnection() = true

    override fun requiresJavaScript() = true

    override fun getWebViewTitle() = R.string.support

    override fun getWebViewUrl(isDarkTheme: Boolean) =
        getBaseUrl().toUri()
            .buildUpon()
            .appendQueryParameter("lang", LocaleUtil.getAppLanguage())
            .appendQueryParameter("version", ConfigUtils.getDeviceInfo(true))
            .appendQueryParameter("identity", userService.identity)
            .build()
            .toString()

    private fun getBaseUrl(): String {
        if (ConfigUtils.isWorkBuild()) {
            val customSupportUrl = preferenceService.customSupportUrl
            if (!customSupportUrl.isNullOrEmpty()) {
                return customSupportUrl
            }
        }
        return getString(R.string.support_url)
    }
}

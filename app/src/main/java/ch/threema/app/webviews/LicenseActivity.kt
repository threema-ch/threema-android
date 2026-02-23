package ch.threema.app.webviews

import android.content.Context
import ch.threema.android.buildActivityIntent
import ch.threema.app.R
import ch.threema.app.utils.logScreenVisibility
import ch.threema.base.utils.getThreemaLogger

private val logger = getThreemaLogger("LicenseActivity")

class LicenseActivity : SimpleWebViewActivity() {
    init {
        logScreenVisibility(logger)
    }

    override fun getWebViewTitle() = R.string.os_licenses

    override fun getWebViewUrl(isDarkTheme: Boolean) = "file:///android_asset/license.html"

    override fun requiresConnection() = false

    companion object {
        fun createIntent(context: Context) = buildActivityIntent<LicenseActivity>(context)
    }
}

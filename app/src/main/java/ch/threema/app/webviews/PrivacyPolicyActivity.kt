package ch.threema.app.webviews

import android.content.Context
import ch.threema.android.buildActivityIntent
import ch.threema.app.R
import ch.threema.app.utils.ConfigUtils
import ch.threema.app.utils.logScreenVisibility
import ch.threema.base.utils.getThreemaLogger

private val logger = getThreemaLogger("PrivacyPolicyActivity")

class PrivacyPolicyActivity : SimpleWebViewActivity() {
    init {
        logScreenVisibility(logger)
    }

    override fun getWebViewTitle() = R.string.privacy_policy

    override fun getWebViewUrl(isDarkTheme: Boolean) = ConfigUtils.getPrivacyPolicyURL(
        /* context = */
        this,
        /* isDarkTheme = */
        isDarkTheme,
    )

    companion object {
        @JvmStatic
        fun createIntent(context: Context, forceDarkTheme: Boolean = false) = buildActivityIntent<PrivacyPolicyActivity>(context) {
            if (forceDarkTheme) {
                putExtra(FORCE_DARK_THEME, true)
            }
        }
    }
}

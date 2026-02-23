package ch.threema.app.webviews

import android.content.Context
import ch.threema.android.buildActivityIntent
import ch.threema.app.R
import ch.threema.app.utils.ConfigUtils
import ch.threema.app.utils.logScreenVisibility
import ch.threema.base.utils.getThreemaLogger

private val logger = getThreemaLogger("EulaActivity")

class EulaActivity : SimpleWebViewActivity() {
    init {
        logScreenVisibility(logger)
    }

    override fun getWebViewTitle() = R.string.eula

    override fun getWebViewUrl(isDarkTheme: Boolean) = ConfigUtils.getEulaURL(
        /* context = */
        this,
        /* isDarkTheme = */
        isDarkTheme,
    )

    companion object {
        fun createIntent(context: Context) = buildActivityIntent<EulaActivity>(context)
    }
}

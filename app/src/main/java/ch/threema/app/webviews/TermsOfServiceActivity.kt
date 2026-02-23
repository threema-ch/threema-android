package ch.threema.app.webviews

import android.content.Context
import ch.threema.android.buildActivityIntent
import ch.threema.app.R
import ch.threema.app.utils.ConfigUtils
import ch.threema.app.utils.logScreenVisibility
import ch.threema.base.utils.getThreemaLogger

private val logger = getThreemaLogger("TermsOfServiceActivity")

class TermsOfServiceActivity : SimpleWebViewActivity() {
    init {
        logScreenVisibility(logger)
    }

    override fun getWebViewTitle() = R.string.terms_of_service

    override fun getWebViewUrl(isDarkTheme: Boolean) = ConfigUtils.getTermsOfServiceURL(
        /* context = */
        this,
        /* isDarkTheme = */
        isDarkTheme,
    )

    companion object {
        fun createIntent(context: Context) = buildActivityIntent<TermsOfServiceActivity>(context)
    }
}

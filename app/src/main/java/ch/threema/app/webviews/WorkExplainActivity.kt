package ch.threema.app.webviews

import android.content.Context
import android.os.Bundle
import ch.threema.android.buildActivityIntent
import ch.threema.app.R
import ch.threema.app.utils.ConfigUtils
import ch.threema.app.utils.logScreenVisibility
import ch.threema.base.utils.getThreemaLogger

private val logger = getThreemaLogger("WorkExplainActivity")

class WorkExplainActivity : SimpleWebViewActivity() {
    init {
        logScreenVisibility(logger)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        if (ConfigUtils.isAppInstalled(WORK_PACKAGE_NAME)) {
            val launchIntent = packageManager.getLaunchIntentForPackage(WORK_PACKAGE_NAME)
            if (launchIntent != null) {
                startActivity(launchIntent)
                overridePendingTransition(0, 0)
            }
            finish()
        }
        super.onCreate(savedInstanceState)
    }

    override fun getWebViewTitle() = R.string.threema_work

    override fun getWebViewUrl(isDarkTheme: Boolean) = ConfigUtils.getWorkExplainURL(this)

    companion object {
        private const val WORK_PACKAGE_NAME = "ch.threema.app.work"

        @JvmStatic
        fun createIntent(context: Context) = buildActivityIntent<WorkExplainActivity>(context)
    }
}

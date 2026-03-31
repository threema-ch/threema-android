package ch.threema.app.activities

import android.content.Context
import android.os.Bundle
import android.text.Html
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import ch.threema.android.buildActivityIntent
import ch.threema.android.postDelayed
import ch.threema.app.BuildConfig
import ch.threema.app.R
import ch.threema.app.ui.InsetSides.Companion.all
import ch.threema.app.ui.applyDeviceInsetsAsPadding
import ch.threema.app.utils.AnimationUtil
import ch.threema.app.utils.logScreenVisibility
import ch.threema.base.utils.getThreemaLogger
import kotlin.time.Duration.Companion.milliseconds

private val logger = getThreemaLogger("WhatsNewActivity")

class WhatsNewActivity : ThreemaAppCompatActivity() {
    init {
        logScreenVisibility(logger)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_whatsnew)
        findViewById<View?>(R.id.content_container).applyDeviceInsetsAsPadding(all())

        val title = getString(R.string.whatsnew_title, BuildConfig.VERSION_NAME)
        val body = Html.fromHtml(getString(R.string.whatsnew_headline), Html.FROM_HTML_MODE_LEGACY)

        findViewById<TextView>(R.id.whatsnew_title).text = title
        findViewById<TextView>(R.id.whatsnew_body).text = body

        findViewById<View?>(R.id.next_text)?.setOnClickListener { finish() }

        if (savedInstanceState == null) {
            val buttonLayout = findViewById<LinearLayout>(R.id.button_layout)
            buttonLayout.isVisible = false
            buttonLayout.postDelayed(200.milliseconds) {
                AnimationUtil.slideInFromBottomOvershoot(buttonLayout)
            }
        }
    }

    companion object {
        @JvmStatic
        fun createIntent(context: Context) = buildActivityIntent<WhatsNewActivity>(context)
    }
}

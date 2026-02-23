package ch.threema.app.activities

import android.content.Context
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import ch.threema.android.buildActivityIntent
import ch.threema.app.R
import ch.threema.app.ui.InsetSides
import ch.threema.app.ui.applyDeviceInsetsAsPadding
import ch.threema.app.utils.logScreenVisibility
import ch.threema.base.utils.getThreemaLogger

private val logger = getThreemaLogger("VerificationLevelActivity")

class VerificationLevelActivity : ThreemaToolbarActivity() {

    init {
        logScreenVisibility(logger)
    }

    override fun getLayoutResource(): Int = R.layout.activity_verification_level

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val actionBar = supportActionBar
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true)
            actionBar.setTitle(R.string.verification_levels_title)
        }
    }

    override fun handleDeviceInsets() {
        super.handleDeviceInsets()
        findViewById<View>(R.id.scroll_container)?.applyDeviceInsetsAsPadding(
            insetSides = InsetSides.lbr(),
        )
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
        }
        return false
    }

    companion object {
        @JvmStatic
        fun createIntent(context: Context) = buildActivityIntent<VerificationLevelActivity>(context)
    }
}

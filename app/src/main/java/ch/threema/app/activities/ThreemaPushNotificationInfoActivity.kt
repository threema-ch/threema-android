package ch.threema.app.activities

import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.ScrollView
import ch.threema.android.buildActivityIntent
import ch.threema.app.R
import ch.threema.app.ui.InsetSides
import ch.threema.app.ui.SpacingValues
import ch.threema.app.ui.applyDeviceInsetsAsMargin
import ch.threema.app.ui.applyDeviceInsetsAsPadding
import ch.threema.app.utils.logScreenVisibility
import ch.threema.base.utils.getThreemaLogger
import com.google.android.material.button.MaterialButton

private val logger = getThreemaLogger("ThreemaPushNotificationInfoActivity")

/**
 * Activity that is shown when the user taps on the persistent Threema Push notification.
 */
class ThreemaPushNotificationInfoActivity : ThreemaActivity() {
    init {
        logScreenVisibility(logger)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        logger.debug("onCreate")

        super.onCreate(savedInstanceState)

        // Load layout
        setContentView(R.layout.activity_threema_push_notification_info)

        // Set up click handlers
        findViewById<View>(R.id.close_button).setOnClickListener { finish() }

        findViewById<ScrollView>(R.id.scroll_container).applyDeviceInsetsAsPadding(
            insetSides = InsetSides.ltr(),
            ownPadding = SpacingValues.all(R.dimen.grid_unit_x2),
        )
        findViewById<MaterialButton>(R.id.close_button).applyDeviceInsetsAsMargin(
            insetSides = InsetSides.lbr(),
            ownMargin = SpacingValues(
                right = R.dimen.grid_unit_x2,
                bottom = R.dimen.grid_unit_x2,
            ),
        )
    }

    companion object {
        fun createIntent(context: Context) = buildActivityIntent<ThreemaPushNotificationInfoActivity>(context)
    }
}

package ch.threema.app.activities.wizard

import android.widget.HorizontalScrollView
import ch.threema.app.R
import ch.threema.app.activities.ThreemaAppCompatActivity

abstract class WizardBackgroundActivity : ThreemaAppCompatActivity() {
    override fun onStart() {
        super.onStart()

        val hsv = findViewById<HorizontalScrollView>(R.id.background_image)
        // disable scrolling
        hsv?.setOnTouchListener { _, _ -> true }
    }
}

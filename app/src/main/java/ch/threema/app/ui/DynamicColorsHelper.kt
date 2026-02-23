package ch.threema.app.ui

import android.app.Application
import androidx.preference.PreferenceManager
import ch.threema.base.utils.getThreemaLogger
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.DynamicColorsOptions

private val logger = getThreemaLogger("DynamicColorsHelper")

object DynamicColorsHelper {
    private const val PREF_KEY_DYNAMIC_COLOR = "pref_dynamic_color"

    @JvmStatic
    fun applyDynamicColorsIfEnabled(application: Application) {
        if (!DynamicColors.isDynamicColorAvailable()) {
            logger.info("Dynamic color not available, skipping")
            return
        }
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(application)
            ?: return
        if (sharedPreferences.getBoolean(PREF_KEY_DYNAMIC_COLOR, false)) {
            val dynamicColorsOptions = DynamicColorsOptions.Builder()
                .setPrecondition { _, _ ->
                    sharedPreferences.getBoolean(PREF_KEY_DYNAMIC_COLOR, false)
                }
                .build()
            DynamicColors.applyToActivitiesIfAvailable(application, dynamicColorsOptions)
        }
    }
}

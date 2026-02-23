package ch.threema.app.crashreporting

import androidx.appcompat.app.AppCompatActivity
import ch.threema.app.R
import ch.threema.app.dialogs.GenericAlertDialog

object CrashReportingDialog {
    @JvmStatic
    fun showDialog(activity: AppCompatActivity, tag: String) {
        // TODO(ANDR-4339): The message and buttons in this dialog need to be adjusted and translated
        GenericAlertDialog.newInstance(
            /* titleString = */
            "Crash detected",
            /* messageString = */
            "It looks like the app has crashed. Automatic crash reporting is not yet supported. " +
                "Please inform the Android team. Thanks.",
            /* positive = */
            R.string.ok,
            /* negative = */
            0,
        )
            .show(activity.supportFragmentManager, tag)
    }
}

package ch.threema.app.preference

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.core.net.toUri
import ch.threema.android.ToastDuration
import ch.threema.android.showToast
import ch.threema.app.BuildConfig
import ch.threema.app.BuildFlavor
import ch.threema.app.R
import ch.threema.app.dialogs.GenericAlertDialog
import ch.threema.app.dialogs.GenericAlertDialog.DialogClickListener
import ch.threema.app.rating.RateDialog
import ch.threema.app.rating.RateDialog.RateDialogClickListener
import ch.threema.app.utils.ConfigUtils
import ch.threema.app.utils.logScreenVisibility
import ch.threema.base.utils.getThreemaLogger

private val logger = getThreemaLogger("SettingsRateFragment")

class SettingsRateFragment : ThreemaPreferenceFragment(), RateDialogClickListener, DialogClickListener {
    init {
        logScreenVisibility(logger)
    }

    override fun initializePreferences() {
        val rateDialog = RateDialog.newInstance(getString(R.string.rate_title))
        rateDialog.setTargetFragment(this, 0)
        rateDialog.show(parentFragmentManager, DIALOG_TAG_RATE)
    }

    @Throws(ActivityNotFoundException::class)
    private fun goToPlayStore() {
        openUrl("market://details?id=" + BuildConfig.APPLICATION_ID) ||
            openUrl("https://play.google.com/store/apps/details?id=" + BuildConfig.APPLICATION_ID)
    }

    private fun openUrl(uri: String): Boolean {
        val intent = Intent(Intent.ACTION_VIEW, uri.toUri())
            .addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
        try {
            startActivity(intent)
        } catch (_: Exception) {
            return false
        }
        return true
    }

    override fun onYes(tag: String?, rating: Int, text: String?) {
        if (rating >= 4 && shouldRedirectToGooglePlay()) {
            val dialog = GenericAlertDialog.newInstance(
                R.string.rate_title,
                getString(R.string.rate_thank_you) + " " + getString(R.string.rate_forward_to_play_store),
                R.string.yes,
                R.string.no,
            )
            dialog.setTargetFragment(this)
            dialog.show(getParentFragmentManager(), DIALOG_TAG_RATE_ON_GOOGLE_PLAY)
        } else {
            showToast(R.string.rate_thank_you, ToastDuration.LONG)
            onBackPressed()
        }
    }

    private fun shouldRedirectToGooglePlay() =
        when (BuildFlavor.current.licenseType) {
            BuildFlavor.LicenseType.GOOGLE,
            BuildFlavor.LicenseType.NONE,
            -> true
            BuildFlavor.LicenseType.GOOGLE_WORK -> ConfigUtils.isInstalledFromPlayStore(requireContext())
            else -> false
        }

    override fun onYes(tag: String?, data: Any?) {
        if (tag == DIALOG_TAG_RATE_ON_GOOGLE_PLAY) {
            goToPlayStore()
            onBackPressed()
        }
    }

    override fun onNo(tag: String?, data: Any?) {
        navigateBackIfPossible()
    }

    override fun onCancel(tag: String?) {
        navigateBackIfPossible()
    }

    private fun navigateBackIfPossible() {
        if (!ConfigUtils.isTabletLayout()) {
            // We only need to navigate back on phones, because on tablets this would leave the
            // preferences entirely.
            onBackPressed()
        }
    }

    private fun onBackPressed() {
        activity?.onBackPressedDispatcher?.onBackPressed()
    }

    override fun getPreferenceTitleResource() = R.string.rate_title

    override fun getPreferenceResource() = R.xml.preference_rate

    companion object {
        private const val DIALOG_TAG_RATE = "rate"
        private const val DIALOG_TAG_RATE_ON_GOOGLE_PLAY = "ratep"
    }
}

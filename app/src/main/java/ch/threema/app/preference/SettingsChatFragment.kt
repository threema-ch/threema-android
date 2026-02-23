package ch.threema.app.preference

import ch.threema.app.R
import ch.threema.app.utils.logScreenVisibility
import ch.threema.base.utils.getThreemaLogger

private val logger = getThreemaLogger("SettingsChatFragment")

@Suppress("unused")
class SettingsChatFragment : ThreemaPreferenceFragment() {
    init {
        logScreenVisibility(logger)
    }

    override fun getPreferenceTitleResource(): Int = R.string.prefs_chatdisplay

    override fun getPreferenceResource() = R.xml.preference_chat
}

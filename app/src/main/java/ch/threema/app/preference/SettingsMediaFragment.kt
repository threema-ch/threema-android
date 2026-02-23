package ch.threema.app.preference

import android.content.Intent
import android.os.Build
import android.text.format.Formatter
import androidx.preference.MultiSelectListPreference
import androidx.preference.Preference
import ch.threema.app.R
import ch.threema.app.activities.StorageManagementActivity
import ch.threema.app.preference.service.PreferenceService
import ch.threema.app.restrictions.AppRestrictionUtil
import ch.threema.app.services.MessageServiceImpl
import ch.threema.app.utils.ConfigUtils
import ch.threema.app.utils.logScreenVisibility
import ch.threema.base.utils.getThreemaLogger
import org.koin.android.ext.android.inject

private val logger = getThreemaLogger("SettingsMediaFragment")

class SettingsMediaFragment : ThreemaPreferenceFragment() {
    init {
        logScreenVisibility(logger)
    }

    private var saveMediaPreference: Preference? = null

    private val preferenceService: PreferenceService by inject()

    override fun initializePreferences() {
        super.initializePreferences()

        initAutoDownloadPref()

        initMediaPref()

        initSaveMediaPref()

        initDownloadPref()
    }

    override fun getPreferenceTitleResource(): Int = R.string.prefs_media_title

    override fun getPreferenceResource(): Int = R.xml.preference_media

    private fun initAutoDownloadPref() {
        val autoDownloadExplainSizePreference: Preference =
            getPref(R.string.preferences__auto_download_explain)
        autoDownloadExplainSizePreference.summary = getString(
            R.string.auto_download_limit_explain,
            Formatter.formatShortFileSize(
                context,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    MessageServiceImpl.FILE_AUTO_DOWNLOAD_MAX_SIZE_SI
                } else {
                    MessageServiceImpl.FILE_AUTO_DOWNLOAD_MAX_SIZE_ISO
                },
            ),
        )
    }

    private fun initMediaPref() {
        getPref<Preference>(R.string.preferences__storage_management).onClick {
            startActivity(Intent(activity, StorageManagementActivity::class.java))
        }
    }

    private fun initSaveMediaPref() {
        saveMediaPreference = getPref(R.string.preferences__save_media)
        saveMediaPreference?.setOnPreferenceChangeListener { _, newValue ->
            if (newValue as Boolean) {
                ConfigUtils.requestWriteStoragePermissions(
                    requireActivity(),
                    this@SettingsMediaFragment,
                    PERMISSION_REQUEST_SAVE_MEDIA,
                )
            } else {
                true
            }
        }

        if (ConfigUtils.isWorkRestricted()) {
            val value =
                AppRestrictionUtil.getBooleanRestriction(getString(R.string.restriction__disable_save_to_gallery))
            if (value != null) {
                saveMediaPreference?.isEnabled = false
                saveMediaPreference?.isSelectable = false
            }
        }
    }

    private fun initDownloadPref() {
        val wifiDownloadPreference: MultiSelectListPreference = getPref(R.string.preferences__auto_download_wifi)
        wifiDownloadPreference.onChange<Set<*>> { selectedOptions ->
            wifiDownloadPreference.summary = getAutoDownloadSummary(selectedOptions)
        }
        wifiDownloadPreference.summary = getAutoDownloadSummary(preferenceService.wifiAutoDownload)

        val mobileDownloadPreference: MultiSelectListPreference = getPref(R.string.preferences__auto_download_mobile)
        mobileDownloadPreference.onChange<Set<*>> { selectedOptions ->
            mobileDownloadPreference.summary = getAutoDownloadSummary(selectedOptions)
        }
        mobileDownloadPreference.summary =
            getAutoDownloadSummary(preferenceService.mobileAutoDownload)
    }

    private fun getAutoDownloadSummary(selectedOptions: Set<*>): CharSequence? {
        val values = resources.getStringArray(R.array.list_auto_download_values)
        val result: MutableList<String?> = ArrayList(selectedOptions.size)
        for (i in values.indices) {
            if (selectedOptions.contains(values[i])) {
                result.add(resources.getStringArray(R.array.list_auto_download)[i])
            }
        }
        return if (result.isEmpty()) {
            resources.getString(R.string.never)
        } else {
            result.joinToString(separator = ", ")
        }
    }

    companion object {
        private const val PERMISSION_REQUEST_SAVE_MEDIA = 1
    }
}

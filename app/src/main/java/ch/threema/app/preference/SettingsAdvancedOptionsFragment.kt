package ch.threema.app.preference

import android.Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.core.content.getSystemService
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.preference.DropDownPreference
import androidx.preference.Preference
import androidx.preference.Preference.SummaryProvider
import androidx.preference.PreferenceCategory
import androidx.preference.TwoStatePreference
import ch.threema.android.Destroyer.Companion.createDestroyer
import ch.threema.android.ToastDuration
import ch.threema.android.buildActivityIntent
import ch.threema.android.ownedBy
import ch.threema.android.registerPermissionResultContract
import ch.threema.android.registerSimpleActivityResultContract
import ch.threema.android.showToast
import ch.threema.app.AppConstants
import ch.threema.app.BuildConfig
import ch.threema.app.BuildFlavor
import ch.threema.app.R
import ch.threema.app.activities.DisableBatteryOptimizationsActivity
import ch.threema.app.asynctasks.SendToSupportBackgroundTask
import ch.threema.app.asynctasks.SendToSupportResult
import ch.threema.app.dev.hasDevFeatures
import ch.threema.app.dialogs.CancelableHorizontalProgressDialog
import ch.threema.app.dialogs.GenericAlertDialog
import ch.threema.app.dialogs.GenericAlertDialog.DialogClickListener
import ch.threema.app.dialogs.GenericProgressDialog
import ch.threema.app.dialogs.SimpleStringAlertDialog
import ch.threema.app.dialogs.TextEntryDialog
import ch.threema.app.dialogs.TextEntryDialog.TextEntryDialogClickListener
import ch.threema.app.errorreporting.SentryIdProvider
import ch.threema.app.listeners.ConversationListener
import ch.threema.app.logging.DebugLogHelper
import ch.threema.app.managers.ListenerManager
import ch.threema.app.preference.service.PreferenceService
import ch.threema.app.preference.service.PreferenceService.ErrorReportingState
import ch.threema.app.push.PushService
import ch.threema.app.restrictions.AppRestrictions
import ch.threema.app.services.ContactService
import ch.threema.app.services.GroupService
import ch.threema.app.services.MessageService
import ch.threema.app.services.MessageServiceImpl.SendResultListener
import ch.threema.app.services.RingtoneService
import ch.threema.app.services.ThreemaPushService
import ch.threema.app.services.UserService
import ch.threema.app.services.WallpaperService
import ch.threema.app.services.notification.NotificationService
import ch.threema.app.ui.MediaItem
import ch.threema.app.usecases.ExportDebugLogUseCase
import ch.threema.app.usecases.ShareDebugLogUseCase
import ch.threema.app.utils.ConfigUtils
import ch.threema.app.utils.DialogUtil
import ch.threema.app.utils.DispatcherProvider
import ch.threema.app.utils.HibernationUtil
import ch.threema.app.utils.MimeUtil
import ch.threema.app.utils.PowermanagerUtil
import ch.threema.app.utils.PushUtil
import ch.threema.app.utils.executor.BackgroundExecutor
import ch.threema.app.utils.logScreenVisibility
import ch.threema.app.voip.activities.WebRTCDebugActivity
import ch.threema.app.webclient.activities.WebDiagnosticsActivity
import ch.threema.base.utils.getThreemaLogger
import ch.threema.common.TimeProvider
import ch.threema.common.minus
import ch.threema.data.models.ContactModel
import ch.threema.data.repositories.ContactModelRepository
import ch.threema.domain.protocol.api.APIConnector
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.IOException
import kotlin.collections.indexOf
import kotlin.system.exitProcess
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject

private val logger = getThreemaLogger("SettingsAdvancedOptionsFragment")

class SettingsAdvancedOptionsFragment :
    ThreemaPreferenceFragment(),
    DialogClickListener,
    OnSharedPreferenceChangeListener,
    TextEntryDialogClickListener,
    CancelableHorizontalProgressDialog.ProgressDialogClickListener {
    init {
        logScreenVisibility(logger)
    }

    private val wallpaperService: WallpaperService by inject()
    private val sharedPreferences: SharedPreferences by inject()
    private val preferenceService: PreferenceService by inject()
    private val ringtoneService: RingtoneService by inject()
    private val notificationService: NotificationService by inject()
    private val userService: UserService by inject()
    private val messageService: MessageService by inject()
    private val contactService: ContactService by inject()
    private val groupService: GroupService by inject()
    private val apiConnector: APIConnector by inject()
    private val contactModelRepository: ContactModelRepository by inject()
    private val dispatcherProvider: DispatcherProvider by inject()
    private val hibernationUtil: HibernationUtil by inject()
    private val exportDebugLogUseCase: ExportDebugLogUseCase by inject()
    private val shareDebugLogUseCase: ShareDebugLogUseCase by inject()
    private val debugLogHelper: DebugLogHelper by inject()
    private val sentryIdProvider: SentryIdProvider by inject()
    private val appRestrictions: AppRestrictions by inject()
    private val timeProvider: TimeProvider by inject()
    private val destroyer = createDestroyer()

    private var fragmentView: View? = null

    private lateinit var threemaPushTwoStatePreference: TwoStatePreference
    private lateinit var debugLogPreference: TwoStatePreference
    private lateinit var sendLogPreference: Preference
    private lateinit var exportLogPreference: Preference
    private lateinit var ipv6Preferences: TwoStatePreference
    private lateinit var sentryIdPreference: Preference

    private lateinit var pushTokenResetBroadcastReceiver: BroadcastReceiver
    private val backgroundExecutor: BackgroundExecutor by lazy {
        BackgroundExecutor().ownedBy(destroyer)
    }
    private val pushServicesInstalled by lazy {
        PushService.servicesInstalled(context)
    }

    private val readPhoneStatePermissionLauncher = registerPermissionResultContract { isGranted ->
        if (isGranted) {
            updateReadPhoneStatePermissionPref()
        } else {
            context?.let { context ->
                ConfigUtils.showPermissionRationale(context, fragmentView, R.string.read_phone_state_short_message)
            }
        }
    }
    private val bluetoothPermissionLauncher = registerPermissionResultContract { isGranted ->
        if (isGranted) {
            updateBluetoothPermissionPref()
        } else {
            context?.let { context ->
                ConfigUtils.showPermissionRationale(context, fragmentView, R.string.permission_bluetooth_connect_required)
            }
        }
    }
    private val disableBatteryOptimizationsLauncher = registerSimpleActivityResultContract { result ->
        threemaPushTwoStatePreference.setChecked(result.resultCode == Activity.RESULT_OK)
    }
    private val disableBatteryOptimizationsHuaweiLauncher = registerSimpleActivityResultContract {
        updatePowerManagerPrefs()
    }

    /**
     * This activity result launcher is needed to open the settings to disable hibernation.
     * Unfortunately the intent cannot be called with `startActivity` even if the results are
     * not needed.
     */
    private val hibernationSettingsLauncher = registerSimpleActivityResultContract()

    private var debugLogWarningShown = false

    override fun initializePreferences() {
        initLoggingPrefs()
        initErrorReportingPrefs()
        initPushPrefs()
        initWallpaperPrefs()
        initRingtonePrefs()
        initIpV6Prefs()
        initPowerRestrictionPrefs()
        initWebClientDebugPrefs()
        initEchoCancelPrefs()
        initWebRtcDebugPrefs()
        initVideoCodecPrefs()

        updatePowerManagerPrefs()
        updateHibernationPref()

        if (appRestrictions.isCallsDisabled()) {
            getPref<PreferenceCategory>(R.string.preferences__voip).isVisible = false
        }
    }

    private fun initLoggingPrefs() {
        debugLogPreference = getPref(R.string.preferences__debug_log_switch)
        sendLogPreference = getPref(R.string.preferences__sendlog)
        exportLogPreference = getPref(R.string.preferences__exportlog)
        updateDebugLogPreferences()
        debugLogPreference.onChange<Boolean> { isEnabled ->
            debugLogHelper.setEnabled(isEnabled)
            updateDebugLogPreferences()
        }
        debugLogPreference.setSummaryProvider(
            SummaryProvider<TwoStatePreference> {
                val logEnabledSince = preferenceService.getDebugLogEnabledTimestamp()
                if (logEnabledSince != null || debugLogHelper.isDebugLogFileLoggingForceEnabled()) {
                    if (hasDevFeatures() && logEnabledSince != null) {
                        "Enabled since $logEnabledSince"
                    } else {
                        getString(R.string.prefs_title_sum_message_log_on)
                    }
                } else {
                    getString(R.string.prefs_title_sum_message_log_off)
                }
            },
        )

        if (BuildFlavor.current.isOnPrem) {
            sendLogPreference.isVisible = false
        } else {
            sendLogPreference.onClick {
                logger.info("Preparing to send debug log to support")
                lifecycleScope.launch {
                    showWarningIfEnabledTooRecently()
                    prepareSendLogfile()
                }
            }
        }

        exportLogPreference.onClick {
            logger.info("Preparing to share debug log")
            lifecycleScope.launch {
                showWarningIfEnabledTooRecently()
                GenericProgressDialog.newInstance(R.string.preparing_messages, R.string.please_wait)
                    .show(parentFragmentManager, DIALOG_TAG_SHARE_LOG)
                try {
                    shareDebugLogUseCase.call()
                } catch (e: Exception) {
                    showToast(R.string.an_error_occurred)
                    logger.error("Failed to share debug log", e)
                } finally {
                    DialogUtil.dismissDialog(parentFragmentManager, DIALOG_TAG_SHARE_LOG, true)
                }
            }
        }
    }

    private fun updateDebugLogPreferences() {
        val forceEnabled = debugLogHelper.isDebugLogFileLoggingForceEnabled()
        val loggingEnabled = preferenceService.isDebugLogEnabled() || forceEnabled
        debugLogPreference.isChecked = loggingEnabled
        debugLogPreference.isEnabled = !forceEnabled
        sendLogPreference.isEnabled = loggingEnabled
        exportLogPreference.isEnabled = loggingEnabled
    }

    private fun showWarningIfEnabledTooRecently() {
        val enableTime = preferenceService.getDebugLogEnabledTimestamp() ?: return
        if (!debugLogWarningShown && timeProvider.get() - enableTime < 20.seconds) {
            debugLogWarningShown = true
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.warning)
                .setMessage(R.string.debug_log_enabled_recently_warning)
                .setPositiveButton(R.string.ok, null)
                .show()
            throw CancellationException()
        }
    }

    private fun prepareSendLogfile() {
        val dialog = TextEntryDialog.newInstance(
            /* title = */
            R.string.prefs_sendlog_summary,
            /* message = */
            R.string.enter_description,
            /* positive = */
            R.string.send,
            /* negative = */
            R.string.cancel,
            /* maxLines = */
            5,
            /* maxLength = */
            3000,
            /* minLength = */
            1,
        )
        dialog.setTargetFragment(this, 0)
        dialog.show(parentFragmentManager, DIALOG_TAG_SEND_LOG)
    }

    private fun initErrorReportingPrefs() {
        @Suppress("KotlinConstantConditions")
        if (BuildConfig.ERROR_REPORTING_SUPPORTED) {
            getPref<PreferenceCategory>(R.string.preferences__error_reporting_category).isVisible = true

            val errorReportingSummaries = resources.getStringArray(R.array.list_error_reporting_summaries)
            val errorReportingOptionValues = resources.getStringArray(R.array.list_error_reporting_values)
            val errorReportingPreference = getPref<DropDownPreference>(R.string.preferences__error_reporting)
            errorReportingPreference.value = when (preferenceService.getErrorReportingState()) {
                ErrorReportingState.ALWAYS_SEND -> getString(R.string.error_reporting_value_always_send)
                ErrorReportingState.NEVER_SEND -> getString(R.string.error_reporting_value_never_send)
                ErrorReportingState.ALWAYS_ASK -> getString(R.string.error_reporting_value_always_ask)
            }
            errorReportingPreference.summaryProvider = SummaryProvider { _: DropDownPreference ->
                errorReportingSummaries[errorReportingOptionValues.indexOf(errorReportingPreference.value)]
            }
            errorReportingPreference.onChange<String> { newValue ->
                when (newValue) {
                    getString(R.string.error_reporting_value_never_send) -> sentryIdProvider.deleteSentryId()
                    getString(R.string.error_reporting_value_always_send) -> sentryIdProvider.getOrGenerateSentryId()
                }
                updateSentryIdPreference()
            }

            sentryIdPreference = getPref(R.string.preferences__sentry_id)
            sentryIdPreference.onClick {
                sentryIdProvider.getSentryId()?.let { sentryId ->
                    requireContext().getSystemService<ClipboardManager>()?.setPrimaryClip(ClipData.newPlainText(null, sentryId))
                    showToast(R.string.generic_copied_to_clipboard_hint)
                }
            }
            updateSentryIdPreference()
        }
    }

    private fun updateSentryIdPreference() {
        val sentryId = sentryIdProvider.getSentryId()
        sentryIdPreference.summary = sentryId
            ?.let {
                getString(R.string.prefs_error_reporting_id_summary, sentryId)
            }
            ?: "-"
        sentryIdPreference.isEnabled = sentryId != null
    }

    private fun initPushPrefs() {
        pushTokenResetBroadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                DialogUtil.dismissDialog(parentFragmentManager, DIALOG_TAG_PUSH_REGISTER, true)
                val message = when {
                    intent.getBooleanExtra(PushUtil.EXTRA_REGISTRATION_ERROR_BROADCAST, false) -> {
                        getString(R.string.token_register_failed)
                    }
                    intent.getBooleanExtra(PushUtil.EXTRA_CLEAR_TOKEN, false) -> {
                        getString(R.string.push_token_cleared)
                    }
                    else -> {
                        getString(R.string.push_reset_text)
                    }
                }
                SimpleStringAlertDialog.newInstance(-1, message)
                    .show(parentFragmentManager, DIALOG_TAG_PUSH_RESULT)
            }
        }

        threemaPushTwoStatePreference = getPref(R.string.preferences__threema_push_switch)
        threemaPushTwoStatePreference.setOnPreferenceChangeListener { _, newValue: Any? ->
            if (newValue == true) {
                if (pushServicesInstalled) {
                    val dialog = GenericAlertDialog.newInstance(
                        R.string.prefs_title_threema_push_switch,
                        R.string.push_disable_text,
                        R.string.ok,
                        R.string.cancel,
                    )
                    dialog.setTargetFragment(this, 0)
                    dialog.show(parentFragmentManager, DIALOG_TAG_REALLY_ENABLE_THREEMA_PUSH)
                    return@setOnPreferenceChangeListener false
                }
            } else {
                if (!pushServicesInstalled) {
                    showToast(R.string.play_services_not_installed_unable_to_use_push)
                    return@setOnPreferenceChangeListener false
                }
            }
            true
        }

        getPref<Preference>(R.string.preferences__reset_push).onClick {
            if (pushServicesInstalled) {
                PushUtil.clearPushTokenSentDate(requireContext())
                PushUtil.enqueuePushTokenUpdate(requireContext(), false, true)
                GenericProgressDialog.newInstance(R.string.push_reset_title, R.string.please_wait)
                    .showNow(parentFragmentManager, DIALOG_TAG_PUSH_REGISTER)
            }
        }
    }

    private fun initWallpaperPrefs() {
        getPref<Preference>(R.string.preferences__remove_wallpapers).onClick {
            val dialog = GenericAlertDialog.newInstance(
                R.string.prefs_title_remove_wallpapers,
                R.string.really_remove_wallpapers,
                R.string.ok,
                R.string.cancel,
            )
            dialog.setTargetFragment(this, 0)
            dialog.show(parentFragmentManager, DIALOG_TAG_REMOVE_WALLPAPERS)
        }
    }

    private fun initRingtonePrefs() {
        getPref<Preference>(R.string.preferences__reset_ringtones).onClick {
            val dialog = GenericAlertDialog.newInstance(
                R.string.prefs_title_reset_ringtones,
                R.string.really_reset_ringtones,
                R.string.ok,
                R.string.cancel,
            )
            dialog.setTargetFragment(this, 0)
            dialog.show(parentFragmentManager, DIALOG_TAG_RESET_RINGTONES)
        }
    }

    private fun initIpV6Prefs() {
        ipv6Preferences = getPref(R.string.preferences__ipv6_preferred)
        ipv6Preferences.setOnPreferenceChangeListener { preference: Preference?, newValue: Any? ->
            val newCheckedValue = newValue == true
            val oldCheckedValue = (preference as TwoStatePreference).isChecked
            if (oldCheckedValue != newCheckedValue) {
                val dialog = GenericAlertDialog.newInstance(
                    R.string.prefs_title_ipv6_preferred,
                    R.string.ipv6_requires_restart,
                    R.string.ipv6_restart_now,
                    R.string.cancel,
                )

                dialog.setTargetFragment(this, 0)
                dialog.setData(oldCheckedValue)
                dialog.show(parentFragmentManager, DIALOG_TAG_IPV6_APP_RESTART)
                return@setOnPreferenceChangeListener false
            }
            true
        }
    }

    private fun initPowerRestrictionPrefs() {
        getPref<Preference>(R.string.preferences__powermanager_workarounds).onClick {
            if (PowermanagerUtil.hasPowerManagerOption(requireContext())) {
                val dialog = GenericAlertDialog.newInstance(
                    R.string.disable_powermanager_title,
                    getString(R.string.disable_powermanager_explain, getString(R.string.app_name)),
                    R.string.next,
                    R.string.cancel,
                )

                dialog.setTargetFragment(this, 0)
                dialog.show(parentFragmentManager, DIALOG_TAG_POWER_MANAGER_WORKAROUNDS)
            } else {
                disableAutostart()
            }
        }
    }

    private fun initWebClientDebugPrefs() {
        getPref<Preference>(R.string.preferences__webclient_debug).onClick {
            startActivity(WebDiagnosticsActivity.createIntent(requireContext()))
        }
    }

    private fun initEchoCancelPrefs() {
        val echoCancelIndex = if (preferenceService.getAECMode() == "sw") 1 else 0
        val echoCancelArray = resources.getStringArray(R.array.list_echocancel)
        val echoCancelValuesArray = resources.getStringArray(R.array.list_echocancel_values)

        val echoCancelPreference = getPref<DropDownPreference>(R.string.preferences__voip_echocancel)
        echoCancelPreference.setSummary(echoCancelArray[echoCancelIndex])
        echoCancelPreference.onChange<String> { newValue ->
            echoCancelPreference.setSummary(echoCancelArray[echoCancelValuesArray.indexOf(newValue)])
        }
    }

    private fun initWebRtcDebugPrefs() {
        getPref<Preference>(R.string.preferences__webrtc_debug).onClick {
            startActivity(WebRTCDebugActivity.createIntent(requireContext()))
        }
    }

    private fun initVideoCodecPrefs() {
        val videoCodecListDescription = resources.getStringArray(R.array.list_video_codec)
        val videoCodecValuesList = resources.getStringArray(R.array.list_video_codec_values)

        getPref<DropDownPreference>(R.string.preferences__voip_video_codec).setSummaryProvider(
            SummaryProvider { preference: DropDownPreference ->
                preference.getEntry()
                    .toString()
                    .ifEmpty {
                        videoCodecListDescription[videoCodecValuesList.indexOf(PreferenceService.VIDEO_CODEC_HW)]
                    }
            },
        )
    }

    override fun onResume() {
        super.onResume()
        updateReadPhoneStatePermissionPref()
        updateBluetoothPermissionPref()
        updateHibernationPref()
    }

    private fun updateReadPhoneStatePermissionPref() {
        val context = requireContext()

        val phonePref = getPref<Preference>(R.string.preferences__grant_read_phone_state_permission)
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
            phonePref.isEnabled = false
        } else {
            phonePref.isEnabled = true
            phonePref.onClick {
                ConfigUtils.requestReadPhonePermission(requireActivity(), readPhoneStatePermissionLauncher)
            }
        }
    }

    private fun updateBluetoothPermissionPref() {
        val context = getContext()
        val bluetoothPref = getPref<Preference>(R.string.preferences__grant_bluetooth_permission)

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            (context != null && ConfigUtils.isPermissionGranted(context, Manifest.permission.BLUETOOTH_CONNECT))
        ) {
            bluetoothPref.isEnabled = false
        } else {
            bluetoothPref.isEnabled = true
            bluetoothPref.onPreferenceClickListener = Preference.OnPreferenceClickListener {
                val activity = activity ?: return@OnPreferenceClickListener false
                ConfigUtils.requestBluetoothConnectPermission(activity, bluetoothPermissionLauncher)
                true
            }
        }
    }

    private fun updateHibernationPref() {
        val context = requireContext()
        val hibernationPref = getPref<Preference>(R.string.preferences__hibernation_mode)

        // Set summary depending on sdk version to match the exact description in settings
        // Note that on API 31 and 32 the hibernation setting is called differently (even though it is activated!)
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
            hibernationPref.setSummary(R.string.prefs_summary_hibernation_api_32)
        }

        lifecycleScope.launch {
            val status = try {
                hibernationUtil.getHibernationStatus()
            } catch (e: Exception) {
                logger.error("Could not get hibernation status", e)
                hibernationPref.isVisible = false
                return@launch
            }
            when (status) {
                HibernationUtil.HibernationStatus.AVAILABLE -> {
                    hibernationPref.isEnabled = true
                    hibernationPref.onClick {
                        hibernationSettingsLauncher.launch(
                            IntentCompat.createManageUnusedAppRestrictionsIntent(context, context.packageName),
                        )
                    }
                }
                HibernationUtil.HibernationStatus.AVAILABLE_BUT_DISABLED -> hibernationPref.isEnabled = false
                HibernationUtil.HibernationStatus.UNAVAILABLE -> hibernationPref.isVisible = false
            }
        }
    }

    private fun updatePowerManagerPrefs() {
        getPref<Preference>(R.string.preferences__powermanager_workarounds).isEnabled = PowermanagerUtil.needsFixing(requireContext())
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        this.fragmentView = view
        super.onViewCreated(view, savedInstanceState)
    }

    override fun onYes(tag: String?, data: Any?) {
        if (tag == null) {
            return
        }
        when (tag) {
            DIALOG_TAG_REMOVE_WALLPAPERS -> {
                lifecycleScope.launch {
                    try {
                        withContext(dispatcherProvider.io) {
                            wallpaperService.deleteAll()
                        }
                    } catch (e: IOException) {
                        logger.error("Failed to delete wallpapers", e)
                        showToast(R.string.an_error_occurred)
                        return@launch
                    }
                    showToast(R.string.wallpapers_removed)
                    preferenceService.setCustomWallpaperEnabled(false)
                }
            }

            DIALOG_TAG_RESET_RINGTONES -> {
                ringtoneService.resetRingtones(requireContext())
                contactService.resetAllNotificationTriggerPolicyOverrideFromLocal()
                groupService.resetAllNotificationTriggerPolicyOverrideFromLocal()
                notificationService.recreateNotificationChannels()
                if (BuildFlavor.current.isWork) {
                    preferenceService.setAfterWorkDNDEnabled(false)
                }
                showToast(R.string.reset_ringtones_confirm)
                ListenerManager.conversationListeners.handle { obj: ConversationListener -> obj.onModifiedAll() }
            }

            DIALOG_TAG_IPV6_APP_RESTART -> if (data != null) {
                ipv6Preferences.setChecked(!(data as Boolean))
                lifecycleScope.launch {
                    delay(700.milliseconds)
                    exitProcess(0)
                }
            }

            DIALOG_TAG_AUTOSTART_WORKAROUNDS -> PowermanagerUtil.callAutostartManager(this)
            DIALOG_TAG_POWER_MANAGER_WORKAROUNDS -> PowermanagerUtil.callPowerManager(this)
            DIALOG_TAG_REALLY_ENABLE_THREEMA_PUSH -> disableBatteryOptimizationsLauncher.launch(
                createDisableBatteryOptimizationsIntent(
                    getString(R.string.threema_push),
                ),
            )
        }
    }

    private fun disableAutostart() {
        if (PowermanagerUtil.hasAutostartOption(requireContext())) {
            val dialog = GenericAlertDialog.newInstance(
                R.string.disable_autostart_title,
                getString(R.string.disable_autostart_explain, getString(R.string.app_name)),
                R.string.next,
                R.string.cancel,
            )

            dialog.setTargetFragment(this, 0)
            dialog.show(parentFragmentManager, DIALOG_TAG_AUTOSTART_WORKAROUNDS)
        } else {
            disableBatteryOptimizationsHuaweiLauncher.launch(
                createDisableBatteryOptimizationsIntent(getString(R.string.app_name)),
            )
        }
    }

    override fun onStart() {
        super.onStart()

        sharedPreferences.registerOnSharedPreferenceChangeListener(this)
        LocalBroadcastManager.getInstance(requireContext()).registerReceiver(
            pushTokenResetBroadcastReceiver,
            IntentFilter(AppConstants.INTENT_PUSH_REGISTRATION_COMPLETE),
        )

        updateDebugLogPreferences()
    }

    override fun onStop() {
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(this)
        LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(pushTokenResetBroadcastReceiver)

        DialogUtil.dismissDialog(parentFragmentManager, DIALOG_TAG_PUSH_REGISTER, true)

        super.onStop()
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
        if (BuildFlavor.current.forceThreemaPush) {
            return
        }

        if (key == getString(R.string.preferences__threema_push_switch)) {
            val newValue = sharedPreferences.getBoolean(getString(R.string.preferences__threema_push_switch), false)

            if (!isAdded) {
                return
            }

            if (pushServicesInstalled) {
                // remove token silently
                PushUtil.enqueuePushTokenUpdate(requireContext(), newValue, false)
            }

            if (newValue) {
                if (ThreemaPushService.tryStart(logger, requireContext())) {
                    showToast(R.string.threema_push_activated, ToastDuration.LONG)
                } else {
                    logger.info("Unable to activate threema push")
                    threemaPushTwoStatePreference.setChecked(false)
                }
            } else {
                requireActivity().stopService(Intent(requireContext(), ThreemaPushService::class.java))
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            PowermanagerUtil.RESULT_DISABLE_POWERMANAGER -> {
                disableAutostart()
                updatePowerManagerPrefs()
            }
            PowermanagerUtil.RESULT_DISABLE_AUTOSTART -> {
                disableBatteryOptimizationsHuaweiLauncher.launch(
                    createDisableBatteryOptimizationsIntent(getString(R.string.app_name)),
                )
                updatePowerManagerPrefs()
            }
            else -> super.onActivityResult(requestCode, resultCode, data)
        }
    }

    private fun createDisableBatteryOptimizationsIntent(name: String): Intent =
        buildActivityIntent<DisableBatteryOptimizationsActivity>(requireContext()) {
            putExtra(DisableBatteryOptimizationsActivity.EXTRA_NAME, name)
            putExtra(DisableBatteryOptimizationsActivity.EXTRA_CANCEL_LABEL, R.string.cancel)
        }

    override fun onYes(tag: String, text: String) {
        if (tag == DIALOG_TAG_SEND_LOG) {
            sendLogFileToSupport(text)
        }
    }

    private fun sendLogFileToSupport(caption: String) {
        val context = getContext()
        if (context == null) {
            logger.warn("Cannot send logfile as the context is null")
            return
        }

        val sendLogFileTask: SendToSupportBackgroundTask = object : SendToSupportBackgroundTask(
            myIdentity = userService.getIdentity()!!,
            apiConnector = apiConnector,
            contactModelRepository = contactModelRepository,
            appRestrictions = appRestrictions,
        ) {
            override fun onBefore() {
                GenericProgressDialog.newInstance(R.string.preparing_messages, R.string.please_wait)
                    .show(parentFragmentManager, DIALOG_TAG_SEND_LOG)
            }

            override fun onSupportAvailable(contactModel: ContactModel): SendToSupportResult {
                val zipFile = try {
                    // Using `runBlocking` here is acceptable, as we are on a background thread and the call is expected to finish quickly
                    runBlocking {
                        exportDebugLogUseCase.call()
                    }
                } catch (e: Exception) {
                    logger.error("Failed to create debug log zip file", e)
                    return SendToSupportResult.FAILED
                }

                try {
                    val receiver = contactService.createReceiver(contactModel)
                    messageService.sendText(buildSupportMessage(caption), receiver)
                    val mediaItem = MediaItem(Uri.fromFile(zipFile), MediaItem.TYPE_FILE)
                    mediaItem.filename = zipFile.getName()
                    mediaItem.mimeType = MimeUtil.MIME_TYPE_ZIP

                    messageService.sendMediaAsync(
                        listOf(mediaItem),
                        listOf(receiver),
                        object : SendResultListener {
                            override fun onError(errorMessage: String?) {
                                logger.error("Could not send file message: {}", errorMessage)
                                showToast(R.string.an_error_occurred_during_send, ToastDuration.LONG)
                            }

                            override fun onCompleted() {
                                showToast(R.string.message_sent, ToastDuration.LONG)
                            }
                        },
                    )
                } catch (e: Exception) {
                    logger.error("Could not send log file", e)
                    return SendToSupportResult.FAILED
                }

                return SendToSupportResult.SUCCESS
            }

            override fun onFinished(result: SendToSupportResult) {
                if (!isAdded) {
                    return
                }
                DialogUtil.dismissDialog(parentFragmentManager, DIALOG_TAG_SEND_LOG, true)

                if (result == SendToSupportResult.SUCCESS) {
                    showToast(R.string.message_sent, ToastDuration.LONG)
                } else {
                    showToast(R.string.an_error_occurred, ToastDuration.LONG)
                }
            }
        }

        backgroundExecutor.execute(sendLogFileTask)
    }

    private fun buildSupportMessage(caption: String) = buildString {
        append(caption)
        append("\n-- \n")
        append(ConfigUtils.getSupportDeviceInfo())
        append("\n")
        append(ConfigUtils.getAppVersion())
        if (BuildConfig.DEBUG) {
            append(" (Commit ${BuildConfig.GIT_HASH})")
        }
        append("\n")
        append(userService.getIdentity())
    }

    override fun onNo(tag: String?, data: Any?) {
        if (tag == DIALOG_TAG_IPV6_APP_RESTART && data != null) {
            ipv6Preferences.setChecked(data as Boolean)
        }
    }

    override fun getPreferenceTitleResource() = R.string.prefs_advanced_options

    override fun getPreferenceResource() = R.xml.preference_advanced_options

    companion object {
        private const val DIALOG_TAG_REMOVE_WALLPAPERS = "removeWP"
        private const val DIALOG_TAG_PUSH_REGISTER = "pushReg"
        private const val DIALOG_TAG_PUSH_RESULT = "pushRes"
        private const val DIALOG_TAG_RESET_RINGTONES = "rri"
        private const val DIALOG_TAG_IPV6_APP_RESTART = "rs"
        private const val DIALOG_TAG_POWER_MANAGER_WORKAROUNDS = "hw"
        private const val DIALOG_TAG_AUTOSTART_WORKAROUNDS = "as"
        private const val DIALOG_TAG_REALLY_ENABLE_THREEMA_PUSH = "enp"
        private const val DIALOG_TAG_SHARE_LOG = "shl"
        private const val DIALOG_TAG_SEND_LOG = "sl"
    }
}

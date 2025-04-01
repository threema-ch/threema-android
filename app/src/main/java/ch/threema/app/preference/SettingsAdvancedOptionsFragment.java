/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2013-2025 Threema GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package ch.threema.app.preference;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.view.View;
import android.widget.Toast;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import org.slf4j.Logger;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.content.IntentCompat;
import androidx.core.content.PackageManagerCompat;
import androidx.core.content.UnusedAppRestrictionsConstants;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.preference.DropDownPreference;
import androidx.preference.Preference;
import androidx.preference.Preference.SummaryProvider;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceScreen;
import androidx.preference.TwoStatePreference;
import ch.threema.app.BuildConfig;
import ch.threema.app.BuildFlavor;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.activities.DisableBatteryOptimizationsActivity;
import ch.threema.app.asynctasks.SendToSupportBackgroundTask;
import ch.threema.app.asynctasks.SendToSupportResult;
import ch.threema.app.dialogs.CancelableHorizontalProgressDialog;
import ch.threema.app.dialogs.GenericAlertDialog;
import ch.threema.app.dialogs.GenericProgressDialog;
import ch.threema.app.dialogs.SimpleStringAlertDialog;
import ch.threema.app.dialogs.TextEntryDialog;
import ch.threema.app.listeners.ConversationListener;
import ch.threema.app.managers.ListenerManager;
import ch.threema.app.managers.ServiceManager;
import ch.threema.app.messagereceiver.ContactMessageReceiver;
import ch.threema.app.push.PushService;
import ch.threema.app.services.ContactService;
import ch.threema.app.services.DeadlineListService;
import ch.threema.app.services.FileService;
import ch.threema.app.services.LifetimeService;
import ch.threema.app.services.MessageService;
import ch.threema.app.services.MessageServiceImpl;
import ch.threema.app.services.notification.NotificationService;
import ch.threema.app.services.PreferenceService;
import ch.threema.app.services.RingtoneService;
import ch.threema.app.services.ThreemaPushService;
import ch.threema.app.services.UserService;
import ch.threema.app.services.WallpaperService;
import ch.threema.app.ui.MediaItem;
import ch.threema.app.utils.AppRestrictionUtil;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.DialogUtil;
import ch.threema.app.utils.MimeUtil;
import ch.threema.app.utils.PowermanagerUtil;
import ch.threema.app.utils.PushUtil;
import ch.threema.app.utils.RuntimeUtil;
import ch.threema.app.utils.ShareUtil;
import ch.threema.app.utils.TestUtil;
import ch.threema.app.utils.executor.BackgroundExecutor;
import ch.threema.app.voip.activities.WebRTCDebugActivity;
import ch.threema.app.webclient.activities.WebDiagnosticsActivity;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.data.models.ContactModel;
import ch.threema.data.repositories.ContactModelRepository;
import ch.threema.domain.protocol.api.APIConnector;
import ch.threema.logging.backend.DebugLogFileBackend;

import static ch.threema.app.utils.PowermanagerUtil.RESULT_DISABLE_AUTOSTART;
import static ch.threema.app.utils.PowermanagerUtil.RESULT_DISABLE_POWERMANAGER;

public class SettingsAdvancedOptionsFragment extends ThreemaPreferenceFragment implements GenericAlertDialog.DialogClickListener, SharedPreferences.OnSharedPreferenceChangeListener, TextEntryDialog.TextEntryDialogClickListener, CancelableHorizontalProgressDialog.ProgressDialogClickListener {
    private static final Logger logger = LoggingUtil.getThreemaLogger("SettingsAdvancedOptionsFragment");

    private static final String DIALOG_TAG_REMOVE_WALLPAPERS = "removeWP";
    private static final String DIALOG_TAG_PUSH_REGISTER = "pushReg";
    private static final String DIALOG_TAG_PUSH_RESULT = "pushRes";
    private static final String DIALOG_TAG_RESET_RINGTONES = "rri";
    private static final String DIALOG_TAG_IPV6_APP_RESTART = "rs";
    private static final String DIALOG_TAG_POWERMANAGER_WORKAROUNDS = "hw";
    private static final String DIALOG_TAG_AUTOSTART_WORKAROUNDS = "as";
    private static final String DIALOG_TAG_REALLY_ENABLE_THREEMA_PUSH = "enp";

    public static final int REQUEST_ID_DISABLE_BATTERY_OPTIMIZATIONS = 441;
    public static final int REQUEST_ID_DISABLE_BATTERY_OPTIMIZATIONS_HUAWEI = 442;
    private static final String DIALOG_TAG_SENDLOG = "sl";

    public static final String THREEMA_SUPPORT_IDENTITY = "*SUPPORT";

    private static final int PERMISSION_REQUEST_MESSAGE_LOG = 1;
    private static final int PERMISSION_REQUEST_SEND_LOG = 2;

    private TwoStatePreference threemaPushTwoStatePreference;
    private TwoStatePreference messageLogPreference, ipv6Preferences;

    private WallpaperService wallpaperService;
    private SharedPreferences sharedPreferences;
    private PreferenceService preferenceService;
    private RingtoneService ringtoneService;
    private NotificationService notificationService;
    private FileService fileService;
    private UserService userService;
    private LifetimeService lifetimeService;
    private DeadlineListService mutedChatsListService, mentionOnlyChatsListService;
    private MessageService messageService;
    private ContactService contactService;
    private APIConnector apiConnector;
    private ContactModelRepository contactModelRepository;
    private View fragmentView;

    private BroadcastReceiver pushTokenResetBroadcastReceiver;

    private @Nullable BackgroundExecutor backgroundExecutor;

    private boolean pushServicesInstalled;

    /**
     * This activity result launcher is needed to open the settings to disable hibernation.
     * Unfortunately the intent cannot be called with {@code startActivity} even if the results are
     * not needed.
     */
    private final ActivityResultLauncher<Intent> activityResultLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), ignored -> {
        // results are ignored
    });

    private final ActivityResultLauncher<String> readPhoneStatePermissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
        if (isGranted != null && isGranted) {
            updateReadPhoneStatePermissionPref();
        } else {
            Context context = getContext();
            if (context != null) {
                ConfigUtils.showPermissionRationale(context, fragmentView, R.string.read_phone_state_short_message);
            }
        }
    });

    private final ActivityResultLauncher<String> bluetoothPermissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
        if (isGranted != null && isGranted) {
            updateBluetoothPermissionPref();
        } else {
            Context context = getContext();
            if (context != null) {
                ConfigUtils.showPermissionRationale(context, fragmentView, R.string.permission_bluetooth_connect_required);
            }
        }
    });

    @Override
    protected void initializePreferences() {
        if (!requiredInstances()) {
            return;
        }

        PreferenceScreen preferenceScreen = getPref("pref_key_advanced_options");

        sharedPreferences = getPreferenceManager().getSharedPreferences();
        pushServicesInstalled = PushService.servicesInstalled(getContext());

        pushTokenResetBroadcastReceiver = new BroadcastReceiver() {
            // register listener for fcm registration result
            @Override
            public void onReceive(Context context, Intent intent) {
                DialogUtil.dismissDialog(getParentFragmentManager(), DIALOG_TAG_PUSH_REGISTER, true);

                String message;
                if (intent.getBooleanExtra(PushUtil.EXTRA_REGISTRATION_ERROR_BROADCAST, false)) {
                    message = getString(R.string.token_register_failed);
                } else if (intent.getBooleanExtra(PushUtil.EXTRA_CLEAR_TOKEN, false)) {
                    message = getString(R.string.push_token_cleared);
                } else {
                    message = getString(R.string.push_reset_text);
                }
                SimpleStringAlertDialog.newInstance(-1, message).show(getParentFragmentManager(), DIALOG_TAG_PUSH_RESULT);
            }
        };

        threemaPushTwoStatePreference = getPref(getResources().getString(R.string.preferences__threema_push_switch));
        threemaPushTwoStatePreference.setOnPreferenceChangeListener((preference, newValue) -> {
            boolean newCheckedValue = newValue.equals(true);
            if (((TwoStatePreference) preference).isChecked() != newCheckedValue) {
                if (newCheckedValue) {
                    if (pushServicesInstalled) {
                        GenericAlertDialog dialog = GenericAlertDialog.newInstance(
                            R.string.prefs_title_threema_push_switch,
                            R.string.push_disable_text,
                            R.string.ok,
                            R.string.cancel
                        );
                        dialog.setTargetFragment(this, 0);
                        dialog.show(getParentFragmentManager(), DIALOG_TAG_REALLY_ENABLE_THREEMA_PUSH);
                        return false;
                    }
                    return true;
                } else {
                    if (!pushServicesInstalled) {
                        Toast.makeText(getContext(), R.string.play_services_not_installed_unable_to_use_push, Toast.LENGTH_SHORT).show();
                        return false;
                    }
                }
            }
            return true;
        });

        messageLogPreference = getPref(getResources().getString(R.string.preferences__message_log_switch));
        messageLogPreference.setOnPreferenceChangeListener((preference, newValue) -> {
            boolean newCheckedValue = newValue.equals(true);

            DebugLogFileBackend.setEnabled(newCheckedValue);
            if (newCheckedValue) {
                ThreemaApplication.logVersion();
            }

            return true;
        });

        PreferenceCategory loggingCategory = getPref("pref_key_logging");
        Preference sendLogPreference = getPref(getResources().getString(R.string.preferences__sendlog));
        Preference exportLogPreference = getPref(getResources().getString(R.string.preferences__exportlog));

        // Do not show send log preference on on prem builds
        if (ConfigUtils.isOnPremBuild()) {
            loggingCategory.removePreference(sendLogPreference);

            // Show share options
            exportLogPreference.setOnPreferenceClickListener(preference -> {
                ShareUtil.shareLogfile(requireContext(), fileService);
                return true;
            });
        } else {
            loggingCategory.removePreference(exportLogPreference);
            sendLogPreference.setOnPreferenceClickListener(preference -> {
                prepareSendLogfile();
                return true;
            });
        }

        Preference resetPushPreference = getPref(getResources().getString(R.string.preferences__reset_push));
        resetPushPreference.setOnPreferenceClickListener(preference -> {
            if (pushServicesInstalled) {
                PushUtil.clearPushTokenSentDate(getActivity());
                PushUtil.enqueuePushTokenUpdate(getContext(), false, true);
                GenericProgressDialog.newInstance(R.string.push_reset_title, R.string.please_wait).showNow(getParentFragmentManager(), DIALOG_TAG_PUSH_REGISTER);
            }
            return true;
        });

        Preference wallpaperDeletePreferences = getPref(getResources().getString(R.string.preferences__remove_wallpapers));
        wallpaperDeletePreferences.setOnPreferenceClickListener(preference -> {
            GenericAlertDialog dialog = GenericAlertDialog.newInstance(R.string.prefs_title_remove_wallpapers,
                R.string.really_remove_wallpapers,
                R.string.ok,
                R.string.cancel);

            dialog.setTargetFragment(this, 0);
            dialog.show(getParentFragmentManager(), DIALOG_TAG_REMOVE_WALLPAPERS);
            return false;
        });

        Preference ringtoneResetPreferences = getPref(getResources().getString(R.string.preferences__reset_ringtones));
        ringtoneResetPreferences.setOnPreferenceClickListener(preference -> {
            GenericAlertDialog dialog = GenericAlertDialog.newInstance(R.string.prefs_title_reset_ringtones,
                R.string.really_reset_ringtones,
                R.string.ok,
                R.string.cancel);

            dialog.setTargetFragment(this, 0);
            dialog.show(getParentFragmentManager(), DIALOG_TAG_RESET_RINGTONES);
            return false;
        });

        ipv6Preferences = getPref(getResources().getString(R.string.preferences__ipv6_preferred));
        ipv6Preferences.setOnPreferenceChangeListener((preference, newValue) -> {
            boolean newCheckedValue = newValue.equals(true);
            boolean oldCheckedValue = ((TwoStatePreference) preference).isChecked();
            if (oldCheckedValue != newCheckedValue) {
                // value has changed
                GenericAlertDialog dialog = GenericAlertDialog.newInstance(R.string.prefs_title_ipv6_preferred,
                    R.string.ipv6_requires_restart,
                    R.string.ipv6_restart_now,
                    R.string.cancel);

                dialog.setTargetFragment(this, 0);
                dialog.setData(oldCheckedValue);
                dialog.show(getParentFragmentManager(), DIALOG_TAG_IPV6_APP_RESTART);
                return false;
            }
            return true;
        });

        Preference powerManagerPrefs = getPref(getResources().getString(R.string.preferences__powermanager_workarounds));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            powerManagerPrefs.setOnPreferenceClickListener(preference -> {
                if (PowermanagerUtil.hasPowerManagerOption(this.getActivity())) {
                    GenericAlertDialog dialog = GenericAlertDialog.newInstance(R.string.disable_powermanager_title,
                        String.format(getString(R.string.disable_powermanager_explain), getString(R.string.app_name)),
                        R.string.next,
                        R.string.cancel);

                    dialog.setTargetFragment(this, 0);
                    dialog.show(getParentFragmentManager(), DIALOG_TAG_POWERMANAGER_WORKAROUNDS);
                } else {
                    disableAutostart();
                }
                return true;
            });

            updatePowerManagerPrefs();
        } else {
            PreferenceCategory preferenceCategory = getPref("pref_key_fix_device");
            preferenceScreen.removePreference(preferenceCategory);
        }

        final Context context = getContext();
        if (context != null) {
            updateHibernationPref(context);
        } else {
            try {
                Preference hibernationPref = getPref(getString(R.string.preferences__hibernation_mode));
                preferenceScreen.removePreference(hibernationPref);
            } catch (IllegalArgumentException e) {
                logger.debug("Unable to remove prefs");
            }
        }

        DropDownPreference echoCancelPreference = getPref(getResources().getString(R.string.preferences__voip_echocancel));
        int echoCancelIndex = preferenceService.getAECMode().equals("sw") ? 1 : 0;
        final String[] echoCancelArray = getResources().getStringArray(R.array.list_echocancel);
        final List<String> echoCancelValuesArrayList = Arrays.asList(getResources().getStringArray(R.array.list_echocancel_values));

        echoCancelPreference.setSummary(echoCancelArray[echoCancelIndex]);
        echoCancelPreference.setOnPreferenceChangeListener((preference, newValue) -> {
            preference.setSummary(echoCancelArray[echoCancelValuesArrayList.indexOf(newValue.toString())]);
            return true;
        });

        final Preference webrtcDebugPreference = getPref(getResources().getString(R.string.preferences__webrtc_debug));
        webrtcDebugPreference.setOnPreferenceClickListener(preference -> {
            Intent intent = new Intent(getActivity(), WebRTCDebugActivity.class);
            requireActivity().startActivity(intent);
            return true;
        });

        final DropDownPreference videoCodecPreference = findPreference(getResources().getString(R.string.preferences__voip_video_codec));
        assert videoCodecPreference != null;
        final String[] videoCodecListDescription = getResources().getStringArray(R.array.list_video_codec);
        final List<String> videoCodecValuesList = Arrays.asList(getResources().getStringArray(R.array.list_video_codec_values));

        videoCodecPreference.setSummaryProvider((SummaryProvider<DropDownPreference>) preference -> {
            CharSequence value = preference.getEntry().toString();
            if (TextUtils.isEmpty(value)) {
                return videoCodecListDescription[videoCodecValuesList.indexOf(PreferenceService.VIDEO_CODEC_HW)];
            }
            return value;
        });

        final Preference webclientDebugPreference = getPref(getResources().getString(R.string.preferences__webclient_debug));
        webclientDebugPreference.setOnPreferenceClickListener(preference -> {
            Intent intent = new Intent(getActivity(), WebDiagnosticsActivity.class);
            requireActivity().startActivity(intent);
            return true;
        });

        if (ConfigUtils.isWorkRestricted()) {
            Boolean value = AppRestrictionUtil.getBooleanRestriction(getString(R.string.restriction__disable_calls));
            if (value != null && value) {
                PreferenceCategory preferenceCategory = findPreference("pref_key_voip");
                if (preferenceCategory != null) {
                    preferenceScreen.removePreference(preferenceCategory);
                }
            }
        }

        if (BuildFlavor.getCurrent().getForceThreemaPush()) {
            PreferenceCategory preferenceCategory = findPreference("pref_key_workarounds");
            if (preferenceCategory != null) {
                preferenceScreen.removePreference(preferenceCategory);
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        updateReadPhoneStatePermissionPref();

        updateBluetoothPermissionPref();

        final Context context = getContext();
        if (context != null) {
            updateHibernationPref(context);
        }
    }

    private void updateReadPhoneStatePermissionPref() {
        Context context = getContext();

        Preference phonePref = getPrefOrNull(R.string.preferences__grant_read_phone_state_permission);
        if (phonePref == null) {
            // This preference is not available if th_disable_calls is set to true
            return;
        }
        if (context != null && ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
            phonePref.setEnabled(false);
        } else {
            phonePref.setEnabled(true);
            phonePref.setOnPreferenceClickListener(preference -> {
                Activity activity = getActivity();
                if (activity == null) {
                    return false;
                }
                ConfigUtils.requestReadPhonePermission(activity, readPhoneStatePermissionLauncher);
                return true;
            });
        }
    }

    private void updateBluetoothPermissionPref() {
        Context context = getContext();

        Preference bluetoothPref = getPrefOrNull(R.string.preferences__grant_bluetooth_permission);
        if (bluetoothPref == null) {
            // This preference is not available if th_disable_calls is set to true
            return;
        }

        // This permission is not required before Android S, and therefore we do not enable this
        // preference in this case.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S
            || (context != null && ConfigUtils.isPermissionGranted(context, Manifest.permission.BLUETOOTH_CONNECT))
        ) {
            bluetoothPref.setEnabled(false);
        } else {
            bluetoothPref.setEnabled(true);
            bluetoothPref.setOnPreferenceClickListener(preference -> {
                Activity activity = getActivity();
                if (activity == null) {
                    return false;
                }
                ConfigUtils.requestBluetoothConnectPermission(activity, bluetoothPermissionLauncher);
                return true;
            });
        }
    }

    /**
     * Update the hibernation preference depending on the system settings and android version.
     * <p>
     * If hibernation is available and can be applied to Threema, the preference is shown and redirects
     * the users to the system setting. If hibernation is available on the device but Threema is excluded,
     * the preference is shown but disabled. If hibernation is not available on the device, the preference
     * isn't shown at all.
     *
     * @param context the context is needed to read the hibernation setting and to open system settings
     */
    private void updateHibernationPref(@NonNull Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            // "fix device" settings are not present on older Android versions
            return;
        }

        PreferenceCategory fixDeviceCategory = getPref("pref_key_fix_device");
        final Preference hibernationPref = getPrefOrNull(getString(R.string.preferences__hibernation_mode));
        if (hibernationPref == null) {
            return;
        }

        // Set summary depending on sdk version to match the exact description in settings
        // Note that on API 31 and 32 the hibernation setting is called differently (even though it is activated!)
        if (Build.VERSION.SDK_INT <= 32) {
            hibernationPref.setSummary(R.string.prefs_summary_hibernation_api_32);
        }

        // Handle the current state of hibernation
        final ListenableFuture<Integer> future = PackageManagerCompat.getUnusedAppRestrictionsStatus(context);
        Futures.addCallback(future, new FutureCallback<>() {
            @Override
            public void onSuccess(Integer result) {
                switch (result) {
                    case UnusedAppRestrictionsConstants.DISABLED:
                        // Only Android S and newer have the hibernation mode
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            // Disable hibernation preference if hibernation is explicitly disabled
                            hibernationPref.setEnabled(false);
                            break;
                        }
                        // fall through
                    case UnusedAppRestrictionsConstants.ERROR:
                        // fall through
                    case UnusedAppRestrictionsConstants.FEATURE_NOT_AVAILABLE:
                        // fall through
                    case UnusedAppRestrictionsConstants.API_30_BACKPORT:
                        // fall through
                    case UnusedAppRestrictionsConstants.API_30:
                        // Don't show the hibernation preference when hibernation is not available
                        fixDeviceCategory.removePreference(hibernationPref);
                        break;
                    case UnusedAppRestrictionsConstants.API_31:
                        // In this case hibernation is possible and can be disabled in settings
                        hibernationPref.setEnabled(true);
                        hibernationPref.setOnPreferenceClickListener(preference -> {
                            Intent intent = IntentCompat.createManageUnusedAppRestrictionsIntent(context, context.getPackageName());
                            activityResultLauncher.launch(intent);
                            return true;
                        });
                        break;
                }
            }

            @Override
            public void onFailure(@NonNull Throwable t) {
                logger.error("Could not get hibernation status", t);
                fixDeviceCategory.removePreference(hibernationPref);
            }
        }, ContextCompat.getMainExecutor(context));
    }

    private void updatePowerManagerPrefs() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            getPref(getResources().getString(R.string.preferences__powermanager_workarounds)).setEnabled(PowermanagerUtil.needsFixing(getActivity()));
        }
    }

    final protected boolean requiredInstances() {
        if (!this.checkInstances()) {
            this.instantiate();
        }
        return this.checkInstances();
    }

    protected boolean checkInstances() {
        return TestUtil.required(
            this.wallpaperService,
            this.lifetimeService,
            this.preferenceService,
            this.fileService,
            this.userService,
            this.ringtoneService,
            this.mutedChatsListService,
            this.messageService,
            this.contactService,
            this.apiConnector,
            this.contactModelRepository
        );
    }

    protected void instantiate() {
        ServiceManager serviceManager = ThreemaApplication.getServiceManager();
        if (serviceManager != null) {
            try {
                this.wallpaperService = serviceManager.getWallpaperService();
                this.lifetimeService = serviceManager.getLifetimeService();
                this.preferenceService = serviceManager.getPreferenceService();
                this.fileService = serviceManager.getFileService();
                this.userService = serviceManager.getUserService();
                this.ringtoneService = serviceManager.getRingtoneService();
                this.mutedChatsListService = serviceManager.getMutedChatsListService();
                this.mentionOnlyChatsListService = serviceManager.getMentionOnlyChatsListService();
                this.messageService = serviceManager.getMessageService();
                this.contactService = serviceManager.getContactService();
                this.notificationService = serviceManager.getNotificationService();
                this.apiConnector = serviceManager.getAPIConnector();
                this.contactModelRepository = serviceManager.getModelRepositories().getContacts();
            } catch (Exception e) {
                logger.error("Exception", e);
            }
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        this.fragmentView = view;
        super.onViewCreated(view, savedInstanceState);
    }

    @Override
    public void onYes(String tag, Object data) {
        switch (tag) {
            case DIALOG_TAG_REMOVE_WALLPAPERS:
                wallpaperService.removeAll(getActivity(), false);
                preferenceService.setCustomWallpaperEnabled(false);
                break;
            case DIALOG_TAG_RESET_RINGTONES:
                ringtoneService.resetRingtones(requireActivity().getApplicationContext());
                mutedChatsListService.clear();
                mentionOnlyChatsListService.clear();
                notificationService.deleteNotificationChannels();
                notificationService.createNotificationChannels();
                if (ConfigUtils.isWorkBuild()) {
                    preferenceService.setAfterWorkDNDEnabled(false);
                }
                Toast.makeText(requireActivity().getApplicationContext(), getString(R.string.reset_ringtones_confirm), Toast.LENGTH_SHORT).show();
                ListenerManager.conversationListeners.handle(ConversationListener::onModifiedAll);
                break;
            case DIALOG_TAG_IPV6_APP_RESTART:
                ipv6Preferences.setChecked(!(boolean) data);
                new Handler().postDelayed(() -> RuntimeUtil.runOnUiThread(() -> System.exit(0)), 700);
                break;
            case DIALOG_TAG_AUTOSTART_WORKAROUNDS:
                PowermanagerUtil.callAutostartManager(this);
                break;
            case DIALOG_TAG_POWERMANAGER_WORKAROUNDS:
                PowermanagerUtil.callPowerManager(this);
                break;
            case DIALOG_TAG_REALLY_ENABLE_THREEMA_PUSH:
                requestDisableBatteryOptimizations(
                    getString(R.string.threema_push),
                    R.string.cancel,
                    REQUEST_ID_DISABLE_BATTERY_OPTIMIZATIONS
                );
                break;
        }
    }

    private void disableAutostart() {
        if (PowermanagerUtil.hasAutostartOption(getActivity())) {
            GenericAlertDialog dialog = GenericAlertDialog.newInstance(R.string.disable_autostart_title,
                String.format(getString(R.string.disable_autostart_explain), getString(R.string.app_name)),
                R.string.next,
                R.string.cancel);

            dialog.setTargetFragment(this, 0);
            dialog.show(getParentFragmentManager(), DIALOG_TAG_AUTOSTART_WORKAROUNDS);
        } else {
            requestDisableBatteryOptimizations(getString(R.string.app_name), R.string.cancel, REQUEST_ID_DISABLE_BATTERY_OPTIMIZATIONS_HUAWEI);
        }
    }

    @Override
    public void onStart() {
        super.onStart();

        sharedPreferences.registerOnSharedPreferenceChangeListener(this);
        LocalBroadcastManager.getInstance(requireActivity()).registerReceiver(pushTokenResetBroadcastReceiver,
            new IntentFilter(ThreemaApplication.INTENT_PUSH_REGISTRATION_COMPLETE));
    }

    @Override
    public void onStop() {
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(this);
        LocalBroadcastManager.getInstance(requireActivity()).unregisterReceiver(pushTokenResetBroadcastReceiver);

        DialogUtil.dismissDialog(getParentFragmentManager(), DIALOG_TAG_PUSH_REGISTER, true);

        super.onStop();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (BuildFlavor.getCurrent().getForceThreemaPush()) {
            return;
        }

        if (key.equals(getString(R.string.preferences__threema_push_switch))) {
            boolean newValue = sharedPreferences.getBoolean(getString(R.string.preferences__threema_push_switch), false);

            if (!isAdded()) {
                return;
            }

            if (pushServicesInstalled) {
                // remove token silently
                PushUtil.enqueuePushTokenUpdate(getContext(), newValue, false);
            }

            if (newValue) {
                if (ThreemaPushService.tryStart(logger, ThreemaApplication.getAppContext())) {
                    Toast.makeText(getContext(), R.string.threema_push_activated, Toast.LENGTH_LONG).show();
                } else {
                    logger.info("Unable to activate threema push");
                    threemaPushTwoStatePreference.setChecked(false);
                }
            } else {
                requireActivity().stopService(new Intent(getActivity(), ThreemaPushService.class));
            }
        }
    }

    private void prepareSendLogfile() {
        TextEntryDialog dialog = TextEntryDialog.newInstance(R.string.prefs_sendlog_summary,
            R.string.enter_description,
            R.string.send,
            R.string.cancel,
            5,
            3000,
            1);
        dialog.setTargetFragment(this, 0);
        dialog.show(getParentFragmentManager(), DIALOG_TAG_SENDLOG);
    }


    @SuppressLint("StaticFieldLeak")
    public void sendLogFileToSupport(final String caption) {
        final Context context = getContext();
        if (context == null) {
            logger.warn("Cannot send logfile as the context is null");
            return;
        }

        SendToSupportBackgroundTask sendLogFileTask = new SendToSupportBackgroundTask(
            userService.getIdentity(),
            apiConnector,
            contactModelRepository,
            context
        ) {
            @Override
            public void onBefore() {
                GenericProgressDialog.newInstance(R.string.preparing_messages, R.string.please_wait).show(getParentFragmentManager(), DIALOG_TAG_SENDLOG);
            }

            @NonNull
            @Override
            public SendToSupportResult onSupportAvailable(@NonNull ContactModel contactModel) {
                File zipFile = DebugLogFileBackend.getZipFile(fileService);

                try {
                    ContactMessageReceiver receiver = contactService.createReceiver(contactModel);

                    messageService.sendText(caption +
                        "\n-- \n" +
                        ConfigUtils.getSupportDeviceInfo() + "\n" +
                        getVersionString() + "\n" +
                        userService.getIdentity(), receiver);

                    MediaItem mediaItem = new MediaItem(Uri.fromFile(zipFile), MediaItem.TYPE_FILE);
                    mediaItem.setFilename(zipFile.getName());
                    mediaItem.setMimeType(MimeUtil.MIME_TYPE_ZIP);

                    messageService.sendMediaAsync(Collections.singletonList(mediaItem),
                        Collections.singletonList(receiver), new MessageServiceImpl.SendResultListener() {
                            @Override
                            public void onError(String errorMessage) {
                                RuntimeUtil.runOnUiThread(() -> Toast.makeText(getContext(), R.string.an_error_occurred_during_send, Toast.LENGTH_LONG).show());
                            }

                            @Override
                            public void onCompleted() {
                                RuntimeUtil.runOnUiThread(() -> Toast.makeText(getContext(), R.string.message_sent, Toast.LENGTH_LONG).show());
                            }
                        });
                } catch (Exception e) {
                    logger.error("Could not send log file");
                    return SendToSupportResult.FAILED;
                }

                return SendToSupportResult.SUCCESS;
            }

            @Override
            public void onFinished(@NonNull SendToSupportResult result) {
                if (isAdded()) {
                    DialogUtil.dismissDialog(getParentFragmentManager(), DIALOG_TAG_SENDLOG, true);

                    if (result == SendToSupportResult.SUCCESS) {
                        Toast.makeText(requireActivity().getApplicationContext(), R.string.message_sent, Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(requireActivity().getApplicationContext(), R.string.an_error_occurred, Toast.LENGTH_LONG).show();
                    }
                }
            }
        };

        if (backgroundExecutor == null) {
            backgroundExecutor = new BackgroundExecutor();
        }

        backgroundExecutor.execute(sendLogFileTask);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_ID_DISABLE_BATTERY_OPTIMIZATIONS:
                threemaPushTwoStatePreference.setChecked(resultCode == Activity.RESULT_OK);
                break;
            case REQUEST_ID_DISABLE_BATTERY_OPTIMIZATIONS_HUAWEI:
                updatePowerManagerPrefs();
                break;
            case RESULT_DISABLE_POWERMANAGER:
                disableAutostart();
                updatePowerManagerPrefs();
                break;
            case RESULT_DISABLE_AUTOSTART:
                requestDisableBatteryOptimizations(getString(R.string.app_name), R.string.cancel, REQUEST_ID_DISABLE_BATTERY_OPTIMIZATIONS_HUAWEI);
                updatePowerManagerPrefs();
                break;
            default:
                super.onActivityResult(requestCode, resultCode, data);
                break;
        }
    }

    @NonNull
    private String getVersionString() {
        StringBuilder builder = new StringBuilder();
        builder.append(ConfigUtils.getAppVersion());
        if (BuildConfig.DEBUG) {
            builder.append(" (Commit ");
            builder.append(BuildConfig.GIT_HASH);
            builder.append(")");
        }
        return builder.toString();
    }

    /**
     * Request disabling of battery optimizations.
     *
     * @param name The name of the component. This will be used in messages like
     *             "Battery optimizations prevent X from working properly".
     */
    private void requestDisableBatteryOptimizations(@NonNull String name, int label, int requestId) {
        final Intent intent = new Intent(getActivity(), DisableBatteryOptimizationsActivity.class);
        intent.putExtra(DisableBatteryOptimizationsActivity.EXTRA_NAME, name);
        intent.putExtra(DisableBatteryOptimizationsActivity.EXTRA_CANCEL_LABEL, label);
        startActivityForResult(intent, requestId);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions, @NonNull int[] grantResults) {
        boolean result = (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED);

        switch (requestCode) {
            case PERMISSION_REQUEST_MESSAGE_LOG:
                DebugLogFileBackend.setEnabled(result);
                messageLogPreference.setChecked(result);
                break;
            case PERMISSION_REQUEST_SEND_LOG:
                if (result) {
                    prepareSendLogfile();
                }
                break;
        }
    }

    @Override
    public void onYes(@NonNull String tag, @NonNull String text) {
        sendLogFileToSupport(text);
    }

    @Override
    public void onNo(String tag) {
    }

    @Override
    public void onNo(String tag, Object data) {
        if (DIALOG_TAG_IPV6_APP_RESTART.equals(tag)) {
            boolean oldValue = (boolean) data;
            ipv6Preferences.setChecked(oldValue);
        }
    }

    @Override
    public void onCancel(String tag, Object object) {
    }

    @Override
    protected int getPreferenceTitleResource() {
        return R.string.prefs_advanced_options;
    }

    @Override
    public int getPreferenceResource() {
        return R.xml.preference_advanced_options;
    }
}

/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2014-2025 Threema GmbH
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

package ch.threema.app.utils;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.OperationApplicationException;
import android.content.SharedPreferences;
import android.content.pm.ConfigurationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.RemoteException;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.search.SearchBar;
import com.google.android.material.snackbar.BaseTransientBottomBar;
import com.google.android.material.snackbar.Snackbar;

import org.maplibre.android.MapLibre;
import org.slf4j.Logger;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import javax.net.ssl.SSLSocketFactory;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.AttrRes;
import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.Px;
import androidx.annotation.RequiresApi;
import androidx.annotation.StringDef;
import androidx.annotation.StringRes;
import androidx.annotation.UiThread;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.view.menu.MenuBuilder;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.MenuCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.media3.ui.PlayerView;
import androidx.preference.PreferenceManager;
import androidx.window.layout.WindowMetrics;
import androidx.window.layout.WindowMetricsCalculator;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import ch.threema.app.BuildConfig;
import ch.threema.app.BuildFlavor;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.home.HomeActivity;
import ch.threema.app.dialogs.SimpleStringAlertDialog;
import ch.threema.app.managers.ServiceManager;
import ch.threema.app.notifications.NotificationChannels;
import ch.threema.app.onprem.OnPremSSLSocketFactoryProvider;
import ch.threema.app.restrictions.AppRestrictionService;
import ch.threema.app.restrictions.AppRestrictionUtil;
import ch.threema.app.services.LockAppService;
import ch.threema.app.preference.service.PreferenceService;
import ch.threema.app.services.license.LicenseService;
import ch.threema.app.threemasafe.ThreemaSafeConfigureActivity;
import ch.threema.app.workers.RestartWorker;
import ch.threema.base.utils.LoggingUtil;

import static android.content.res.Configuration.UI_MODE_NIGHT_YES;
import static androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
import static androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO;
import static androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES;
import static ch.threema.app.camera.CameraUtil.isInternalCameraSupported;
import static ch.threema.app.preference.service.PreferenceService.EmojiStyle_DEFAULT;
import static ch.threema.app.services.notification.NotificationServiceImpl.APP_RESTART_NOTIFICATION_ID;
import static ch.threema.app.utils.IntentDataUtil.PENDING_INTENT_FLAG_MUTABLE;

public class ConfigUtils {
    private static final Logger logger = LoggingUtil.getThreemaLogger("ConfigUtils");

    private static final int CONTENT_PROVIDER_BATCH_SIZE = 50;
    private static final String WORKER_RESTART_AFTER_RESTORE = "restartAfterRestore";

    /* app theme settings in shared preferences */
    @StringDef({THEME_LIGHT, THEME_DARK, THEME_FOLLOW_SYSTEM})
    public @interface AppThemeSetting {
    }

    public static final String THEME_LIGHT = "0";
    public static final String THEME_DARK = "1";
    public static final String THEME_FOLLOW_SYSTEM = "2";

    private static Integer miuiVersion = null;

    @PreferenceService.EmojiStyle
    public static int emojiStyle = 0;

    private static Boolean isTablet = null, isBiggerSingleEmojis = null;
    private static int preferredThumbnailWidth = -1, preferredAudioMessageWidth = -1, currentDayNightMode;
    private static WeakReference<MapLibre> mapLibreWeakReference = null;

    private static final float[] NEGATIVE_MATRIX = {
        -1.0f, 0, 0, 0, 255, // red
        0, -1.0f, 0, 0, 255, // green
        0, 0, -1.0f, 0, 255, // blue
        0, 0, 0, 1.0f, 0  // alpha
    };

    public static boolean isTabletLayout(Context context) {
        if (isTablet != null) {
            return isTablet;
        }
        isTablet = false;

        if (context != null) {
            Resources res = context.getResources();

            if (res != null) {
                isTablet = res.getBoolean(R.bool.tablet_layout);
            }
        }
        return isTablet;
    }

    public static boolean isTabletLayout() {
        Context appContext = ThreemaApplication.getAppContext();
        return isTabletLayout(appContext);
    }

    public static boolean isLandscape(Context context) {
        return context.getResources().getBoolean(R.bool.is_landscape);
    }

    public static boolean isAmazonDevice() {
        return (Build.MANUFACTURER.equals("Amazon"));
    }

    public static boolean isHuaweiDevice() {
        return (Build.MANUFACTURER.equalsIgnoreCase("Huawei") && !Build.MODEL.contains("Nexus"));
    }

    public static boolean isOnePlusDevice() {
        return (Build.MANUFACTURER.equalsIgnoreCase("OnePlus"));
    }

    public static boolean isSamsungDevice() {
        return (Build.MANUFACTURER.equalsIgnoreCase("Samsung"));
    }

    public static boolean isMotorolaDevice() {
        return (Build.MANUFACTURER.equalsIgnoreCase("motorola"));
    }

    public static boolean isSonyDevice() {
        return (Build.MANUFACTURER.equalsIgnoreCase("Sony"));
    }

    public static boolean isNokiaDevice() {
        return Build.MANUFACTURER.equalsIgnoreCase("HMD Global");
    }

    public static boolean supportsNotificationChannels() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O;
    }

    public static boolean supportsVideoCapture() {
        return isInternalCameraSupported();
    }

    public static boolean supportsPictureInPicture(Context context) {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE);
    }

    /**
     * Get the supported gl es version on this device. Note that in cases where the version could
     * not be determined, {@link Double#NaN} is returned.
     * Note that this check may not work on all devices. On some devices, this returns version 3.0
     * even if only version 2.x is supported. However, on some devices this check works.
     */
    public static double getSupportedGlEsVersion(@NonNull Context context) {
        try {
            ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            ConfigurationInfo configurationInfo = activityManager.getDeviceConfigurationInfo();
            double supportedGlVersion = Double.parseDouble(configurationInfo.getGlEsVersion());
            logger.debug("GlEs version: {}", supportedGlVersion);
            return supportedGlVersion;
        } catch (Exception e) {
            logger.error("Could not determine gl es version", e);
            return Double.NaN;
        }
    }

    /**
     * Check if this device has a bug related to media codec's async mode as described here https://github.com/google/ExoPlayer/issues/10021
     */
    public static boolean hasAsyncMediaCodecBug() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && ConfigUtils.isSamsungDevice() && Build.MODEL.startsWith("SM-G97");
    }

    /* device creates distorted audio recordings with a 44.1kHz sampling rate */
    public static boolean hasBrokenAudioRecorder() {
        return ConfigUtils.isXiaomiDevice() && "Mi 9T".equals(Build.MODEL);
    }

    public static boolean hasScopedStorage() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q;
    }

    public static boolean isCallsEnabled() {
        ServiceManager serviceManager = ThreemaApplication.getServiceManager();

        if (serviceManager != null) {
            return serviceManager.getPreferenceService().isVoipEnabled()
                && !AppRestrictionUtil.isCallsDisabled();
        }
        return true;
    }

    public static boolean isVideoCallsEnabled() {
        ServiceManager serviceManager = ThreemaApplication.getServiceManager();

        if (serviceManager != null) {
            return BuildConfig.VIDEO_CALLS_ENABLED
                && serviceManager.getPreferenceService().areVideoCallsEnabled()
                && !AppRestrictionUtil.isVideoCallsDisabled();
        }
        return BuildConfig.VIDEO_CALLS_ENABLED;
    }

    public static boolean isGroupCallsEnabled() {
        ServiceManager serviceManager = ThreemaApplication.getServiceManager();

        return serviceManager != null
            && serviceManager.getPreferenceService().areGroupCallsEnabled()
            && !AppRestrictionUtil.isGroupCallsDisabled()
            && !AppRestrictionUtil.isCallsDisabled();
    }

    public static boolean isWorkDirectoryEnabled() {
        ServiceManager serviceManager = ThreemaApplication.getServiceManager();

        if (serviceManager != null) {
            return serviceManager.getPreferenceService().getWorkDirectoryEnabled()
                && !AppRestrictionUtil.isWorkDirectoryDisabled();
        }
        return false;
    }

    /**
     * Check if multi-device is enabled. This checks the restriction th_disable_multidevice. If not
     * set, then check th_disable_web which is used as fallback when th_disable_multidevice is not
     * set.
     */
    public static boolean isMultiDeviceEnabled(@NonNull Context context) {
        Boolean isMultiDeviceDisabled = AppRestrictionUtil.isMultiDeviceDisabled(context);
        if (isMultiDeviceDisabled != null) {
            return !isMultiDeviceDisabled;
        } else {
            return !AppRestrictionUtil.isWebDisabled(context);
        }
    }

    /**
     * Check, whether remote secrets are supported by this build. Note that support for remote secrets
     * does not necessarily mean, that the feature is active. This still depends on the app configuration.
     *
     * @return `true` if remote secrets are supported by this build, `false` otherwise.
     */
    public static boolean isRemoteSecretsSupported() {
        return BuildConfig.REMOTE_SECRETS_SUPPORTED && isOnPremBuild();
    }

    public static boolean isXiaomiDevice() {
        return Build.MANUFACTURER.equalsIgnoreCase("Xiaomi");
    }

    /**
     * return current MIUI version level or 0 if no Xiaomi device or MIUI version is not recognized or not relevant
     *
     * @return MIUI version level or 0
     */
    public static int getMIUIVersion() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || !isXiaomiDevice()) {
            return 0;
        }
        if (miuiVersion == null) {
            miuiVersion = 0;

            try {
                Class<?> c = Class.forName("android.os.SystemProperties");
                Method get = c.getMethod("get", String.class);
                String version = (String) get.invoke(c, "ro.miui.ui.version.name");

                if (version != null) {
                    if (version.startsWith("V10")) {
                        miuiVersion = 10;
                    } else if (version.startsWith("V11")) {
                        miuiVersion = 11;
                    } else if (version.startsWith("V12")) {
                        miuiVersion = 12;
                    } else if (version.startsWith("V13")) {
                        miuiVersion = 13;
                    } else if (version.startsWith("V14")) {
                        miuiVersion = 14;
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return miuiVersion;
    }

    /**
     * Set the setting for the app wide theme and optionally save to shared prefs and apply setting to system
     *
     * @param theme One of THEME_LIGHT, THEME_DARK or THEME_FOLLOW_SYSTEM
     */
    public static void saveAppThemeToPrefs(@AppThemeSetting String theme, Context context) {
        PreferenceManager.getDefaultSharedPreferences(ThreemaApplication.getAppContext()).edit().putString(ThreemaApplication.getAppContext().getString(R.string.preferences__theme), String.valueOf(theme)).apply();
        AppCompatDelegate.setDefaultNightMode(getDayNightModeFromAppThemeSetting(theme));
        getCurrentDayNightMode(context);
    }

    public static @AppThemeSetting String getAppThemeSettingFromDayNightMode(@AppCompatDelegate.NightMode int dayNightMode) {
        switch (dayNightMode) {
            case MODE_NIGHT_NO:
                return THEME_LIGHT;
            case MODE_NIGHT_YES:
                return THEME_DARK;
            default:
                return THEME_FOLLOW_SYSTEM;
        }
    }

    public static @AppCompatDelegate.NightMode int getDayNightModeFromAppThemeSetting(@AppThemeSetting String appThemeSetting) {
        switch (appThemeSetting) {
            case THEME_DARK:
                return MODE_NIGHT_YES;
            case THEME_LIGHT:
                return MODE_NIGHT_NO;
            default:
                return MODE_NIGHT_FOLLOW_SYSTEM;
        }
    }

    @SuppressLint("WrongConstant")
    public static @AppThemeSetting String getAppThemePrefsSettings(@NonNull Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

        // set default day/night setting depending on app type
        @AppThemeSetting String appThemeSetting = null;
        if (prefs != null) {
            appThemeSetting = prefs.getString(context.getString(R.string.preferences__theme), null);
        }
        if (TestUtil.isEmptyOrNull(appThemeSetting)) {
            // fix default setting according to app flavor
            appThemeSetting = BuildConfig.DEFAULT_APP_THEME;
            if (prefs != null) {
                prefs.edit().putString(context.getString(R.string.preferences__theme), BuildConfig.DEFAULT_APP_THEME).apply();
            }
        }
        return appThemeSetting;
    }

    public static @AppCompatDelegate.NightMode int getAppThemePrefs(@NonNull Context context) {
        return getDayNightModeFromAppThemeSetting(getAppThemePrefsSettings(context));
    }

    public static boolean isTheDarkSide(@NonNull Context context) {
        return getCurrentDayNightMode(context) == MODE_NIGHT_YES;
    }

    /**
     * Get current day night of the system
     *
     * @return Either MODE_NIGHT_NO or MODE_NIGHT_YES
     */
    public static @AppCompatDelegate.NightMode int getCurrentDayNightMode(@NonNull Context context) {
        if (context != ThreemaApplication.getAppContext()) {
            currentDayNightMode = (context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == UI_MODE_NIGHT_YES ? MODE_NIGHT_YES : MODE_NIGHT_NO;
        }
        return currentDayNightMode;
    }

    /**
     * Store current day/night mode.
     *
     * @param dayNightMode must be either MODE_NIGHT_YES or MODE_NIGHT_NO
     */
    public static void setCurrentDayNightMode(@AppCompatDelegate.NightMode int dayNightMode) {
        currentDayNightMode = dayNightMode;
    }

    public static @ColorInt int getColorFromAttribute(@NonNull Context context, @AttrRes int attr) {
        TypedArray typedArray = context.getTheme().obtainStyledAttributes(new int[]{attr});
        @ColorInt int color = typedArray.getColor(0, -1);

        typedArray.recycle();

        return color;
    }

    /**
     * Tint the icons of a complete menu to the provided color. This is necessary when using MenuInflater which does not support the iconTint attribute
     * (SupportMenuInflater / startSupportActionMode respects iconTint)
     *
     * @param menu  The menu to tint
     * @param color The ColorInt to tint the icons
     */
    public static void tintMenuIcons(@NonNull Menu menu, @ColorInt int color) {
        for (int i = 0, size = menu.size(); i < size; i++) {
            final MenuItem menuItem = menu.getItem(i);
            tintMenuIcon(menuItem, color);
            if (menuItem.hasSubMenu() && menuItem.getSubMenu() != null) {
                tintMenuIcons(menuItem.getSubMenu(), color);
            }
        }
    }

    public static void tintMenuIcon(@Nullable final MenuItem menuItem, @ColorInt int color) {
        if (menuItem != null) {
            final Drawable drawable = menuItem.getIcon();
            if (drawable != null) {
                drawable.mutate();
                drawable.setColorFilter(color, PorterDuff.Mode.SRC_IN);
            }
        }
    }

    public static void tintMenuIcon(@NonNull Context context, @Nullable final MenuItem menuItem, @AttrRes int colorAttr) {
        tintMenuIcon(menuItem, getColorFromAttribute(context, colorAttr));
    }

    public static void setEmojiStyle(Context context, int newStyle) {
        if (newStyle != -1) {
            emojiStyle = newStyle;
        } else {
            emojiStyle = Integer.parseInt(
                PreferenceManager.getDefaultSharedPreferences(context).getString(context.getString(R.string.preferences__emoji_style), "0")
            );
        }
    }

    public static boolean isBiggerSingleEmojis(Context context) {
        if (isBiggerSingleEmojis == null) {
            isBiggerSingleEmojis = PreferenceManager.getDefaultSharedPreferences(context).getBoolean(context.getString(R.string.preferences__bigger_single_emojis), true);
        }
        return isBiggerSingleEmojis;
    }

    public static void setBiggerSingleEmojis(boolean value) {
        isBiggerSingleEmojis = value;
    }

    public static boolean isDefaultEmojiStyle() {
        return emojiStyle == EmojiStyle_DEFAULT;
    }

    /**
     * Get user-facing application version string including alpha/beta version suffix
     */
    public static String getAppVersion() {
        return BuildConfig.VERSION_NAME;
    }

    @NonNull
    public static String getRatingAppVersion() {
        return BuildConfig.VERSION_NAME + "/" + BuildFlavor.getCurrent().getFullDisplayName();
    }

    /**
     * Get build number of this app build, including architecture-specific multipliers.
     * <p>
     * NOTE: This is almost the same as `BuildConfig.VERSION_CODE`. However, the value returned
     * from this function may contain a multiplier depending on the target architecture,
     * while `BuildConfig.VERSION_CODE` always returns just the plain version code. For example:
     * If this function returns `9000936`, then `BuildConfig.VERSION_CODE` would contain just `936`.
     */
    public static int getBuildNumber(Context context) {
        try {
            PackageInfo packageInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            if (packageInfo != null) {
                return packageInfo.versionCode;
            }
        } catch (PackageManager.NameNotFoundException x) {
            logger.error("Exception", x);
        }
        return 0;
    }

    /**
     * Return information about the device, including the manufacturer and the model.
     */
    public static @NonNull String getDeviceInfo(boolean includeAppVersion) {
        final StringBuilder info = new StringBuilder();
        if (includeAppVersion) {
            info.append(getAppVersion()).append("/");
        }
        info.append(Build.MANUFACTURER).append(";")
            .append(Build.MODEL).append("/")
            .append(Build.VERSION.RELEASE).append("/")
            .append(BuildFlavor.getCurrent().getFullDisplayName());
        return info.toString();
    }

    /**
     * Return information about the device including the manufacturer and the model.
     * The version is NOT included.
     * If mdm parameters are active on this device they are also appended according to ANDR-2213.
     *
     * @return The device info meant to be sent with support requests
     */
    public static @NonNull String getSupportDeviceInfo() {
        final StringBuilder info = new StringBuilder(getDeviceInfo(false));
        if (isWorkRestricted()) {
            String mdmSource = AppRestrictionService.getInstance().getMdmSource();
            if (mdmSource != null) {
                info.append("/").append(mdmSource);
            }
        }
        return info.toString();
    }

    public static String getPrivacyPolicyURL(Context context) {
        return getLicenceURL(context, R.string.privacy_policy_url);
    }

    public static String getTermsOfServiceURL(Context context) {
        return getLicenceURL(context, R.string.terms_of_service_url);
    }

    public static String getEulaURL(Context context) {
        return getLicenceURL(context, R.string.eula_url);
    }

    private static String getLicenceURL(Context context, @StringRes int url) {
        String language = LocaleUtil.getAppLanguage();
        String lang = language.startsWith("de") || language.startsWith("gsw")
            ? "de"
            : "en";
        String version = ConfigUtils.getAppVersion();
        boolean darkModeOverride = false;
        if (context instanceof AppCompatActivity) {
            darkModeOverride = ((AppCompatActivity) context).getDelegate().getLocalNightMode() == MODE_NIGHT_YES;
        }

        String theme = isTheDarkSide(context) || darkModeOverride ? "dark" : "light";

        return String.format(context.getString(url), lang, version, theme);
    }

    public static String getWorkExplainURL(Context context) {
        String lang = LocaleUtil.getAppLanguage();

        if (lang.length() >= 2) {
            lang = lang.substring(0, 2);
        } else {
            lang = "en";
        }

        return String.format(context.getString(R.string.work_explain_url), lang);
    }

    public static void recreateActivity(Activity activity) {
        activity.finish();

        final Intent intent = new Intent(activity, HomeActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        activity.startActivity(intent);
    }

    public static void scheduleAppRestart(@NonNull Context context) {
        scheduleAppRestart(context, 1000, null);
    }

    public static void scheduleAppRestart(@NonNull Context context, int delayMs) {
        scheduleAppRestart(context, delayMs, null);
    }

    /**
     * Android Q and newer does not allow restart in the background:
     * <a href="https://developer.android.com/preview/privacy/background-activity-starts">Restrictions on starting activities from the background</a>
     *
     * @param eventTriggerTitle Will have no effect on Android versions below Q or if we do not own the POST_NOTIFICATIONS
     *                          permission on newer Android versions.
     */
    public static void scheduleAppRestart(@NonNull Context context, int delayMs, @Nullable String eventTriggerTitle) {
        Intent restartIntent = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
        Objects.requireNonNull(restartIntent).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(
            context,
            0,
            restartIntent,
            PendingIntent.FLAG_CANCEL_CURRENT | PENDING_INTENT_FLAG_MUTABLE
        );
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            // on older android version we restart directly after delayMs
            AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            manager.set(AlarmManager.RTC, System.currentTimeMillis() + delayMs, pendingIntent);
        } else if (
            eventTriggerTitle == null ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            // use WorkManager to restart the app in the background
            final WorkManager workManager = WorkManager.getInstance(ThreemaApplication.getAppContext());
            final OneTimeWorkRequest workRequest = RestartWorker.Companion.buildOneTimeWorkRequest(delayMs);
            workManager.enqueueUniqueWork(WORKER_RESTART_AFTER_RESTORE, ExistingWorkPolicy.REPLACE, workRequest);
        } else {
            // use a notification to trigger restart (app in background can no longer start activities in Android 12+)
            String text = context.getString(R.string.tap_to_start, context.getString(R.string.app_name));

            NotificationCompat.Builder builder =
                new NotificationCompat.Builder(context, NotificationChannels.NOTIFICATION_CHANNEL_ALERT)
                    .setSmallIcon(R.drawable.ic_notification_small)
                    .setContentTitle(eventTriggerTitle)
                    .setContentText(eventTriggerTitle)
                    .setDefaults(Notification.DEFAULT_LIGHTS | Notification.DEFAULT_VIBRATE)
                    .setPriority(NotificationCompat.PRIORITY_MAX)
                    .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                    .setContentIntent(pendingIntent)
                    .setAutoCancel(false);

            NotificationManagerCompat notificationManagerCompat = NotificationManagerCompat.from(context);
            notificationManagerCompat.notify(APP_RESTART_NOTIFICATION_ID, builder.build());
        }
    }

    public static boolean checkAvailableMemory(float required) {
        // Get max available VM memory, exceeding this amount will throw an
        // OutOfMemory exception
        return (Runtime.getRuntime().maxMemory() > required);
    }

    public static boolean isWorkBuild() {
        return BuildFlavor.getCurrent().isWork();
    }

    public static boolean isOnPremBuild() {
        return BuildFlavor.getCurrent().isOnPrem();
    }

    public static boolean isWhitelabelOnPremBuild(@NonNull Context context) {
        return isOnPremBuild() && !context.getPackageName().equals("ch.threema.app.onprem");
    }

    public static boolean isDemoOPServer(@NonNull PreferenceService preferenceService) {
        return preferenceService.getOnPremServer() != null && preferenceService.getOnPremServer().toLowerCase().contains(".3ma.ch/");
    }

    public static boolean isDevBuild() {
        String currentBuildFlavorDisplayName = BuildFlavor.getCurrent().getFullDisplayName();
        return currentBuildFlavorDisplayName.contains("DEBUG") ||
            currentBuildFlavorDisplayName.equals("Blue") || currentBuildFlavorDisplayName.equals("DEV") ||
            currentBuildFlavorDisplayName.equals("Green");
    }

    public static boolean supportGroupDescription() {
        return false;
    }

    /**
     * Returns true if this is a work build and app is under control of a device policy controller (DPC) or Threema MDM
     *
     * @return boolean
     */
    public static boolean isWorkRestricted() {
        if (!isWorkBuild()) {
            return false;
        }

        Bundle restrictions = AppRestrictionService.getInstance()
            .getAppRestrictions();

        return restrictions != null && !restrictions.isEmpty();
    }

    public static boolean isSerialLicenseValid() {
        ServiceManager serviceManager = ThreemaApplication.getServiceManager();
        if (serviceManager != null) {
            if (isOnPremBuild()) {
                // OnPrem needs server info in addition to license
                if (serviceManager.getPreferenceService().getOnPremServer() == null) {
                    return false;
                }
            }

            LicenseService<?> licenseService = serviceManager.getLicenseService();
            return isSerialLicensed() && licenseService.hasCredentials() && licenseService.isLicensed();
        }
        return false;
    }

    public static boolean isSerialLicensed() {
        BuildFlavor.LicenseType currentLicenseType = BuildFlavor.getCurrent().getLicenseType();
        return currentLicenseType.equals(BuildFlavor.LicenseType.GOOGLE_WORK)
            || currentLicenseType.equals(BuildFlavor.LicenseType.HMS_WORK)
            || currentLicenseType.equals(BuildFlavor.LicenseType.SERIAL)
            || currentLicenseType.equals(BuildFlavor.LicenseType.ONPREM);
    }

    public static boolean hasInvalidCredentials() {
        return (ConfigUtils.isOnPremBuild() || ConfigUtils.isWorkBuild()) && ConfigUtils.isSerialLicensed() && !ConfigUtils.isSerialLicenseValid();
    }

    /**
     * Returns true if privacy settings imply that screenshots and app switcher thumbnails should be disabled
     *
     * @return true if disabled, false otherwise or in case of failure
     */
    public static boolean getScreenshotsDisabled(@Nullable PreferenceService preferenceService, @Nullable LockAppService lockAppService) {
        return preferenceService != null && lockAppService != null && lockAppService.isLockingEnabled();
    }

    public static void setScreenshotsAllowed(@NonNull Activity activity, @Nullable PreferenceService preferenceService, @Nullable LockAppService lockAppService) {
        // call this before setContentView
        if (getScreenshotsDisabled(preferenceService, lockAppService) ||
            (preferenceService != null && preferenceService.areScreenshotsDisabled()) ||
            activity instanceof ThreemaSafeConfigureActivity) {
            activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        } else {
            activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
        }
    }

    @Deprecated
    public static boolean useContentUris() {
        return true;
    }

    public static boolean hasProtection(PreferenceService preferenceService) {
        return !PreferenceService.LockingMech_NONE.equals(preferenceService.getLockMechanism());
    }

    /*
     * Returns the height of the status bar (showing battery or network status) on top of the screen
     */
    public static int getStatusBarHeight(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowMetrics windowMetrics = WindowMetricsCalculator.getOrCreate().computeCurrentWindowMetrics(context);
            Insets insets = windowMetrics.getWindowInsets().getInsets(WindowInsetsCompat.Type.statusBars());
            return insets.top - insets.bottom;
        }

        int result = 0;
        @SuppressLint({"InternalInsetResource", "DiscouragedApi"})
        int resourceId = context.getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resourceId > 0) {
            result = context.getResources().getDimensionPixelSize(resourceId);
        }
        return result;
    }

    /*
     * Returns the height of the navigation bar at the bottom of some devices
     */
    public static int getNavigationBarHeight(Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowMetrics windowMetrics = WindowMetricsCalculator.getOrCreate().computeCurrentWindowMetrics(activity);
            androidx.core.graphics.Insets insets = windowMetrics.getWindowInsets().getInsets(WindowInsetsCompat.Type.navigationBars());
            return insets.bottom - insets.top;
        }

        if (activity.isInMultiWindowMode()) {
            return 0;
        }

        NavigationBarDimensions dimensions = new NavigationBarDimensions();
        getNavigationBarDimensions(activity.getWindowManager(), dimensions);

        return dimensions.height;
    }

    @Deprecated
    public static void getNavigationBarDimensions(WindowManager windowManager, NavigationBarDimensions dimensions) {
        dimensions.width = dimensions.height = 0;

        DisplayMetrics metrics = new DisplayMetrics();
        // get dimensions of usable display space with decorations (status bar / navigation bar) subtracted
        windowManager.getDefaultDisplay().getMetrics(metrics);
        int usableHeight = metrics.heightPixels;
        int usableWidth = metrics.widthPixels;
        // get dimensions of display without subtracting any decorations
        windowManager.getDefaultDisplay().getRealMetrics(metrics);
        int realHeight = metrics.heightPixels;
        int realWidth = metrics.widthPixels;
        if (realHeight > usableHeight)
            dimensions.height = realHeight - usableHeight;

        if (realWidth > usableWidth)
            dimensions.width = realWidth - usableWidth;
    }

    /**
     * Get real height of window including system insets in case of a fullscreen window
     * Also works for floating or split screen windows
     *
     * @param windowManager WindowManager
     * @return Height in pixel
     */
    public static int getRealWindowHeight(@NonNull WindowManager windowManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return windowManager.getCurrentWindowMetrics().getBounds().height();
        } else {
            DisplayMetrics metrics = new DisplayMetrics();
            windowManager.getDefaultDisplay().getRealMetrics(metrics);
            return metrics.heightPixels;
        }
    }

    public static boolean checkManifestPermission(Context context, String packageName, final String permission) {
        if (TextUtils.isEmpty(permission)) {
            return false;
        }

        PackageManager pm = context.getPackageManager();
        try {
            PackageInfo packageInfo = pm.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS);
            if (packageInfo != null) {
                String[] requestedPermissions = packageInfo.requestedPermissions;

                if (requestedPermissions != null && requestedPermissions.length > 0) {
                    for (String requestedPermission : requestedPermissions) {
                        if (permission.equalsIgnoreCase(requestedPermission)) {
                            return true;
                        }
                    }
                }
            }
        } catch (PackageManager.NameNotFoundException e) {
            logger.error("Exception", e);
        }
        return false;
    }

    public static class NavigationBarDimensions {
        public int width;
        public int height;
    }

    /**
     * @return The resolved height of attribute {@code R.attr.actionBarSize} in pixels.
     */
    @Px
    public static int getActionBarSize(@NonNull Context context) {
        TypedValue typedValue = new TypedValue();
        if (context.getTheme().resolveAttribute(R.attr.actionBarSize, typedValue, true)) {
            return TypedValue.complexToDimensionPixelSize(typedValue.data, context.getResources().getDisplayMetrics());
        }
        return 0;
    }

    public static void adjustToolbar(Context context, Toolbar toolbar) {
        // adjust toolbar height after rotate
        if (toolbar != null) {
            int size = getActionBarSize(context);
            toolbar.setMinimumHeight(size);
            ViewGroup.LayoutParams lp = toolbar.getLayoutParams();
            lp.height = size;
            toolbar.setLayoutParams(lp);
        }
    }

    public static void invertColors(ImageView imageView) {
        imageView.setColorFilter(new ColorMatrixColorFilter(NEGATIVE_MATRIX));
    }

    public static boolean isPermissionGranted(@NonNull Context context, @NonNull String permission) {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Request all possibly required permissions of Contacts group
     *
     * @param activity    Activity context for onRequestPermissionsResult callback
     * @param requestCode request code for onRequestPermissionsResult callback
     * @return true if permissions are already granted, false otherwise
     */
    public static boolean requestContactPermissions(@NonNull Activity activity, Fragment fragment, int requestCode) {
        String[] permissions = new String[]{Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS, Manifest.permission.GET_ACCOUNTS};

        if (checkIfNeedsPermissionRequest(activity, permissions)) {
            requestPermissions(activity, fragment, permissions, requestCode);
            return false;
        }
        return true;
    }

    /**
     * Determine whether you have been granted the permission for read access to videos and images.
     * Prior to Android 13 READ_EXTERNAL_STORAGE is checked.
     *
     * @param context the context to check the permissions
     * @return true if permissions are already granted, false otherwise
     */
    public static boolean isVideoImagePermissionGranted(@NonNull Context context) {
        return isPartialVideoImagePermissionGranted(context) || isFullVideoImagePermissionGranted(context);
    }

    /**
     * Determine whether you have been fully granted the permission for access to videos and images.
     * Prior to Android 13 READ_EXTERNAL_STORAGE is checked.
     *
     * @param context the context to check the permissions
     * @return true if permissions are already granted, false otherwise
     */
    public static boolean isFullVideoImagePermissionGranted(@NonNull Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return isPermissionGranted(context, Manifest.permission.READ_MEDIA_VIDEO)
                && isPermissionGranted(context, Manifest.permission.READ_MEDIA_IMAGES);
        } else {
            return isPermissionGranted(context, Manifest.permission.READ_EXTERNAL_STORAGE);
        }
    }

    /**
     * Determine whether you have been partially granted the permission for access to videos and images.
     * Partial access was introduced in Android 14 (SDK 34)
     *
     * @param context the context to check the permissions
     * @return true if permission is already granted, false otherwise
     */
    public static boolean isPartialVideoImagePermissionGranted(@NonNull Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            return isPermissionGranted(context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED);
        } else {
            return false;
        }
    }

    /**
     * Request all required permissions to read external storage. For Android 13 and higher this
     * requests READ_MEDIA_IMAGES and READ_MEDIA_VIDEO. READ_MEDIA_AUDIO is not requested.
     *
     * @param activity    Activity context for onRequestPermissionsResult callback
     * @param requestCode request code for onRequestPermissionsResult callback
     * @return true if permissions are already granted, false otherwise
     */
    public static boolean requestReadStoragePermission(@NonNull Activity activity, Fragment fragment, int requestCode) {
        String[] permissions;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            permissions = new String[]{Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED};
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions = new String[]{Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO};
        } else {
            permissions = new String[]{Manifest.permission.READ_EXTERNAL_STORAGE};
        }

        if (checkIfNeedsPermissionRequest(activity, permissions)) {
            requestPermissions(activity, fragment, permissions, requestCode);
            return false;
        }
        return true;
    }

    /**
     * Request all possibly required permissions of Storage group. For Android 13 and higher this
     * requests READ_MEDIA_IMAGES and READ_MEDIA_VIDEO. READ_MEDIA_AUDIO is not requested.
     *
     * @param activity    Activity context for onRequestPermissionsResult callback
     * @param requestCode request code for onRequestPermissionsResult callback
     * @return true if permissions are already granted, false otherwise
     */
    public static boolean requestStoragePermissions(@NonNull Activity activity, Fragment fragment, int requestCode) {
        String[] permissions;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            permissions = new String[]{Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED};
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions = new String[]{Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO};
        } else {
            permissions = new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE};
        }

        if (checkIfNeedsPermissionRequest(activity, permissions)) {
            requestPermissions(activity, fragment, permissions, requestCode);
            return false;
        }
        return true;
    }

    /**
     * Request storage write permission
     *
     * @param activity    Activity context for onRequestPermissionsResult callback
     * @param requestCode request code for onRequestPermissionsResult callback
     * @return true if permissions are already granted, false otherwise
     */
    public static boolean requestWriteStoragePermissions(@NonNull Activity activity, Fragment fragment, int requestCode) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // scoped storage
            return true;
        }

        String[] permissions = new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE};

        if (checkIfNeedsPermissionRequest(activity, permissions)) {
            requestPermissions(activity, fragment, permissions, requestCode);
            return false;
        }
        return true;
    }

    /**
     * Request all possibly required permissions of Location group
     *
     * @param activity    Activity context for onRequestPermissionsResult callback
     * @param requestCode request code for onRequestPermissionsResult callback
     * @return true if permissions are already granted, false otherwise
     */
    public static boolean requestLocationPermissions(@NonNull Activity activity, Fragment fragment, int requestCode) {
        String[] permissions = new String[]{Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION};

        if (checkIfNeedsPermissionRequest(activity, permissions)) {
            requestPermissions(activity, fragment, permissions, requestCode);
            return false;
        }
        return true;
    }

    /**
     * Asynchronously request audio permissions.
     *
     * @param activity    Activity context for onRequestPermissionsResult callback
     * @param requestCode request code for onRequestPermissionsResult callback
     * @return true if permissions are already granted, false otherwise
     */
    public static boolean requestAudioPermissions(@NonNull Activity activity, Fragment fragment, int requestCode) {
        final String[] permissions = new String[]{Manifest.permission.RECORD_AUDIO};
        if (checkIfNeedsPermissionRequest(activity, permissions)) {
            requestPermissions(activity, fragment, permissions, requestCode);
            return false;
        }
        return true;
    }

    /**
     * Asynchronously request permission required for checking for connected bluetooth devices in Android S
     *
     * @param activity    Activity context for onRequestPermissionsResult callback
     * @param requestCode request code for onRequestPermissionsResult callback
     * @return true if permissions are already granted, false otherwise
     */
    @RequiresApi(api = Build.VERSION_CODES.S)
    public static boolean requestBluetoothConnectPermissions(@NonNull AppCompatActivity activity, Fragment fragment, int requestCode, boolean showHelpDialog) {
        final String[] permissions = new String[]{Manifest.permission.BLUETOOTH_CONNECT};
        if (checkIfNeedsPermissionRequest(activity, permissions)) {
            if (showHelpDialog) {
                SimpleStringAlertDialog.newInstance(R.string.voip_bluetooth, R.string.permission_bluetooth_connect_required)
                    .setOnDismissRunnable(() -> requestPermissions(activity, fragment, permissions, requestCode))
                    .show(activity.getSupportFragmentManager(), "");
            } else {
                requestPermissions(activity, fragment, permissions, requestCode);
            }
            return false;
        }
        return true;
    }

    /**
     * Request permission required for checking for connected bluetooth devices in Android S.
     *
     * @param context            the context
     * @param permissionLauncher the permission launcher that is launched if the permission is not
     *                           granted yet
     */
    @RequiresApi(api = Build.VERSION_CODES.S)
    public static void requestBluetoothConnectPermission(
        @NonNull Context context,
        @NonNull ActivityResultLauncher<String> permissionLauncher
    ) {
        final String permission = Manifest.permission.BLUETOOTH_CONNECT;
        if (checkIfNeedsPermissionRequest(context, new String[]{permission})) {
            permissionLauncher.launch(permission);
        }
    }

    /**
     * Request all possibly required permissions of Phone group
     *
     * @param activity    Activity context for onRequestPermissionsResult callback
     * @param fragment    Fragment context for onRequestPermissionsResult callback
     * @param requestCode request code for onRequestPermissionsResult callback
     * @return true if permissions are already granted, false otherwise
     */
    public static boolean requestPhonePermissions(@NonNull Activity activity, Fragment fragment, int requestCode) {
        String[] permissions;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            permissions = new String[]{Manifest.permission.READ_PHONE_STATE, Manifest.permission.CALL_PHONE, Manifest.permission.ANSWER_PHONE_CALLS};
        } else {
            permissions = new String[]{Manifest.permission.READ_PHONE_STATE, Manifest.permission.CALL_PHONE};
        }
        if (checkIfNeedsPermissionRequest(activity, permissions)) {
            requestPermissions(activity, fragment, permissions, requestCode);
            return false;
        }
        return true;
    }

    /**
     * Request read phone state permission.
     *
     * @param activity                  activity context
     * @param requestPermissionLauncher the request permission launcher
     */
    public static void requestReadPhonePermission(@NonNull Activity activity, @Nullable ActivityResultLauncher<String> requestPermissionLauncher) {
        String permission = Manifest.permission.READ_PHONE_STATE;
        if (checkIfNeedsPermissionRequest(activity, new String[]{permission}) && requestPermissionLauncher != null) {
            requestPermissionLauncher.launch(permission);
        }
    }

    /**
     * Request the POST_NOTIFICATION permission. For Android versions below 13, this is not needed.
     *
     * @param context                   the context is needed to check whether the permission is already granted
     * @param requestPermissionLauncher the request permission launcher will be used to get the result
     * @param preferenceService         the preference service is used to update the last notification permission request timestamp if needed
     * @return {@code true} if the permission is already granted, {@code false} otherwise
     */
    public static boolean requestNotificationPermission(
        @NonNull Context context,
        @NonNull ActivityResultLauncher<String> requestPermissionLauncher,
        @Nullable PreferenceService preferenceService
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return true;
        }
        String permission = Manifest.permission.POST_NOTIFICATIONS;
        if (checkIfNeedsPermissionRequest(context, new String[]{permission})) {
            if (preferenceService != null) {
                preferenceService.setLastNotificationPermissionRequestTimestamp(System.currentTimeMillis());
            }
            requestPermissionLauncher.launch(permission);
            return false;
        }
        return true;
    }

    /**
     * Asynchronously request camera permissions.
     *
     * @param activity    Activity context for onRequestPermissionsResult callback
     * @param fragment    Fragment context for onRequestPermissionsResult callback
     * @param requestCode request code for onRequestPermissionsResult callback
     * @return true if permissions are already granted, false otherwise
     */
    public static boolean requestCameraPermissions(@NonNull Activity activity, Fragment fragment, int requestCode) {
        String[] permissions = new String[]{Manifest.permission.CAMERA};
        if (checkIfNeedsPermissionRequest(activity, permissions)) {
            requestPermissions(activity, fragment, permissions, requestCode);
            return false;
        }
        return true;
    }

    private static void requestPermissions(Activity activity, Fragment fragment, String[] permissions, int requestCode) {
        if (fragment != null) {
            fragment.requestPermissions(permissions, requestCode);
        } else {
            ActivityCompat.requestPermissions(activity, permissions, requestCode);
        }
    }

    private static boolean checkIfNeedsPermissionRequest(@NonNull Context context, String[] permissions) {
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                return true;
            }
        }
        return false;
    }

    /**
     * Show a snackbar explaining the reason why the user should enable a certain permission.
     *
     * @param context        the context; if null, nothing is shown
     * @param parentLayout   the parent layout where the snackbar is placed; if null, a toast is shown
     * @param stringResource the string resource of the message that will be shown
     */
    public static void showPermissionRationale(@Nullable Context context, @Nullable View parentLayout, @StringRes int stringResource) {
        if (context != null) {
            showPermissionRationale(context, parentLayout, context.getString(stringResource));
        }
    }

    /**
     * Show a snackbar explaining the reason why the user should enable a certain permission.
     *
     * @param context      the context; if null, nothing is shown
     * @param parentLayout the parent layout where the snackbar is placed; if null, a toast is shown
     * @param message      the message that is shown
     */
    public static void showPermissionRationale(@Nullable Context context, @Nullable View parentLayout, @NonNull String message) {
        showPermissionRationale(context, parentLayout, message, null);
    }

    /**
     * Show a snackbar explaining the reason why the user should enable a certain permission.
     *
     * @param context        the context; if null, nothing is shown
     * @param parentLayout   the parent layout where the snackbar is placed; if null, a toast is shown
     * @param stringResource the string resource of the message that will be shown
     * @param callback       the callback for the snackbar
     */
    public static void showPermissionRationale(
        @Nullable Context context,
        @Nullable View parentLayout,
        @StringRes int stringResource,
        @Nullable BaseTransientBottomBar.BaseCallback<Snackbar> callback
    ) {
        if (context != null) {
            showPermissionRationale(context, parentLayout, context.getString(stringResource), callback);
        }
    }

    /**
     * Show a snackbar explaining the reason why the user should enable a certain permission.
     *
     * @param context      the context; if null, nothing is shown
     * @param parentLayout the parent layout where the snackbar is placed; if null, a toast is shown
     * @param message      the message that is shown
     * @param callback     the callback for the snackbar
     */
    public static void showPermissionRationale(
        @Nullable Context context,
        @Nullable View parentLayout,
        @NonNull String message,
        @Nullable BaseTransientBottomBar.BaseCallback<Snackbar> callback
    ) {
        if (context == null) {
            return;
        }

        if (parentLayout == null) {
            Toast.makeText(context, message, Toast.LENGTH_LONG).show();
        } else {
            Snackbar snackbar = SnackbarUtil.make(parentLayout, message, BaseTransientBottomBar.LENGTH_LONG, 5);
            snackbar.setAction(R.string.menu_settings, v -> {
                Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                intent.setData(Uri.parse("package:" + BuildConfig.APPLICATION_ID));
                context.startActivity(intent);
            });
            if (callback != null) {
                snackbar.addCallback(callback);
            }
            snackbar.show();
        }
    }

    public static int getPreferredThumbnailWidth(Context context, boolean reset) {
        if (preferredThumbnailWidth == -1 || reset) {
            if (context != null) {
                int width = context.getResources().getDisplayMetrics().widthPixels;
                int height = context.getResources().getDisplayMetrics().heightPixels;

                if (ConfigUtils.isTabletLayout()) {
                    width -= context.getResources().getDimensionPixelSize(R.dimen.message_fragment_width);
                }

                // width of thumbnail should be 60% of smallest display width
                preferredThumbnailWidth = (int) ((float) width < height ? width * 0.6f : height * 0.6f);
            }
        }
        return preferredThumbnailWidth;
    }

    public static int getPreferredAudioMessageWidth(Context context, boolean reset) {
        if (preferredAudioMessageWidth == -1 || reset) {
            if (context != null) {
                int width = context.getResources().getDisplayMetrics().widthPixels;
                int height = context.getResources().getDisplayMetrics().heightPixels;

                if (ConfigUtils.isTabletLayout()) {
                    width -= context.getResources().getDimensionPixelSize(R.dimen.message_fragment_width);
                }

                // width of audio message should be 80% of smallest display width
                preferredAudioMessageWidth = (int) ((float) width < height ? width * 0.75f : height * 0.75f);
            }
        }
        return preferredAudioMessageWidth;
    }

    public static int getPreferredImageDimensions(@PreferenceService.ImageScale int imageScale) {
        int maxSize = 0;
        switch (imageScale) {
            case PreferenceService.ImageScale_SMALL:
                maxSize = 640;
                break;
            case PreferenceService.ImageScale_MEDIUM:
                maxSize = 1024;
                break;
            case PreferenceService.ImageScale_LARGE:
                maxSize = 1600;
                break;
            case PreferenceService.ImageScale_XLARGE:
                maxSize = 2592;
                break;
            case PreferenceService.ImageScale_ORIGINAL:
                maxSize = 65535;
                break;
        }
        return maxSize;
    }

    /**
     * Check if a particular app with packageName is installed on the system
     *
     * @return true if app is installed, false otherwise or an error occured
     */
    public static boolean isAppInstalled(String packageName) {
        try {
            ThreemaApplication.getAppContext().getPackageManager().getPackageInfo(packageName, 0);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Configure menu to display dividers. Call this in onCreateOptionsMenu()
     */
    @SuppressLint("RestrictedApi")
    public static void addIconsToOverflowMenu(@NonNull Menu menu) {
        MenuCompat.setGroupDividerEnabled(menu, true);

        try {
            // restricted API
            if (menu instanceof MenuBuilder) {
                MenuBuilder menuBuilder = (MenuBuilder) menu;
                menuBuilder.setOptionalIconsVisible(true);
            }
        } catch (Exception ignored) {
        }
    }

    /**
     * Return whether or not to use Threema Push, based on the build flavor and the preferences.
     */
    public static boolean useThreemaPush(@NonNull PreferenceService preferenceService) {
        return BuildFlavor.getCurrent().getForceThreemaPush() || preferenceService.useThreemaPush();
    }

    /**
     * Return whether or not to use Threema Push, based on the build flavor and the preferences.
     */
    public static boolean useThreemaPush(@NonNull SharedPreferences rawSharedPreferences, @NonNull Context context) {
        return BuildFlavor.getCurrent().getForceThreemaPush()
            || rawSharedPreferences.getBoolean(context.getString(R.string.preferences__threema_push_switch), false);
    }

    /**
     * Apply operations to content provider in small batches preventing TransactionTooLargeException
     *
     * @param authority                 Authority
     * @param contentProviderOperations Operations to apply in smaller batches
     */
    public static void applyToContentResolverInBatches(@NonNull String authority, ArrayList<ContentProviderOperation> contentProviderOperations) throws OperationApplicationException, RemoteException {
        ContentResolver contentResolver = ThreemaApplication.getAppContext().getContentResolver();

        for (int i = 0; i < contentProviderOperations.size(); i += CONTENT_PROVIDER_BATCH_SIZE) {
            List<ContentProviderOperation> contentProviderOperationsBatch = contentProviderOperations.subList(i, Math.min(contentProviderOperations.size(), i + CONTENT_PROVIDER_BATCH_SIZE));
            contentResolver.applyBatch(authority, new ArrayList<>(contentProviderOperationsBatch));
        }
    }

    public static boolean isGroupAckEnabled() {
        return true;
    }

    public static void clearAppData(Context context) {
        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (manager != null) {
            manager.clearApplicationUserData();
        }
    }

    /**
     * @param context    context required to know which string resource it is.
     * @param id         of the object
     * @param quantity   quantity of given values
     * @param formatArgs how the string should be formatted
     * @return returns the QuantityString or missing translation.
     */
    public static @NonNull
    String getSafeQuantityString(@NonNull Context context, int id, int quantity, @NonNull Object... formatArgs) {
        String result = "missing translation";
        try {
            result = context.getResources().getQuantityString(id, quantity, formatArgs);
        } catch (Exception e) {
            logger.error("Quantity String not found.", e);
        }
        return result;
    }

    /**
     * Adjust padding of SearchView so that the search icon is no longer cut off
     *
     * @param searchView The instance of a appcompat SearchView
     */
    public static void adjustSearchViewPadding(@NonNull SearchView searchView) {
        LinearLayout searchFrame = searchView.findViewById(R.id.search_edit_frame);
        if (searchFrame != null) {
            LinearLayout.LayoutParams layoutParams = (LinearLayout.LayoutParams) searchFrame.getLayoutParams();
            if (layoutParams != null) {
                layoutParams.setMargins(0, layoutParams.topMargin, layoutParams.rightMargin, layoutParams.bottomMargin);
                searchFrame.setLayoutParams(layoutParams);
            }
            TextView searchSrcTextView = searchView.findViewById(R.id.search_src_text);
            if (searchSrcTextView != null) {
                searchSrcTextView.setPadding(0, 0, 0, 0);
            }
            searchView.setPadding(0, 0, 0, 0);
        }
    }

    /**
     * Adjust the left margin of a search bar text view so that it matches the search action view's text view
     *
     * @param context   A context
     * @param searchBar The search bar
     */
    public static void adjustSearchBarTextViewMargin(@NonNull Context context, @NonNull SearchBar searchBar) {
        TextView searchBarTextView = searchBar.findViewById(R.id.open_search_bar_text_view);
        if (searchBarTextView != null) {
            try {
                SearchBar.LayoutParams layoutParams = (SearchBar.LayoutParams) searchBarTextView.getLayoutParams();
                layoutParams.setMargins(context.getResources().getDimensionPixelSize(R.dimen.search_bar_text_view_margin), layoutParams.topMargin, layoutParams.rightMargin, layoutParams.bottomMargin);
                searchBarTextView.setLayoutParams(layoutParams);
            } catch (Exception e) {
                logger.debug("Unable to get layout params for search bar");
            }
        }
    }

    public static void adjustExoPlayerControllerMargins(@NonNull Context context, @NonNull PlayerView audioView) {
        final View controllerView = audioView.findViewById(R.id.exo_bottom_bar);
        ViewCompat.setOnApplyWindowInsetsListener(controllerView, (v, insets) -> {
            ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) v.getLayoutParams();
            params.leftMargin = insets.getSystemWindowInsetLeft();
            params.rightMargin = insets.getSystemWindowInsetRight();
            params.bottomMargin = insets.getSystemWindowInsetBottom();
            return insets;
        });

        final View exoTimeBar = audioView.findViewById(R.id.exo_progress);
        ViewCompat.setOnApplyWindowInsetsListener(exoTimeBar, (v, insets) -> {
            ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) v.getLayoutParams();
            params.leftMargin = insets.getSystemWindowInsetLeft();
            params.rightMargin = insets.getSystemWindowInsetRight();
            params.bottomMargin = insets.getSystemWindowInsetBottom() + context.getResources().getDimensionPixelSize(R.dimen.exo_styled_progress_margin_bottom);
            return insets;
        });
    }

    @UiThread
    public static void getMapLibreInstance() {
        if (mapLibreWeakReference == null || mapLibreWeakReference.get() == null) {
            mapLibreWeakReference = new WeakReference<>(MapLibre.getInstance(ThreemaApplication.getAppContext()));
            logger.info("MapLibre enabled");
        }
    }

    /**
     * Query whether the user has enabled background restrictions for this app.
     * The user may chose to do this, if they see that an app is consuming an unreasonable amount of battery while in the background.
     * (Android directs the user to do this - many users are not aware of the consequences)
     * If true, any work that the app tries to do will be aggressively restricted while it is in the background. At a minimum, jobs and alarms will not execute and foreground services cannot be started unless an app activity is in the foreground.
     * Note that these restrictions stay in effect even when the device is charging.
     *
     * @param context A context
     * @return true if background restrictions are enabled
     */
    public static boolean isBackgroundRestricted(@NonNull Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            return activityManager.isBackgroundRestricted();
        } else {
            return false;
        }
    }

    /**
     * Check whether the user has restricted the app from network use when in the background
     *
     * @param context           A context
     * @return true if the app cannot access the network when in background
     */
    public static boolean isBackgroundDataRestricted(@NonNull Context context) {
        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        return connectivityManager.getRestrictBackgroundStatus() == ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED;
    }

    /**
     * Check whether the app can post full screen notifications (e.g. used for calls)
     *
     * @param context A context
     * @return true if the app is forbidden to use fullscreen notifications
     */
    public static boolean isFullScreenNotificationsDisabled(@NonNull Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            NotificationManagerCompat notificationManagerCompat = NotificationManagerCompat.from(context);
            return !notificationManagerCompat.canUseFullScreenIntent();
        }
        return false;
    }

    /**
     * Check whether the app can post any notifications
     *
     * @param context A context
     * @return true if the app if notifications are completely disabled for this app
     */
    public static boolean isNotificationsDisabled(@NonNull Context context) {
        return !NotificationManagerCompat.from(context).areNotificationsEnabled();
    }

    /**
     * Check whether the provided mime type hints at an image format that can be displayed with Glide on this Android version
     *
     * @param mimeType Mime type to check
     * @return true if conditions are met
     */
    public static boolean isDisplayableAnimatedImageFormat(@Nullable String mimeType) {
        return (MimeUtil.isWebPFile(mimeType) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) || MimeUtil.isGifFile(mimeType);
    }

    /**
     * Get position of a PopupWindow that has Gravity.LEFT|Gravity.BOTTOM set and is supposed to be located above the supplied anchor view
     *
     * @param activity   Activity that hosts the PopupWindow
     * @param anchorView Anchor view
     * @return int array of x/y coordinates to be supplied to showAtLocation() as well as the viewable height
     */
    public static int[] getPopupWindowPositionAboveAnchor(@NonNull Activity activity, @NonNull View anchorView) {
        int[] windowLocation = {0, 0};
        anchorView.getLocationInWindow(windowLocation);

        WindowMetrics windowMetrics = WindowMetricsCalculator.getOrCreate().computeCurrentWindowMetrics(activity);
        Rect bounds = windowMetrics.getBounds();

        int x = windowLocation[0] + anchorView.getPaddingLeft();
        int y = bounds.height() - windowLocation[1];

        int viewableHeight = windowLocation[1] - (ConfigUtils.getStatusBarHeight(activity) + ConfigUtils.getActionBarSize(activity));

        return new int[]{x, y, viewableHeight};
    }

    public static boolean isInstalledFromStore(@NonNull Context context) {
        String installerPackageName = getInstallerPackageName(context);
        return "com.android.vending".equals(installerPackageName) || "com.huawei.appmarket".equals(installerPackageName);
    }

    public static boolean isInstalledFromPlayStore(@NonNull Context context) {
        String installerPackageName = getInstallerPackageName(context);
        return "com.android.vending".equals(installerPackageName);
    }

    public static @Nullable String getInstallerPackageName(@NonNull Context context) {
        try {
            String installerPackageName;
            PackageManager packageManager = context.getPackageManager();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                installerPackageName = packageManager.getInstallSourceInfo(context.getPackageName()).getInstallingPackageName();
            } else {
                installerPackageName = packageManager.getInstallerPackageName(context.getPackageName());
            }

            return installerPackageName;
        } catch (Exception e) {
            logger.error("Could not determine package source", e);
            return null;
        }
    }
}

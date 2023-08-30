/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2014-2023 Threema GmbH
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

import static android.content.res.Configuration.UI_MODE_NIGHT_YES;
import static android.os.Build.VERSION_CODES.O_MR1;
import static android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
import static android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
import static android.view.WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS;
import static android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
import static androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
import static androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO;
import static androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES;
import static ch.threema.app.camera.CameraUtil.isInternalCameraSupported;
import static ch.threema.app.services.NotificationService.NOTIFICATION_CHANNEL_ALERT;
import static ch.threema.app.services.NotificationServiceImpl.APP_RESTART_NOTIFICATION_ID;
import static ch.threema.app.utils.IntentDataUtil.PENDING_INTENT_FLAG_MUTABLE;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.OperationApplicationException;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
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
import android.view.SubMenu;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.AttrRes;
import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.StringDef;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.view.menu.MenuBuilder;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.MenuCompat;
import androidx.core.view.ViewCompat;
import androidx.fragment.app.Fragment;
import androidx.media3.ui.PlayerView;
import androidx.preference.PreferenceManager;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import com.datatheorem.android.trustkit.TrustKit;
import com.google.android.material.search.SearchBar;
import com.google.android.material.snackbar.BaseTransientBottomBar;
import com.google.android.material.snackbar.Snackbar;

import org.slf4j.Logger;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocketFactory;

import ch.threema.app.BuildConfig;
import ch.threema.app.BuildFlavor;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.activities.HomeActivity;
import ch.threema.app.dialogs.SimpleStringAlertDialog;
import ch.threema.app.exceptions.FileSystemNotPresentException;
import ch.threema.app.managers.ServiceManager;
import ch.threema.app.notifications.NotificationBuilderWrapper;
import ch.threema.app.services.AppRestrictionService;
import ch.threema.app.services.LockAppService;
import ch.threema.app.services.PreferenceService;
import ch.threema.app.services.license.LicenseService;
import ch.threema.app.threemasafe.ThreemaSafeConfigureActivity;
import ch.threema.app.workers.RestartWorker;
import ch.threema.base.utils.LoggingUtil;

public class ConfigUtils {
	private static final Logger logger = LoggingUtil.getThreemaLogger("ConfigUtils");

	private static final int CONTENT_PROVIDER_BATCH_SIZE = 50;
	private static final String WORKER_RESTART_AFTER_RESTORE = "restartAfterRestore";

	/* app theme settings in shared preferences */
	@StringDef({THEME_LIGHT, THEME_DARK, THEME_FOLLOW_SYSTEM})
	public @interface AppThemeSetting {}
	public static final String THEME_LIGHT = "0";
	public static final String THEME_DARK = "1";
	public static final String THEME_FOLLOW_SYSTEM = "2";

	public static final int EMOJI_DEFAULT = 0;
	public static final int EMOJI_ANDROID = 1;

	@Deprecated private static String localeOverride = null;
	private static Integer miuiVersion = null;
	private static int emojiStyle = 0;
	private static Boolean isTablet = null, isBiggerSingleEmojis = null, hasMapLibreSupport = null;
	private static int preferredThumbnailWidth = -1, preferredAudioMessageWidth = -1, currentDayNightMode;

	private static final float[] NEGATIVE_MATRIX = {
		-1.0f,     0,     0,    0, 255, // red
		0, -1.0f,     0,    0, 255, // green
		0,     0, -1.0f,    0, 255, // blue
		0,     0,     0, 1.0f,   0  // alpha
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


	public static boolean hasBrokenDeleteNotificationChannelGroup() {
		return isHuaweiDevice() && Build.VERSION.SDK_INT == Build.VERSION_CODES.Q;
	}

	public static boolean isOnePlusDevice() {
		return (Build.MANUFACTURER.equalsIgnoreCase("OnePlus"));
	}

	public static boolean isSamsungDevice() {
		return (Build.MANUFACTURER.equalsIgnoreCase("Samsung"));
	}

	public static boolean isSonyDevice() {
		return (Build.MANUFACTURER.equalsIgnoreCase("Sony"));
	}

	public static boolean isNokiaDevice() {
		return Build.MANUFACTURER.equalsIgnoreCase("HMD Global");
	}

	public static boolean canDoGroupedNotifications() {
		return Build.VERSION.SDK_INT >= Build.VERSION_CODES.N;
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

		if (serviceManager != null && serviceManager.getPreferenceService() != null) {
			return serviceManager.getPreferenceService().isVoipEnabled() &&
				!AppRestrictionUtil.isCallsDisabled();
		}
		return true;
	}

	public static boolean isVideoCallsEnabled() {
		ServiceManager serviceManager = ThreemaApplication.getServiceManager();

		if (serviceManager != null && serviceManager.getPreferenceService() != null) {
			return (BuildConfig.VIDEO_CALLS_ENABLED &&
				serviceManager.getPreferenceService().isVideoCallsEnabled() &&
				!AppRestrictionUtil.isVideoCallsDisabled());
		}
		return BuildConfig.VIDEO_CALLS_ENABLED;
	}

	public static boolean isGroupCallsEnabled() {
		ServiceManager serviceManager = ThreemaApplication.getServiceManager();

		if (serviceManager != null && serviceManager.getPreferenceService() != null) {
			return (BuildConfig.GROUP_CALLS_ENABLED &&
				serviceManager.getPreferenceService().isGroupCallsEnabled() &&
				!AppRestrictionUtil.isGroupCallsDisabled() &&
				!AppRestrictionUtil.isCallsDisabled());
		}
		return BuildConfig.GROUP_CALLS_ENABLED;
	}

	public static boolean isWorkDirectoryEnabled() {
		ServiceManager serviceManager = ThreemaApplication.getServiceManager();

		if (serviceManager != null && serviceManager.getPreferenceService() != null) {
			return (serviceManager.getPreferenceService().getWorkDirectoryEnabled() &&
				!AppRestrictionUtil.isWorkDirectoryDisabled());
		}
		return false;
	}

	/**
	 * Get a Socket Factory for certificate pinning and forced TLS version upgrade.
	 */
	public static @NonNull SSLSocketFactory getSSLSocketFactory(String host) {
		return new TLSUpgradeSocketFactoryWrapper(
			ConfigUtils.isOnPremBuild() ?
				HttpsURLConnection.getDefaultSSLSocketFactory() :
				TrustKit.getInstance().getSSLSocketFactory(host));
	}

	public static boolean hasNoMapLibreSupport() {
		/* Some broken Samsung devices crash on MapLibre initialization due to a compiler bug, see https://issuetracker.google.com/issues/37013676 */
		/* Device that do not support OCSP stapling cannot use our maps and POI servers */
		if (hasMapLibreSupport == null) {
			hasMapLibreSupport =
				Build.VERSION.SDK_INT <= Build.VERSION_CODES.LOLLIPOP_MR1 && Build.MANUFACTURER.equalsIgnoreCase("marshall");
		}
		return hasMapLibreSupport;
	}

	public static boolean isXiaomiDevice() {
		return Build.MANUFACTURER.equalsIgnoreCase("Xiaomi");
	}

	/**
	 * return current MIUI version level or 0 if no Xiaomi device or MIUI version is not recognized or not relevant
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
					} else if  (version.startsWith("V14")) {
						miuiVersion = 14;
					}
				}
			} catch (Exception ignored) { }
		}
		return miuiVersion;
	}

	/**
	 * Set the setting for the app wide theme and optionally save to shared prefs and apply setting to system
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
	public static @AppThemeSetting String getAppThemePrefsSettings() {
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ThreemaApplication.getAppContext());

		// set default day/night setting depending on app type
		@AppThemeSetting String appThemeSetting = null;
		if (prefs != null) {
			appThemeSetting = prefs.getString(ThreemaApplication.getAppContext().getString(R.string.preferences__theme), null);
		}
		if (TestUtil.empty(appThemeSetting)) {
			// fix default setting according to app flavor
			appThemeSetting = BuildConfig.DEFAULT_APP_THEME;
			if (prefs != null) {
				prefs.edit().putString(ThreemaApplication.getAppContext().getString(R.string.preferences__theme), BuildConfig.DEFAULT_APP_THEME).apply();
			}
		}
		return appThemeSetting;
	}

	public static @AppCompatDelegate.NightMode int getAppThemePrefs() {
		return getDayNightModeFromAppThemeSetting(getAppThemePrefsSettings());
	}

	public static boolean isTheDarkSide(@NonNull Context context) {
		return getCurrentDayNightMode(context) == MODE_NIGHT_YES;
	}

	/**
	 * Get current day night of the system
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
	 * @param dayNightMode must be either MODE_NIGHT_YES or MODE_NIGHT_NO
	 */
	public static void setCurrentDayNightMode(@AppCompatDelegate.NightMode int dayNightMode) {
		currentDayNightMode = dayNightMode;
	}

	public static @ColorInt int getColorFromAttribute(Context context, @AttrRes int attr) {
		TypedArray typedArray = context.getTheme().obtainStyledAttributes(new int[] { attr });
		@ColorInt int color = typedArray.getColor(0, -1);

		typedArray.recycle();

		return color;
	}

	/**
	 * Tint the icons of a complete menu to the provided color. This is necessary when using MenuInflater which does not support the iconTint attribute
	 * (SupportMenuInflater / startSupportActionMode respects iconTint)
	 * @param menu The menu to tint
	 * @param color The ColorInt to tint the icons
	 */
	public static void tintMenu(Menu menu, @ColorInt int color) {
		for (int i = 0, size = menu.size(); i < size; i++) {
			final MenuItem menuItem = menu.getItem(i);
			tintMenuItem(menuItem, color);
			if (menuItem.hasSubMenu()) {
				final SubMenu subMenu = menuItem.getSubMenu();
				for (int j = 0; j < subMenu.size(); j++) {
					tintMenuItem(subMenu.getItem(j), color);
				}
			}
		}
	}

	public static void tintMenuItem(@Nullable final MenuItem menuItem, @ColorInt int color) {
		if (menuItem != null) {
			final Drawable drawable = menuItem.getIcon();
			if (drawable != null) {
				drawable.mutate();
				drawable.setColorFilter(color, PorterDuff.Mode.SRC_IN);
			}
		}
	}

	public static void tintMenuItem(@NonNull Context context, @Nullable final MenuItem menuItem, @AttrRes int colorAttr) {
		tintMenuItem(menuItem, getColorFromAttribute(context, colorAttr));
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
		return emojiStyle == EMOJI_DEFAULT;
	}

	/**
	 * Get user-facing application version string including alpha/beta version suffix
	 *
	 * @return application version string
	 */
	public static String getAppVersion(@NonNull Context context) {
		try {
			PackageInfo packageInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
			if (packageInfo != null) {
				return packageInfo.versionName;
			}
		} catch (PackageManager.NameNotFoundException e) {
			logger.error("Exception", e);
		}
		return "";
	}

	/**
	 * Get user-facing application version represented as a float value stripping any non-numeric characters such as suffixes for build type (e.g. 4.0f)
	 * @param context
	 * @return version number
	 */
	public static float getAppVersionFloat(@NonNull Context context) {
		try {
			String versionString = ConfigUtils.getAppVersion(context).replaceAll("[^\\d.]", "");
			return Float.parseFloat(versionString);
		} catch (NumberFormatException e) {
			logger.error("Exception", e);
		}
		return 1.0f;
	}

	/**
	 * Get build number of this app build
	 * @param context
	 * @return build number
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
	 *
	 * @param context The Android context.
	 */
	public static @NonNull String getDeviceInfo(Context context, boolean includeAppVersion) {
		final StringBuilder info = new StringBuilder();
		if (includeAppVersion) {
			info.append(getAppVersion(context)).append("/");
		}
		info.append(Build.MANUFACTURER).append(";")
			.append(Build.MODEL).append("/")
			.append(Build.VERSION.RELEASE).append("/")
			.append(BuildFlavor.getName());
		return info.toString();
	}

	/**
	 * Return information about the device including the manufacturer and the model.
	 * The version is NOT included.
	 * If mdm parameters are active on this device they are also appended according to ANDR-2213.
	 *
	 * @return The device info meant to be sent with support requests
	 */
	public static @NonNull String getSupportDeviceInfo(Context context) {
		final StringBuilder info = new StringBuilder(getDeviceInfo(context, false));
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
		String lang = LocaleUtil.getAppLanguage().startsWith("de") ? "de" : "en";
		String version = ConfigUtils.getAppVersion(context);
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

	public static void recreateActivity(Activity activity, Class<?> cls,  Bundle bundle) {
		activity.finish();

		final Intent intent = new Intent(activity, cls);
		if (bundle != null) {
			intent.putExtras(bundle);
		}
		intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
		activity.startActivity(intent);
	}

	public static void scheduleAppRestart(Context context, int delayMs, String eventTriggerTitle) {
		// Android Q does not allow restart in the background
		// https://developer.android.com/preview/privacy/background-activity-starts
		Intent restartIntent = context.getPackageManager()
			.getLaunchIntentForPackage(context.getPackageName());
		restartIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
		PendingIntent pendingIntent = PendingIntent.getActivity(
			context, 0,
			restartIntent, PendingIntent.FLAG_CANCEL_CURRENT | PENDING_INTENT_FLAG_MUTABLE);

		if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
			// on older android version we restart directly after delayMs
			AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
			manager.set(AlarmManager.RTC, System.currentTimeMillis() + delayMs, pendingIntent);
		} else {
			if (eventTriggerTitle == null) {
				// use WorkManager to restart the app in the background
				final WorkManager workManager = WorkManager.getInstance(ThreemaApplication.getAppContext());
				final OneTimeWorkRequest workRequest = RestartWorker.Companion.buildOneTimeWorkRequest(delayMs);
				workManager.enqueueUniqueWork(WORKER_RESTART_AFTER_RESTORE, ExistingWorkPolicy.REPLACE, workRequest);
			} else {
				// use a notification to trigger restart (app in background can no longer start activities in Android 12+)
				String text = context.getString(R.string.tap_to_start, context.getString(R.string.app_name));

				NotificationCompat.Builder builder =
					new NotificationBuilderWrapper(context, NOTIFICATION_CHANNEL_ALERT, null)
						.setSmallIcon(R.drawable.ic_notification_small)
						.setContentTitle(eventTriggerTitle)
						.setContentText(eventTriggerTitle)
						.setDefaults(Notification.DEFAULT_LIGHTS | Notification.DEFAULT_VIBRATE)
						.setColor(context.getResources().getColor(R.color.material_green))
						.setPriority(NotificationCompat.PRIORITY_MAX)
						.setStyle(new NotificationCompat.BigTextStyle().bigText(text))
						.setContentIntent(pendingIntent)
						.setAutoCancel(false);

				NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
				if (notificationManager != null) {
					notificationManager.notify(APP_RESTART_NOTIFICATION_ID, builder.build());
				}
			}
		}
	}

	public static boolean checkAvailableMemory(float required) {
		// Get max available VM memory, exceeding this amount will throw an
		// OutOfMemory exception
		return (Runtime.getRuntime().maxMemory() > required);
	}

	public static boolean isWorkBuild() {
		return BuildFlavor.getLicenseType().equals(BuildFlavor.LicenseType.GOOGLE_WORK)
			|| BuildFlavor.getLicenseType().equals(BuildFlavor.LicenseType.HMS_WORK)
			|| isOnPremBuild();
	}

	public static boolean isOnPremBuild() {
		return BuildFlavor.getLicenseType().equals(BuildFlavor.LicenseType.ONPREM);
	}

	public static boolean isDemoOPServer(@NonNull PreferenceService preferenceService) {
		return preferenceService.getOnPremServer() != null && preferenceService.getOnPremServer().toLowerCase().contains(".3ma.ch/");
	}

	public static boolean isTestBuild() {
		return BuildFlavor.getName().contains("DEBUG") ||
			BuildFlavor.getName().equals("Red") || BuildFlavor.getName().equals("DEV") ||
			BuildFlavor.getName().equals("Sandbox");
	}

	public static boolean supportsGroupLinks() {
		return false;
	}

	public static boolean supportGroupDescription() {
		return false;
	}


	/**
	 * Returns true if this is a work build and app is under control of a device policy controller (DPC) or Threema MDM
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

			try {
				LicenseService<?> licenseService = serviceManager.getLicenseService();
				if (licenseService != null) {
					return isSerialLicensed() && licenseService.hasCredentials() && licenseService.isLicensed();
				}
			} catch (FileSystemNotPresentException e) {
				logger.error("Exception", e);
			}
		}
		return false;
	}

	public static boolean isSerialLicensed() {
		return BuildFlavor.getLicenseType().equals(BuildFlavor.LicenseType.GOOGLE_WORK)
			|| BuildFlavor.getLicenseType().equals(BuildFlavor.LicenseType.HMS_WORK)
			|| BuildFlavor.getLicenseType().equals(BuildFlavor.LicenseType.SERIAL)
			|| BuildFlavor.getLicenseType().equals(BuildFlavor.LicenseType.ONPREM);
	}

	public static boolean hasInvalidCredentials() {
		return (ConfigUtils.isOnPremBuild() || ConfigUtils.isWorkBuild()) && ConfigUtils.isSerialLicensed() && !ConfigUtils.isSerialLicenseValid();
	}

	/**
	 * Returns true if privacy settings imply that screenshots and app switcher thumbnails should be disabled
	 * @param preferenceService
	 * @param lockAppService
	 * @return true if disabled, false otherwise or in case of failure
	 */
	public static boolean getScreenshotsDisabled(@Nullable PreferenceService preferenceService, @Nullable LockAppService lockAppService) {
		return preferenceService != null && lockAppService != null && lockAppService.isLockingEnabled();
	}

	public static void setScreenshotsAllowed(@NonNull Activity activity, @Nullable PreferenceService preferenceService, @Nullable LockAppService lockAppService) {
		// call this before setContentView
		if (getScreenshotsDisabled(preferenceService, lockAppService) ||
			(preferenceService != null && preferenceService.isDisableScreenshots()) ||
			activity instanceof ThreemaSafeConfigureActivity) {
			activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
		} else {
			activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
		}
	}

	@Deprecated
	public static @ColorInt int getAccentColor(Context context) {
		return getColorFromAttribute(context, R.attr.colorPrimary);
	}

	@Deprecated
	public static boolean useContentUris() {
		return true;
	}

	public static boolean hasProtection(PreferenceService preferenceService) {
		return !PreferenceService.LockingMech_NONE.equals(preferenceService.getLockMechanism());
	}

	public static void setLocaleOverride(@Nullable Context context, @Nullable PreferenceService preferenceService) {
		if (context == null) {
			return;
		}

		if (preferenceService == null) {
			return;
		}

		if (localeOverride == null) {
			String localeString = preferenceService.getLocaleOverride();
			localeOverride = localeString != null ? localeString : "";
		}

		try {
			Resources res = context.getResources();

			String systemLanguage = Resources.getSystem().getConfiguration().locale.getLanguage();
			String confLanguage = res.getConfiguration().locale.getLanguage();

			if (localeOverride.isEmpty()) {
				if (systemLanguage.equals(confLanguage)) {
					return;
				} else {
					confLanguage = systemLanguage;
				}
			} else {
				confLanguage = localeOverride;
			}

			DisplayMetrics dm = res.getDisplayMetrics();
			android.content.res.Configuration conf = res.getConfiguration();
			switch (confLanguage) {
				case "pt":
					conf.locale = new Locale(confLanguage, "BR");
					break;
				case "zh-rCN":
					conf.locale = new Locale("zh", "CN");
					break;
				case "zh-rTW":
					conf.locale = new Locale("zh", "TW");
					break;
				case "be-rBY":
					conf.locale = new Locale("be", "BY");
					break;
				default:
					conf.locale = new Locale(confLanguage);
					break;
			}
			res.updateConfiguration(conf, dm);
			Locale.setDefault(conf.locale);
		} catch (Exception e) {
			//
		}
	}

	public static void updateLocaleOverride(Object newValue) {
		if (newValue != null) {
			String newLocale = newValue.toString();
			if (!TestUtil.empty(newLocale)) {
				localeOverride = newLocale;
				return;
			}
		}
		localeOverride = null;
	}

	/*
	 * Update the app locale to avoid having to restart if relying on the app context to get resources
	 */
	public static void updateAppContextLocale(Context context, String lang) {
		Configuration config = new Configuration();
		if (!TextUtils.isEmpty(lang)) {
			config.locale = new Locale(lang);
		} else {
			config.locale = Locale.getDefault();
		}
		context.getResources().updateConfiguration(config, null);
	}

	/*
	 * Returns the height of the status bar (showing battery or network status) on top of the screen
	 * DEPRECATED: use ViewCompat.setOnApplyWindowInsetsListener() on Lollipop+
	 */
	@Deprecated
	public static int getStatusBarHeight(Context context) {
		int result = 0;
		int resourceId = context.getResources().getIdentifier("status_bar_height", "dimen", "android");
		if (resourceId > 0) {
			result = context.getResources().getDimensionPixelSize(resourceId);
		}
		return result;
	}

	/*
	 * Returns the height of the navigation softkey bar at the bottom of some devices
	 * DEPRECATED: use ViewCompat.setOnApplyWindowInsetsListener() on Lollipop+
	 */
	@Deprecated
	public static int getNavigationBarHeight(Activity activity) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && activity.isInMultiWindowMode()) {
			return 0;
		}

		NavigationBarDimensions dimensions = new NavigationBarDimensions();

		dimensions = getNavigationBarDimensions(activity.getWindowManager(), dimensions);

		return dimensions.height;
	}

	@Deprecated
	public static NavigationBarDimensions getNavigationBarDimensions(WindowManager windowManager, NavigationBarDimensions dimensions) {
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
		return dimensions;
	}

	public static int getUsableWidth(WindowManager windowManager) {
		DisplayMetrics metrics = new DisplayMetrics();
		windowManager.getDefaultDisplay().getMetrics(metrics);

		return metrics.widthPixels;
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
		}
		catch (PackageManager.NameNotFoundException e) {
			logger.error("Exception", e);
		}
		return false;
	}

	public static class NavigationBarDimensions {
		public int width;
		public int height;
	}

	public static int getActionBarSize(Context context) {
		TypedValue tv = new TypedValue();
		if (context.getTheme().resolveAttribute(R.attr.actionBarSize, tv, true)) {
			return TypedValue.complexToDimensionPixelSize(tv.data, context.getResources().getDisplayMetrics());
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
		return Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
			ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED;
	}

	/**
	 * Request all possibly required permissions of Contacts group
	 * @param activity Activity context for onRequestPermissionsResult callback
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
	 * Request all possibly required permissions of Storage group
	 * @param activity Activity context for onRequestPermissionsResult callback
	 * @param requestCode request code for onRequestPermissionsResult callback
	 * @return true if permissions are already granted, false otherwise
	 */
	public static boolean requestStoragePermissions(@NonNull Activity activity, Fragment fragment, int requestCode) {
		String[] permissions = new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE};

		if (checkIfNeedsPermissionRequest(activity, permissions)) {
			requestPermissions(activity, fragment, permissions, requestCode);
			return false;
		}
		return true;
	}

	/**
	 * Request storage write permission
	 * @param activity Activity context for onRequestPermissionsResult callback
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
	 * @param activity Activity context for onRequestPermissionsResult callback
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
	 * @param activity Activity context for onRequestPermissionsResult callback
	 * @param requestCode request code for onRequestPermissionsResult callback
	 * @return true if permissions are already granted, false otherwise
	 */
	public static boolean requestAudioPermissions(@NonNull Activity activity, Fragment fragment, int requestCode) {
		final String[] permissions = new String[]{ Manifest.permission.RECORD_AUDIO };
		if (checkIfNeedsPermissionRequest(activity, permissions)) {
			requestPermissions(activity, fragment, permissions, requestCode);
			return false;
		}
		return true;
	}

	/**
	 * Asynchronously request permission required for checking for connected bluetooth devices in Android S
	 *
	 * @param activity Activity context for onRequestPermissionsResult callback
	 * @param requestCode request code for onRequestPermissionsResult callback
	 * @return true if permissions are already granted, false otherwise
	 */
	@RequiresApi(api = Build.VERSION_CODES.S)
	public static boolean requestBluetoothConnectPermissions(@NonNull AppCompatActivity activity, Fragment fragment, int requestCode, boolean showHelpDialog) {
		final String[] permissions = new String[]{ Manifest.permission.BLUETOOTH_CONNECT };
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
	 * @param activity Activity context for onRequestPermissionsResult callback
	 * @param fragment Fragment context for onRequestPermissionsResult callback
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
	public static void requestReadPhonePermission(@NonNull Activity activity,@Nullable ActivityResultLauncher<String> requestPermissionLauncher) {
		String permission = Manifest.permission.READ_PHONE_STATE;
		if (checkIfNeedsPermissionRequest(activity, new String[]{permission}) && requestPermissionLauncher != null) {
			requestPermissionLauncher.launch(permission);
		}
	}

	/**
	 * Asynchronously request camera permissions.
	 *
	 * @param activity Activity context for onRequestPermissionsResult callback
	 * @param fragment Fragment context for onRequestPermissionsResult callback
	 * @param requestCode request code for onRequestPermissionsResult callback
	 * @return true if permissions are already granted, false otherwise
	 */
	public static boolean requestCameraPermissions(@NonNull Activity activity, Fragment fragment, int requestCode) {
		String[] permissions = new String[]{ Manifest.permission.CAMERA };
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
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
			for (String permission : permissions) {
				if (ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * Show a snackbar explaining the reason why the user should enable a certain permission
	 * @param context
	 * @param parentLayout
	 * @param stringResource
	 */
	public static void showPermissionRationale(Context context, View parentLayout, @StringRes int stringResource) {
		showPermissionRationale(context, parentLayout, stringResource, null);
	}

	/**
	 * Show a snackbar explaining the reason why the user should enable a certain permission
	 * @param context
	 * @param parentLayout
	 * @param stringResource
	 * @param callback Callback for the snackbar
	 */
	public static void showPermissionRationale(
		Context context,
		@Nullable View parentLayout,
		@StringRes int stringResource,
		@Nullable BaseTransientBottomBar.BaseCallback<Snackbar> callback
	) {
		if (context == null) {
			return;
		}

		if (parentLayout == null) {
			Toast.makeText(context, context.getString(stringResource), Toast.LENGTH_LONG).show();
		} else {
			Snackbar snackbar = SnackbarUtil.make(parentLayout, context.getString(stringResource), Snackbar.LENGTH_LONG, 5);
			snackbar.setAction(R.string.menu_settings, new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
					intent.setData(Uri.parse("package:" + BuildConfig.APPLICATION_ID));
					context.startActivity(intent);
				}
			});
			if (callback != null) {
				snackbar.addCallback(callback);
			}
			snackbar.show();
		}
	}

	/**
	 * Configure navigation and status bar color and style of provided activity to look nice on all kinds of older android version.
	 * Must be called before super.onCreate(). Thanks for the mess, Google.
	 * @param activity
	 */
	public static void configureSystemBars(Activity activity) {
		@ColorInt int statusBarColor = getColorFromAttribute(activity, android.R.attr.colorBackground);
		@ColorInt int navigationBarColor = statusBarColor;
		if (Build.VERSION.SDK_INT < O_MR1) {
			int activityTheme;
			try {
				activityTheme = activity.getPackageManager().getActivityInfo(activity.getComponentName(), 0).theme;
			} catch (Exception e) {
				logger.error("Exception", e);
				return;
			}

			if (ConfigUtils.isTheDarkSide(activity)) {
				if (activityTheme != R.style.Theme_Threema_TransparentStatusbar) {
					activity.getWindow().addFlags(FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
					statusBarColor = Color.BLACK;
					if (Build.VERSION.SDK_INT == Build.VERSION_CODES.M) {
						View decorView = activity.getWindow().getDecorView();
						decorView.setSystemUiVisibility(FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS & ~SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
					}
				}
			} else {
				if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
					navigationBarColor = Color.BLACK;
					if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.LOLLIPOP_MR1) {
						statusBarColor = Color.BLACK;
					} else if (Build.VERSION.SDK_INT == Build.VERSION_CODES.M) {
						View decorView = activity.getWindow().getDecorView();
						decorView.setSystemUiVisibility(FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS | SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
					}
				} else if (activityTheme != R.style.Theme_Threema_MediaViewer && activityTheme != R.style.Theme_Threema_Transparent_Background) {
					View decorView = activity.getWindow().getDecorView();
					decorView.setSystemUiVisibility(FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS |
						SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR | SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
				}
			}
		}

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
			WindowManager.LayoutParams params = activity.getWindow().getAttributes();
			params.layoutInDisplayCutoutMode = LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
		}

		activity.getWindow().setStatusBarColor(statusBarColor);
		activity.getWindow().setNavigationBarColor(navigationBarColor);
	}

	public static void configureTransparentStatusBar(AppCompatActivity activity) {
		activity.getWindow().setStatusBarColor(activity.getResources().getColor(R.color.status_bar_detail));
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
			activity.getWindow().getDecorView().setSystemUiVisibility(
				activity.getWindow().getDecorView().getSystemUiVisibility() & ~SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
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
	 * @param packageName
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
	 * Configure menu to display icons and dividers. Call this in onCreateOptionsMenu()
	 * @param context Context - required for theming, set to null if you want the icon color not to be touched
	 * @param menu Menu to configure
	 */
	@SuppressLint("RestrictedApi")
	public static void addIconsToOverflowMenu(@Nullable Context context, @NonNull Menu menu) {
		MenuCompat.setGroupDividerEnabled(menu, true);

		try {
			// restricted API
			if (menu instanceof MenuBuilder) {
				MenuBuilder menuBuilder = (MenuBuilder) menu;
				menuBuilder.setOptionalIconsVisible(true);

				if (context != null) {
					ConfigUtils.tintMenu(menu, ConfigUtils.getColorFromAttribute(context, R.attr.colorOnSurface));
				}
			}
		} catch (Exception ignored) {}
	}

	/**
	 * Return whether or not to use Threema Push, based on the build flavor and the preferences.
	 */
	public static boolean useThreemaPush(@NonNull PreferenceService preferenceService) {
		return BuildFlavor.forceThreemaPush() || preferenceService.useThreemaPush();
	}

	/**
	 * Return whether or not to use Threema Push, based on the build flavor and the preferences.
	 */
	public static boolean useThreemaPush(@NonNull SharedPreferences rawSharedPreferences, @NonNull Context context) {
		return BuildFlavor.forceThreemaPush()
			|| rawSharedPreferences.getBoolean(context.getString(R.string.preferences__threema_push_switch), false);
	}


	/**
	 * Return whether to show border around qr code to indicate its purpose
	 * @return true if borders are to be shown, false otherwise
	 */
	public static boolean showQRCodeTypeBorders() {
		return false;
	}

	/**
	 * Apply operations to content provider in small batches preventing TransactionTooLargeException
	 * @param authority Authority
	 * @param contentProviderOperations Operations to apply in smaller batches
	 * @throws OperationApplicationException
	 * @throws RemoteException
	 */
	public static void applyToContentResolverInBatches(@NonNull String authority, ArrayList<ContentProviderOperation> contentProviderOperations) throws OperationApplicationException, RemoteException {
		ContentResolver contentResolver = ThreemaApplication.getAppContext().getContentResolver();

		for (int i = 0; i < contentProviderOperations.size(); i += CONTENT_PROVIDER_BATCH_SIZE) {
			List<ContentProviderOperation> contentProviderOperationsBatch = contentProviderOperations.subList(i, Math.min(contentProviderOperations.size(), i + CONTENT_PROVIDER_BATCH_SIZE));
			contentResolver.applyBatch(authority, new ArrayList<>(contentProviderOperationsBatch));
		}
	}

	/**
	 * Return whether forward security features should be enabled.
	 */
	public static boolean isForwardSecurityEnabled() {
		return BuildConfig.FORWARD_SECURITY;
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
	 * @param context A context
	 * @param searchBar The search bar
	 */
	public static void adjustSearchBarTextViewMargin(@NonNull Context context, @NonNull SearchBar searchBar) {
		TextView searchBarTextView = searchBar.findViewById(R.id.search_bar_text_view);
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
}

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
import android.content.pm.ActivityInfo;
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
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.AttrRes;
import androidx.annotation.ColorInt;
import androidx.annotation.DrawableRes;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.appcompat.view.menu.MenuBuilder;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.MenuCompat;
import androidx.fragment.app.Fragment;
import androidx.preference.Preference;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceManager;

import com.datatheorem.android.trustkit.TrustKit;
import com.google.android.material.snackbar.BaseTransientBottomBar;
import com.google.android.material.snackbar.Snackbar;

import org.slf4j.Logger;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
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
import ch.threema.app.backuprestore.csv.BackupService;
import ch.threema.app.backuprestore.csv.RestoreService;
import ch.threema.app.dialogs.SimpleStringAlertDialog;
import ch.threema.app.exceptions.FileSystemNotPresentException;
import ch.threema.app.managers.ServiceManager;
import ch.threema.app.notifications.NotificationBuilderWrapper;
import ch.threema.app.services.AppRestrictionService;
import ch.threema.app.services.LockAppService;
import ch.threema.app.services.PreferenceService;
import ch.threema.app.services.license.LicenseService;
import ch.threema.app.threemasafe.ThreemaSafeConfigureActivity;
import ch.threema.base.utils.LoggingUtil;

import static android.content.res.Configuration.UI_MODE_NIGHT_YES;
import static android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
import static android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
import static android.view.WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS;
import static android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
import static ch.threema.app.ThreemaApplication.getAppContext;
import static ch.threema.app.camera.CameraUtil.isInternalCameraSupported;
import static ch.threema.app.services.NotificationService.NOTIFICATION_CHANNEL_ALERT;
import static ch.threema.app.services.NotificationServiceImpl.APP_RESTART_NOTIFICATION_ID;
import static ch.threema.app.utils.IntentDataUtil.PENDING_INTENT_FLAG_MUTABLE;

public class ConfigUtils {
	private static final Logger logger = LoggingUtil.getThreemaLogger("ConfigUtils");

	public static final int THEME_LIGHT = 0;
	public static final int THEME_DARK = 1;
	public static final int THEME_SYSTEM = 2;
	public static final int THEME_NONE = -1;
	private static final int CONTENT_PROVIDER_BATCH_SIZE = 50;

	@Retention(RetentionPolicy.SOURCE)
	@IntDef({THEME_LIGHT, THEME_DARK})
	public @interface AppTheme {}

	public static final int EMOJI_DEFAULT = 0;
	public static final int EMOJI_ANDROID = 1;

	private static int appTheme = THEME_NONE;
	private static String localeOverride = null;
	private static Integer primaryColor = null, accentColor = null, miuiVersion = null;
	private static int emojiStyle = 0;
	private static Boolean isTablet = null, isBiggerSingleEmojis = null, hasMapLibreSupport = null;
	private static int preferredThumbnailWidth = -1, preferredAudioMessageWidth = -1;

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

	public static boolean isBlackBerry() {
		String osName = System.getProperty("os.name");

		return osName != null && osName.equalsIgnoreCase("qnx");
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
					} else if (version.startsWith("V12") || version.startsWith("V13")) {
						miuiVersion = 12;
					}
				}
			} catch (Exception ignored) { }
		}
		return miuiVersion;
	}

	public static int getAppTheme(Context context) {
		if (appTheme == THEME_NONE) {
			appTheme = Integer.valueOf(PreferenceManager.getDefaultSharedPreferences(context).getString(context.getString(R.string.preferences__theme), "2"));
		}
		if (appTheme == THEME_SYSTEM) {
			appTheme = (context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == UI_MODE_NIGHT_YES ? THEME_DARK : THEME_LIGHT;
		}
		return appTheme;
	}

	public static void setAppTheme(int theme) {
		appTheme = theme;
		primaryColor = null;
	}

	public static void resetAppTheme() {
		appTheme = THEME_NONE;
		primaryColor = null;
	}

	private static void setPrimaryColor(Context context) {
		if (primaryColor == null) {
			primaryColor = getColorFromAttribute(context, R.attr.textColorPrimary);
		}
	}

	public static @ColorInt int getColorFromAttribute(Context context, @AttrRes int attr) {
		TypedArray typedArray = context.getTheme().obtainStyledAttributes(new int[] { attr });
		@ColorInt int color = typedArray.getColor(0, -1);

		typedArray.recycle();

		return color;
	}

	public static @ColorInt int getPrimaryColor() {
		return primaryColor != null ? primaryColor : 0xFFFFFFFF;
	}

	public static Drawable getThemedDrawable(Context context, @DrawableRes int resId) {
		Drawable drawable = AppCompatResources.getDrawable(context, resId);

		if (drawable != null) {
			if (appTheme != THEME_LIGHT) {
				setPrimaryColor(context);

				drawable.setColorFilter(primaryColor, PorterDuff.Mode.SRC_IN);
				return drawable;
			} else {
				drawable.clearColorFilter();
			}
		}
		return drawable;
	}

	public static void themeImageView(Context context, ImageView view) {
		if (appTheme != THEME_LIGHT) {
			if (context == null) {
				return;
			}

			setPrimaryColor(context);

			view.setColorFilter(primaryColor, PorterDuff.Mode.SRC_IN);
		} else {
			view.clearColorFilter();
		}
	}

	public static void themeMenu(Menu menu, @ColorInt int color) {
		for (int i = 0, size = menu.size(); i < size; i++) {
			final MenuItem menuItem = menu.getItem(i);
			themeMenuItem(menuItem, color);
			if (menuItem.hasSubMenu()) {
				final SubMenu subMenu = menuItem.getSubMenu();
				for (int j = 0; j < subMenu.size(); j++) {
					themeMenuItem(subMenu.getItem(j), color);
				}
			}
		}
	}

	public static void themeMenuItem(final MenuItem menuItem, @ColorInt int color) {
		if (menuItem != null) {
			final Drawable drawable = menuItem.getIcon();
			if (drawable != null) {
				drawable.mutate();
				drawable.setColorFilter(color, PorterDuff.Mode.SRC_IN);
			}
		}
	}

	public static void setEmojiStyle(Context context, int newStyle) {
		if (newStyle != -1) {
			emojiStyle = newStyle;
		} else {
			if (BuildFlavor.isLibre()) {
				emojiStyle = EMOJI_ANDROID;
				return;
			}
			emojiStyle = Integer.valueOf(
				PreferenceManager.getDefaultSharedPreferences(context).
					getString(context.getString(R.string.preferences__emoji_style),
						"0"));
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
	 * Get user-facing application version string without alpha/beta version suffix
	 * @param context
	 * @return version string
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
	 * Get full user-facing application version string including alpha/beta version suffix
	 * Deprecated! use getAppVersion()
	 * @param context
	 * @return version string
	 */
	@Deprecated
	public static String getFullAppVersion(@NonNull Context context) {
		return getAppVersion(context);
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
			if (ConfigUtils.isWorkRestricted()) {
				AppRestrictionService appRestrictionService = AppRestrictionService.getInstance();
				if (appRestrictionService != null) {
					final StringBuilder mdmBuilder = new StringBuilder();
					if (appRestrictionService.hasThreemaMDMRestrictions()) {
						mdmBuilder.append("m");
					}
					if (appRestrictionService.hasExternalMDMRestrictions()) {
						mdmBuilder.append("e");
					}

					if (mdmBuilder.length() > 0) {
						info.append(mdmBuilder).append("/");
					}
				}
			}
		}
		info.append(Build.MANUFACTURER).append(";")
			.append(Build.MODEL).append("/")
			.append(Build.VERSION.RELEASE).append("/")
			.append(BuildFlavor.getName());
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
		String theme = ConfigUtils.getAppTheme(context) == ConfigUtils.THEME_DARK ? "dark" : "light";

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
				LicenseService licenseService = serviceManager.getLicenseService();
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

	public static @ColorInt int getAccentColor(Context context) {
		if (accentColor == null) {
			resetAccentColor(context);
		}
		return accentColor;
	}

	public static void resetAccentColor(Context context) {
		TypedArray a = context.getTheme().obtainStyledAttributes(new int[]{R.attr.colorAccent});
		accentColor = a.getColor(0, 0);
		a.recycle();
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
	 * Configure activity and status bar based on user selected theme. Must be called before super.onCreate()
	 * @param activity
	 */

	public static void configureActivityTheme(Activity activity) {
		configureActivityTheme(activity, THEME_NONE);
	}

	public static void configureActivityTheme(Activity activity, int themeOverride) {
		int orgTheme = 0;

		try {
			orgTheme = activity.getPackageManager().getActivityInfo(activity.getComponentName(), 0).theme;
		} catch (Exception e) {
			logger.error("Exception", e);
		}

		int desiredTheme = themeOverride == THEME_NONE ? getAppTheme(activity) : themeOverride;

		if (desiredTheme == ConfigUtils.THEME_DARK) {
			int newTheme;

			switch (orgTheme) {
				case R.style.AppBaseTheme:
					newTheme = R.style.AppBaseTheme_Dark;
					break;
				case R.style.Theme_Threema_WithToolbarAndCheck:
					newTheme = R.style.Theme_Threema_WithToolbarAndCheck_Dark;
					break;
				case R.style.Theme_Threema_TransparentStatusbar:
					newTheme = R.style.Theme_Threema_TransparentStatusbar_Dark;
					break;
				case R.style.Theme_Threema_Translucent:
					newTheme = R.style.Theme_Threema_Translucent_Dark;
					break;
				case R.style.Theme_Threema_VoiceRecorder:
					newTheme = R.style.Theme_Threema_VoiceRecorder_Dark;
					break;
				case R.style.Theme_LocationPicker:
					newTheme = R.style.Theme_LocationPicker_Dark;
					break;
				case R.style.Theme_MediaAttacher:
					newTheme = R.style.Theme_MediaAttacher_Dark;
					break;
				case R.style.Theme_Threema_WhatsNew:
					newTheme = R.style.Theme_Threema_WhatsNew_Dark;
					break;
				case R.style.Theme_Threema_WithToolbar_NoAnim:
					newTheme = R.style.Theme_Threema_WithToolbar_NoAnim_Dark;
					break;
				case R.style.Theme_Threema_BiometricUnlock:
					newTheme = R.style.Theme_Threema_BiometricUnlock_Dark;
					break;
				case R.style.Theme_Threema_NoActionBar:
				case R.style.Theme_Threema_LowProfile:
				case R.style.Theme_Threema_Transparent_Background:
				case R.style.Theme_Threema_MediaViewer:
					// agnostic themes: leave them alone
					newTheme = orgTheme;
					break;
				default:
					newTheme = R.style.Theme_Threema_WithToolbar_Dark;
					break;
			}

			if (newTheme != orgTheme) {
				activity.setTheme(newTheme);
			}

			if (orgTheme != R.style.Theme_Threema_TransparentStatusbar) {
				activity.getWindow().addFlags(FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
				activity.getWindow().setStatusBarColor(Color.BLACK);
				if (Build.VERSION.SDK_INT == Build.VERSION_CODES.M) {
					View decorView = activity.getWindow().getDecorView();
					decorView.setSystemUiVisibility(FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS & ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
				}
			}
		} else {
			if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
				activity.getWindow().setNavigationBarColor(Color.BLACK);
				if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.LOLLIPOP_MR1) {
					activity.getWindow().setStatusBarColor(Color.BLACK);
				} else if (Build.VERSION.SDK_INT == Build.VERSION_CODES.M) {
					View decorView = activity.getWindow().getDecorView();
					decorView.setSystemUiVisibility(FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS | SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
				}
			} else if (Build.VERSION.SDK_INT == Build.VERSION_CODES.O && (orgTheme != R.style.Theme_Threema_MediaViewer && orgTheme != R.style.Theme_Threema_Transparent_Background)) {
				View decorView = activity.getWindow().getDecorView();
				decorView.setSystemUiVisibility(FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS |
					SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR | SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
			}
		}

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
			WindowManager.LayoutParams params = activity.getWindow().getAttributes();
			params.layoutInDisplayCutoutMode = LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
		}
	}

	public static void configureTransparentStatusBar(AppCompatActivity activity) {
		activity.getWindow().setStatusBarColor(activity.getResources().getColor(R.color.status_bar_detail));
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
			activity.getWindow().getDecorView().setSystemUiVisibility(
				activity.getWindow().getDecorView().getSystemUiVisibility() & ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
		}
	}

	private static void tintPrefIcons(Preference preference, int color) {
		if (preference != null) {
			if (preference instanceof PreferenceGroup) {
				PreferenceGroup group = ((PreferenceGroup) preference);
				for (int i = 0; i < group.getPreferenceCount(); i++) {
					tintPrefIcons(group.getPreference(i), color);
				}
			} else {
				Drawable icon = preference.getIcon();
				if (icon != null) {
					icon.setColorFilter(color, PorterDuff.Mode.SRC_IN);
				}
			}
		}
	}

	public static void tintPreferencesIcons(Context context, Preference preference) {
		tintPrefIcons(preference, getColorFromAttribute(context, R.attr.textColorSecondary));
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

	public static int getCurrentScreenOrientation(Activity activity) {
		int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
		DisplayMetrics dm = new DisplayMetrics();
		activity.getWindowManager().getDefaultDisplay().getMetrics(dm);
		int width = dm.widthPixels;
		int height = dm.heightPixels;
		int orientation;
		// if the device's natural orientation is portrait:
		if ((rotation == Surface.ROTATION_0
			|| rotation == Surface.ROTATION_180) && height > width ||
			(rotation == Surface.ROTATION_90
				|| rotation == Surface.ROTATION_270) && width > height) {
			switch(rotation) {
				case Surface.ROTATION_0:
					orientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
					break;
				case Surface.ROTATION_90:
					orientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
					break;
				case Surface.ROTATION_180:
					orientation =
						ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT;
					break;
				case Surface.ROTATION_270:
					orientation =
						ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE;
					break;
				default:
					orientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
					break;
			}
		}
		// if the device's natural orientation is landscape or if the device
		// is square:
		else {
			switch(rotation) {
				case Surface.ROTATION_0:
					orientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
					break;
				case Surface.ROTATION_90:
					orientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
					break;
				case Surface.ROTATION_180:
					orientation =
						ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE;
					break;
				case Surface.ROTATION_270:
					orientation =
						ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT;
					break;
				default:
					orientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
					break;
			}
		}

		return orientation;
	}

	/**
	 * Set app theme according to device theme if theme setting is set to "system"
	 * @param context
	 * @return
	 */
	public static boolean refreshDeviceTheme(Context context) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
			if (!BackupService.isRunning() && !RestoreService.isRunning()) {
				SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getAppContext());
				int themeIndex = Integer.parseInt(prefs.getString(context.getResources().getString(R.string.preferences__theme), String.valueOf(THEME_LIGHT)));
				if (themeIndex == THEME_SYSTEM) {
					int newTheme = (context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == UI_MODE_NIGHT_YES ? THEME_DARK : THEME_LIGHT;
					int oldTheme = ConfigUtils.getAppTheme(context);

					if (oldTheme != newTheme) {
						ConfigUtils.setAppTheme(newTheme);
						return true;
					}
				}
			}
		}
		return false;
	}

	/**
	 * Request desired orientation ignoring IllegalStateException on API 26 with targetApi == 28.
	 * Workaround for Android bug https://issuetracker.google.com/issues/68454482
	 * @param activity activity to request orientation for
	 * @param requestedOrientation requested orientation
	 */
	public static void setRequestedOrientation(@NonNull Activity activity, int requestedOrientation) {
		try {
			activity.setRequestedOrientation(requestedOrientation);
		} catch (IllegalStateException ignore) {}
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
	 * @param context Context - required for themeing, set to null if you want the icon color not to be touched
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
					ConfigUtils.themeMenu(menu, ConfigUtils.getColorFromAttribute(context, R.attr.textColorSecondary));
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
}

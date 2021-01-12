/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2018-2021 Threema GmbH
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

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import androidx.fragment.app.Fragment;

import java.util.List;

import ch.threema.app.activities.DisableBatteryOptimizationsActivity;

public class PowermanagerUtil {
	// https://stackoverflow.com/questions/48166206/how-to-start-power-manager-of-all-android-manufactures-to-enable-push-notificati/48166241
	// https://stackoverflow.com/questions/31638986/protected-apps-setting-on-huawei-phones-and-how-to-handle-it

	private static final Intent[] POWERMANAGER_INTENTS = {
			new Intent().setComponent(new ComponentName("com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity")),
			new Intent().setComponent(new ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity")),
			new Intent().setComponent(new ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity")),
			new Intent().setComponent(new ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager")),
			new Intent().setComponent(new ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity")),
			new Intent("miui.intent.action.POWER_HIDE_MODE_APP_LIST").addCategory(Intent.CATEGORY_DEFAULT)
	};

	private static final Intent[] AUTOSTART_INTENTS = {
			new Intent().setComponent(new ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")),
			new Intent().setComponent(new ComponentName("com.letv.android.letvsafe", "com.letv.android.letvsafe.AutobootManageActivity")),
			new Intent().setComponent(new ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity")),
			new Intent().setComponent(new ComponentName("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity")),
			new Intent().setComponent(new ComponentName("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity")),
			new Intent().setComponent(new ComponentName("com.asus.mobilemanager", "com.asus.mobilemanager.MainActivity")),
			new Intent("miui.intent.action.OP_AUTO_START").addCategory(Intent.CATEGORY_DEFAULT),
	};

	public static final int RESULT_DISABLE_POWERMANAGER = 661;
	public static final int RESULT_DISABLE_AUTOSTART = 662;

	/*
	public static boolean hasProtectedApps(Context context) {
		Intent intent = new Intent();
		intent.setClassName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity");
		return isCallable(context, intent);
	}
*/
	private static boolean isCallable(Context context, Intent intent) {
		List<ResolveInfo> list = context.getPackageManager().queryIntentActivities(intent,
			PackageManager.MATCH_DEFAULT_ONLY);
		return list.size() > 0;
/*
		return (context.getPackageManager().resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) != null);
*/
	}

	private static Intent getPowermanagerIntent(Context context) {
		for (Intent intent : POWERMANAGER_INTENTS) {
			if (isCallable(context, intent)) {
				return intent;
			}
		}
		return null;
	}

	private static Intent getAutostartIntent(Context context) {
		for (Intent intent : AUTOSTART_INTENTS) {
			if (isCallable(context, intent)) {
				return intent;
			}
		}
		return null;
	}

	public static void callPowerManager(Fragment fragment) {
		Intent intent = getPowermanagerIntent(fragment.getActivity());
		fragment.startActivityForResult(intent, RESULT_DISABLE_POWERMANAGER);
	}

	public static void callAutostartManager(Fragment fragment) {
		Intent intent = getAutostartIntent(fragment.getActivity());
		fragment.startActivityForResult(intent, RESULT_DISABLE_AUTOSTART);
	}

	public static boolean hasPowerManagerOption(Context context) {
		return getPowermanagerIntent(context) != null;
	}

	public static boolean hasAutostartOption(Context context) {
		return getAutostartIntent(context) != null;
	}

	public static boolean needsFixing(Context context) {
		return !DisableBatteryOptimizationsActivity.isWhitelisted(context) || hasAutostartOption(context) || hasPowerManagerOption(context);
	}
}

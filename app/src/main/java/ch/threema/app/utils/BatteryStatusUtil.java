package ch.threema.app.utils;

import android.content.Intent;
import android.os.BatteryManager;

import androidx.annotation.Nullable;

public class BatteryStatusUtil {

    /**
     * Return whether the device is charging or not.
     *
     * @param intent An intent from a battery status broadcast.
     */
    @Nullable
    public static Boolean isCharging(Intent intent) {
        final int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        if (status == -1) {
            return null;
        }
        return status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL;
    }

    @Nullable
    public static Integer getPercent(Intent intent) {
        final int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        final int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        if (level == -1 || scale == -1) {
            return null;
        }
        return level * 100 / scale;
    }

}

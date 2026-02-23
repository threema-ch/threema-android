package ch.threema.app.webclient.converter;

import androidx.annotation.AnyThread;

@AnyThread
public class BatteryStatus extends Converter {
    private final static String PERCENT = "percent";
    private final static String IS_CHARGING = "isCharging";

    public static MsgpackObjectBuilder convert(int percent, boolean isCharging) {
        MsgpackObjectBuilder builder = new MsgpackObjectBuilder();
        builder.put(PERCENT, percent);
        builder.put(IS_CHARGING, isCharging);
        return builder;
    }
}

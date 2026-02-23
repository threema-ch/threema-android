package ch.threema.app.webclient.converter;

import androidx.annotation.AnyThread;
import androidx.annotation.Nullable;

// Currently not yet used until we decided on a proper message format
@AnyThread
public class AppLogo extends Converter {
    private final static String LIGHT = "light";
    private final static String DARK = "dark";

    @Nullable
    public static MsgpackObjectBuilder convert(@Nullable String logoLight, @Nullable String logoDark) {
        if (logoLight == null && logoDark == null) {
            return null;
        }
        MsgpackObjectBuilder builder = new MsgpackObjectBuilder();
        builder.maybePut(LIGHT, logoLight);
        builder.maybePut(DARK, logoDark);
        return builder;
    }
}

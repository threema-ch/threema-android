package ch.threema.app.utils;

import android.content.Context;
import android.view.View;

import androidx.annotation.ColorInt;
import androidx.annotation.UiThread;
import ch.threema.app.R;
import ch.threema.domain.protocol.connection.ConnectionState;

public class ConnectionIndicatorUtil {
    private static ConnectionIndicatorUtil ourInstance;
    private final @ColorInt int red, orange, transparent;

    public static ConnectionIndicatorUtil getInstance() {
        return ourInstance;
    }

    public static void init(Context context) {
        ConnectionIndicatorUtil.ourInstance = new ConnectionIndicatorUtil(context);
    }

    private ConnectionIndicatorUtil(Context context) {
        this.red = context.getResources().getColor(R.color.material_red);
        this.orange = context.getResources().getColor(R.color.material_orange);
        this.transparent = context.getResources().getColor(android.R.color.transparent);
    }

    @UiThread
    public void updateConnectionIndicator(View connectionIndicator, ConnectionState connectionState) {
        if (connectionIndicator != null) {
            if (connectionState == ConnectionState.CONNECTED) {
                connectionIndicator.setBackgroundColor(this.orange);
            } else if (connectionState == ConnectionState.LOGGEDIN) {
                connectionIndicator.setBackgroundColor(this.transparent);
            } else {
                connectionIndicator.setBackgroundColor(this.red);
            }
            connectionIndicator.invalidate();
        }
    }
}

package ch.threema.app.utils;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

import ch.threema.app.ThreemaApplication;

public class NetworkUtil {

    public static boolean isOnline() {
        ConnectivityManager cm = (ConnectivityManager) ThreemaApplication.getAppContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm != null) {
            NetworkInfo netInfo = cm.getActiveNetworkInfo();
            return netInfo != null
                && netInfo.isConnected();
        }
        return false;
    }

}

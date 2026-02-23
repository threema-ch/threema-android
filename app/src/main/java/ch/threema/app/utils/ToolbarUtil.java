package ch.threema.app.utils;

import android.view.View;

public class ToolbarUtil {

    public static boolean getMenuItemCenterPosition(View toolbar, int itemId, int[] location) {
        if (toolbar != null) {
            View itemView = toolbar.findViewById(itemId);
            if (itemView != null) {
                itemView.getLocationInWindow(location);
            } else {
                location[1] = toolbar.getHeight() / 2;
                location[0] = toolbar.getWidth() - toolbar.getHeight();
            }
            return true;
        }
        return false;
    }
}

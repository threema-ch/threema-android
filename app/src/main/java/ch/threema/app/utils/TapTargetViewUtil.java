package ch.threema.app.utils;

import android.app.Activity;
import android.view.ViewGroup;

import com.getkeepsafe.taptargetview.TapTarget;
import com.getkeepsafe.taptargetview.TapTargetView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class TapTargetViewUtil {
    /**
     * Replaces {@link TapTargetView#showFor(Activity, TapTarget, TapTargetView.Listener)}
     * so we can set the correct boundingParent which may have different insets than (ViewGroup) decor.findViewById(android.R.id.content)
     *
     * @param activity       current activity
     * @param target         {@link TapTarget} to highlight
     * @param listener       Optional. The {@link TapTargetView.Listener} instance for this view
     * @param boundingParent Optional. Will be used to calculate boundaries if needed. For example,
     *                       if your view is added to the decor view of your Window, then you want
     *                       to adjust for system ui like the navigation bar or status bar, and so
     *                       you would pass in the boundingParent view (which doesn't include system ui)
     *                       here.
     * @return new instance of shown TapTargetView
     */
    @NonNull
    public static TapTargetView showFor(@NonNull Activity activity, @NonNull TapTarget target, @Nullable TapTargetView.Listener listener, @Nullable ViewGroup boundingParent) {
        final ViewGroup decor = (ViewGroup) activity.getWindow().getDecorView();
        final ViewGroup.LayoutParams layoutParams = new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);

        final TapTargetView tapTargetView = new TapTargetView(activity, decor, boundingParent, target, listener);
        decor.addView(tapTargetView, layoutParams);

        return tapTargetView;
    }
}

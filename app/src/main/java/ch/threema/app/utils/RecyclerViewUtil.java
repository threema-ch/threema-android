package ch.threema.app.utils;

import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.core.util.Consumer;
import ch.threema.app.R;
import ch.threema.app.ui.ThumbDatePopupBackground;

public class RecyclerViewUtil {
    /**
     * Style for a popup displayed next to a thumbscroller
     */
    public static final Consumer<TextView> thumbScrollerPopupStyle = popupView -> {
        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams)
            popupView.getLayoutParams();
        layoutParams.gravity = Gravity.CENTER;
        layoutParams.setMarginEnd(20);
        popupView.setLayoutParams(layoutParams);
        popupView.setBackground(new ThumbDatePopupBackground(popupView.getContext()));
        popupView.setElevation(6);
        popupView.setEllipsize(TextUtils.TruncateAt.END);
        popupView.setGravity(Gravity.CENTER);
        popupView.setIncludeFontPadding(false);
        popupView.setSingleLine(true);
        popupView.setTextColor(ConfigUtils.getColorFromAttribute(popupView.getContext(), R.attr.colorOnSecondaryContainer));
        popupView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
    };
}

package ch.threema.app.utils;

import android.view.View;
import android.widget.TextView;

import com.google.android.material.snackbar.Snackbar;

import androidx.annotation.NonNull;
import ch.threema.app.R;

public class SnackbarUtil {
    @NonNull
    public static Snackbar make(View parentLayout, String text, int length, int maxLines) {
        final Snackbar snackbar = Snackbar.make(parentLayout, text, length);
        snackbar.setBackgroundTint(
            ConfigUtils.getColorFromAttribute(snackbar.getContext(), R.attr.colorSurfaceContainerHigh)
        );
        TextView textView = snackbar.getView().findViewById(com.google.android.material.R.id.snackbar_text);
        if (textView != null) {
            textView.setMaxLines(maxLines);
            textView.setTextColor(
                ConfigUtils.getColorFromAttribute(snackbar.getContext(), R.attr.colorOnSurface)
            );
        }
        return snackbar;
    }
}

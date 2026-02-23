package ch.threema.app.utils;

import android.content.Context;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.inputmethod.InputMethodManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;

public class EditTextUtil {
    @UiThread
    public static void focusWindowAndShowSoftKeyboard(@Nullable View view) {
        if (view == null) {
            return;
        }
        focusWindow(view, () -> {
            if (view.isFocused()) {
                // post in case window focus changed, but InputMethodManager is not setup yet.
                view.post(() -> showSoftKeyboard(view));
            }
        });
    }

    @UiThread
    public static void showSoftKeyboard(@Nullable View view) {
        if (view == null) {
            return;
        }
        InputMethodManager inputManager = (InputMethodManager) view.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        inputManager.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT);
    }

    @UiThread
    public static void hideSoftKeyboard(@Nullable View view) {
        if (view == null) {
            return;
        }
        InputMethodManager imm = (InputMethodManager) view.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (!imm.isActive()) {
            return;
        }
        imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }


    private static void focusWindow(@NonNull View view, Runnable onFocus) {
        view.requestFocus();
        if (view.hasWindowFocus()) {
            onFocus.run();
        } else {
            // window needs to have focus to show keyboard
            view.getViewTreeObserver().addOnWindowFocusChangeListener(new ViewTreeObserver.OnWindowFocusChangeListener() {
                @Override
                public void onWindowFocusChanged(boolean hasFocus) {
                    if (hasFocus) {
                        onFocus.run();
                        view.getViewTreeObserver().removeOnWindowFocusChangeListener(this);
                    }
                }
            });
        }
    }
}

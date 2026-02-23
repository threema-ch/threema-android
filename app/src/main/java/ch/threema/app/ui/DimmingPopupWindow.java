package ch.threema.app.ui;

import android.content.Context;
import android.os.Build;
import android.view.View;
import android.view.WindowManager;
import android.widget.PopupWindow;

import java.lang.ref.WeakReference;

import androidx.annotation.Nullable;

public abstract class DimmingPopupWindow extends PopupWindow {
    private final WeakReference<Context> contextRef;

    protected DimmingPopupWindow(Context context) {
        super(context);
        contextRef = new WeakReference<>(context);
    }

    protected void dimBackground() {
        if (getContext() == null) {
            return;
        }
        View container = (View) getContentView().getParent().getParent();
        if (container != null) {
            WindowManager wm = (WindowManager) getContext().getSystemService(Context.WINDOW_SERVICE);
            WindowManager.LayoutParams p = (WindowManager.LayoutParams) container.getLayoutParams();

            if (p != null && wm != null) {
                p.flags = WindowManager.LayoutParams.FLAG_DIM_BEHIND;
                p.dimAmount = 0.6f;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    p.flags |= WindowManager.LayoutParams.FLAG_BLUR_BEHIND;
                    p.setBlurBehindRadius(20);
                }
                wm.updateViewLayout(container, p);
            }
        }
    }

    @Nullable
    protected Context getContext() {
        return this.contextRef.get();
    }
}

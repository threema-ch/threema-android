package ch.threema.app.ui;

import android.content.Context;
import android.os.Handler;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ScrollView;


/* ScrollView that behaves correctly in full screen activities upon resize (i.e. when opening the soft keyboard) */
/* Fixes https://code.google.com/p/android/issues/detail?id=5497 */

public class ResizingScrollView extends ScrollView {

    public ResizingScrollView(Context context) {
        super(context);
    }

    public ResizingScrollView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ResizingScrollView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        // called when size of view changes (e.g. soft keyboard appears or screen is rotated)
        super.onSizeChanged(w, h, oldw, oldh);

        if (h < oldh) {
            new Handler().post(new Runnable() {
                @Override
                public void run() {
                    fullScroll(View.FOCUS_DOWN);
                }
            });
        }
    }
}

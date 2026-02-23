package ch.threema.app.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.Checkable;

public class CheckableView extends View implements Checkable {
    private boolean checked = false;
    private static final int[] CHECKED_STATE_SET = {android.R.attr.state_checked};

    public CheckableView(Context context) {
        super(context);
    }

    public CheckableView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public CheckableView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public int[] onCreateDrawableState(int extraSpace) {
        final int[] drawableState = super.onCreateDrawableState(extraSpace + 1);
        if (isChecked())
            mergeDrawableStates(drawableState, CHECKED_STATE_SET);
        return drawableState;
    }

    @Override
    public boolean isChecked() {
        return checked;
    }

    @Override
    public void setChecked(boolean _checked) {
        checked = _checked;
        refreshDrawableState();
    }

    @Override
    public void toggle() {
        setChecked(!checked);
    }
}

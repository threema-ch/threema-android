package ch.threema.app.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.Checkable;

import androidx.constraintlayout.widget.ConstraintLayout;

public class CheckableConstraintLayout extends ConstraintLayout implements Checkable {
    private boolean checked = false;
    private CheckableConstraintLayout.OnCheckedChangeListener onCheckedChangeListener;
    private static final int[] CHECKED_STATE_SET = {android.R.attr.state_checked};

    public interface OnCheckedChangeListener {
        /**
         * Called when the checked state of the checkable has changed.
         *
         * @param checkableView The view whose state has changed.
         * @param isChecked     The new checked state of checkableView.
         */
        void onCheckedChanged(CheckableConstraintLayout checkableView, boolean isChecked);
    }

    public CheckableConstraintLayout(Context context) {
        super(context);
    }

    public CheckableConstraintLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public CheckableConstraintLayout(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    protected int[] onCreateDrawableState(int extraSpace) {
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

        if (onCheckedChangeListener != null) {
            onCheckedChangeListener.onCheckedChanged(this, checked);
        }
    }

    @Override
    public void toggle() {
        setChecked(!checked);
    }

    /**
     * Register a callback to be invoked when the checked state of this view changes.
     *
     * @param listener the callback to call on checked state change
     */
    public void setOnCheckedChangeListener(CheckableConstraintLayout.OnCheckedChangeListener listener) {
        onCheckedChangeListener = listener;
    }
}

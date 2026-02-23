package ch.threema.app.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.TextView;

import com.google.android.material.materialswitch.MaterialSwitch;

import ch.threema.app.R;

/**
 * Add setCheckedSilent() to Switch to prevent listener from firing when there's no user interaction
 */

public class SilentSwitchCompat extends MaterialSwitch {
    private OnCheckedChangeListener listener = null;
    private TextView label = null;

    public SilentSwitchCompat(Context context) {
        super(context);
    }

    public SilentSwitchCompat(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public SilentSwitchCompat(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public void setOnCheckedChangeListener(OnCheckedChangeListener listener) {
        super.setOnCheckedChangeListener(listener);
        this.listener = listener;
    }

    public void setCheckedSilent(boolean checked) {
        OnCheckedChangeListener tmpListener = this.listener;
        setOnCheckedChangeListener(null);
        setChecked(checked);
        setOnCheckedChangeListener(tmpListener);
    }

    @Override
    public void setChecked(boolean checked) {
        super.setChecked(checked);
        if (label != null) {
            label.setText(checked ? R.string.on_cap : R.string.off_cap);
        }
    }

    public void setOnOffLabel(TextView textView) {
        label = textView;
    }
}

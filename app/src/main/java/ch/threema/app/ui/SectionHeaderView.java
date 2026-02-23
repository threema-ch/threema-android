package ch.threema.app.ui;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.widget.LinearLayout;
import android.widget.TextView;

import ch.threema.app.R;
import ch.threema.app.utils.TestUtil;

public class SectionHeaderView extends LinearLayout {
    private TextView textView;

    public SectionHeaderView(Context context) {
        super(context);
        this.init(null);
    }

    public SectionHeaderView(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.init(attrs);
    }

    public SectionHeaderView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        this.init(attrs);
    }

    private void init(AttributeSet attrs) {
        inflate(getContext(), R.layout.header_section, this);
        this.textView = this.findViewById(R.id.text);

        if (attrs != null) {
            TypedArray a = this.getContext()
                .obtainStyledAttributes(attrs, R.styleable.SectionHeaderView);

            if (a != null) {
                if (this.textView != null) {
                    String text = a.getString(R.styleable.SectionHeaderView_android_text);
                    this.setText(text);
                }
                a.recycle();
            }
        }
    }

    public void setText(String text) {
        if (this.textView != null) {
            if (!TestUtil.isEmptyOrNull(text)) {
                this.textView.setText(text);
            } else {
                this.textView.setText("");
            }
        }
    }
}

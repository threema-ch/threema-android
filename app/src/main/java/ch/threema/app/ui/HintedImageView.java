package ch.threema.app.ui;

import android.content.Context;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.Toast;

public class HintedImageView extends androidx.appcompat.widget.AppCompatImageView implements View.OnClickListener {

    private OnClickListener onClickListener;

    public HintedImageView(Context context) {
        super(context);

        setOnClickListener(this);
    }

    public HintedImageView(Context context, AttributeSet attrs) {
        super(context, attrs);

        setOnClickListener(this);
    }

    public HintedImageView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    public void setOnClickListener(OnClickListener l) {
        if (l == this) {
            super.setOnClickListener(l);
            this.onClickListener = l;
        }
    }

    @Override
    public void onClick(View v) {
        if (this.onClickListener != null) {
            handleClick();
        }
    }

    private void handleClick() {
        if (getContentDescription() != null && getContext() != null) {
            String contentDesc = getContentDescription().toString();
            if (!TextUtils.isEmpty(contentDesc)) {
                int[] pos = new int[2];
                getLocationInWindow(pos);
                SingleToast.getInstance().text(contentDesc, Toast.LENGTH_SHORT, Gravity.TOP | Gravity.LEFT, pos[0] - ((contentDesc.length() / 2) * 12), pos[1] - 128);
            }
        }
    }
}

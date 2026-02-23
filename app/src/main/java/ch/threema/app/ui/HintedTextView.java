package ch.threema.app.ui;

import android.content.Context;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.Toast;

import ch.threema.app.emojis.EmojiConversationTextView;

public class HintedTextView extends EmojiConversationTextView implements View.OnClickListener {
    private View.OnClickListener onClickListener;
    private Toast toaster = null;

    public HintedTextView(Context context) {
        super(context);

        setOnClickListener(this);
    }

    public HintedTextView(Context context, AttributeSet attrs) {
        super(context, attrs);

        setOnClickListener(this);
    }

    public HintedTextView(Context context, AttributeSet attrs, int defStyle) {
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
        if (getText() != null && getContext() != null) {
            String text = getText().toString();
            if (!TextUtils.isEmpty(text)) {
                if (this.toaster != null) {
                    this.toaster.cancel();
                }

                int[] pos = new int[2];
                getLocationInWindow(pos);

                this.toaster = Toast.makeText(getContext(), text, Toast.LENGTH_SHORT);
                this.toaster.setGravity(Gravity.TOP | Gravity.LEFT, pos[0] - ((text.length() / 2) * 12), pos[1] - 128);
                this.toaster.show();
            }
        }
    }
}

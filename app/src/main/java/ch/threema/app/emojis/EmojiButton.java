package ch.threema.app.emojis;

import android.content.Context;
import android.util.AttributeSet;

import androidx.appcompat.widget.AppCompatImageButton;
import ch.threema.app.R;
import ch.threema.app.utils.ConfigUtils;

public class EmojiButton extends AppCompatImageButton implements EmojiPicker.EmojiPickerListener {
    private Context context;

    public EmojiButton(Context context) {
        this(context, null);
    }

    public EmojiButton(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public EmojiButton(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        this.context = context;
        showEmojiIcon();
    }

    public void showEmojiIcon() {
        setImageResource(R.drawable.ic_tag_faces_outline);
    }

    public void showKeyboardIcon() {
        if (ConfigUtils.isLandscape(context) && !ConfigUtils.isTabletLayout()) {
            setImageResource(R.drawable.ic_keyboard_arrow_down_outline);
        } else {
            setImageResource(R.drawable.ic_keyboard_outline);
        }
    }

    public void attach(EmojiPicker emojiPicker) {
        emojiPicker.addEmojiPickerListener(this);
    }

    public void detach(EmojiPicker emojiPicker) {
        if (emojiPicker != null) {
            emojiPicker.removeEmojiPickerListener(this);
        }
    }

    @Override
    public void onEmojiPickerOpen() {
        showKeyboardIcon();
    }

    @Override
    public void onEmojiPickerClose() {
        showEmojiIcon();
    }
}

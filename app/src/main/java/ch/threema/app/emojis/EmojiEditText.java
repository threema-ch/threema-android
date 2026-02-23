package ch.threema.app.emojis;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import ch.threema.app.ui.SimpleTextWatcher;
import ch.threema.app.ui.ThreemaEditText;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.TestUtil;
import ch.threema.base.utils.Utils;

public class EmojiEditText extends ThreemaEditText {

    protected Context appContext;
    protected CharSequence hint;
    private String currentText;
    private int maxByteSize;

    public EmojiEditText(Context context) {
        super(context);

        init2(context);
    }

    public EmojiEditText(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        init2(context);
    }

    public EmojiEditText(Context context, AttributeSet attrs) {
        super(context, attrs);

        init2(context);
    }

    private void init2(Context context) {
        this.appContext = context.getApplicationContext();
        this.hint = getHint();
        this.currentText = "";
        this.maxByteSize = 0;

        if (ConfigUtils.isDefaultEmojiStyle()) {
            setFilters(appendEmojiFilter(this.getFilters()));
        }
    }

    /**
     * Add our EmojiFilter as the first item to the array of existing InputFilters
     *
     * @param originalFilters
     * @return Array of filters
     */
    private InputFilter[] appendEmojiFilter(@Nullable InputFilter[] originalFilters) {
        InputFilter[] result;

        if (originalFilters != null) {
            result = new InputFilter[originalFilters.length + 1];
            System.arraycopy(originalFilters, 0, result, 1, originalFilters.length);
        } else {
            result = new InputFilter[1];
        }
        result[0] = new EmojiFilter(this);

        return result;
    }

    /**
     * Add single emoji at the current cursor position
     *
     * @param emojiCodeString
     */
    public void addEmoji(String emojiCodeString) {
        final int start = getSelectionStart();
        final int end = getSelectionEnd();

        // fix reverse selections
        getText().replace(Math.min(start, end), Math.max(start, end), emojiCodeString);

        final int newEnd = start + emojiCodeString.length();
        if (newEnd <= length()) {
            // move cursor after newly inserted emoji. it may be that nothing was inserted because of filters
            setSelection(newEnd);
        }
    }

    /**
     * Callback called by invalidateSelf of EmojiDrawable
     *
     * @param drawable
     */
    @Override
    public void invalidateDrawable(@NonNull Drawable drawable) {
        if (drawable instanceof EmojiDrawable) {
            /* setHint() invalidates the view while invalidate() does not */
            setHint(this.hint);
        } else {
            super.invalidateDrawable(drawable);
        }
    }

    /**
     * Limit input size to maxByteSize by not allowing any input that exceeds the value thus keeping multi-byte characters intact
     *
     * @param maxByteSize Maximum input size in byte
     */
    public void setMaxByteSize(int maxByteSize) {
        removeTextChangedListener(textLengthWatcher);
        if (maxByteSize > 0) {
            addTextChangedListener(textLengthWatcher);
        }
        this.maxByteSize = maxByteSize;
    }


    private final TextWatcher textLengthWatcher = new SimpleTextWatcher() {
        @Override
        public void afterTextChanged(@NonNull Editable editable) {
            String text = editable.toString();
            String cropped = Utils.truncateUTF8String(text, maxByteSize);

            if (!TestUtil.compare(text, cropped)) {
                setText(currentText);
                setSelection(currentText.length());
            } else {
                currentText = text;
            }
        }
    };
}

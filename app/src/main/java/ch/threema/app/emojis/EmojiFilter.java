package ch.threema.app.emojis;

import android.text.InputFilter;
import android.text.Spannable;
import android.text.Spanned;
import android.text.TextUtils;
import android.widget.EditText;

/**
 * InputFilter to replace Emojis in EditText
 */
public class EmojiFilter implements InputFilter {
    private EditText editText;

    public EmojiFilter(EditText editText) {
        this.editText = editText;
    }

    @Override
    public CharSequence filter(CharSequence source, int start, int end, Spanned dest, int dstart, int dend) {
        Spannable spannable = (Spannable) EmojiMarkupUtil.getInstance().addTextSpans(editText.getContext(), source, editText, true);
        if (source instanceof Spanned && spannable != null) {
            TextUtils.copySpansFrom((Spanned) source, start, end, null, spannable, 0);
        }

        return spannable;
    }
}

package ch.threema.app.emojis;

import android.graphics.Paint;
import android.graphics.Paint.FontMetricsInt;

import androidx.annotation.NonNull;

import android.text.style.ImageSpan;
import android.widget.TextView;

public class EmojiImageSpan extends ImageSpan {
    private final int size, scale;
    private final FontMetricsInt fm;

    public EmojiImageSpan(@NonNull EmojiDrawable drawable, @NonNull TextView tv, int scale) {
        super(drawable);
        drawable.setCallback(tv);
        this.scale = scale;

        fm = tv.getPaint().getFontMetricsInt();
        size = fm != null ? (Math.abs(fm.descent) + Math.abs(fm.ascent)) * scale : 64 * scale;
        getDrawable().setBounds(0, 0, size, size);
    }

    @Override
    public int getSize(@NonNull Paint paint, CharSequence text, int start, int end, FontMetricsInt fm) {
        if (fm != null && this.fm != null) {
            fm.ascent = this.fm.ascent * scale;
            fm.descent = this.fm.descent * scale;
            fm.top = this.fm.top * scale;
            fm.bottom = this.fm.bottom * scale;
            return size;
        } else {
            return super.getSize(paint, text, start, end, fm);
        }
    }
}

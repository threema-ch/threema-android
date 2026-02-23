package ch.threema.app.ui;

import android.text.style.ClickableSpan;
import android.view.View;

import org.slf4j.Logger;

import androidx.annotation.NonNull;
import static ch.threema.base.utils.LoggingKt.getThreemaLogger;

public class MentionClickableSpan extends ClickableSpan {
    private static final Logger logger = getThreemaLogger("MentionClickableSpan");
    private String text;

    public MentionClickableSpan(String text) {
        super();
        this.text = text;
    }

    public String getText() {
        return this.text;
    }

    @Override
    public void onClick(@NonNull View widget) {
        logger.debug("onClick");
    }
}

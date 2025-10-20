/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2018-2025 Threema GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package ch.threema.app.ui;

import android.text.Editable;
import android.text.style.ReplacementSpan;
import android.widget.EditText;

import org.slf4j.Logger;

import java.util.concurrent.CopyOnWriteArrayList;

import androidx.annotation.NonNull;
import ch.threema.base.utils.LoggingUtil;

public class MentionTextWatcher extends SimpleTextWatcher {
    private static final Logger logger = LoggingUtil.getThreemaLogger("MentionTextWatcher");

    private final EditText editText;
    private final CharSequence hint;
    private int maxLines;
    private final CopyOnWriteArrayList<ReplacementSpan> spansToRemove = new CopyOnWriteArrayList<>();

    public MentionTextWatcher(EditText editor) {
        editText = editor;

        hint = editText.getHint();
        maxLines = editText.getMaxLines();

        editText.addTextChangedListener(this);
    }

    @Override
    public void beforeTextChanged(@NonNull CharSequence text, int start, int count, int after) {
        if (count == 1) {
            int end = start + count;
            Editable editableText = editText.getEditableText();
            if (editableText == null) {
                logger.error("Before text changed: Editable of edit text is null (text is not editable)");
                return;
            }
            ReplacementSpan[] list = editableText.getSpans(start, end, ReplacementSpan.class);

            for (ReplacementSpan span : list) {
                int spanStart = editableText.getSpanStart(span);
                int spanEnd = editableText.getSpanEnd(span);
                if ((spanStart < end) && (spanEnd > start)) {
                    spansToRemove.add(span);
                }
            }
        }
    }

    @Override
    public void afterTextChanged(@NonNull Editable editable) {
        Editable editableText = editText.getEditableText();

        if (editableText == null) {
            logger.error("After text changed: Editable of edit text is null (text is not editable)");
            return;
        }

        for (ReplacementSpan span : spansToRemove) {
            int start = editableText.getSpanStart(span);
            int end = editableText.getSpanEnd(span);

            editableText.removeSpan(span);

            if (start != end) {
                editableText.delete(start, end);
            }
        }
        spansToRemove.clear();

        // workaround to keep hint ellipsized on the first line
        if (editable.length() > 0) {
            editText.setHint(null);
            editText.setMaxLines(maxLines);
        } else {
            editText.setMaxLines(1);
            editText.setHint(this.hint);
        }
    }

    public void setMaxLines(int maxLines) {
        this.maxLines = maxLines;
    }
}

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

import android.content.Context;
import android.text.Editable;
import android.text.Selection;
import android.util.AttributeSet;

import androidx.annotation.NonNull;

public class PrefixEditText extends ThreemaTextInputEditText {
    String prefix = "";

    public PrefixEditText(Context context) {
        super(context, null);
        init();
    }

    public PrefixEditText(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public PrefixEditText(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        prefix = (String) getTag();

        Selection.setSelection(getText(), getText().length());

        addTextChangedListener(new SimpleTextWatcher() {
            @Override
            public void afterTextChanged(@NonNull Editable editable) {
                if (prefix != null && editable.toString().startsWith(prefix + prefix)) {
                    setText(editable.subSequence(prefix.length(), editable.length()));
                    setSelection(editable.length() - prefix.length());
                } else {
                    if (!editable.toString().startsWith(prefix)) {
                        String cleanString;
                        String deletedPrefix = prefix.substring(0, prefix.length() - 1);
                        if (editable.toString().startsWith(deletedPrefix)) {
                            cleanString = editable.toString().replaceAll(deletedPrefix, "");
                        } else {
                            cleanString = editable.toString().replaceAll(prefix, "");
                        }
                        setText(prefix + cleanString);
                        setSelection(prefix.length());
                    }
                }
            }
        });
    }

    @Override
    public void setText(CharSequence text, BufferType type) {
        if (text != null && prefix != null && !text.toString().startsWith(prefix)) {
            text = prefix + text;
        }
        super.setText(text, type);
    }

    @Override
    protected void onSelectionChanged(int selStart, int selEnd) {
        CharSequence text = getText();
        if (prefix != null && text != null) {
            if (selEnd < selStart) {
                setSelection(text.length(), text.length());
                return;
            } else if (text.length() >= prefix.length()) {
                if (selStart <= prefix.length()) {
                    if (selEnd <= prefix.length()) {
                        setSelection(prefix.length(), prefix.length());
                        return;
                    } else if (selEnd > prefix.length()) {
                        setSelection(prefix.length(), selEnd);
                        return;
                    }
                }
            }
        }

        super.onSelectionChanged(selStart, selEnd);
    }
}


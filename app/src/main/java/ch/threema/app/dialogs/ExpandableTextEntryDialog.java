/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2016-2025 Threema GmbH
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

package ch.threema.app.dialogs;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputLayout;

import org.slf4j.Logger;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDialog;
import ch.threema.app.R;
import ch.threema.app.services.ActivityService;
import ch.threema.app.ui.ComposeEditText;
import ch.threema.app.ui.SimpleTextWatcher;
import ch.threema.app.utils.AnimationUtil;
import ch.threema.app.utils.TestUtil;
import ch.threema.base.utils.LoggingUtil;

import static ch.threema.app.utils.ActiveScreenLoggerKt.logScreenVisibility;

public class ExpandableTextEntryDialog extends ThreemaDialogFragment {
    private static final Logger logger = LoggingUtil.getThreemaLogger("ExpandableTextEntryDialog");
    private ExpandableTextEntryDialogClickListener callback;
    private Activity activity;
    private ComposeEditText captionEditText;
    private ComposeEditText.MentionPopupData mentionPopupData;
    private AlertDialog alertDialog;

    public static ExpandableTextEntryDialog newInstance(String title, int hint, int positive, int negative, boolean expandable) {
        ExpandableTextEntryDialog dialog = new ExpandableTextEntryDialog();
        Bundle args = new Bundle();
        args.putString("title", title);
        args.putInt("message", hint);
        args.putInt("positive", positive);
        args.putInt("negative", negative);
        args.putBoolean("expandable", expandable);

        dialog.setArguments(args);
        return dialog;
    }

    public static ExpandableTextEntryDialog newInstance(String title, int hint, String preset, int positive, int negative, boolean expandable) {
        ExpandableTextEntryDialog dialog = new ExpandableTextEntryDialog();
        Bundle args = new Bundle();
        args.putString("title", title);
        args.putString("preset", preset);
        args.putInt("message", hint);
        args.putInt("positive", positive);
        args.putInt("negative", negative);
        args.putBoolean("expandable", expandable);

        dialog.setArguments(args);
        return dialog;
    }

    public static ExpandableTextEntryDialog newInstance(String title, String subtitle, int hint, String preset, int positive, int negative, boolean expandable) {
        ExpandableTextEntryDialog dialog = new ExpandableTextEntryDialog();
        Bundle args = new Bundle();
        args.putString("title", title);
        args.putString("subtitle", subtitle);
        args.putString("preset", preset);
        args.putInt("message", hint);
        args.putInt("positive", positive);
        args.putInt("negative", negative);
        args.putBoolean("expandable", expandable);

        dialog.setArguments(args);
        return dialog;
    }

    public interface ExpandableTextEntryDialogClickListener {
        void onYes(String tag, Object data, String text);

        void onNo(String tag);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        logScreenVisibility(this, logger);

        if (callback == null) {
            try {
                callback = (ExpandableTextEntryDialogClickListener) getTargetFragment();
            } catch (ClassCastException e) {
                //
            }

            // called from an activity rather than a fragment
            if (callback == null) {
                if (activity instanceof ExpandableTextEntryDialogClickListener) {
                    callback = (ExpandableTextEntryDialogClickListener) activity;
                }
            }
        }
    }

    @Override
    public void onAttach(@NonNull Activity activity) {
        super.onAttach(activity);

        this.activity = activity;
    }

    @NonNull
    @Override
    public AppCompatDialog onCreateDialog(Bundle savedInstanceState) {
        String title = getArguments().getString("title");
        String subtitle = getArguments().getString("subtitle");
        String preset = getArguments().getString("preset", null);
        int message = getArguments().getInt("message");
        int positive = getArguments().getInt("positive");
        int negative = getArguments().getInt("negative");
        boolean expandable = getArguments().getBoolean("expandable");

        final String tag = this.getTag();

        final View dialogView = activity.getLayoutInflater().inflate(R.layout.dialog_text_entry_expandable, null);

        final ComposeEditText editText = dialogView.findViewById(R.id.caption_edittext);
        final TextInputLayout editTextContainer = dialogView.findViewById(R.id.edittext_container);
        final TextView addCaptionText = dialogView.findViewById(R.id.add_caption_text);
        final TextView subtitleText = dialogView.findViewById(R.id.subtitle_text);
        final ImageView expandButton = dialogView.findViewById(R.id.expand_button);
        final LinearLayout addCaptionLayout = dialogView.findViewById(R.id.add_caption_intro);

        addCaptionLayout.setClickable(true);
        addCaptionLayout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleLayout(expandButton, editTextContainer);
            }
        });

        editText.addTextChangedListener(new SimpleTextWatcher() {
            @Override
            public void onTextChanged(@NonNull CharSequence text, int start, int before, int count) {
                ActivityService.activityUserInteract(activity);
            }
        });

        if (mentionPopupData != null) {
            this.captionEditText = editText;
            this.captionEditText.enableMentionPopup(mentionPopupData, editTextContainer);
            editText.setOnKeyListener((v, keyCode, event) -> {
                if (keyCode == KeyEvent.KEYCODE_BACK) {
                    ExpandableTextEntryDialog.this.captionEditText.dismissMentionPopup();
                    return true;
                }
                return false;
            });
        }

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(activity, getTheme());
        builder.setView(dialogView);

        if (!TestUtil.isEmptyOrNull(title)) {
            builder.setTitle(title);
        }

        if (!TestUtil.isEmptyOrNull(subtitle)) {
            subtitleText.setText(subtitle);
        } else {
            subtitleText.setVisibility(View.GONE);
        }

        if (message != 0) {
            addCaptionText.setText(message);
        }

        if (!TestUtil.isEmptyOrNull(preset)) {
            editText.setText(preset);
            if (expandable) {
                toggleLayout(expandButton, editTextContainer);
            }
        }

        if (!expandable) {
            addCaptionLayout.setVisibility(View.GONE);
        }

        builder.setPositiveButton(getString(positive), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int whichButton) {
                    callback.onYes(tag, object, editText.getText().toString());
                }
            }
        );
        builder.setNegativeButton(getString(negative), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int whichButton) {
                    callback.onNo(tag);
                }
            }
        );
        alertDialog = builder.create();
        setCancelable(false);

        return alertDialog;
    }

    private void toggleLayout(ImageView button, View v) {
        InputMethodManager imm = (InputMethodManager) v.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        EditText editText = v.findViewById(R.id.caption_edittext);

        if (v.isShown()) {
            AnimationUtil.slideUp(activity, v);
            v.setVisibility(View.GONE);
            button.setRotation(0);
            if (imm != null && editText != null) {
                imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
            }
        } else {
            v.setVisibility(View.VISIBLE);
            AnimationUtil.slideDown(activity, v, () -> {
                if (editText != null) {
                    editText.requestFocus();
                    if (imm != null) {
                        imm.showSoftInput(editText, 0);
                    }
                }
            });
            button.setRotation(90);
        }
    }
}

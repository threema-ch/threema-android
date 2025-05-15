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

package ch.threema.app.dialogs;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;

import org.slf4j.Logger;

import ch.threema.app.R;
import ch.threema.base.utils.LoggingUtil;

import static ch.threema.app.utils.ActiveScreenLoggerKt.logScreenVisibility;

/**
 * A dialog with a title and a checkbox
 */
public class TextWithCheckboxDialog extends ThreemaDialogFragment {
    private static final Logger logger = LoggingUtil.getThreemaLogger("TextWithCheckboxDialog");
    private TextWithCheckboxDialogClickListener callback;
    private Activity activity;

    private final static String TITLE_KEY = "title";
    private final static String MESSAGE_KEY = "message";
    private final static String MESSAGE_RES_KEY = "messageRes";
    private final static String CHECKBOX_LABEL_KEY = "checkboxLabel";
    private final static String POSITIVE_KEY = "positive";
    private final static String NEGATIVE_KEY = "negative";
    private static final String ARG_ICON = "icon";


    public interface TextWithCheckboxDialogClickListener {
        void onYes(String tag, Object data, boolean checked);
    }

    public static TextWithCheckboxDialog newInstance(String title, @DrawableRes int icon, @NonNull String message, @StringRes int checkboxLabel, @StringRes int positive, @StringRes int negative) {
        TextWithCheckboxDialog dialog = new TextWithCheckboxDialog();
        Bundle args = new Bundle();
        args.putString(TITLE_KEY, title);
        args.putString(MESSAGE_KEY, message);
        args.putInt(CHECKBOX_LABEL_KEY, checkboxLabel);
        args.putInt(POSITIVE_KEY, positive);
        args.putInt(NEGATIVE_KEY, negative);
        args.putInt(ARG_ICON, icon);

        dialog.setArguments(args);
        return dialog;
    }

    public static TextWithCheckboxDialog newInstance(String title, @StringRes int messageRes, @StringRes int checkboxLabel, @StringRes int positive, @StringRes int negative) {
        TextWithCheckboxDialog dialog = new TextWithCheckboxDialog();
        Bundle args = new Bundle();
        args.putString(TITLE_KEY, title);
        args.putInt(MESSAGE_RES_KEY, messageRes);
        args.putInt(CHECKBOX_LABEL_KEY, checkboxLabel);
        args.putInt(POSITIVE_KEY, positive);
        args.putInt(NEGATIVE_KEY, negative);

        dialog.setArguments(args);
        return dialog;
    }

    public static TextWithCheckboxDialog newInstance(String title, String message, @StringRes int checkboxLabel, @StringRes int positive, @StringRes int negative) {
        TextWithCheckboxDialog dialog = new TextWithCheckboxDialog();
        Bundle args = new Bundle();
        args.putString(TITLE_KEY, title);
        args.putString(MESSAGE_KEY, message);
        args.putInt(CHECKBOX_LABEL_KEY, checkboxLabel);
        args.putInt(POSITIVE_KEY, positive);
        args.putInt(NEGATIVE_KEY, negative);

        dialog.setArguments(args);
        return dialog;
    }

    @Override
    public void onAttach(@NonNull Activity activity) {
        super.onAttach(activity);

        this.activity = activity;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        logScreenVisibility(this, logger);

        if (callback == null) {
            try {
                callback = (TextWithCheckboxDialogClickListener) getTargetFragment();
            } catch (ClassCastException e) {
                //
            }

            // called from an activity rather than a fragment
            if (callback == null) {
                if (activity instanceof TextWithCheckboxDialogClickListener) {
                    callback = (TextWithCheckboxDialogClickListener) activity;
                }
            }
        }
    }

    /**
     * Set the callback of this dialog.
     *
     * @param clickListener the listener
     */
    public void setCallback(TextWithCheckboxDialogClickListener clickListener) {
        callback = clickListener;
    }

    @NonNull
    @Override
    public AppCompatDialog onCreateDialog(Bundle savedInstanceState) {
        String title = getArguments().getString(TITLE_KEY);
        String message = getArguments().getString(MESSAGE_KEY);
        int messageRes = getArguments().getInt(MESSAGE_RES_KEY, 0);
        @StringRes int checkboxLabel = getArguments().getInt(CHECKBOX_LABEL_KEY);
        @StringRes int positive = getArguments().getInt(POSITIVE_KEY);
        @StringRes int negative = getArguments().getInt(NEGATIVE_KEY);
        @DrawableRes int icon = getArguments().getInt(ARG_ICON, 0);

        final View dialogView = activity.getLayoutInflater().inflate(R.layout.dialog_text_with_checkbox, null);
        final MaterialSwitch checkbox = dialogView.findViewById(R.id.checkbox);
        final String tag = this.getTag();

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(getActivity(), getTheme())
            .setTitle(title != null ? title : message)
            .setCancelable(false)
            .setNegativeButton(negative, null)
            .setPositiveButton(positive, (dialog, which) -> callback.onYes(tag, object, checkbox.isChecked()));

        if (icon != 0) {
            builder.setIcon(icon);
        }

        if (messageRes != 0) {
            builder.setMessage(messageRes);
        } else if (message != null) {
            builder.setMessage(message);
        }

        if (checkboxLabel != 0) {
            builder.setView(dialogView);
            checkbox.setChecked(false);
            checkbox.setText(checkboxLabel);
        }

        setCancelable(false);

        return builder.create();
    }
}

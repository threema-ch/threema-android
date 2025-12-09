/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2019-2025 Threema GmbH
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
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.view.View;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputLayout;

import org.slf4j.Logger;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDialog;
import ch.threema.app.R;
import ch.threema.app.emojis.EmojiEditText;
import static ch.threema.base.utils.LoggingKt.getThreemaLogger;
import ch.threema.domain.protocol.csp.ProtocolDefines;

import static ch.threema.app.utils.ActiveScreenLoggerKt.logScreenVisibility;

public class NewContactDialog extends ThreemaDialogFragment {

    private static final Logger logger = getThreemaLogger("NewContactDialog");
    private NewContactDialogClickListener callback;
    private Activity activity;
    private AlertDialog alertDialog;

    public static NewContactDialog newInstance(@StringRes int title, @StringRes int message,
                                               @StringRes int positive, @StringRes int negative) {
        NewContactDialog dialog = new NewContactDialog();
        Bundle args = new Bundle();
        args.putInt("title", title);
        args.putInt("message", message);
        args.putInt("positive", positive);
        args.putInt("negative", negative);

        dialog.setArguments(args);
        return dialog;
    }

    public interface NewContactDialogClickListener {
        void onContactEnter(String tag, String text);

        void onCancel(String tag);

        void onScanButtonClick(String tag);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        logScreenVisibility(this, logger);

        if (callback == null) {
            try {
                callback = (NewContactDialogClickListener) getTargetFragment();
            } catch (ClassCastException e) {
                //
            }

            // called from an activity rather than a fragment
            if (callback == null) {
                if (activity instanceof NewContactDialogClickListener) {
                    callback = (NewContactDialogClickListener) activity;
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
        int title = getArguments().getInt("title");
        int message = getArguments().getInt("message");
        int positive = getArguments().getInt("positive");
        int negative = getArguments().getInt("negative");

        final String tag = this.getTag();

        final View dialogView = activity.getLayoutInflater().inflate(R.layout.dialog_new_contact, null);

        final EmojiEditText editText = dialogView.findViewById(R.id.edit_text);
        final TextInputLayout editTextLayout = dialogView.findViewById(R.id.text_input_layout);

        editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        editText.setFilters(new InputFilter[]{new InputFilter.AllCaps(), new InputFilter.LengthFilter(ProtocolDefines.IDENTITY_LEN)});

        final MaterialButton scanButton = dialogView.findViewById(R.id.scan_button);
        scanButton.setOnClickListener(v -> {
            // do not dismiss dialog
            logger.info("Scan contact clicked");
            callback.onScanButtonClick(tag);
        });

        if (message != 0) {
            editTextLayout.setHint(getString(message));
        }

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(getActivity());
        builder.setView(dialogView);

        if (title != 0) {
            builder.setTitle(title);
        }

        builder.setPositiveButton(getString(positive), (dialog, whichButton) -> {
            logger.info("Add contact confirmed");
            callback.onContactEnter(tag, editText.getText().toString());
        });
        builder.setNegativeButton(getString(negative), (dialog, whichButton) -> {
            logger.info("Adding contact cancelled");
            callback.onCancel(tag);
        });

        alertDialog = builder.create();

        return alertDialog;
    }

    @Override
    public void onCancel(@NonNull DialogInterface dialogInterface) {
        callback.onCancel(getTag());
    }

    @Override
    public void onStart() {
        super.onStart();
    }
}

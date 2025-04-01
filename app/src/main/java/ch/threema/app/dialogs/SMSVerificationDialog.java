/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2015-2025 Threema GmbH
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
import android.content.res.ColorStateList;
import android.graphics.PorterDuff;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import ch.threema.app.R;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.DialogUtil;

public class SMSVerificationDialog extends ThreemaDialogFragment {
    private static final String ARG_PHONE_NUMBER = "title";

    private SMSVerificationDialogCallback callback;
    private AlertDialog alertDialog;
    private Activity activity;
    private String tag;

    public static SMSVerificationDialog newInstance(String phoneNumber) {
        SMSVerificationDialog dialog = new SMSVerificationDialog();
        Bundle args = new Bundle();
        args.putString(ARG_PHONE_NUMBER, phoneNumber);
        dialog.setArguments(args);
        return dialog;
    }

    public interface SMSVerificationDialogCallback {
        void onYes(String tag, String code);

        void onNo(String tag);

        void onCallRequested(String tag);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        try {
            callback = (SMSVerificationDialogCallback) getTargetFragment();
        } catch (ClassCastException e) {
            //
        }

        // called from an activity rather than a fragment
        if (callback == null) {
            if (!(activity instanceof SMSVerificationDialogCallback)) {
                throw new ClassCastException("Calling fragment must implement SMSVerificationDialogCallback interface");
            }
            callback = (SMSVerificationDialogCallback) activity;
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
        String phone = getArguments().getString(ARG_PHONE_NUMBER);
        String title = String.format(getString(R.string.verification_of), phone);
        tag = this.getTag();

        final View dialogView = activity.getLayoutInflater().inflate(R.layout.dialog_sms_verification, null);
        final Button requestCallButton = dialogView.findViewById(R.id.request_call);
        requestCallButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                callback.onCallRequested(tag);
            }
        });
        if (ConfigUtils.isTheDarkSide(getContext())) {
            if (requestCallButton.getCompoundDrawables()[0] != null) {
                requestCallButton.getCompoundDrawables()[0].setColorFilter(getResources().getColor(android.R.color.white), PorterDuff.Mode.SRC_IN);
            }
        }

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(getActivity(), getTheme());
        builder.setTitle(title);
        builder.setView(dialogView);
        builder.setPositiveButton(getString(R.string.ok), new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {
                }
            }
        );
        builder.setNegativeButton(getString(R.string.cancel), new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {
                    callback.onNo(tag);
                }
            }
        );
        alertDialog = builder.create();

        return alertDialog;
    }

    @Override
    public void onCancel(@NonNull DialogInterface dialogInterface) {
        callback.onNo(tag);
    }

    @Override
    public void onStart() {
        super.onStart();

        ColorStateList colorStateList = DialogUtil.getButtonColorStateList(activity);

        alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(colorStateList);
        alertDialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(colorStateList);
        alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                EditText editText = alertDialog.findViewById(R.id.code_edittext);
                String code = editText.getText().toString();

                callback.onYes(tag, code);
            }
        });
    }
}

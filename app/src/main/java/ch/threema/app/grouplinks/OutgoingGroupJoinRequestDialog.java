/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2021-2025 Threema GmbH
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

package ch.threema.app.grouplinks;

import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.slf4j.Logger;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDialog;
import androidx.core.text.HtmlCompat;
import ch.threema.app.R;
import ch.threema.app.dialogs.ThreemaDialogFragment;
import ch.threema.app.emojis.EmojiEditText;
import ch.threema.app.ui.SimpleTextWatcher;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.domain.protocol.csp.ProtocolDefines;

import static ch.threema.app.utils.ActiveScreenLoggerKt.logScreenVisibility;

public class OutgoingGroupJoinRequestDialog extends ThreemaDialogFragment {
    private static final Logger logger = LoggingUtil.getThreemaLogger("OutgoingGroupJoinRequestDialog");

    public static final String EXTRA_GROUP_NAME = "groupName";
    public static final String EXTRA_GROUP_ADMIN = "groupAdmin";

    private OutgoingGroupJoinRequestDialogClickListener callback;
    private AlertDialog alertDialog;

    public static OutgoingGroupJoinRequestDialog newInstance(String groupName, String groupAdmin) {
        OutgoingGroupJoinRequestDialog dialog = new OutgoingGroupJoinRequestDialog();
        Bundle args = new Bundle();
        args.putString(EXTRA_GROUP_NAME, groupName);
        args.putString(EXTRA_GROUP_ADMIN, groupAdmin);
        dialog.setArguments(args);
        return dialog;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        logScreenVisibility(this, logger);
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        this.callback = (OutgoingGroupJoinRequestDialogClickListener) context;
    }

    @NonNull
    @Override
    public AppCompatDialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        String groupName = null;
        String groupAdmin = null;
        if (getArguments() != null) {
            groupName = getArguments().getString(EXTRA_GROUP_NAME);
            groupAdmin = getArguments().getString(EXTRA_GROUP_ADMIN);
        }

        final View dialogView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_new_group_join_request, null);
        final TextView infoText = dialogView.findViewById(R.id.message);
        final EmojiEditText editText = dialogView.findViewById(R.id.edit_text);

        infoText.setText(HtmlCompat.fromHtml(
            String.format(getString(R.string.group_join_request_message_info), groupAdmin, groupName),
            HtmlCompat.FROM_HTML_MODE_COMPACT));
        editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        editText.setFilters(new InputFilter[]{new InputFilter.LengthFilter(ProtocolDefines.GROUP_JOIN_MESSAGE_LEN)});
        editText.setHint(getString(R.string.group_request_hint));

        editText.addTextChangedListener(new SimpleTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                alertDialog.getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(!s.toString().trim().isEmpty());
            }
        });

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireActivity());
        builder
            .setView(dialogView)
            .setTitle(getString(R.string.group_request_send_title))
            .setPositiveButton(getString(R.string.send), (dialog, whichButton) -> {
                if (editText.getText() == null) {
                    editText.setHint(getString(R.string.group_request_message_empty));
                    editText.setHintTextColor(Color.RED);
                } else {
                    callback.onSend(editText.getText().toString());
                }
            })
            .setNegativeButton(getString(R.string.cancel), (dialog, which) -> callback.cancel());

        alertDialog = builder.create();

        return alertDialog;
    }

    @Override
    public void onStart() {
        super.onStart();
        alertDialog.getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(false);
    }

    @Override
    public void onCancel(@NonNull DialogInterface dialogInterface) {
        callback.cancel();
    }

    public interface OutgoingGroupJoinRequestDialogClickListener {
        void onSend(String message);

        void cancel();
    }
}

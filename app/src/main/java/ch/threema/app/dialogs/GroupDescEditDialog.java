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

import android.os.Bundle;
import android.view.View;
import android.widget.EditText;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.slf4j.Logger;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatDialog;
import ch.threema.app.R;
import ch.threema.app.utils.EditTextUtil;
import ch.threema.app.utils.TestUtil;
import static ch.threema.base.utils.LoggingKt.getThreemaLogger;

import static ch.threema.app.utils.ActiveScreenLoggerKt.logScreenVisibility;


public class GroupDescEditDialog extends ThreemaDialogFragment {
    private static final Logger logger = getThreemaLogger("GroupDescEditDialog");


    private static final String ARG_TITLE = "title";
    private static final String ARG_GROUP_DESC = "groupDesc";


    private OnNewGroupDescription callback;

    /**
     * Create an EditDialog for a group-description
     */
    public static GroupDescEditDialog newGroupDescriptionInstance(@StringRes int title,
                                                                  String description, OnNewGroupDescription callback) {
        final Bundle args = new Bundle();
        args.putInt(ARG_TITLE, title);
        args.putString(ARG_GROUP_DESC, description);

        GroupDescEditDialog dialog = new GroupDescEditDialog(callback);
        dialog.setArguments(args);
        return dialog;
    }

    private GroupDescEditDialog(OnNewGroupDescription callback) {
        this.callback = callback;
    }

    public interface OnNewGroupDescription {
        void onNewGroupDescSet(String newGroupDesc);
    }

    public void setCallback(OnNewGroupDescription callback) {
        this.callback = callback;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        logScreenVisibility(this, logger);
    }

    @NonNull
    @Override
    public AppCompatDialog onCreateDialog(Bundle savedInstanceState) {
        int title = getArguments().getInt(ARG_TITLE);
        String groupDesc = getArguments().getString(ARG_GROUP_DESC);

        final View dialogView = requireActivity().getLayoutInflater().inflate(R.layout.dialog_group_description_edit, null);
        final EditText groupDescEditText = dialogView.findViewById(R.id.group_desc_edit_text);


        if (!TestUtil.isEmptyOrNull(groupDesc)) {
            groupDescEditText.setText(groupDesc);
        }

        EditTextUtil.showSoftKeyboard(groupDescEditText);

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireActivity(), getTheme());

        if (title != 0) {
            builder.setTitle(title);
        }

        builder.setView(dialogView);

        builder.setPositiveButton(getString(R.string.ok), (dialog, whichButton) -> callback.onNewGroupDescSet(groupDescEditText.getText().toString().trim())
        );
        builder.setNegativeButton(getString(R.string.cancel), (dialog, whichButton) -> {
                // do nothing
            }
        );

        setCancelable(false);
        return builder.create();
    }
}

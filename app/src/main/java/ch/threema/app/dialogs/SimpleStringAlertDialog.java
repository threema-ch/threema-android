/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2014-2025 Threema GmbH
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
import android.view.View;
import android.widget.TextView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.slf4j.Logger;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDialog;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import ch.threema.app.R;
import ch.threema.app.emojis.EmojiMarkupUtil;
import ch.threema.app.utils.TestUtil;
import ch.threema.base.utils.LoggingUtil;

import static ch.threema.app.utils.ActiveScreenLoggerKt.logScreenVisibility;

public class SimpleStringAlertDialog extends ThreemaDialogFragment {
    private static final Logger logger = LoggingUtil.getThreemaLogger("SimpleStringAlertDialog");
    protected Activity activity;
    @Nullable
    private Runnable onDismissRunnable;

    public static SimpleStringAlertDialog newInstance(int title, CharSequence message) {
        SimpleStringAlertDialog dialog = new SimpleStringAlertDialog();
        Bundle args = new Bundle();
        args.putInt("title", title);
        args.putCharSequence("message", message);

        dialog.setArguments(args);
        return dialog;
    }

    public static SimpleStringAlertDialog newInstance(int title, int message) {
        SimpleStringAlertDialog dialog = new SimpleStringAlertDialog();
        Bundle args = new Bundle();
        args.putInt("title", title);
        args.putInt("messageInt", message);

        dialog.setArguments(args);
        return dialog;
    }

    public static SimpleStringAlertDialog newInstance(int title, int message, boolean noButton) {
        SimpleStringAlertDialog dialog = new SimpleStringAlertDialog();
        Bundle args = new Bundle();
        args.putInt("title", title);
        args.putInt("messageInt", message);
        args.putBoolean("noButton", noButton);

        dialog.setArguments(args);
        return dialog;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        logScreenVisibility(this, logger);
    }

    @Override
    public void onAttach(@NonNull Activity activity) {
        super.onAttach(activity);

        this.activity = activity;
    }

    @Override
    // generally allow state loss for simple string alerts
    public void show(FragmentManager manager, String tag) {
        FragmentTransaction ft = manager.beginTransaction();
        ft.add(this, tag);
        ft.commitAllowingStateLoss();
    }

    @NonNull
    @Override
    public AppCompatDialog onCreateDialog(Bundle savedInstanceState) {
        int title = getArguments().getInt("title");
        int messageInt = getArguments().getInt("messageInt");
        CharSequence message = getArguments().getCharSequence("message");
        boolean noButton = getArguments().getBoolean("noButton", false);

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(getActivity(), getTheme())
            .setCancelable(false);

        if (title != -1) {
            builder.setTitle(title);
        }

        if (!noButton) {
            builder.setPositiveButton(getString(R.string.ok), null);
        } else {
            setCancelable(false);
        }

        if (TestUtil.isBlankOrNull(message)) {
            builder.setMessage(messageInt);
        } else {
            builder.setMessage(message);
        }
        AlertDialog alertDialog = builder.create();
        alertDialog.setOnShowListener((dialog) -> applyEmojis(alertDialog));
        return alertDialog;
    }

    private void applyEmojis(AlertDialog alertDialog) {
        View messageView = alertDialog.findViewById(android.R.id.message);
        if (messageView instanceof TextView) {
            TextView textView = (TextView) messageView;
            textView.setText(
                EmojiMarkupUtil.getInstance().addTextSpans(getContext(), textView.getText(), textView, true, true, false, false)
            );
        }
    }

    public SimpleStringAlertDialog setOnDismissRunnable(Runnable onDismissRunnable) {
        this.onDismissRunnable = onDismissRunnable;
        return this;
    }

    @Override
    public void onDismiss(@NonNull DialogInterface dialog) {
        super.onDismiss(dialog);

        if (onDismissRunnable != null) {
            onDismissRunnable.run();
        }
    }
}

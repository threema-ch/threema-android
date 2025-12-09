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
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.slf4j.Logger;

import ch.threema.app.R;
import ch.threema.app.activities.wizard.components.WizardButtonStyle;
import ch.threema.app.activities.wizard.components.WizardButtonXml;
import static ch.threema.base.utils.LoggingKt.getThreemaLogger;

import static ch.threema.app.utils.ActiveScreenLoggerKt.logScreenVisibility;

public class WizardDialog extends ThreemaDialogFragment {
    private static final Logger logger = getThreemaLogger("WizardDialog");

    private static final String ARG_TITLE = "title";
    private static final String ARG_TITLE_STRING = "titleString";
    private static final String ARG_POSITIVE = "positive";
    private static final String ARG_NEGATIVE = "negative";
    private static final String ARG_HIGHLIGHT = "highlight";

    private WizardDialogCallback callback;
    private Activity activity;

    public enum Highlight {
        POSITIVE,
        NEGATIVE,
        EQUAL,
        NONE
    }

    @NonNull
    public static WizardDialog newInstance(@StringRes int title, @StringRes int positive, @StringRes int negative, Highlight highlight) {
        WizardDialog dialog = new WizardDialog();
        Bundle args = new Bundle();
        args.putInt(ARG_TITLE, title);
        args.putInt(ARG_POSITIVE, positive);
        args.putInt(ARG_NEGATIVE, negative);
        args.putSerializable(ARG_HIGHLIGHT, highlight);
        dialog.setArguments(args);
        return dialog;
    }

    @NonNull
    public static WizardDialog newInstance(@StringRes int title, @StringRes int positive) {
        WizardDialog dialog = new WizardDialog();
        Bundle args = new Bundle();
        args.putInt(ARG_TITLE, title);
        args.putInt(ARG_POSITIVE, positive);
        dialog.setArguments(args);
        return dialog;
    }

    @NonNull
    public static WizardDialog newInstance(@NonNull String title, @StringRes int positive) {
        WizardDialog dialog = new WizardDialog();
        Bundle args = new Bundle();
        args.putString(ARG_TITLE_STRING, title);
        args.putInt(ARG_POSITIVE, positive);
        dialog.setArguments(args);
        return dialog;
    }

    public interface WizardDialogCallback {
        void onYes(String tag, Object data);

        void onNo(String tag);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        logScreenVisibility(this, logger);

        try {
            callback = (WizardDialogCallback) getTargetFragment();
        } catch (ClassCastException e) {
            //
        }

        // called from an activity rather than a fragment
        if (callback == null) {
            if (!(activity instanceof WizardDialogCallback)) {
                throw new ClassCastException("Calling fragment must implement WizardDialogCallback interface");
            }
            callback = (WizardDialogCallback) activity;
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
        final @StringRes int titleResOrZero = getArguments().getInt(ARG_TITLE, 0);
        final @Nullable String titleString = getArguments().getString(ARG_TITLE_STRING);

        final Highlight highlight = (Highlight) getArguments().getSerializable(ARG_HIGHLIGHT);

        final View dialogView = activity.getLayoutInflater().inflate(R.layout.dialog_wizard, null);
        final TextView titleText = dialogView.findViewById(R.id.wizard_dialog_title);

        if (titleResOrZero != 0) {
            titleText.setText(titleResOrZero);
        } else {
            titleText.setText(titleString);
        }
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireActivity(), R.style.Threema_Dialog_Wizard);
        builder.setView(dialogView);

        final WizardButtonXml positiveButtonCompose = dialogView.findViewById(R.id.wizard_yes_compose);
        final WizardButtonXml negativeButtonCompose = dialogView.findViewById(R.id.wizard_no_compose);

        positiveButtonCompose.setOnClickListener(v -> {
            dismiss();
            callback.onYes(getTag(), object);
        });
        negativeButtonCompose.setOnClickListener(v -> {
            dismiss();
            callback.onNo(getTag());
        });

        @StringRes final int positiveButtonResOrZero = getArguments().getInt(ARG_POSITIVE, 0);
        @StringRes final int negativeButtonResOrZero = getArguments().getInt(ARG_NEGATIVE, 0);

        if (positiveButtonResOrZero != 0) {
            positiveButtonCompose.setText(getString(positiveButtonResOrZero));
        } else {
            positiveButtonCompose.setVisibility(View.GONE);
        }

        if (negativeButtonResOrZero != 0) {
            negativeButtonCompose.setText(getString(negativeButtonResOrZero));
        } else {
            negativeButtonCompose.setVisibility(View.GONE);
        }

        if (highlight != null) {
            switch (highlight) {
                case NONE:
                    positiveButtonCompose.setStyle(WizardButtonStyle.INVERSE);
                    negativeButtonCompose.setStyle(WizardButtonStyle.INVERSE);
                    break;
                case EQUAL:
                    positiveButtonCompose.setStyle(WizardButtonStyle.DEFAULT);
                    negativeButtonCompose.setStyle(WizardButtonStyle.DEFAULT);
                    break;
                case NEGATIVE:
                    positiveButtonCompose.setStyle(WizardButtonStyle.INVERSE);
                    negativeButtonCompose.setStyle(WizardButtonStyle.DEFAULT);
                    break;
                case POSITIVE:
                default:
                    positiveButtonCompose.setStyle(WizardButtonStyle.DEFAULT);
                    negativeButtonCompose.setStyle(WizardButtonStyle.INVERSE);
            }
        } else {
            positiveButtonCompose.setStyle(WizardButtonStyle.DEFAULT);
            negativeButtonCompose.setStyle(WizardButtonStyle.INVERSE);
        }

        setCancelable(false);

        return builder.create();
    }

    @Override
    public void onCancel(DialogInterface dialogInterface) {
        callback.onNo(getTag());
    }
}
